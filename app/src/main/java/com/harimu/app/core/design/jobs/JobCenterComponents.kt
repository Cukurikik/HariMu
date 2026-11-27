package com.harimu.app.core.design.jobs

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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.cloud.UploadStatus
import com.harimu.app.core.cloud.UploadTask
import com.harimu.app.core.data.HistoryEntity
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.CapabilityBadge
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolOutputCodec
import com.harimu.app.core.toolapi.ToolOutputFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun decodeJobOutputs(job: JobEntity): List<ToolOutputFile> {
    val decoded = ToolOutputCodec.decode(job.outputsJson).map { entry ->
        ToolOutputFile(
            file = File(entry.path),
            mimeType = entry.mimeType,
            displayName = entry.displayName
        )
    }
    if (decoded.isNotEmpty()) return decoded
    if (job.outputPath.isBlank()) return emptyList()
    val f = File(job.outputPath)
    return listOf(
        ToolOutputFile(
            file = f,
            mimeType = job.outputMime.ifBlank { "application/octet-stream" },
            displayName = f.name
        )
    )
}

fun decodeHistoryOutputs(item: HistoryEntity): List<ToolOutputFile> {
    val decoded = ToolOutputCodec.decode(item.outputsJson).map { entry ->
        ToolOutputFile(
            file = File(entry.path),
            mimeType = entry.mimeType,
            displayName = entry.displayName
        )
    }
    if (decoded.isNotEmpty()) return decoded
    if (item.outputPath.isBlank()) return emptyList()
    val f = File(item.outputPath)
    return listOf(
        ToolOutputFile(
            file = f,
            mimeType = item.mimeType.ifBlank { "application/octet-stream" },
            displayName = item.outputName.ifBlank { f.name }
        )
    )
}

