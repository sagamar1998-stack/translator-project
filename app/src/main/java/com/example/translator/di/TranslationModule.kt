package com.example.translator.di

import com.example.translator.translation.TranslationService
import com.example.translator.translation.mlkit.MlKitTranslationService

/**
 * Lightweight dependency-injection wiring for [TranslationService].
 *
 * Swap [createTranslationService] to return a different implementation
 * (e.g. [FakeTranslationService] in tests) without changing the ViewModel.
 */
object TranslationModule {

    /** Production [TranslationService] backed by on-device ML Kit Translate. */
    fun createTranslationService(
        onDownloadProgress: (Float?) -> Unit = {},
    ): TranslationService = MlKitTranslationService(onDownloadProgress)
}
