package com.example.translator.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.translator.translation.TargetLanguage
import com.example.translator.viewmodel.ConversationEntry
import com.example.translator.viewmodel.PermissionState
import com.example.translator.viewmodel.TranslatorSessionState
import com.example.translator.viewmodel.TranslationUiState
import com.example.translator.viewmodel.TranslationViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TranslationScreen(viewModel: TranslationViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO) { granted ->
        viewModel.onMicPermissionResult(
            granted = granted,
            canAskAgain = true,
        )
    }

    LaunchedEffect(micPermissionState.status) {
        val status = micPermissionState.status
        viewModel.onMicPermissionResult(
            granted = status is PermissionStatus.Granted,
            canAskAgain = status is PermissionStatus.Granted ||
                    (status as? PermissionStatus.Denied)?.shouldShowRationale == true,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("English → Chinese Translator") },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { innerPadding ->
        TranslationScreenContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            uiState = uiState,
            onStartTranslator = {
                if (micPermissionState.status is PermissionStatus.Granted) {
                    viewModel.onStartTranslatorClicked()
                } else {
                    micPermissionState.launchPermissionRequest()
                }
            },
            onPauseTranslator = viewModel::onPauseTranslatorClicked,
            onStopTranslator = viewModel::onStopTranslatorClicked,
            onReplayTranslation = viewModel::onReplayTranslation,
        )
    }
}

@Composable
private fun TranslationScreenContent(
    modifier: Modifier = Modifier,
    uiState: TranslationUiState,
    onStartTranslator: () -> Unit,
    onPauseTranslator: () -> Unit,
    onStopTranslator: () -> Unit,
    onReplayTranslation: (String) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val reversedHistory = uiState.conversationHistory.asReversed()

    LaunchedEffect(uiState.conversationHistory.size) {
        if (reversedHistory.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.micPermission == PermissionState.PermanentlyDenied) {
            PermissionDeniedBanner(
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                },
            )
        }

        TranslatorControls(
            uiState = uiState,
            onStartTranslator = onStartTranslator,
            onPauseTranslator = onPauseTranslator,
            onStopTranslator = onStopTranslator,
        )

        StatusLine(uiState = uiState)

        RecognitionDebugPanel(debug = uiState.recognitionDebug)

        if (uiState.recognizedEnglish.isNotBlank()) {
            LiveRecognitionCard(text = uiState.recognizedEnglish)
        }

        Text(
            text = "Conversation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (uiState.conversationHistory.isEmpty()) {
            Text(
                text = "Start the translator and speak in English. Each phrase will appear with pinyin and Chinese.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = reversedHistory,
                    key = { it.id },
                ) { entry ->
                    ConversationHistoryCard(
                        entry = entry,
                        targetLanguage = uiState.targetLanguage,
                        onReplay = { onReplayTranslation(entry.translatedText) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslatorControls(
    uiState: TranslationUiState,
    onStartTranslator: () -> Unit,
    onPauseTranslator: () -> Unit,
    onStopTranslator: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onStartTranslator,
            enabled = uiState.canStartTranslator,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Start", style = MaterialTheme.typography.labelLarge)
        }

        OutlinedButton(
            onClick = onPauseTranslator,
            enabled = uiState.canPauseTranslator,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Pause", style = MaterialTheme.typography.labelLarge)
        }

        OutlinedButton(
            onClick = onStopTranslator,
            enabled = uiState.canStopTranslator,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Stop", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusLine(uiState: TranslationUiState) {
    val downloadProgress = uiState.modelDownloadProgress
    val message = when {
        downloadProgress != null ->
            "Downloading Chinese language model… ${(downloadProgress * 100).toInt()}%"
        uiState.sessionState == TranslatorSessionState.PAUSED ->
            "Translator paused. Tap Start to resume."
        uiState.isTranslatorActive && uiState.isListening ->
            "Listening… speak in English."
        uiState.isTranslatorActive && uiState.isTranslating ->
            "Translating to Chinese…"
        uiState.isTranslatorActive ->
            "Translator active."
        uiState.micPermission == PermissionState.Denied ->
            "Microphone permission is required. Tap Start to grant it."
        else ->
            "Tap Start to begin continuous translation (runs in background)."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            downloadProgress != null -> {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            uiState.isTranslatorActive && (uiState.isListening || uiState.isTranslating) -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveRecognitionCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Hearing…",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ConversationHistoryCard(
    entry: ConversationEntry,
    targetLanguage: TargetLanguage,
    onReplay: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.english,
                style = MaterialTheme.typography.bodyLarge,
            )

            if (targetLanguage == TargetLanguage.CHINESE) {
                Spacer(Modifier.height(8.dp))
                if (!entry.pinyin.isNullOrBlank()) {
                    Text(
                        text = entry.pinyin,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.translatedText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onReplay) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Replay translation",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionDeniedBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Microphone permission is permanently denied.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Enable the microphone permission in system settings to use the translator.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Open settings")
            }
        }
    }
}
