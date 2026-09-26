package com.example.translator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.translator.ui.TranslationScreen
import com.example.translator.ui.theme.TranslatorTheme
import com.example.translator.viewmodel.TranslationViewModel

/**
 * Single Activity that hosts the only screen in the app.
 *
 * The Activity is intentionally tiny — all behavior lives in the ViewModel and
 * the Compose layer. This is the canonical MVVM split: Activity = entry point
 * and lifecycle owner, ViewModel = state + logic, Composable = rendering.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: TranslationViewModel by viewModels {
        TranslationViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TranslatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TranslationScreen(viewModel = viewModel)
                }
            }
        }
    }
}
