package com.harimu.app.feature.toolscreen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.cloud.UploadQueue
import com.harimu.app.core.cloud.UploadTask
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ResultAction
import com.harimu.app.core.design.ResultActions
import com.harimu.app.core.design.ToolMultiResultCard
import com.harimu.app.core.design.ToolResultCard
import com.harimu.app.core.design.ToolResultPreview
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportStatusBanner(
    exportState: ExportState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (exportState is ExportState.Idle) return
    val semantic = HariMuThemeTokens.semantic
    val (container, border, content, icon, message) = when (exportState) {
        is ExportState.Exporting -> ExportBannerVisuals(
            semantic.infoContainer,
            semantic.infoBorder,
            semantic.onInfoContainer,
            Icons.Filled.Download,
            stringResource(R.string.workspace_export_in_progress, exportState.displayName)
        )
        is ExportState.Succeeded -> ExportBannerVisuals(
            semantic.successContainer,
            semantic.successBorder,
            semantic.onSuccessContainer,
            Icons.Filled.CheckCircle,
            stringResource(R.string.workspace_export_success, exportState.displayName)
        )
        is ExportState.Failed -> ExportBannerVisuals(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.ErrorOutline,
            stringResource(R.string.workspace_export_failed, exportState.displayName)
        )
        ExportState.Idle -> return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("export_status_banner"),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                    modifier = Modifier.weight(1f)
                )
                if (exportState !is ExportState.Exporting) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = content
                        )
                    }
                }
            }
            if (exportState is ExportState.Exporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private data class ExportBannerVisuals(
    val container: androidx.compose.ui.graphics.Color,
    val border: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val message: String
)

@Composable
fun DetailedResultView(
    toolId: String,
    toolGroup: ToolGroup,
    result: ToolResult,
    state: ToolUiState,
    vm: ToolViewModel,
    context: Context,
    container: AppContainer,
    settings: AppSettings,
    safSaveLauncher: ActivityResultLauncher<String>,
    copiedToast: String,
    scope: CoroutineScope,
    onOpenJobCenter: (() -> Unit)? = null
) {
    val visibleOutputs = remember(result.outputs, state.deletedOutputPaths) {
        result.outputs.filterNot { it.file.absolutePath in state.deletedOutputPaths }
    }
    val effectiveResult = remember(result, visibleOutputs) {
        if (visibleOutputs.size == result.outputs.size) result else result.copy(outputs = visibleOutputs)
    }
    val firstOut = effectiveResult.outputs.firstOrNull()
    val uploadTasks by container.uploadQueue.tasksFlow.collectAsStateWithLifecycle()
    val matchedUpload = firstOut?.let { UploadQueue.findLatestForFile(it.file.absolutePath, uploadTasks) }

    fun handleSave(output: ToolOutputFile) {
        vm.exportToDefaultFolderOrRequestSaf(
            output = output,
            defaultTreeUri = settings.outputFolderUri,
            onRequestSaf = { target -> safSaveLauncher.launch(target.displayName) }
        )
    }

    ExportStatusBanner(
        exportState = state.exportState,
        onDismiss = vm::clearExportState
    )

    ToolResultCard(
        result = effectiveResult,
        storage = container.storage,
        actions = ResultActions(
            onOpen = firstOut?.let { out -> { openFile(out, container.storage, context) } },
            onShare = firstOut?.let { out -> { shareFile(out, container.storage, context) } },
            onSave = firstOut?.let { out -> { handleSave(out) } },
            onDelete = if (effectiveResult.outputs.size == 1 && firstOut != null) {
                { vm.deleteOutput(firstOut) }
            } else null,
            onUploadToCloud = firstOut?.let { out -> { uploadToCloud(out, container, settings, scope, context, settings.isEnglish) } },
            onCopyText = { txt -> copyText(txt, context, copiedToast) }
        ),
        showPreview = false,
        uploadTask = matchedUpload,
        onRetryUpload = { task -> retryCloudUpload(task, container, scope, context, settings.isEnglish) },
        onViewJobCenter = onOpenJobCenter
    )

    if (effectiveResult.outputs.size > 1) {
        ToolMultiResultCard(
            outputs = effectiveResult.outputs,
            storage = container.storage,
            onItemClick = { out -> openFile(out, container.storage, context) },
            onItemAction = { out, action ->
                when (action) {
                    ResultAction.Open, ResultAction.Details -> openFile(out, container.storage, context)
                    ResultAction.Share -> shareFile(out, container.storage, context)
                    ResultAction.Save -> handleSave(out)
                    ResultAction.Delete -> vm.deleteOutput(out)
                }
            }
        )
    }

    if (effectiveResult.outputs.isNotEmpty()) {
        effectiveResult.outputs.forEach { out ->
            if (out.file.exists()) {
                val isAudioWithExistingBeforeAfter =
                    toolGroup == ToolGroup.AUDIO && out.mimeType.startsWith("audio/", ignoreCase = true)
                val skipRedundantTextPreview =
                    out.mimeType.startsWith("text/", ignoreCase = true) &&
                        !result.textOutput.isNullOrBlank() &&
                        effectiveResult.outputs.size == 1 &&
                        toolId != "TXT"
                if (!isAudioWithExistingBeforeAfter && !skipRedundantTextPreview) {
                    ToolResultPreview(output = out, storage = container.storage)
                }
            }
        }
    }

    result.textOutput?.let { txt ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.label_output), style = MaterialTheme.typography.labelMedium)
                    IconButton(
                        onClick = { copyText(txt, context, copiedToast) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(txt, style = MonoTextStyle)
            }
        }
    }
}

