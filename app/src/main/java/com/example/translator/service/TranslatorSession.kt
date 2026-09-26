package com.example.translator.service

import android.content.Context
import com.example.translator.data.TranslatorStateHolder
import com.example.translator.di.PinyinModule
import com.example.translator.di.TranslationModule
import com.example.translator.pinyin.PinyinService
import com.example.translator.speech.RecognitionDiagnostics
import com.example.translator.speech.SpeechRecognitionManager
import com.example.translator.speech.TextToSpeechManager
import com.example.translator.translation.TargetLanguage
import com.example.translator.translation.TranslationException
import com.example.translator.translation.TranslationService
import com.example.translator.viewmodel.ConversationEntry
import com.example.translator.viewmodel.TranslatorSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs the listen → recognize → translate → speak loop inside the foreground
 * service so translation continues with the screen off.
 */
class TranslatorSession(
    context: Context,
    private val scope: CoroutineScope,
    private val onStateChanged: () -> Unit = {},
    private val translationService: TranslationService = TranslationModule.createTranslationService { progress ->
        TranslatorStateHolder.update { it.copy(modelDownloadProgress = progress) }
    },
    private val pinyinService: PinyinService = PinyinModule.createPinyinService(),
    speechFactory: (Context) -> SpeechRecognitionManager = ::SpeechRecognitionManager,
    ttsFactory: (Context, (String) -> Unit) -> TextToSpeechManager = { ctx, onError ->
        TextToSpeechManager(ctx, onError)
    },
) {

    private val appContext = context.applicationContext
    private val speech: SpeechRecognitionManager = speechFactory(appContext)
    private val tts: TextToSpeechManager = ttsFactory(appContext) { message ->
        TranslatorStateHolder.update { it.copy(errorMessage = message) }
    }

    private var lastAcceptedUtterance: String? = null
    private var isReleased = false
    private var hasCompletedRecognitionSession = false

    val sessionState: TranslatorSessionState
        get() = TranslatorStateHolder.uiState.value.sessionState

    fun start() {
        if (isReleased) return
        if (!speech.hasMicPermission) {
            TranslatorStateHolder.update {
                it.copy(errorMessage = "Microphone permission is required to record.")
            }
            return
        }

        when (sessionState) {
            TranslatorSessionState.RUNNING -> return
            TranslatorSessionState.PAUSED -> resume()
            TranslatorSessionState.STOPPED -> startFresh()
        }
    }

    fun pause() {
        if (isReleased || sessionState != TranslatorSessionState.RUNNING) return
        speech.destroy()
        TranslatorStateHolder.update {
            it.copy(
                sessionState = TranslatorSessionState.PAUSED,
                isTranslatorActive = false,
                isListening = false,
                isTranslating = false,
                recognizedEnglish = "",
            )
        }
        onStateChanged()
    }

    fun stop() {
        if (isReleased) return
        speech.destroy()
        RecognitionDiagnostics.reset()
        releaseResources()
        lastAcceptedUtterance = null
        TranslatorStateHolder.update {
            it.copy(
                sessionState = TranslatorSessionState.STOPPED,
                isTranslatorActive = false,
                isListening = false,
                isTranslating = false,
                recognizedEnglish = "",
                modelDownloadProgress = null,
            )
        }
        onStateChanged()
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        speech.destroy()
        releaseResources()
    }

    private fun startFresh() {
        lastAcceptedUtterance = null
        hasCompletedRecognitionSession = false
        RecognitionDiagnostics.reset()
        TranslatorStateHolder.update {
            it.copy(
                sessionState = TranslatorSessionState.RUNNING,
                isTranslatorActive = true,
                isTranslating = false,
                recognizedEnglish = "",
                errorMessage = null,
            )
        }
        onStateChanged()
        startListeningInternal()
    }

    private fun resume() {
        TranslatorStateHolder.update {
            it.copy(
                sessionState = TranslatorSessionState.RUNNING,
                isTranslatorActive = true,
                isTranslating = false,
                recognizedEnglish = "",
            )
        }
        onStateChanged()
        startListeningInternal()
    }

    private fun releaseResources() {
        tts.shutdown()
        translationService.release()
    }

    private val recognitionCallbacks = object : SpeechRecognitionManager.Callbacks {
        override fun onReadyForSpeech() = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onPartialResult(text: String) {
            if (sessionState != TranslatorSessionState.RUNNING) return
            TranslatorStateHolder.update { it.copy(recognizedEnglish = text) }
        }

        override fun onFinalResult(text: String, confidence: Float?) {
            if (sessionState != TranslatorSessionState.RUNNING) return
            hasCompletedRecognitionSession = true

            val finalText = text.trim()
            TranslatorStateHolder.update {
                it.copy(
                    isListening = false,
                    recognizedEnglish = finalText.ifBlank { it.recognizedEnglish },
                )
            }

            if (finalText.isBlank()) {
                restartListeningIfActive()
                return
            }

            if (!shouldProcessUtterance(finalText)) {
                TranslatorStateHolder.update { it.copy(recognizedEnglish = "") }
                restartListeningIfActive()
                return
            }

            translateAndSpeak(finalText)
        }

        override fun onError(message: String, errorCode: Int) {
            if (sessionState != TranslatorSessionState.RUNNING) return
            hasCompletedRecognitionSession = true

            TranslatorStateHolder.update {
                it.copy(
                    isListening = false,
                    isTranslating = false,
                    recognizedEnglish = "",
                )
            }

            if (!SpeechRecognitionManager.isRecoverableError(errorCode)) {
                TranslatorStateHolder.update { it.copy(errorMessage = message) }
            }
            restartListeningIfActive()
        }

        override fun onEndOfSpeech() {
            if (sessionState != TranslatorSessionState.RUNNING) return
            TranslatorStateHolder.update { it.copy(isListening = false, isTranslating = true) }
            onStateChanged()
        }
    }

    private fun shouldProcessUtterance(text: String): Boolean {
        if (countWords(text) < MIN_WORD_COUNT) return false
        val normalized = normalizeUtterance(text)
        if (normalized == lastAcceptedUtterance) return false
        return true
    }

    private fun translateAndSpeak(englishText: String) {
        scope.launch {
            TranslatorStateHolder.update { it.copy(isTranslating = true) }
            onStateChanged()
            try {
                val chinese = translationService.translateEnglishToChinese(englishText)
                val targetLanguage = TranslatorStateHolder.uiState.value.targetLanguage
                val pinyin = if (targetLanguage == TargetLanguage.CHINESE) {
                    pinyinService.toPinyin(chinese).takeIf { it.isNotBlank() }
                } else {
                    null
                }
                lastAcceptedUtterance = normalizeUtterance(englishText)
                TranslatorStateHolder.update {
                    it.copy(
                        isTranslating = false,
                        modelDownloadProgress = null,
                        recognizedEnglish = "",
                        conversationHistory = it.conversationHistory + ConversationEntry(
                            english = englishText,
                            translatedText = chinese,
                            pinyin = pinyin,
                        ),
                    )
                }
                tts.speakChinese(chinese)
            } catch (e: TranslationException) {
                TranslatorStateHolder.update {
                    it.copy(
                        isTranslating = false,
                        modelDownloadProgress = null,
                        errorMessage = e.message ?: "Translation failed.",
                    )
                }
            } finally {
                onStateChanged()
                restartListeningIfActive()
            }
        }
    }

    private fun startListeningInternal() {
        if (sessionState != TranslatorSessionState.RUNNING) return
        if (!speech.hasMicPermission) return

        if (hasCompletedRecognitionSession) {
            RecognitionDiagnostics.onRestartRequested()
        } else {
            RecognitionDiagnostics.onInitialListenRequested()
        }

        TranslatorStateHolder.update {
            it.copy(
                isListening = true,
                isTranslating = false,
                recognizedEnglish = "",
            )
        }
        onStateChanged()
        speech.startListening(recognitionCallbacks)
    }

    private fun restartListeningIfActive() {
        if (sessionState == TranslatorSessionState.RUNNING) {
            startListeningInternal()
        }
    }

    companion object {
        private const val MIN_WORD_COUNT = 2

        private fun countWords(text: String): Int =
            text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

        private fun normalizeUtterance(text: String): String =
            text.trim().lowercase()
    }
}
