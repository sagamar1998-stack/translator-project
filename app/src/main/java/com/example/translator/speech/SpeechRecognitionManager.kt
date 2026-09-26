package com.example.translator.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Thin wrapper around the platform [SpeechRecognizer].
 *
 * Forwards framework callbacks to [Callbacks] and records diagnostics via
 * [RecognitionDiagnostics] (Logcat tag: SpeechRecognition).
 */
class SpeechRecognitionManager(
    private val context: Context,
) {

    interface Callbacks {
        fun onReadyForSpeech()
        fun onBeginningOfSpeech()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String, confidence: Float?)
        fun onError(message: String, errorCode: Int = SpeechRecognizer.ERROR_CLIENT)
        fun onEndOfSpeech()
    }

    private var recognizer: SpeechRecognizer? = null
    private var callbacks: Callbacks? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    val hasMicPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    fun startListening(callbacks: Callbacks) {
        if (!isAvailable) {
            callbacks.onError(
                "Speech recognition is not available on this device.",
                SpeechRecognizer.ERROR_CLIENT,
            )
            return
        }
        if (!hasMicPermission) {
            callbacks.onError(
                "Microphone permission has not been granted.",
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            )
            return
        }

        destroy()

        this.callbacks = callbacks
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        runCatching { recognizer?.startListening(intent) }
            .onFailure { e ->
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                callbacks.onError(
                    "Could not start recognizer: ${e.message ?: "unknown error"}",
                    SpeechRecognizer.ERROR_CLIENT,
                )
            }
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.setRecognitionListener(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        callbacks = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            RecognitionDiagnostics.onReadyForSpeech()
            callbacks?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            RecognitionDiagnostics.onBeginningOfSpeech()
            callbacks?.onBeginningOfSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            RecognitionDiagnostics.onEndOfSpeech()
            callbacks?.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            val message = errorMessage(error)
            RecognitionDiagnostics.onError(message, error)
            callbacks?.onError(message, error)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            val confidence = extractTopConfidence(results)
            RecognitionDiagnostics.onResults(text, confidence)
            callbacks?.onFinalResult(text, confidence)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                RecognitionDiagnostics.onPartialResults(text)
                callbacks?.onPartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun extractTopConfidence(results: Bundle?): Float? {
        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES) ?: return null
        return scores.firstOrNull()
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Recognizer client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission."
        SpeechRecognizer.ERROR_NETWORK -> "Network error."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy."
        SpeechRecognizer.ERROR_SERVER -> "Server error from recognition service."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
        else -> "Unknown recognition error (code=$error)."
    }

    companion object {
        private const val TAG = "SpeechRecognitionMgr"

        fun isRecoverableError(errorCode: Int): Boolean = when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            -> true
            else -> false
        }
    }
}
