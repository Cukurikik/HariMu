package com.harimu.app.feature.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.cloud.UploadQueue
import com.harimu.app.core.cloud.UploadStatus
import com.harimu.app.core.cloud.UploadTask
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.data.HistoryEntity
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.jobs.JobExecutionCard
import com.harimu.app.core.design.jobs.UploadTaskStatusRow
import com.harimu.app.core.design.jobs.decodeHistoryOutputs
import com.harimu.app.core.design.jobs.decodeJobOutputs
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.feature.toolscreen.openFile
import com.harimu.app.feature.toolscreen.retryCloudUpload
import com.harimu.app.feature.toolscreen.saveFile
import com.harimu.app.feature.toolscreen.shareFile
import com.harimu.app.feature.toolscreen.uploadToCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class JobCenterFilter {
    ALL,
    ACTIVE,
    ATTENTION,
    HISTORY,
    UPLOADS
}

private data class DetailTarget(
    val jobId: String? = null,
    val history: HistoryEntity? = null
)

private data class ResolvedHistoryFiles(
    val existing: List<ToolOutputFile> = emptyList(),
    val resolved: Boolean = false
)

@Composable
fun HistoryScreen(
    container: AppContainer,
    initialFilter: JobCenterFilter = JobCenterFilter.ALL,
    onOpenTool: (toolId: String, jobId: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val currentLocale = remember(configuration) {
        if (!configuration.locales.isEmpty) configuration.locales[0] else Locale.forLanguageTag("id")
    }
    val scope = rememberCoroutineScope()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val items by container.workspace.historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val jobs by container.jobManager.observeAllJobs().collectAsStateWithLifecycle(initialValue = emptyList())
    val uploadTasks by container.uploadQueue.tasksFlow.collectAsStateWithLifecycle()

    var selectedFilterName by rememberSaveable(initialFilter.name) { mutableStateOf(initialFilter.name) }
    val selectedFilter = remember(selectedFilterName) {
        JobCenterFilter.entries.firstOrNull { it.name == selectedFilterName } ?: initialFilter
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var detailTarget by remember { mutableStateOf<DetailTarget?>(null) }
    var pendingSave by remember { mutableStateOf<ToolOutputFile?>(null) }

    LaunchedEffect(Unit) {
        container.uploadQueue.snapshot()
    }

    val isEnglish = settings.isEnglish || currentLocale.language == "en"
    val savedToast = stringResource(R.string.toast_saved)
    val failedToast = stringResource(R.string.toast_save_failed)

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        val src = pendingSave
        if (destUri != null && src != null && src.file.exists()) {
            scope.launch(Dispatchers.IO) {
                val ok = container.storage.copyFileToUri(src.file, destUri)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, if (ok) savedToast else failedToast, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun triggerSave(output: ToolOutputFile) {
        saveFile(
            output = output,
            storage = container.storage,
            settings = settings,
            scope = scope,
            context = context,
            savedToast = savedToast,
            saveFailedToast = failedToast,
            pendingExportFile = { pendingSave = it },
            safSaveLauncher = safLauncher
        )
    }

    val activeJobs = remember(jobs) { jobs.filter { ToolJobWorker.isActiveStatus(it.status) } }
    val attentionJobs = remember(jobs) { jobs.filter { ToolJobWorker.isAttentionStatus(it.status) } }
    val finishedJobs = remember(jobs) { jobs.filter { !ToolJobWorker.isActiveStatus(it.status) } }
    val jobsById = remember(jobs) { jobs.associateBy { it.jobId } }

    val query = remember(searchQuery) { searchQuery.trim().lowercase() }
    val filteredActiveJobs = remember(activeJobs, query) {
        if (query.isBlank()) activeJobs else activeJobs.filter { it.matchesQuery(query) }
    }
    val filteredAttentionJobs = remember(attentionJobs, query) {
        if (query.isBlank()) attentionJobs else attentionJobs.filter { it.matchesQuery(query) }
    }
    val filteredHistory = remember(items, query) {
        if (query.isBlank()) items else items.filter { it.matchesQuery(query) }
    }
    val filteredFinishedJobs = remember(finishedJobs, query) {
        if (query.isBlank()) finishedJobs else finishedJobs.filter { it.matchesQuery(query) }
    }
    val filteredUploads = remember(uploadTasks, query) {
        if (query.isBlank()) uploadTasks else uploadTasks.filter {
            it.displayName.lowercase().contains(query) ||
                it.provider.name.lowercase().contains(query) ||
                it.status.name.lowercase().contains(query) ||
                it.lastError.lowercase().contains(query)
        }
    }

    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    val dateFormatter = remember(currentLocale) { SimpleDateFormat("dd MMM HH:mm", currentLocale) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = layout.catalogMaxWidth)
                .testTag("job_center_list"),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            item(key = "overview") {
                JobCenterOverviewRow(
                    activeCount = activeJobs.size,
                    attentionCount = attentionJobs.size,
                    historyCount = items.size,
                    uploadCount = uploadTasks.size,
                    onSelectFilter = { selectedFilterName = it.name }
                )
            }

            item(key = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_search_input"),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.job_search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    FilterChip(
                        selected = selectedFilter == JobCenterFilter.ALL,
                        onClick = { selectedFilterName = JobCenterFilter.ALL.name },
                        label = { Text(stringResource(R.string.job_filter_all)) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("job_filter_all")
                    )
                    FilterChip(
                        selected = selectedFilter == JobCenterFilter.ACTIVE,
                        onClick = { selectedFilterName = JobCenterFilter.ACTIVE.name },
                        label = { Text(stringResource(R.string.job_filter_active, activeJobs.size)) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("job_filter_active")
                    )
                    FilterChip(
                        selected = selectedFilter == JobCenterFilter.ATTENTION,
                        onClick = { selectedFilterName = JobCenterFilter.ATTENTION.name },
                        label = { Text(stringResource(R.string.job_filter_attention, attentionJobs.size)) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("job_filter_attention")
                    )
                    FilterChip(
                        selected = selectedFilter == JobCenterFilter.HISTORY,
                        onClick = { selectedFilterName = JobCenterFilter.HISTORY.name },
                        label = { Text(stringResource(R.string.job_filter_history, items.size)) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("job_filter_history")
                    )
                    FilterChip(
                        selected = selectedFilter == JobCenterFilter.UPLOADS,
                        onClick = { selectedFilterName = JobCenterFilter.UPLOADS.name },
                        label = { Text(stringResource(R.string.job_filter_uploads, uploadTasks.size)) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("job_filter_uploads")
                    )
                }
            }

            if (selectedFilter == JobCenterFilter.ALL || selectedFilter == JobCenterFilter.ACTIVE) {
                if (filteredActiveJobs.isNotEmpty()) {
                    item(key = "header_active") {
                        Text(
                            text = stringResource(R.string.job_section_active_now, filteredActiveJobs.size),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.testTag("job_section_active")
                        )
                    }
                    items(filteredActiveJobs, key = { "active_${it.jobId}" }) { job ->
                        JobExecutionCard(
                            job = job,
                            canRetry = false,
                            uploadTask = decodeJobOutputs(job).firstOrNull()?.let { out ->
                                UploadQueue.findLatestForFile(out.file.absolutePath, uploadTasks)
                            },
                            isEnglish = isEnglish,
                            dateFormatter = dateFormatter,
                            onCancel = { scope.launch { container.jobManager.cancelJob(job.jobId) } },
                            onOpenTool = { onOpenTool(job.toolId, job.jobId) },
                            onOpenDetails = { detailTarget = DetailTarget(jobId = job.jobId) }
                        )
                    }
                } else if (selectedFilter == JobCenterFilter.ACTIVE) {
                    item(key = "empty_active") {
                        EmptyState(
                            icon = Icons.Filled.WorkHistory,
                            title = stringResource(R.string.job_empty_active_title),
                            subtitle = stringResource(R.string.job_empty_active_desc)
                        )
                    }
                }
            }

            if (selectedFilter == JobCenterFilter.ALL || selectedFilter == JobCenterFilter.ATTENTION) {
                if (filteredAttentionJobs.isNotEmpty()) {
                    item(key = "header_attention") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("job_section_attention"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.job_section_needs_attention, filteredAttentionJobs.size),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { scope.launch(Dispatchers.IO) { container.jobManager.clearFinishedJobs() } },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("job_clear_finished")
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.job_clear_finished))
                            }
                        }
                    }
                    items(filteredAttentionJobs, key = { "attention_${it.jobId}" }) { job ->
                        val retryable = remember(job.jobId, job.status, job.isRetryable, job.updatedAt) {
                            container.jobManager.canRetry(job)
                        }
                        JobExecutionCard(
                            job = job,
                            canRetry = retryable,
                            isEnglish = isEnglish,
                            dateFormatter = dateFormatter,
                            onRetry = {
                                scope.launch {
                                    val retried = container.jobManager.retryJob(job.jobId, isEnglish)
                                    val msg = if (retried != null) {
                                        context.getString(R.string.job_retry_started, retried.toolTitle)
                                    } else {
                                        context.getString(R.string.job_retry_unavailable)
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenTool = { onOpenTool(job.toolId, job.jobId) },
                            onOpenDetails = { detailTarget = DetailTarget(jobId = job.jobId) },
                            onDelete = { scope.launch(Dispatchers.IO) { container.jobManager.deleteJob(job.jobId) } }
                        )
                    }
                } else if (selectedFilter == JobCenterFilter.ATTENTION) {
                    item(key = "empty_attention") {
                        EmptyState(
                            icon = Icons.Filled.ErrorOutline,
                            title = stringResource(R.string.job_empty_attention_title),
                            subtitle = stringResource(R.string.job_empty_attention_desc)
                        )
                    }
                }
            }

            if (selectedFilter == JobCenterFilter.ALL || selectedFilter == JobCenterFilter.HISTORY) {
                if (filteredHistory.isNotEmpty()) {
                    item(key = "header_history") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.history_count_title, filteredHistory.size),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = { scope.launch(Dispatchers.IO) { container.workspace.clearHistory() } },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_history_button")
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.action_clear))
                            }
                        }
                    }

                    items(filteredHistory, key = { "hist_${it.id}" }) { item ->
                        val outputs = remember(item.id, item.outputsJson, item.outputPath, item.outputName, item.mimeType) {
                            decodeHistoryOutputs(item)
                        }
                        val resolvedFiles by produceState(
                            initialValue = ResolvedHistoryFiles(),
                            key1 = item.id,
                            key2 = outputs
                        ) {
                            value = withContext(Dispatchers.IO) {
                                ResolvedHistoryFiles(
                                    existing = outputs.filter { it.file.exists() },
                                    resolved = true
                                )
                            }
                        }
                        val primaryOut = resolvedFiles.existing.firstOrNull()
                        val matchedUpload = (primaryOut?.file?.absolutePath ?: item.outputPath)
                            .let { UploadQueue.findLatestForFile(it, uploadTasks) }
                        HistoryItemCard(
                            item = item,
                            primaryOut = primaryOut,
                            hasMissingFile = resolvedFiles.resolved && outputs.isNotEmpty() && resolvedFiles.existing.isEmpty() && item.textOutput.isBlank(),
                            uploadTask = matchedUpload,
                            dateFormatter = dateFormatter,
                            storageFormat = { container.storage.formatBytes(it) },
                            onClick = {
                                val linkedJob = item.jobId.takeIf { it.isNotBlank() }?.let { jobsById[it] }
                                detailTarget = DetailTarget(jobId = linkedJob?.jobId, history = item)
                            },
                            onDelete = { scope.launch(Dispatchers.IO) { container.workspace.deleteHistory(item.id) } },
                            onOpen = { out -> openFile(out, container.storage, context) },
                            onShare = { out -> shareFile(out, container.storage, context) },
                            onSave = ::triggerSave,
                            onUpload = { out -> uploadToCloud(out, container, settings, scope, context, isEnglish) },
                            onRetryUpload = { task -> retryCloudUpload(task, container, scope, context, isEnglish) }
                        )
                    }
                }

                if (selectedFilter == JobCenterFilter.HISTORY && filteredFinishedJobs.isNotEmpty()) {
                    item(key = "header_recent_jobs") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.job_section_recent_jobs, filteredFinishedJobs.size),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { scope.launch(Dispatchers.IO) { container.jobManager.clearFinishedJobs() } },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("job_clear_finished")
                            ) {
                                Text(stringResource(R.string.job_clear_finished))
                            }
                        }
                    }
                    items(filteredFinishedJobs.take(20), key = { "log_${it.jobId}" }) { job ->
                        val retryable = remember(job.jobId, job.status, job.isRetryable, job.updatedAt) {
                            container.jobManager.canRetry(job)
                        }
                        JobExecutionCard(
                            job = job,
                            canRetry = retryable,
                            uploadTask = decodeJobOutputs(job).firstOrNull()?.let { out ->
                                UploadQueue.findLatestForFile(out.file.absolutePath, uploadTasks)
                            },
                            isEnglish = isEnglish,
                            dateFormatter = dateFormatter,
                            onRetry = if (ToolJobWorker.isAttentionStatus(job.status) && retryable) {
                                { scope.launch { container.jobManager.retryJob(job.jobId, isEnglish) } }
                            } else null,
                            onRetryUpload = { task -> retryCloudUpload(task, container, scope, context, isEnglish) },
                            onOpenTool = { onOpenTool(job.toolId, job.jobId) },
                            onOpenDetails = { detailTarget = DetailTarget(jobId = job.jobId) },
                            onDelete = { scope.launch(Dispatchers.IO) { container.jobManager.deleteJob(job.jobId) } }
                        )
                    }
                }

                val showEmptyAll = selectedFilter == JobCenterFilter.ALL &&
                    filteredActiveJobs.isEmpty() && filteredAttentionJobs.isEmpty() && filteredHistory.isEmpty()
                val showEmptyHistory = selectedFilter == JobCenterFilter.HISTORY &&
                    filteredHistory.isEmpty() && filteredFinishedJobs.isEmpty()
                if (showEmptyAll || showEmptyHistory) {
                    item(key = "empty_history") {
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = stringResource(R.string.history_empty_title),
                            subtitle = stringResource(R.string.history_empty_desc)
                        )
                    }
                }
            }

            if (selectedFilter == JobCenterFilter.UPLOADS) {
                item(key = "upload_controls") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    container.uploadQueue.processPending(
                                        configProvider = { container.cloudStore.load() },
                                        persistConfig = { container.cloudStore.save(it) }
                                    )
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text(stringResource(R.string.job_upload_process)) }
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    container.uploadQueue.retryFailed(
                                        configProvider = { container.cloudStore.load() },
                                        persistConfig = { container.cloudStore.save(it) }
                                    )
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text(stringResource(R.string.job_upload_retry_all)) }
                        TextButton(
                            onClick = { scope.launch(Dispatchers.IO) { container.uploadQueue.clearFinished() } },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) { Text(stringResource(R.string.job_upload_clear)) }
                    }
                }
                if (filteredUploads.isEmpty()) {
                    item(key = "empty_uploads") {
                        EmptyState(
                            icon = Icons.Filled.CloudQueue,
                            title = stringResource(R.string.job_empty_uploads_title),
                            subtitle = stringResource(R.string.job_empty_uploads_desc)
                        )
                    }
                } else {
                    items(filteredUploads, key = { "upload_${it.id}" }) { task ->
                        UploadTaskStatusRow(
                            task = task,
                            onRetry = if (task.status == UploadStatus.FAILED) {
                                { retryCloudUpload(task, container, scope, context, isEnglish) }
                            } else null,
                            modifier = Modifier.testTag("upload_task_${task.id}")
                        )
                    }
                }
            }
        }
    }

    val activeTarget = detailTarget
    if (activeTarget != null) {
        val currentJob = activeTarget.jobId?.let { jobsById[it] }
        val canRetryTarget = currentJob != null && container.jobManager.canRetry(currentJob)
        JobResultDetailSheet(
            job = currentJob,
            history = activeTarget.history,
            uploadTasks = uploadTasks,
            container = container,
            isEnglish = isEnglish,
            dateFormatter = dateFormatter,
            canRetry = canRetryTarget,
            onCancelJob = { id -> scope.launch { container.jobManager.cancelJob(id) } },
            onRetryJob = { id ->
                scope.launch {
                    val retried = container.jobManager.retryJob(id, isEnglish)
                    detailTarget = retried?.let { DetailTarget(jobId = it.jobId) }
                }
            },
            onRetryUpload = { task -> retryCloudUpload(task, container, scope, context, isEnglish) },
            onUploadOutput = { out -> uploadToCloud(out, container, settings, scope, context, isEnglish) },
            onSaveOutput = ::triggerSave,
            onOpenOutput = { out -> openFile(out, container.storage, context) },
            onShareOutput = { out -> shareFile(out, container.storage, context) },
            onOpenTool = onOpenTool,
            onDismiss = { detailTarget = null }
        )
    }
}

