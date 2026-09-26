package com.example.translator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.translator.viewmodel.RecognitionDebugState
import com.example.translator.viewmodel.RecognitionEventLog
import com.example.translator.viewmodel.RecognitionPhase
import com.example.translator.viewmodel.RestartGapMeasurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecognitionDebugPanel(
    debug: RecognitionDebugState,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recognition Debug",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhaseChip(phase = debug.phase)
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                        )
                    }
                }
            }

            DebugField(label = "Partial", value = debug.partialTranscript.ifBlank { "—" })
            DebugField(label = "Final", value = debug.finalTranscript.ifBlank { "—" })
            DebugField(
                label = "Confidence",
                value = debug.confidence?.let { String.format(Locale.US, "%.0f%%", it * 100) } ?: "—",
            )
            DebugField(
                label = "Last event",
                value = debug.lastEventTimestampMs?.let { formatTimestamp(it) } ?: "—",
            )

            debug.lastRestartGapMs?.let { gap ->
                val slow = debug.lastRestartGapSlow
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = if (slow) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ) {
                    Text(
                        text = if (slow) {
                            "Last restart gap: ${gap}ms (slow — > ${RestartGapMeasurement.SLOW_RESTART_THRESHOLD_MS}ms)"
                        } else {
                            "Last restart gap: ${gap}ms"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (slow) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (debug.restartGaps.isNotEmpty()) {
                        Text(
                            text = "Restart gaps",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        debug.restartGaps.asReversed().forEach { gap ->
                            RestartGapRow(gap = gap)
                        }
                    }

                    Text(
                        text = "Event log",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (debug.eventLog.isEmpty()) {
                        Text(
                            text = "No events yet. Start the translator to begin logging.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        debug.eventLog.asReversed().forEach { event ->
                            EventLogRow(event = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseChip(phase: RecognitionPhase) {
    val (label, color) = when (phase) {
        RecognitionPhase.LISTENING -> "Listening" to MaterialTheme.colorScheme.primaryContainer
        RecognitionPhase.PROCESSING -> "Processing" to MaterialTheme.colorScheme.tertiaryContainer
        RecognitionPhase.RESTARTING -> "Restarting" to MaterialTheme.colorScheme.secondaryContainer
        RecognitionPhase.IDLE -> "Idle" to MaterialTheme.colorScheme.surface
    }
    val textColor = when (phase) {
        RecognitionPhase.LISTENING -> MaterialTheme.colorScheme.onPrimaryContainer
        RecognitionPhase.PROCESSING -> MaterialTheme.colorScheme.onTertiaryContainer
        RecognitionPhase.RESTARTING -> MaterialTheme.colorScheme.onSecondaryContainer
        RecognitionPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DebugField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun RestartGapRow(gap: RestartGapMeasurement) {
    val slow = gap.isSlow
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (slow) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatTimestamp(gap.readyAtMs),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = if (slow) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${gap.gapMs}ms",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (slow) FontWeight.Bold else FontWeight.Normal,
            color = if (slow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EventLogRow(event: RecognitionEventLog) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = event.event,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formatTimestamp(event.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (event.detail.isNotBlank()) {
            Text(
                text = event.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 1.dp),
            )
        }
    }
}

private fun formatTimestamp(epochMs: Long): String =
    TIME_FORMAT.format(Date(epochMs))

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
