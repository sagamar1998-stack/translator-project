package com.example.translator.translation

/**
 * Abstraction over a translation backend.
 *
 * Defined as an interface so we can swap implementations later (e.g. ML Kit,
 * a cloud API, an on-device Whisper model) without touching the ViewModel.
 *
 * The function is marked `suspend` because a real implementation will be
 * asynchronous (network or heavy on-device compute). The current fake
 * implementation simply returns a hardcoded string, but keeping the signature
 * async-ready avoids breaking the call site later.
 */
interface TranslationService {

    /**
     * Translate [sourceText] (assumed English) into the service's target
     * language and return the translated text.
     *
     * @throws TranslationException if the service cannot produce a translation.
     */
    suspend fun translateEnglishToChinese(sourceText: String): String

    /** Release native resources held by the implementation, if any. */
    fun release() = Unit
}

/** Exception type surfaced to callers when translation fails. */
class TranslationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Stub implementation used while we wire up the speech-recognition pipeline.
 *
 * It ignores the input and always returns a single hardcoded Chinese phrase.
 * Replace with a real implementation when a translation backend is chosen.
 */
class FakeTranslationService : TranslationService {

    override suspend fun translateEnglishToChinese(sourceText: String): String {
        // Intentionally synchronous — no network calls in the prototype.
        return HARDCODED_CHINESE_REPLY
    }

    companion object {
        const val HARDCODED_CHINESE_REPLY = "你好，我听到了你的话。"
    }
}
