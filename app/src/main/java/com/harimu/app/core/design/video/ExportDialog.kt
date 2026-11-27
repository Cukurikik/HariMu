package com.harimu.app.core.design.video

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.harimu.app.R
import com.harimu.app.core.design.ErrorState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
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
    initialResolution: String = "720p",
    onResolutionChanged: ((String) -> Unit)? = null,
    estimatedFileSizeBytes: Long? = null,
    estimateSizeBytes: ((String) -> Long)? = null,
    progress: ToolProgress? = null,
    result: ToolResult? = null,
    onCancelExport: (() -> Unit)? = null,
    onOpenResult: (() -> Unit)? = null,
    onShareResult: (() -> Unit)? = null,
    onSaveResult: (() -> Unit)? = null
) {
    if (!isOpen) return

    var resolution by remember(initialResolution) { mutableStateOf(initialResolution) }
    LaunchedEffect(initialResolution) {
        resolution = initialResolution
    }

    val isRunning = progress is ToolProgress.Queued || progress is ToolProgress.Running
    val displayedSizeBytes = estimateSizeBytes?.invoke(resolution) ?: estimatedFileSizeBytes
    val resolvedResult = result ?: (progress as? ToolProgress.Done)?.result

    AlertDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        title = {
            Text(text = stringResource(R.string.editor_export_dialog_title), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                onClick = {
                                    resolution = res
                                    onResolutionChanged?.invoke(res)
                                },
                                enabled = !isRunning,
                                label = { Text(res) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_res_$res"),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    selectedContainerColor = HariMuColors.ElectricOrange.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                // Estimates
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.editor_estimated_duration), style = MaterialTheme.typography.bodyMedium)
                        Text(text = formatDurationMs(totalDurationMs), style = MonoTextStyle)
                    }
                    if (displayedSizeBytes != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = stringResource(R.string.editor_estimated_size), style = MaterialTheme.typography.bodyMedium)
                            Text(text = formatBytes(displayedSizeBytes), style = MonoTextStyle.copy(color = HariMuColors.ElectricOrange))
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

                // Result / Running Progress / Error / Ready button
                when {
                    resolvedResult != null -> {
                        ResultCard(
                            summary = resolvedResult.summary,
                            onOpen = onOpenResult,
                            onShare = onShareResult,
                            onSave = onSaveResult,
                            modifier = Modifier.testTag("export_result_card")
                        )
                    }
                    progress is ToolProgress.Queued || progress is ToolProgress.Running -> {
                        ProgressSheet(
                            percent = if (progress is ToolProgress.Running) progress.percent else null,
                            message = if (progress is ToolProgress.Running) {
                                progress.message
                            } else {
                                stringResource(R.string.progress_queued)
                            },
                            onCancel = { (onCancelExport ?: onDismiss)() },
                            modifier = Modifier.testTag("export_progress_sheet")
                        )
                    }
                    progress is ToolProgress.Failed -> {
                        ErrorState(
                            message = progress.error,
                            onRetry = { onExport(resolution) },
                            modifier = Modifier.testTag("export_error_state")
                        )
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.editor_export_ready_desc, clipsCount, resolution),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onExport(resolution) },
                                enabled = clipsCount > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("export_dialog_confirm_button")
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
            if (isRunning) {
                TextButton(
                    onClick = { (onCancelExport ?: onDismiss)() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("export_dialog_cancel_button")
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("export_dialog_close_button")
                ) {
                    Text(text = stringResource(R.string.action_close))
                }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isRunning,
            dismissOnClickOutside = !isRunning
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
        percent = when (progress) {
            is ToolProgress.Running -> progress.percent
            is ToolProgress.Queued -> null
            else -> 100
        },
        message = when (progress) {
            is ToolProgress.Running -> progress.message
            is ToolProgress.Queued -> stringResource(R.string.progress_queued)
            is ToolProgress.Failed -> progress.error
            is ToolProgress.Done -> stringResource(R.string.status_success)
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