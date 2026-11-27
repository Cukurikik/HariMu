package com.harimu.app.feature.history

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.cloud.UploadQueue
import com.harimu.app.core.cloud.UploadStatus
import com.harimu.app.core.cloud.UploadTask
import com.harimu.app.core.data.HistoryEntity
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.ToolResultPreview
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.jobs.JobStatusBadgeRow
import com.harimu.app.core.design.jobs.UploadTaskStatusRow
import com.harimu.app.core.design.jobs.decodeHistoryOutputs
import com.harimu.app.core.design.jobs.decodeJobOutputs
import com.harimu.app.core.design.jobs.formatElapsedDuration
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolOutputFile
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JobResultDetailSheet(
    job: JobEntity?,
    history: HistoryEntity?,
    uploadTasks: List<UploadTask>,
    container: AppContainer,
    isEnglish: Boolean,
    dateFormatter: SimpleDateFormat,
    canRetry: Boolean,
    onCancelJob: (String) -> Unit,
    onRetryJob: (String) -> Unit,
    onRetryUpload: (UploadTask) -> Unit,
    onUploadOutput: (ToolOutputFile) -> Unit,
    onSaveOutput: (ToolOutputFile) -> Unit,
    onOpenOutput: (ToolOutputFile) -> Unit,
    onShareOutput: (ToolOutputFile) -> Unit,
    onOpenTool: (toolId: String, jobId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val semantic = HariMuThemeTokens.semantic
    val spacing = HariMuThemeTokens.spacing
    val copiedToast = stringResource(R.string.toast_copied)

    val toolId = job?.toolId ?: history?.toolId.orEmpty()
    val toolTitle = job?.toolTitle ?: history?.toolTitle.orEmpty()
    val groupName = job?.groupName ?: history?.groupName.orEmpty()
    val group = ToolGroup.entries.firstOrNull { it.name == groupName } ?: ToolGroup.CORE
    val accent = group.accentColor()

    val outputs = when {
        job != null -> decodeJobOutputs(job)
        history != null -> decodeHistoryOutputs(history)
        else -> emptyList()
    }
    val existingOutputs = outputs.filter { it.file.exists() }
    val primaryExisting = existingOutputs.firstOrNull()
    val textOutput = job?.textOutput?.takeIf { it.isNotBlank() }
        ?: history?.textOutput?.takeIf { it.isNotBlank() }
        ?: primaryExisting?.takeIf { it.mimeType.startsWith("text/") && it.file.length() < 16_384L }
            ?.file?.runCatching { readText() }?.getOrNull()
    val errorMsg = job?.errorMessage?.takeIf { it.isNotBlank() }
        ?: history?.errorMessage?.takeIf { it.isNotBlank() }.orEmpty()
    val summary = job?.summary?.takeIf { it.isNotBlank() }
        ?: history?.summary?.takeIf { it.isNotBlank() }.orEmpty()
    val missingFiles = outputs.isNotEmpty() && existingOutputs.isEmpty() && textOutput.isNullOrBlank()
    val matchedUpload = (primaryExisting?.file?.absolutePath ?: outputs.firstOrNull()?.file?.absolutePath)
        ?.let { UploadQueue.findLatestForFile(it, uploadTasks) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("job_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.md, vertical = spacing.xs)
                .padding(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.job_detail_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (toolId.isNotBlank()) {
                    ToolIdBadge(id = toolId, accent = accent)
                }
                Text(
                    text = toolTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (job != null) {
                JobStatusBadgeRow(
                    status = job.status,
                    percent = job.progressPercent,
                    isRetryable = canRetry,
                    executionMode = job.executionMode
                )
                if (job.status == ToolJobWorker.STATUS_RUNNING) {
                    LinearProgressIndicator(
                        progress = { (job.progressPercent.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round
                    )
                } else if (job.status == ToolJobWorker.STATUS_QUEUED || job.status == ToolJobWorker.STATUS_PREPARING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                }
            } else if (history != null) {
                JobStatusBadgeRow(
                    status = if (history.success) ToolJobWorker.STATUS_SUCCEEDED else ToolJobWorker.STATUS_FAILED,
                    percent = if (history.success) 100 else 0,
                    isRetryable = false,
                    executionMode = null
                )
            }

            if (summary.isNotBlank()) {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = HariMuThemeTokens.shapes.control,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val displayJobId = job?.jobId ?: history?.jobId.orEmpty()
                    if (displayJobId.isNotBlank()) {
                        MetaRow(stringResource(R.string.job_detail_id), "#${displayJobId.take(12)}")
                    }
                    if (job != null) {
                        val modeLabel = if (job.executionMode == ToolJobWorker.MODE_IN_PROCESS) {
                            stringResource(R.string.job_badge_in_process)
                        } else {
                            stringResource(R.string.job_badge_worker)
                        }
                        MetaRow(stringResource(R.string.job_detail_mode), modeLabel)
                        MetaRow(stringResource(R.string.job_detail_created), dateFormatter.format(Date(job.createdAt)))
                        if (job.startedAt > 0L) {
                            MetaRow(stringResource(R.string.job_detail_started), dateFormatter.format(Date(job.startedAt)))
                        }
                        if (job.completedAt > 0L) {
                            MetaRow(stringResource(R.string.job_detail_finished), dateFormatter.format(Date(job.completedAt)))
                        }
                        if (job.startedAt > 0L || job.completedAt > 0L) {
                            MetaRow(
                                stringResource(R.string.job_detail_duration),
                                formatElapsedDuration(job.startedAt, job.completedAt, job.createdAt, isEnglish)
                            )
                        }
                        if (job.inputSummary.isNotBlank()) {
                            MetaRow(stringResource(R.string.job_detail_inputs), job.inputSummary)
                        }
                    } else if (history != null) {
                        MetaRow(stringResource(R.string.job_detail_created), dateFormatter.format(Date(history.createdAt)))
                    }
                }
            }

            if (errorMsg.isNotBlank()) {
                val isInterrupted = job?.status == ToolJobWorker.STATUS_INTERRUPTED
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = HariMuThemeTokens.shapes.control,
                    color = if (isInterrupted) semantic.warningContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isInterrupted) Icons.Filled.PauseCircleOutline else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = if (isInterrupted) semantic.onWarningContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.job_detail_error),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isInterrupted) semantic.onWarningContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isInterrupted) semantic.onWarningContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (missingFiles) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = HariMuThemeTokens.shapes.control,
                    color = semantic.warningContainer
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = semantic.onWarningContainer, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.job_file_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.onWarningContainer
                        )
                    }
                }
            }

            if (primaryExisting != null) {
                Text(
                    text = stringResource(R.string.job_detail_outputs, existingOutputs.size),
                    style = MaterialTheme.typography.titleSmall
                )
                ToolResultPreview(output = primaryExisting, storage = container.storage)
                existingOutputs.forEach { out ->
                    Text(
                        text = "${out.displayName} • ${container.storage.formatBytes(out.file.length())} • ${out.mimeType}",
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    OutlinedButton(
                        onClick = { onOpenOutput(primaryExisting) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_open))
                    }
                    OutlinedButton(
                        onClick = { onShareOutput(primaryExisting) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_share))
                    }
                    Button(
                        onClick = { onSaveOutput(primaryExisting) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_save))
                    }
                    OutlinedButton(
                        onClick = { onUploadOutput(primaryExisting) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_upload_cloud))
                    }
                }
            }

            if (!textOutput.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.label_output), style = MaterialTheme.typography.labelMedium)
                            IconButton(
                                onClick = {
                                    context.getSystemService(ClipboardManager::class.java)
                                        ?.setPrimaryClip(ClipData.newPlainText("HariMu", textOutput))
                                    Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("job_detail_copy_text_btn")
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy), modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            text = textOutput.take(2000),
                            style = MonoTextStyle,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (matchedUpload != null) {
                UploadTaskStatusRow(
                    task = matchedUpload,
                    onRetry = if (matchedUpload.status == UploadStatus.FAILED) {
                        { onRetryUpload(matchedUpload) }
                    } else null
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (job != null && ToolJobWorker.isActiveStatus(job.status)) {
                    OutlinedButton(
                        onClick = { onCancelJob(job.jobId) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("job_detail_cancel_btn")
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                if (job != null && ToolJobWorker.isAttentionStatus(job.status) && canRetry) {
                    Button(
                        onClick = { onRetryJob(job.jobId) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("job_detail_retry_btn")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.job_action_retry))
                    }
                }
                if (toolId.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onOpenTool(toolId, job?.jobId ?: history?.jobId?.takeIf { it.isNotBlank() })
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("job_detail_open_tool_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.job_action_open_tool))
                    }
                }
            }
            Spacer(Modifier.height(spacing.xs))
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
