package com.example.translator.speech

import android.util.Log
import com.example.translator.data.TranslatorStateHolder
import com.example.translator.viewmodel.RecognitionDebugState
import com.example.translator.viewmodel.RecognitionEventLog
import com.example.translator.viewmodel.RecognitionPhase
import com.example.translator.viewmodel.RestartGapMeasurement

/**
 * Central speech-recognition diagnostics: Logcat logging and UI state updates.
 *
 * Tag [TAG] is used for all Logcat output so gaps and missed sentences can be
 * correlated with recognizer lifecycle in Android Studio.
 */
object RecognitionDiagnostics {

    private const val TAG = "SpeechRecognition"
    private const val MAX_EVENT_LOG = 80
    private const val MAX_RESTART_GAPS = 30

    @Volatile
    private var lastSessionCompletedAtMs: Long? = null

    fun reset() {
        lastSessionCompletedAtMs = null
        updateDebug {
            RecognitionDebugState(phase = RecognitionPhase.IDLE)
        }
        log("reset", "Diagnostics cleared")
    }

    fun onReadyForSpeech() {
        val now = System.currentTimeMillis()
        log("onReadyForSpeech")
        updateDebug { state ->
            var gapMs: Long? = null
            var gapSlow = false
            var gaps = state.restartGaps
            lastSessionCompletedAtMs?.let { ended ->
                val gap = now - ended
                gapMs = gap
                gapSlow = gap > RestartGapMeasurement.SLOW_RESTART_THRESHOLD_MS
                val measurement = RestartGapMeasurement(
                    endedAtMs = ended,
                    readyAtMs = now,
                    gapMs = gap,
                )
                gaps = (state.restartGaps + measurement).takeLast(MAX_RESTART_GAPS)
                if (gapSlow) {
                    Log.w(TAG, "Slow restart gap: ${gap}ms (threshold ${RestartGapMeasurement.SLOW_RESTART_THRESHOLD_MS}ms)")
                } else {
                    Log.i(TAG, "Restart gap: ${gap}ms")
                }
            }
            state.copy(
                phase = RecognitionPhase.LISTENING,
                lastEventTimestampMs = now,
                lastRestartGapMs = gapMs ?: state.lastRestartGapMs,
                lastRestartGapSlow = gapSlow || (gapMs == null && state.lastRestartGapSlow),
                restartGaps = gaps,
            )
        }
        appendEvent("onReadyForSpeech")
    }

    fun onBeginningOfSpeech() {
        log("onBeginningOfSpeech")
        touchEvent(RecognitionPhase.LISTENING)
    }

    fun onPartialResults(text: String) {
        log("onPartialResults", text)
        val now = System.currentTimeMillis()
        updateDebug {
            it.copy(
                phase = RecognitionPhase.LISTENING,
                partialTranscript = text,
                lastEventTimestampMs = now,
            )
        }
        appendEvent("onPartialResults", text)
    }

    fun onResults(text: String, confidence: Float?) {
        lastSessionCompletedAtMs = System.currentTimeMillis()
        val detail = buildString {
            append(text.ifBlank { "(empty)" })
            confidence?.let { append(" conf=").append(String.format("%.2f", it)) }
        }
        log("onResults", detail)
        val now = lastSessionCompletedAtMs!!
        updateDebug {
            it.copy(
                phase = RecognitionPhase.PROCESSING,
                finalTranscript = text,
                confidence = confidence,
                lastEventTimestampMs = now,
            )
        }
        appendEvent("onResults", detail)
    }

    fun onEndOfSpeech() {
        log("onEndOfSpeech")
        updateDebug {
            it.copy(
                phase = RecognitionPhase.PROCESSING,
                lastEventTimestampMs = System.currentTimeMillis(),
            )
        }
        appendEvent("onEndOfSpeech")
    }

    fun onError(message: String, errorCode: Int) {
        lastSessionCompletedAtMs = System.currentTimeMillis()
        val detail = "$message (code=$errorCode)"
        log("onError", detail, isError = true)
        updateDebug {
            it.copy(
                phase = RecognitionPhase.PROCESSING,
                lastEventTimestampMs = lastSessionCompletedAtMs,
            )
        }
        appendEvent("onError", detail)
    }

    /** Called immediately before tearing down and starting a new recognizer session. */
    fun onRestartRequested() {
        log("restartRequested", "Destroying recognizer and starting new session")
        updateDebug {
            it.copy(
                phase = RecognitionPhase.RESTARTING,
                lastEventTimestampMs = System.currentTimeMillis(),
                partialTranscript = "",
            )
        }
        appendEvent("restartRequested")
    }

    /** First listen of a translator run — no prior session to measure gap from. */
    fun onInitialListenRequested() {
        log("initialListenRequested")
        updateDebug {
            it.copy(
                phase = RecognitionPhase.RESTARTING,
                lastEventTimestampMs = System.currentTimeMillis(),
            )
        }
        appendEvent("initialListenRequested")
    }

    private fun touchEvent(phase: RecognitionPhase) {
        updateDebug {
            it.copy(phase = phase, lastEventTimestampMs = System.currentTimeMillis())
        }
        appendEvent("onBeginningOfSpeech")
    }

    private fun log(event: String, detail: String = "", isError: Boolean = false) {
        val message = if (detail.isEmpty()) event else "$event: $detail"
        if (isError) Log.e(TAG, message) else Log.d(TAG, message)
    }

    private fun appendEvent(event: String, detail: String = "") {
        updateDebug { state ->
            state.copy(
                eventLog = (state.eventLog + RecognitionEventLog(
                    event = event,
                    detail = detail,
                )).takeLast(MAX_EVENT_LOG),
            )
        }
    }

    private fun updateDebug(transform: (RecognitionDebugState) -> RecognitionDebugState) {
        TranslatorStateHolder.update { ui ->
            ui.copy(recognitionDebug = transform(ui.recognitionDebug))
        }
    }
}
