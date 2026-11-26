package com.harimu.app.core.design.video

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onExport: (String) -> Unit, // resolution preset
    clipsCount: Int,
    totalDurationMs: Long,
    estimatedFileSizeBytes: Long? = null,
    progress: ToolProgress? = null,
    result: ToolResult? = null,
    onOpenResult: (() -> Unit)? = null,
    onShareResult: (() -> Unit)? = null,
    onSaveResult: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var resolution by remember { mutableStateOf("720p") }

    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.editor_export_dialog_title), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Resolution picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.editor_resolution_label), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("480p", "720p", "1080p").forEach { res ->
                            FilterChip(
                                selected = resolution == res,
                                onClick = { resolution = res },
                                label = { Text(res) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    selectedContainerColor = HariMuColors.ElectricOrange.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                // Estimates
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.editor_estimated_duration), style = MaterialTheme.typography.bodyMedium)
                        Text(text = formatDurationMs(totalDurationMs), style = MonoTextStyle)
                    }
                    if (estimatedFileSizeBytes != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = stringResource(R.string.editor_estimated_size), style = MaterialTheme.typography.bodyMedium)
                            Text(text = formatBytes(estimatedFileSizeBytes), style = MonoTextStyle.copy(color = HariMuColors.ElectricOrange))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.editor_clips_count, clipsCount), style = MaterialTheme.typography.bodyMedium)
                        Text(text = stringResource(R.string.editor_output_format), style = MonoTextStyle)
                    }
                }

                // Progress / Result / Export button
                when {
                    progress != null -> {
                        ProgressSheet(
                            percent = when (val p = progress) {
                                is ToolProgress.Running -> p.percent
                                is ToolProgress.Queued -> null
                                else -> 100
                            },
                            message = when (val p = progress) {
                                is ToolProgress.Running -> p.message
                                is ToolProgress.Queued -> stringResource(R.string.progress_queued)
                                is ToolProgress.Failed -> p.error
                                is ToolProgress.Done -> stringResource(R.string.status_success)
                                else -> ""
                            },
                            onCancel = onDismiss
                        )
                    }
                    result != null -> {
                        ResultCard(
                            summary = result.summary,
                            onOpen = onOpenResult,
                            onShare = onShareResult,
                            onSave = onSaveResult
                        )
                    }
                    else -> {
                        // Ready to export
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.editor_export_ready_desc, clipsCount, resolution),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onExport(resolution) },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                Text(text = stringResource(R.string.editor_export_button, clipsCount), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportProgressSheet(
    progress: ToolProgress,
    onCancel: () -> Unit
) {
    ProgressSheet(
        percent = when (val p = progress) {
            is ToolProgress.Running -> p.percent
            is ToolProgress.Queued -> null
            else -> 100
        },
        message = when (val p = progress) {
            is ToolProgress.Running -> p.message
            is ToolProgress.Queued -> "Preparing export..."
            is ToolProgress.Failed -> p.error
            is ToolProgress.Done -> "Export completed"
            else -> ""
        },
        onCancel = onCancel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportResultCard(
    result: ToolResult,
    onOpen: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null
) {
    ResultCard(
        summary = result.summary,
        onOpen = onOpen,
        onShare = onShare,
        onSave = onSave
    )
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb.toDouble())
    val gb = mb / 1024
    return String.format(Locale.US, "%.1f GB", gb.toDouble())
}