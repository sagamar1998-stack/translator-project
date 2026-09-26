package com.example.translator.viewmodel

/** Lifecycle of the background translator foreground service. */
enum class TranslatorSessionState {
    STOPPED,
    RUNNING,
    PAUSED,
}
