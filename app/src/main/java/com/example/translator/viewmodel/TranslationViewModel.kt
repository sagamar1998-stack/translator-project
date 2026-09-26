package com.example.translator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.translator.data.TranslatorStateHolder
import com.example.translator.service.TranslatorForegroundService
import com.example.translator.speech.TextToSpeechManager
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the translation screen.
 *
 * UI commands are delegated to [TranslatorForegroundService]; state is observed
 * from [TranslatorStateHolder] which the service updates while running in the
 * background.
 */
class TranslationViewModel(
    application: Application,
) : AndroidViewModel(application) {

    val uiState: StateFlow<TranslationUiState> = TranslatorStateHolder.uiState

    /** Lazy TTS used only for replaying past translations from the UI. */
    private var replayTts: TextToSpeechManager? = null

    fun onMicPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        TranslatorStateHolder.update {
            it.copy(
                micPermission = when {
                    granted -> PermissionState.Granted
                    canAskAgain -> PermissionState.Denied
                    else -> PermissionState.PermanentlyDenied
                },
                errorMessage = if (granted) null else it.errorMessage,
            )
        }
    }

    fun onStartTranslatorClicked() {
        TranslatorForegroundService.start(getApplication())
    }

    fun onPauseTranslatorClicked() {
        TranslatorForegroundService.pause(getApplication())
    }

    fun onStopTranslatorClicked() {
        TranslatorForegroundService.stop(getApplication())
    }

    fun clearError() {
        TranslatorStateHolder.update { it.copy(errorMessage = null) }
    }

    /** Re-speaks a Chinese translation from the conversation history. */
    fun onReplayTranslation(chinese: String) {
        if (chinese.isBlank()) return
        if (replayTts == null) {
            replayTts = TextToSpeechManager(getApplication()) { message ->
                TranslatorStateHolder.update { it.copy(errorMessage = message) }
            }
        }
        replayTts?.speakChinese(chinese)
    }

    override fun onCleared() {
        replayTts?.shutdown()
        super.onCleared()
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TranslationViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return TranslationViewModel(application) as T
        }
    }
}
