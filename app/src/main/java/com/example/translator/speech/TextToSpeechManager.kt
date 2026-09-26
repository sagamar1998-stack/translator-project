package com.example.translator.speech

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Wraps [TextToSpeech] so the rest of the app can simply call [speakChinese].
 *
 * Uses the Google Speech Services engine and selects the highest-quality
 * Simplified Chinese voice available (preferring online neural voices).
 * Initialization is asynchronous; utterances that arrive early are queued.
 */
class TextToSpeechManager(
    context: Context,
    private val onError: (String) -> Unit = {},
) {

    @Volatile
    private var isInitialized: Boolean = false

    @Volatile
    private var pendingText: String? = null

    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext, { status ->
            if (status != TextToSpeech.SUCCESS) {
                onError(
                    "Google text-to-speech failed to initialize. " +
                        "Install Google Speech Services from the Play Store.",
                )
                return@TextToSpeech
            }
            if (!configureChineseVoice()) {
                onError(
                    "Chinese voice data is not installed. Open system Settings → " +
                        "Text-to-speech → Google Speech Services and download Chinese.",
                )
                return@TextToSpeech
            }
            tts.setSpeechRate(SPEECH_RATE)
            tts.setPitch(SPEECH_PITCH)
            isInitialized = true
            pendingText?.let {
                pendingText = null
                speakInternal(it)
            }
        }, GOOGLE_TTS_ENGINE)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = Unit
            @Deprecated("Deprecated in API 21+")
            override fun onError(utteranceId: String?) {
                onError("Failed to speak utterance.")
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                onError("Failed to speak utterance (code=$errorCode).")
            }
        })
    }

    fun speakChinese(text: String) {
        if (text.isBlank()) return
        if (!isInitialized) {
            pendingText = text
            return
        }
        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TextToSpeech.speak returned ERROR")
            onError("Text-to-speech could not speak the phrase.")
        }
    }

    /**
     * Picks the best Simplified Chinese voice from the Google engine.
     * Prefers higher [Voice.getQuality], online/neural voices, and zh-CN locale.
     */
    private fun configureChineseVoice(): Boolean {
        val selected = selectBestChineseVoice()
        if (selected != null) {
            val setResult = tts.setVoice(selected)
            if (setResult == TextToSpeech.SUCCESS) {
                Log.i(
                    TAG,
                    "Using voice: ${selected.name} " +
                        "(quality=${selected.quality}, network=${selected.isNetworkConnectionRequired})",
                )
                return true
            }
            Log.w(TAG, "setVoice failed for ${selected.name}, falling back to setLanguage")
        } else {
            Log.w(TAG, "No zh-CN voice found via getVoices(), falling back to setLanguage")
        }

        return when (tts.setLanguage(Locale.SIMPLIFIED_CHINESE)) {
            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED,
            -> false
            else -> true
        }
    }

    private fun selectBestChineseVoice(): Voice? {
        val voices = tts.voices ?: return null
        val mandarinVoices = voices.filter { isSimplifiedChineseVoice(it) }
        if (mandarinVoices.isEmpty()) {
            logAvailableChineseVoices(voices)
            return null
        }

        return mandarinVoices.maxWithOrNull(voiceComparator)
    }

    private fun isSimplifiedChineseVoice(voice: Voice): Boolean {
        val locale = voice.locale
        val language = locale.language.lowercase(Locale.ROOT)
        if (language != "zh") return false

        val country = locale.country.uppercase(Locale.ROOT)
        if (country.isEmpty()) return true
        return country == "CN" || country == "CHN"
    }

    private val voiceComparator = compareBy<Voice>(
        { it.quality },
        { voiceNaturalnessScore(it) },
    )

    /** Higher score = more likely to be a natural / neural / online voice. */
    private fun voiceNaturalnessScore(voice: Voice): Int {
        var score = 0
        val name = voice.name.lowercase(Locale.ROOT)
        if (voice.isNetworkConnectionRequired) score += 4
        if (name.contains("neural")) score += 3
        if (name.contains("network")) score += 2
        if (name.contains("local")) score += 1
        if (voice.quality >= Voice.QUALITY_VERY_HIGH) score += 3
        else if (voice.quality >= Voice.QUALITY_HIGH) score += 2
        return score
    }

    private fun logAvailableChineseVoices(allVoices: Set<Voice>) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        allVoices
            .filter { it.locale.language.lowercase(Locale.ROOT) == "zh" }
            .forEach { voice ->
                Log.d(
                    TAG,
                    "Available zh voice: ${voice.name} locale=${voice.locale} " +
                        "quality=${voice.quality} network=${voice.isNetworkConnectionRequired}",
                )
            }
    }

    fun shutdown() {
        if (!::tts.isInitialized) return
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
        private const val UTTERANCE_ID = "translation-utterance"
        private const val GOOGLE_TTS_ENGINE = "com.google.android.tts"
        private const val SPEECH_RATE = 0.95f
        private const val SPEECH_PITCH = 1.0f
    }
}
