package com.example.translator.translation.mlkit

import android.util.Log
import com.example.translator.translation.TranslationException
import com.example.translator.translation.TranslationService
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device [TranslationService] backed by Google ML Kit Translate.
 *
 * Downloads the English and Chinese language models on first use via
 * [RemoteModelManager], reporting aggregated progress through [onDownloadProgress].
 */
class MlKitTranslationService(
    private val onDownloadProgress: (Float?) -> Unit = {},
) : TranslationService {

    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.CHINESE)
            .build(),
    )

    private val remoteModelManager = RemoteModelManager.getInstance()
    private val downloadConditions = DownloadConditions.Builder().build()

    private val models = listOf(
        TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build(),
        TranslateRemoteModel.Builder(TranslateLanguage.CHINESE).build(),
    )

    override suspend fun translateEnglishToChinese(sourceText: String): String {
        val trimmed = sourceText.trim()
        if (trimmed.isEmpty()) {
            throw TranslationException("Nothing to translate.")
        }

        ensureModelsDownloaded()

        return suspendCancellableCoroutine { cont ->
            translator.translate(trimmed)
                .addOnSuccessListener { result ->
                    val translated = result.trim()
                    if (translated.isEmpty()) {
                        cont.resumeWithException(
                            TranslationException("Translation returned an empty result."),
                        )
                    } else {
                        Log.d(TAG, "Translated \"$trimmed\" -> \"$translated\"")
                        cont.resume(translated)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Translation failed", e)
                    cont.resumeWithException(
                        TranslationException(
                            message = "Translation failed: ${e.message ?: "unknown error"}",
                            cause = e,
                        ),
                    )
                }
        }
    }

    override fun release() {
        translator.close()
    }

    /**
     * Ensures both language models are present locally before translating.
     * Reports aggregated download progress across all required models.
     */
    private suspend fun ensureModelsDownloaded() {
        val pending = mutableListOf<TranslateRemoteModel>()
        models.forEachIndexed { index, model ->
            if (isModelDownloaded(model)) {
                reportOverallProgress(index + 1, models.size)
            } else {
                pending.add(model)
            }
        }

        if (pending.isEmpty()) {
            onDownloadProgress(null)
            return
        }

        Log.d(TAG, "Downloading ${pending.size} language model(s)...")
        var completedCount = models.size - pending.size

        pending.forEach { model ->
            onDownloadProgress(completedCount.toFloat() / models.size)
            downloadModel(model)
            completedCount++
            onDownloadProgress(completedCount.toFloat() / models.size)
        }

        // Confirm with the translator client that the pair is ready.
        suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded(downloadConditions)
                .addOnSuccessListener {
                    Log.d(TAG, "All language models ready.")
                    onDownloadProgress(null)
                    cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "downloadModelIfNeeded failed", e)
                    onDownloadProgress(null)
                    cont.resumeWithException(
                        TranslationException(
                            message = "Failed to download language model: ${e.message ?: "unknown error"}",
                            cause = e,
                        ),
                    )
                }
        }
    }

    private suspend fun isModelDownloaded(model: TranslateRemoteModel): Boolean =
        suspendCancellableCoroutine { cont ->
            remoteModelManager.isModelDownloaded(model)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(false) }
        }

    private suspend fun downloadModel(model: TranslateRemoteModel) {
        suspendCancellableCoroutine { cont ->
            remoteModelManager.download(model, downloadConditions)
                .addOnSuccessListener {
                    Log.d(TAG, "Model downloaded successfully.")
                    cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Model download failed", e)
                    cont.resumeWithException(
                        TranslationException(
                            message = "Failed to download language model: ${e.message ?: "unknown error"}",
                            cause = e,
                        ),
                    )
                }
        }
    }

    private fun reportOverallProgress(completedCount: Int, totalCount: Int) {
        onDownloadProgress((completedCount.toFloat() / totalCount).coerceIn(0f, 1f))
    }

    companion object {
        private const val TAG = "MlKitTranslation"
    }
}
