package com.example.translator.data

import com.example.translator.viewmodel.TranslationUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared UI state updated by [com.example.translator.service.TranslatorSession]
 * and observed by [com.example.translator.viewmodel.TranslationViewModel].
 */
object TranslatorStateHolder {

    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    fun update(transform: (TranslationUiState) -> TranslationUiState) {
        _uiState.update(transform)
    }
}
