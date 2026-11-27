package com.harimu.app.core.design.converter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.cloud.UploadQueue
import com.harimu.app.core.cloud.UploadStatus
import com.harimu.app.core.cloud.UploadTask
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.CapabilityBadge
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.jobs.UploadTaskStatusRow
import com.harimu.app.core.design.jobs.decodeJobOutputs
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.ToolOutputFile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterJobQueueSection(
    jobs: List<JobEntity>,
    onCancelJob: (String) -> Unit,
    onRetryJob: ((JobEntity) -> Unit)?,
    canRetryJob: (String) -> Boolean,
    onOpenOutput: (ToolOutputFile) -> Unit,
    onShareOutput: (ToolOutputFile) -> Unit,
    onSaveOutput: (ToolOutputFile) -> Unit,
    onUploadOutput: (ToolOutputFile) -> Unit,
    modifier: Modifier = Modifier,
    uploadTasks: List<UploadTask> = emptyList(),
    onRetryUpload: ((UploadTask) -> Unit)? = null,
    onClearFinished: (() -> Unit)? = null,
    onCopyTextOutput: ((String) -> Unit)? = null
) {
    val activeCount = jobs.count {
        it.status == ToolJobWorker.STATUS_RUNNING || it.status == ToolJobWorker.STATUS_PREPARING
    }
    val queuedCount = jobs.count { it.status == ToolJobWorker.STATUS_QUEUED }
    val issueCount = jobs.count {
        it.status == ToolJobWorker.STATUS_FAILED || it.status == ToolJobWorker.STATUS_INTERRUPTED
    }
    val hasFinished = jobs.any { !ToolJobWorker.isActiveStatus(it.status) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("converter_queue_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.converter_queue_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (jobs.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.converter_queue_stats_fmt, activeCount, queuedCount, issueCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("converter_queue_stats")
                    )
                }
            }
            if (onClearFinished != null && hasFinished) {
                TextButton(
                    onClick = onClearFinished,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("converter_clear_finished_btn")
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.job_clear_finished),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (jobs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.SwapHoriz,
                title = stringResource(R.string.converter_queue_empty_title),
                subtitle = stringResource(R.string.converter_queue_empty_desc)
            )
        } else {
            jobs.take(20).forEach { job ->
                val canRetry = canRetryJob(job.jobId)
                ConverterJobCard(
                    job = job,
                    canRetry = canRetry,
                    uploadTasks = uploadTasks,
                    onCancel = { onCancelJob(job.jobId) },
                    onRetry = if (onRetryJob != null && canRetry) {
                        { onRetryJob(job) }
                    } else null,
                    onRetryUpload = onRetryUpload,
                    onOpenOutput = onOpenOutput,
                    onShareOutput = onShareOutput,
                    onSaveOutput = onSaveOutput,
                    onUploadOutput = onUploadOutput,
                    onCopyTextOutput = onCopyTextOutput
                )
            }
            if (jobs.size > 20) {
                Text(
                    text = stringResource(R.string.converter_queue_overflow_note, jobs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("converter_queue_overflow_note")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConverterJobCard(
    job: JobEntity,
    canRetry: Boolean,
    uploadTasks: List<UploadTask>,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)?,
    onRetryUpload: ((UploadTask) -> Unit)?,
    onOpenOutput: (ToolOutputFile) -> Unit,
    onShareOutput: (ToolOutputFile) -> Unit,
    onSaveOutput: (ToolOutputFile) -> Unit,
    onUploadOutput: (ToolOutputFile) -> Unit,
    onCopyTextOutput: ((String) -> Unit)? = null
) {
    val semantic = HariMuThemeTokens.semantic
    val isActive = ToolJobWorker.isActiveStatus(job.status)
    val isAttention = ToolJobWorker.isAttentionStatus(job.status)
    val borderColor = when (job.status) {
        ToolJobWorker.STATUS_SUCCEEDED -> semantic.successBorder.copy(alpha = 0.6f)
        ToolJobWorker.STATUS_FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        ToolJobWorker.STATUS_INTERRUPTED -> semantic.warning.copy(alpha = 0.6f)
        ToolJobWorker.STATUS_PREPARING,
        ToolJobWorker.STATUS_RUNNING -> HariMuColors.GroupConverter.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("converter_job_card_${job.jobId}"),
        shape = HariMuThemeTokens.shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolIdBadge(id = job.toolId, accent = HariMuColors.GroupConverter)
                    Text(
                        text = job.toolTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                ConverterJobStatusPill(status = job.status, percent = job.progressPercent)
            }

            if (job.inputSummary.isNotBlank()) {
                Text(
                    text = "#${job.jobId.take(8)} • ${job.inputSummary}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (job.status == ToolJobWorker.STATUS_QUEUED || job.status == ToolJobWorker.STATUS_PREPARING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = job.progressMessage.ifBlank {
                        stringResource(
                            if (job.status == ToolJobWorker.STATUS_PREPARING) R.string.progress_preparing
                            else R.string.converter_queue_status_queued
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (job.status == ToolJobWorker.STATUS_RUNNING) {
                LinearProgressIndicator(
                    progress = { (job.progressPercent.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = HariMuColors.GroupConverter
                )
                if (job.progressMessage.isNotBlank()) {
                    Text(
                        text = job.progressMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("converter_job_cancel_${job.jobId}")
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }

            if (isAttention) {
                if (job.status == ToolJobWorker.STATUS_FAILED || job.status == ToolJobWorker.STATUS_INTERRUPTED) {
                    val containerColor = if (job.status == ToolJobWorker.STATUS_FAILED) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        semantic.warningContainer
                    }
                    val contentColor = if (job.status == ToolJobWorker.STATUS_FAILED) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        semantic.onWarningContainer
                    }
                    val statusIcon = if (job.status == ToolJobWorker.STATUS_FAILED) {
                        Icons.Filled.ErrorOutline
                    } else {
                        Icons.Filled.PauseCircleOutline
                    }
                    Surface(
                        shape = HariMuThemeTokens.shapes.control,
                        color = containerColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = job.errorMessage.ifBlank { job.progressMessage }.ifBlank {
                                    stringResource(
                                        if (job.status == ToolJobWorker.STATUS_FAILED) R.string.converter_queue_status_failed
                                        else R.string.job_status_interrupted
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (!canRetry) {
                    Text(
                        text = stringResource(R.string.job_retry_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (onRetry != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_job_retry_${job.jobId}")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.converter_retry))
                        }
                    }
                }
            }

            if (job.status == ToolJobWorker.STATUS_SUCCEEDED) {
                if (job.summary.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = semantic.success,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = job.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val outputs = decodeJobOutputs(job)
                val primaryOut = outputs.firstOrNull { it.file.exists() }
                if (outputs.isNotEmpty() && primaryOut == null && job.textOutput.isBlank()) {
                    Surface(
                        shape = HariMuThemeTokens.shapes.control,
                        color = semantic.warningContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = semantic.onWarningContainer, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.job_file_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.onWarningContainer
                            )
                        }
                    }
                }

                val existingOutputs = outputs.filter { it.file.exists() }
                if (existingOutputs.size > 1) {
                    Text(
                        text = stringResource(R.string.converter_queue_extra_outputs, existingOutputs.size - 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (job.textOutput.isNotBlank()) {
                    Surface(
                        shape = HariMuThemeTokens.shapes.control,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("converter_job_text_preview_${job.jobId}")
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = job.textOutput.take(360),
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (onCopyTextOutput != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { onCopyTextOutput(job.textOutput) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("converter_job_copy_${job.jobId}")
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.action_copy))
                                    }
                                }
                            }
                        }
                    }
                }

                if (primaryOut != null) {
                    val outFile = primaryOut.file
                    val uploadTask = UploadQueue.findLatestForFile(outFile.absolutePath, uploadTasks)
                    Text(
                        text = "${primaryOut.displayName} • ${FileStorageManager.companionFormatBytes(outFile.length())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (uploadTask != null) {
                        UploadTaskStatusRow(
                            task = uploadTask,
                            onRetry = if (uploadTask.status == UploadStatus.FAILED && onRetryUpload != null) {
                                { onRetryUpload(uploadTask) }
                            } else null
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { onOpenOutput(primaryOut) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_job_open_${job.jobId}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_open))
                        }
                        TextButton(
                            onClick = { onShareOutput(primaryOut) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_job_share_${job.jobId}")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_share))
                        }
                        TextButton(
                            onClick = { onSaveOutput(primaryOut) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_job_save_${job.jobId}")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_save))
                        }
                        TextButton(
                            onClick = { onUploadOutput(primaryOut) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_job_cloud_${job.jobId}")
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_upload_cloud))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConverterJobStatusPill(status: String, percent: Int) {
    val semantic = HariMuThemeTokens.semantic
    val (label, container, content) = when (status) {
        ToolJobWorker.STATUS_QUEUED -> Triple(
            stringResource(R.string.job_status_queued),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        ToolJobWorker.STATUS_PREPARING -> Triple(
            stringResource(R.string.job_status_preparing),
            semantic.infoContainer,
            semantic.onInfoContainer
        )
        ToolJobWorker.STATUS_RUNNING -> Triple(
            stringResource(R.string.job_status_running, percent.coerceIn(0, 100)),
            semantic.infoContainer,
            semantic.onInfoContainer
        )
        ToolJobWorker.STATUS_SUCCEEDED -> Triple(
            stringResource(R.string.job_status_completed),
            semantic.successContainer,
            semantic.onSuccessContainer
        )
        ToolJobWorker.STATUS_FAILED -> Triple(
            stringResource(R.string.job_status_failed),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        ToolJobWorker.STATUS_INTERRUPTED -> Triple(
            stringResource(R.string.job_status_interrupted),
            semantic.warningContainer,
            semantic.onWarningContainer
        )
        else -> Triple(
            stringResource(R.string.job_status_cancelled),
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    CapabilityBadge(label = label, containerColor = container, contentColor = content)
}
