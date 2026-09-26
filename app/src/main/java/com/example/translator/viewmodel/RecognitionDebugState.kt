package com.example.translator.viewmodel

/**
 * High-level phase of the speech recognizer lifecycle (for the debug panel).
 */
enum class RecognitionPhase {
    IDLE,
    LISTENING,
    PROCESSING,
    RESTARTING,
}

/** One logged recognizer callback or lifecycle transition. */
data class RecognitionEventLog(
    val id: Long = System.nanoTime(),
    val timestampMs: Long = System.currentTimeMillis(),
    val event: String,
    val detail: String = "",
)

/** Time between one session ending and the next becoming ready to listen. */
data class RestartGapMeasurement(
    val endedAtMs: Long,
    val readyAtMs: Long,
    val gapMs: Long,
) {
    val isSlow: Boolean get() = gapMs > SLOW_RESTART_THRESHOLD_MS

    companion object {
        const val SLOW_RESTART_THRESHOLD_MS = 200L
    }
}

/**
 * Live diagnostics for speech recognition — shown in the Recognition Debug panel.
 */
data class RecognitionDebugState(
    val phase: RecognitionPhase = RecognitionPhase.IDLE,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val confidence: Float? = null,
    val lastEventTimestampMs: Long? = null,
    val lastRestartGapMs: Long? = null,
    val lastRestartGapSlow: Boolean = false,
    val eventLog: List<RecognitionEventLog> = emptyList(),
    val restartGaps: List<RestartGapMeasurement> = emptyList(),
)