fun openFile(output: ToolOutputFile, storage: FileStorageManager, context: Context) {
    runCatching {
        context.startActivity(Intent.createChooser(storage.buildOpenIntent(output.file, output.mimeType), output.displayName))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.workspace_open_no_app), Toast.LENGTH_SHORT).show()
    }
}

fun shareFile(output: ToolOutputFile, storage: FileStorageManager, context: Context) {
    runCatching {
        context.startActivity(Intent.createChooser(storage.buildShareIntent(output.file, output.mimeType), output.displayName))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.workspace_share_no_app), Toast.LENGTH_SHORT).show()
    }
}

fun saveFile(
    output: ToolOutputFile,
    storage: FileStorageManager,
    settings: AppSettings,
    scope: CoroutineScope,
    context: Context,
    savedToast: String,
    saveFailedToast: String,
    pendingExportFile: (ToolOutputFile) -> Unit,
    safSaveLauncher: ActivityResultLauncher<String>
) {
    val defaultTreeUri = settings.outputFolderUri
    if (defaultTreeUri.isNotBlank()) {
        scope.launch(Dispatchers.IO) {
            val savedUri = runCatching {
                storage.saveToTreeUri(source = output.file, treeUri = defaultTreeUri.toUri(), mimeType = output.mimeType)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (savedUri != null) {
                    Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                } else {
                    pendingExportFile(output)
                    safSaveLauncher.launch(output.displayName)
                }
            }
        }
    } else {
        pendingExportFile(output)
        safSaveLauncher.launch(output.displayName)
    }
}

fun uploadToCloud(
    output: ToolOutputFile,
    container: AppContainer,
    settings: AppSettings,
    scope: CoroutineScope,
    context: Context,
    isEnglish: Boolean
) {
    if (!output.file.exists()) {
        Toast.makeText(context, context.getString(R.string.job_file_missing), Toast.LENGTH_SHORT).show()
        return
    }
    scope.launch(Dispatchers.IO) {
        val config = container.cloudStore.load()
        if (!config.isConfigured()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.cloud_upload_configure_first), Toast.LENGTH_LONG).show()
            }
        } else {
            container.uploadQueue.enqueue(output.file, output.mimeType, config.provider)
            val uploaded = container.uploadQueue.processPending(
                configProvider = { container.cloudStore.load() },
                persistConfig = { container.cloudStore.save(it) }
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(if (uploaded > 0) R.string.cloud_upload_done else R.string.cloud_upload_queued),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

fun retryCloudUpload(
    task: UploadTask,
    container: AppContainer,
    scope: CoroutineScope,
    context: Context,
    isEnglish: Boolean
) {
    scope.launch(Dispatchers.IO) {
        val uploaded = container.uploadQueue.retryTask(
            taskId = task.id,
            configProvider = { container.cloudStore.load() },
            persistConfig = { container.cloudStore.save(it) }
        )
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(if (uploaded > 0) R.string.cloud_upload_done else R.string.cloud_upload_still_pending),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

fun copyText(text: String, context: Context, copiedToast: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("HariMu", text))
    Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
}
