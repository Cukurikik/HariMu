package com.harimu.app.core.design.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.harimu.app.R
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.design.ErrorState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
import com.harimu.app.core.toolapi.ToolProgress
import java.util.Locale

@Composable
fun AudioStudioExportDialog(
    isOpen: Boolean,
    tracks: List<AudioTrackSpec>,
    initialFormat: String,
    progress: ToolProgress?,
    onExport: (format: String) -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit,
    onOpenResult: (() -> Unit)?,
    onShareResult: (() -> Unit)?,
    onSaveResult: (() -> Unit)?,
    onUploadResult: (() -> Unit)? = null
) {
    val activeOverlay = progress != null
    if (!isOpen && !activeOverlay) return

    var selectedFormat by remember(initialFormat) { mutableStateOf(initialFormat) }
    val mixSummary = remember(tracks) { AudioTimelineMath.activeClipsSummary(tracks) }
    val estimatedSizeBytes = remember(mixSummary.totalDurationMs, selectedFormat) {
        AudioTimelineMath.estimateAudioExportSizeBytes(mixSummary.totalDurationMs, selectedFormat)
    }

    AlertDialog(
        onDismissRequest = {
            if (progress is ToolProgress.Running || progress is ToolProgress.Queued) {
                // Keep export running unless user explicitly cancels
            } else {
                onDismiss()
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.audio_export_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (progress !is ToolProgress.Running && progress !is ToolProgress.Queued) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("audio_export_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_audio_close)
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (progress) {
                    is ToolProgress.Queued -> ProgressSheet(
                        percent = null,
                        message = stringResource(R.string.progress_queued),
                        onCancel = onCancelExport
                    )

                    is ToolProgress.Running -> ProgressSheet(
                        percent = progress.percent,
                        message = progress.message,
                        onCancel = onCancelExport
                    )

                    is ToolProgress.Failed -> ErrorState(
                        message = progress.error,
                        onRetry = { onExport(selectedFormat) }
                    )

                    is ToolProgress.Done -> {
                        ResultCard(
                            summary = progress.result.summary,
                            onOpen = onOpenResult,
                            onShare = onShareResult,
                            onSave = onSaveResult,
                            onUploadToCloud = onUploadResult
                        )
                    }

                    null -> {
                        Text(
                            text = stringResource(R.string.audio_export_format_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("wav", "mp3", "flac").forEach { fmt ->
                                FilterChip(
                                    selected = selectedFormat == fmt,
                                    onClick = { selectedFormat = fmt },
                                    label = { Text(fmt.uppercase(Locale.US)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("audio_export_fmt_$fmt"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = HariMuColors.GroupAudio.copy(alpha = 0.2f),
                                        selectedLabelColor = HariMuColors.GroupAudio
                                    )
                                )
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.audio_export_active_summary,
                                        mixSummary.activeTrackCount,
                                        mixSummary.activeClipCount
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (mixSummary.soloActive) {
                                    Text(
                                        text = stringResource(R.string.audio_export_solo_active),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HariMuColors.TealEmerald
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.audio_export_est_duration), style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = AudioTimelineMath.formatDurationMs(mixSummary.totalDurationMs),
                                        style = MonoTextStyle
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.audio_export_est_size), style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = formatExportBytes(estimatedSizeBytes),
                                        style = MonoTextStyle,
                                        color = HariMuColors.GroupAudio
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.audio_export_pan_included),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (mixSummary.activeClipCount == 0) {
                            Text(
                                text = stringResource(R.string.audio_export_no_active_clips),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Button(
                                onClick = { onExport(selectedFormat) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("audio_export_start_btn")
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.audio_export_start_btn))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (progress == null || progress is ToolProgress.Done || progress is ToolProgress.Failed) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.cd_audio_close))
                }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = progress !is ToolProgress.Running && progress !is ToolProgress.Queued,
            dismissOnClickOutside = progress !is ToolProgress.Running && progress !is ToolProgress.Queued
        )
    )
}

private fun formatExportBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