fun formatElapsedDuration(
    startedAt: Long,
    completedAt: Long,
    createdAt: Long,
    isEnglish: Boolean
): String {
    val start = if (startedAt > 0L) startedAt else createdAt
    val end = if (completedAt > 0L) completedAt else System.currentTimeMillis()
    val elapsedMs = (end - start).coerceAtLeast(0L)
    if (elapsedMs < 1_000L) return if (isEnglish) "<1s" else "<1 dtk"
    val totalSec = elapsedMs / 1_000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return when {
        mins >= 60L -> {
            val hrs = mins / 60L
            val remMins = mins % 60L
            if (isEnglish) "${hrs}h ${remMins}m" else "${hrs}j ${remMins}m"
        }
        mins > 0L -> if (isEnglish) "${mins}m ${secs}s" else "${mins}m ${secs}d"
        else -> if (isEnglish) "${secs}s" else "${secs} dtk"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobStatusBadgeRow(
    status: String,
    percent: Int,
    isRetryable: Boolean = false,
    executionMode: String? = null,
    modifier: Modifier = Modifier
) {
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

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CapabilityBadge(
            label = label,
            containerColor = container,
            contentColor = content
        )
        if (isRetryable && ToolJobWorker.isAttentionStatus(status)) {
            CapabilityBadge(
                label = stringResource(R.string.job_badge_retryable),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        if (!executionMode.isNullOrBlank()) {
            val modeLabel = if (executionMode == ToolJobWorker.MODE_IN_PROCESS) {
                stringResource(R.string.job_badge_in_process)
            } else {
                stringResource(R.string.job_badge_worker)
            }
            CapabilityBadge(
                label = modeLabel,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UploadTaskStatusRow(
    task: UploadTask,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val semantic = HariMuThemeTokens.semantic
    val (label, icon, container, content) = when (task.status) {
        UploadStatus.QUEUED -> UploadPalette(
            stringResource(R.string.job_upload_status_queued),
            Icons.Filled.CloudQueue,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        UploadStatus.RUNNING -> UploadPalette(
            stringResource(R.string.job_upload_status_running),
            Icons.Filled.CloudUpload,
            semantic.infoContainer,
            semantic.onInfoContainer
        )
        UploadStatus.DONE -> UploadPalette(
            stringResource(R.string.job_upload_status_done),
            Icons.Filled.CloudDone,
            semantic.successContainer,
            semantic.onSuccessContainer
        )
        UploadStatus.FAILED -> UploadPalette(
            stringResource(R.string.job_upload_status_failed),
            Icons.Filled.CloudOff,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = HariMuThemeTokens.shapes.control,
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$label • ${task.provider.name}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = when {
                    task.status == UploadStatus.DONE && task.remoteUrl.isNotBlank() -> task.remoteUrl
                    task.status == UploadStatus.FAILED && task.lastError.isNotBlank() -> task.lastError
                    task.attempts > 0 -> "${task.attempts}x"
                    else -> task.displayName
                }
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MonoTextStyle,
                        color = content,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (task.status == UploadStatus.FAILED && onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.job_upload_retry), color = content)
                }
            }
        }
    }
}

private data class UploadPalette(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobExecutionCard(
    job: JobEntity,
    canRetry: Boolean,
    uploadTask: UploadTask? = null,
    isEnglish: Boolean,
    dateFormatter: SimpleDateFormat,
    onCancel: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onRetryUpload: ((UploadTask) -> Unit)? = null,
    onOpenTool: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val semantic = HariMuThemeTokens.semantic
    val spacing = HariMuThemeTokens.spacing
    val group = ToolGroup.entries.firstOrNull { it.name == job.groupName } ?: ToolGroup.CORE
    val accent = group.accentColor()
    val isActive = ToolJobWorker.isActiveStatus(job.status)
    val isAttention = ToolJobWorker.isAttentionStatus(job.status)

    val borderColor = when (job.status) {
        ToolJobWorker.STATUS_SUCCEEDED -> semantic.successBorder.copy(alpha = 0.6f)
        ToolJobWorker.STATUS_FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        ToolJobWorker.STATUS_INTERRUPTED -> semantic.warning.copy(alpha = 0.6f)
        ToolJobWorker.STATUS_PREPARING,
        ToolJobWorker.STATUS_RUNNING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha)
    }

    val outputs = decodeJobOutputs(job)
    val existingOutputs = outputs.filter { it.file.exists() }
    val missingOutputs = job.status == ToolJobWorker.STATUS_SUCCEEDED &&
        outputs.isNotEmpty() &&
        existingOutputs.isEmpty() &&
        job.textOutput.isBlank()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("job_card_${job.jobId}"),
        shape = HariMuThemeTokens.shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        onClick = { onOpenDetails?.invoke() }
    ) {
        Column(
            modifier = Modifier.padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolIdBadge(id = job.toolId, accent = accent)
                    Text(
                        text = job.toolTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (onDelete != null && !isActive) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_job_${job.jobId}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            JobStatusBadgeRow(
                status = job.status,
                percent = job.progressPercent,
                isRetryable = canRetry,
                executionMode = job.executionMode
            )

            val metaParts = buildList {
                add(dateFormatter.format(Date(job.createdAt)))
                add("#${job.jobId.take(8)}")
                if (job.inputSummary.isNotBlank()) add(job.inputSummary)
                if (!isActive && (job.startedAt > 0L || job.completedAt > 0L)) {
                    add(formatElapsedDuration(job.startedAt, job.completedAt, job.createdAt, isEnglish))
                }
                val firstExisting = existingOutputs.firstOrNull()
                if (firstExisting != null) {
                    add(FileStorageManager.companionFormatBytes(firstExisting.file.length()))
                }
            }
            Text(
                text = metaParts.joinToString(" • "),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            when (job.status) {
                ToolJobWorker.STATUS_QUEUED,
                ToolJobWorker.STATUS_PREPARING -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round
                    )
                    if (job.progressMessage.isNotBlank()) {
                        Text(
                            text = job.progressMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ToolJobWorker.STATUS_RUNNING -> {
                    LinearProgressIndicator(
                        progress = { (job.progressPercent.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round
                    )
                    if (job.progressMessage.isNotBlank()) {
                        Text(
                            text = job.progressMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ToolJobWorker.STATUS_FAILED,
                ToolJobWorker.STATUS_INTERRUPTED -> {
                    val container = if (job.status == ToolJobWorker.STATUS_FAILED) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        semantic.warningContainer
                    }
                    val content = if (job.status == ToolJobWorker.STATUS_FAILED) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        semantic.onWarningContainer
                    }
                    val icon = if (job.status == ToolJobWorker.STATUS_FAILED) {
                        Icons.Filled.ErrorOutline
                    } else {
                        Icons.Filled.PauseCircleOutline
                    }
                    val msg = job.errorMessage.ifBlank { job.progressMessage }.ifBlank {
                        stringResource(
                            if (job.status == ToolJobWorker.STATUS_FAILED) R.string.job_status_failed
                            else R.string.job_status_interrupted
                        )
                    }
                    Surface(
                        shape = HariMuThemeTokens.shapes.control,
                        color = container,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = content,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (!canRetry) {
                        Text(
                            text = stringResource(R.string.job_retry_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ToolJobWorker.STATUS_SUCCEEDED -> {
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
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (missingOutputs) {
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
                }
                ToolJobWorker.STATUS_CANCELLED -> {
                    if (!canRetry) {
                        Text(
                            text = stringResource(R.string.job_retry_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uploadTask != null) {
                UploadTaskStatusRow(
                    task = uploadTask,
                    onRetry = if (uploadTask.status == UploadStatus.FAILED && onRetryUpload != null) {
                        { onRetryUpload(uploadTask) }
                    } else null
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isActive && onCancel != null) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_job_${job.jobId}")
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                if (isAttention && canRetry && onRetry != null) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("retry_job_${job.jobId}")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.job_action_retry))
                    }
                }
                if (onOpenTool != null) {
                    TextButton(
                        onClick = onOpenTool,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_tool_job_${job.jobId}")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.job_action_open_tool))
                    }
                }
                if (onOpenDetails != null) {
                    TextButton(
                        onClick = onOpenDetails,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("details_job_${job.jobId}")
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_details))
                    }
                }
            }
        }
    }
}
