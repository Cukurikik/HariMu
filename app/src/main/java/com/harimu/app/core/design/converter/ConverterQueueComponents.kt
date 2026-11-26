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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.CapabilityBadge
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.ToolOutputFile
import java.io.File

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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("converter_queue_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.converter_queue_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        if (jobs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.SwapHoriz,
                title = stringResource(R.string.converter_queue_empty_title),
                subtitle = stringResource(R.string.converter_queue_empty_desc)
            )
        } else {
            jobs.take(20).forEach { job ->
                ConverterJobCard(
                    job = job,
                    onCancel = { onCancelJob(job.jobId) },
                    onRetry = if (onRetryJob != null && canRetryJob(job.jobId)) {
                        { onRetryJob(job) }
                    } else null,
                    onOpenOutput = onOpenOutput,
                    onShareOutput = onShareOutput,
                    onSaveOutput = onSaveOutput,
                    onUploadOutput = onUploadOutput
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConverterJobCard(
    job: JobEntity,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)?,
    onOpenOutput: (ToolOutputFile) -> Unit,
    onShareOutput: (ToolOutputFile) -> Unit,
    onSaveOutput: (ToolOutputFile) -> Unit,
    onUploadOutput: (ToolOutputFile) -> Unit
) {
    val semantic = HariMuThemeTokens.semantic
    val isActive = job.status == ToolJobWorker.STATUS_QUEUED || job.status == ToolJobWorker.STATUS_RUNNING
    val borderColor = when (job.status) {
        ToolJobWorker.STATUS_SUCCEEDED -> semantic.successBorder.copy(alpha = 0.6f)
        ToolJobWorker.STATUS_FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
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

            if (job.status == ToolJobWorker.STATUS_QUEUED) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = job.progressMessage.ifBlank { stringResource(R.string.converter_queue_status_queued) },
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

            if (job.status == ToolJobWorker.STATUS_FAILED) {
                Surface(
                    shape = HariMuThemeTokens.shapes.control,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = job.errorMessage.ifBlank { stringResource(R.string.converter_queue_status_failed) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (onRetry != null) {
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

                val outFile = job.outputPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
                if (outFile != null) {
                    val out = ToolOutputFile(
                        file = outFile,
                        mimeType = job.outputMime.ifBlank { "application/octet-stream" },
                        displayName = outFile.name
                    )
                    Text(
                        text = "${outFile.name} • ${FileStorageManager.companionFormatBytes(outFile.length())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { onOpenOutput(out) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_job_open_${job.jobId}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_open))
                        }
                        TextButton(
                            onClick = { onShareOutput(out) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_job_share_${job.jobId}")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_share))
                        }
                        TextButton(
                            onClick = { onSaveOutput(out) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_job_save_${job.jobId}")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_save))
                        }
                        TextButton(
                            onClick = { onUploadOutput(out) },
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
            stringResource(R.string.converter_queue_status_queued),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        ToolJobWorker.STATUS_RUNNING -> Triple(
            stringResource(R.string.converter_queue_status_running, percent),
            semantic.infoContainer,
            semantic.onInfoContainer
        )
        ToolJobWorker.STATUS_SUCCEEDED -> Triple(
            stringResource(R.string.converter_queue_status_done),
            semantic.successContainer,
            semantic.onSuccessContainer
        )
        ToolJobWorker.STATUS_FAILED -> Triple(
            stringResource(R.string.converter_queue_status_failed),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Triple(
            stringResource(R.string.converter_queue_status_cancelled),
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    CapabilityBadge(label = label, containerColor = container, contentColor = content)
}
