package com.example.translator.viewmodel

import com.example.translator.translation.TargetLanguage

/**
 * Immutable snapshot of everything the translation screen needs to render.
 */
data class TranslationUiState(

    /** Foreground-service session lifecycle. */
    val sessionState: TranslatorSessionState = TranslatorSessionState.STOPPED,

    /** Whether the listen → translate loop is actively running (not paused). */
    val isTranslatorActive: Boolean = false,

    /** Whether the recognizer is currently capturing audio. */
    val isListening: Boolean = false,

    /** Whether translation/TTS is running after recognition finished. */
    val isTranslating: Boolean = false,

    /** Live partial recognition text while the translator is listening. */
    val recognizedEnglish: String = "",

    /** Completed exchanges, oldest first. */
    val conversationHistory: List<ConversationEntry> = emptyList(),

    /** Target language for translation; controls Chinese + pinyin display. */
    val targetLanguage: TargetLanguage = TargetLanguage.CHINESE,

    /** Current state of the microphone permission. */
    val micPermission: PermissionState = PermissionState.Unknown,

    /** Whether a language model is being downloaded (0f–1f), or null when idle. */
    val modelDownloadProgress: Float? = null,

    /** User-facing error message, or null when no error is being shown. */
    val errorMessage: String? = null,

    /** Speech-recognition diagnostics for the debug panel. */
    val recognitionDebug: RecognitionDebugState = RecognitionDebugState(),
) {
    val canStartTranslator: Boolean
        get() = (sessionState == TranslatorSessionState.STOPPED ||
                sessionState == TranslatorSessionState.PAUSED) &&
                micPermission != PermissionState.PermanentlyDenied

    val canPauseTranslator: Boolean
        get() = sessionState == TranslatorSessionState.RUNNING

    val canStopTranslator: Boolean
        get() = sessionState != TranslatorSessionState.STOPPED
}

/** Tri-state representation of a runtime permission. */
enum class PermissionState {
    Unknown,
    Granted,
    Denied,
    PermanentlyDenied,
}