@Composable
private fun JobCenterOverviewRow(
    activeCount: Int,
    attentionCount: Int,
    historyCount: Int,
    uploadCount: Int,
    onSelectFilter: (JobCenterFilter) -> Unit
) {
    val semantic = HariMuThemeTokens.semantic
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("job_overview_card"),
        shape = HariMuThemeTokens.shapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OverviewStatTile(
                count = activeCount,
                label = stringResource(R.string.job_overview_active),
                accent = MaterialTheme.colorScheme.primary,
                tag = "job_stat_active",
                onClick = { onSelectFilter(JobCenterFilter.ACTIVE) },
                modifier = Modifier.weight(1f)
            )
            OverviewStatTile(
                count = attentionCount,
                label = stringResource(R.string.job_overview_attention),
                accent = if (attentionCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                tag = "job_stat_attention",
                onClick = { onSelectFilter(JobCenterFilter.ATTENTION) },
                modifier = Modifier.weight(1f)
            )
            OverviewStatTile(
                count = historyCount,
                label = stringResource(R.string.job_overview_completed),
                accent = semantic.success,
                tag = "job_stat_history",
                onClick = { onSelectFilter(JobCenterFilter.HISTORY) },
                modifier = Modifier.weight(1f)
            )
            OverviewStatTile(
                count = uploadCount,
                label = stringResource(R.string.job_overview_uploads),
                accent = MaterialTheme.colorScheme.secondary,
                tag = "job_stat_uploads",
                onClick = { onSelectFilter(JobCenterFilter.UPLOADS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OverviewStatTile(
    count: Int,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(HariMuThemeTokens.shapes.control)
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = HariMuThemeTokens.shapes.control,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryItemCard(
    item: HistoryEntity,
    primaryOut: ToolOutputFile?,
    hasMissingFile: Boolean,
    uploadTask: UploadTask?,
    dateFormatter: SimpleDateFormat,
    storageFormat: (Long) -> String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onOpen: (ToolOutputFile) -> Unit,
    onShare: (ToolOutputFile) -> Unit,
    onSave: (ToolOutputFile) -> Unit,
    onUpload: (ToolOutputFile) -> Unit,
    onRetryUpload: (UploadTask) -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    val semantic = HariMuThemeTokens.semantic
    val group = ToolGroup.entries.firstOrNull { it.name == item.groupName } ?: ToolGroup.CORE
    val timeStr = dateFormatter.format(Date(item.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${item.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = layout.cardBorderAlpha)),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    ToolIdBadge(id = item.toolId, accent = group.accentColor())
                    Text(
                        text = item.toolTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .testTag("delete_history_${item.id}")
                        .size(48.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (item.summary.isNotBlank()) {
                Text(item.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = buildString {
                append("$timeStr • ${storageFormat(item.fileSizeBytes)}")
                if (item.jobId.isNotBlank()) append(" • #${item.jobId.take(8)}")
            }
            Text(text = meta, style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (hasMissingFile) {
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

            if (uploadTask != null) {
                UploadTaskStatusRow(
                    task = uploadTask,
                    onRetry = if (uploadTask.status == UploadStatus.FAILED) {
                        { onRetryUpload(uploadTask) }
                    } else null
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (primaryOut != null) {
                    OutlinedButton(
                        onClick = { onOpen(primaryOut) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_open), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { onShare(primaryOut) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_share), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = { onSave(primaryOut) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_save), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(
                        onClick = { onUpload(primaryOut) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = stringResource(R.string.action_upload_cloud))
                    }
                }
                TextButton(
                    onClick = onClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_details))
                }
            }
        }
    }
}

private fun JobEntity.matchesQuery(q: String): Boolean =
    toolId.lowercase().contains(q) ||
        toolTitle.lowercase().contains(q) ||
        jobId.lowercase().contains(q) ||
        inputSummary.lowercase().contains(q) ||
        summary.lowercase().contains(q) ||
        errorMessage.lowercase().contains(q)

private fun HistoryEntity.matchesQuery(q: String): Boolean =
    toolId.lowercase().contains(q) ||
        toolTitle.lowercase().contains(q) ||
        outputName.lowercase().contains(q) ||
        summary.lowercase().contains(q) ||
        jobId.lowercase().contains(q)
