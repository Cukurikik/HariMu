package com.harimu.app.feature.studios

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.converter.ConverterWorkflow
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuPrimaryButton
import com.harimu.app.core.design.HariMuStatusBanner
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.StatusTone
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.ToolParameterPanel
import com.harimu.app.core.design.converter.ConverterBatchSummaryBanner
import com.harimu.app.core.design.converter.ConverterCapabilityNoticeBanner
import com.harimu.app.core.design.converter.ConverterFocusedToolBanner
import com.harimu.app.core.design.converter.ConverterHeroHeader
import com.harimu.app.core.design.converter.ConverterJobQueueSection
import com.harimu.app.core.design.converter.ConverterSourceCard
import com.harimu.app.core.design.converter.ConverterTargetMatrix
import com.harimu.app.core.design.converter.ConverterWorkflowSearchBar
import com.harimu.app.core.design.converter.ConverterWorkflowStrip
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.toolscreen.ExportState
import com.harimu.app.feature.toolscreen.ExportStatusBanner
import com.harimu.app.feature.toolscreen.ToolWorkspaceValidator
import com.harimu.app.feature.toolscreen.UploadConsentDialog
import com.harimu.app.feature.toolscreen.openFile
import com.harimu.app.feature.toolscreen.retryCloudUpload
import com.harimu.app.feature.toolscreen.shareFile
import com.harimu.app.feature.toolscreen.uploadToCloud
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalConverterScreen(
    container: AppContainer,
    isEnglish: Boolean,
    initialToolId: String? = null,
    onOpenTool: (Tool) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: UniversalConverterViewModel = viewModel(
        key = "universal_converter_${initialToolId ?: "default"}",
        factory = UniversalConverterViewModel.Factory(container, initialToolId)
    )
    LaunchedEffect(context) { vm.bindContext(context) }

    val state by vm.state.collectAsStateWithLifecycle()
    val jobs by vm.converterJobs.collectAsStateWithLifecycle()
    val uploadTasks by container.uploadQueue.tasksFlow.collectAsStateWithLifecycle()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    var showUploadConsent by rememberSaveable { mutableStateOf(false) }
    val savedToast = stringResource(R.string.toast_saved)
    val saveFailedToast = stringResource(R.string.toast_save_failed)
    val copiedToast = stringResource(R.string.converter_copy_output_done)

    LaunchedEffect(state.exportState) {
        when (state.exportState) {
            is ExportState.Succeeded -> Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
            is ExportState.Failed -> Toast.makeText(context, saveFailedToast, Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }

    val multiFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }
    val multiMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }
    val textFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.loadTextFromUri(it, isEnglish) }
    }
    val safSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        vm.completeSafExport(destUri)
    }

    fun copyTextToClipboard(text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("HariMu Converter Output", text))
        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
    }

    val activeCount = jobs.count {
        it.status == ToolJobWorker.STATUS_RUNNING || it.status == ToolJobWorker.STATUS_PREPARING
    }
    val queuedCount = jobs.count { it.status == ToolJobWorker.STATUS_QUEUED }

    fun triggerEnqueue() {
        val target = state.selectedTarget
        if (target?.requiresNetwork == true) {
            scope.launch {
                if (!container.aiClient.hasUploadConsent()) {
                    showUploadConsent = true
                } else {
                    vm.enqueueConversion(isEnglish)
                }
            }
            return
        }
        vm.enqueueConversion(isEnglish)
    }

    if (showUploadConsent) {
        UploadConsentDialog(
            onConfirm = {
                showUploadConsent = false
                scope.launch {
                    runCatching { container.aiClient.grantUploadConsent() }
                    vm.enqueueConversion(isEnglish)
                }
            },
            onDismiss = { showUploadConsent = false },
            isEnglish = isEnglish
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.converter_hub_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("converter_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val isExpanded = maxWidth >= 840.dp

            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1120.dp)
                        .padding(16.dp)
                        .testTag("converter_screen"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ConverterHeroHeader(activeJobCount = activeCount, queuedJobCount = queuedCount, isEnglish = isEnglish)
                        ConverterModeSwitcher(mode = state.mode, onSelectMode = vm::setMode)
                        if (state.mode == ConverterScreenMode.WORKBENCH) {
                            ConverterWorkbenchPane(
                                state = state,
                                isEnglish = isEnglish,
                                vm = vm,
                                onPickFiles = { multiFilePicker.launch(arrayOf("*/*")) },
                                onPickMedia = {
                                    multiMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                                onLoadTextFromFile = { textFilePicker.launch(arrayOf("text/*", "application/json", "application/xml", "*/*")) },
                                onRun = ::triggerEnqueue,
                                onOpenAdvancedTool = onOpenTool
                            )
                        } else {
                            ConverterWorkflowsPane(
                                selectedWorkflow = state.selectedWorkflow,
                                searchQuery = state.searchQuery,
                                isEnglish = isEnglish,
                                onSelectWorkflow = vm::setSelectedWorkflow,
                                onSearchQueryChange = vm::setSearchQuery,
                                onOpenWorkbench = { tool -> vm.focusToolOrWorkflow(tool.id) },
                                onOpenTool = onOpenTool
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExportStatusBanner(
                            exportState = state.exportState,
                            onDismiss = vm::clearExportState
                        )
                        ConverterJobQueueSection(
                            jobs = jobs,
                            onCancelJob = vm::cancelJob,
                            onRetryJob = { job -> vm.retryJob(job.jobId, isEnglish) },
                            canRetryJob = vm::canRetry,
                            onOpenOutput = { out -> openFile(out, container.storage, context) },
                            onShareOutput = { out -> shareFile(out, container.storage, context) },
                            onSaveOutput = { out ->
                                vm.exportToDefaultFolderOrRequestSaf(
                                    output = out,
                                    defaultTreeUri = settings.outputFolderUri,
                                    onRequestSaf = { target -> safSaveLauncher.launch(target.displayName) }
                                )
                            },
                            onUploadOutput = { out ->
                                uploadToCloud(out, container, settings, scope, context, isEnglish)
                            },
                            uploadTasks = uploadTasks,
                            onRetryUpload = { task -> retryCloudUpload(task, container, scope, context, isEnglish) },
                            onClearFinished = vm::clearFinishedJobs,
                            onCopyTextOutput = ::copyTextToClipboard
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .testTag("converter_screen"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ConverterHeroHeader(activeJobCount = activeCount, queuedJobCount = queuedCount, isEnglish = isEnglish)
                    ConverterModeSwitcher(mode = state.mode, onSelectMode = vm::setMode)
                    if (state.mode == ConverterScreenMode.WORKBENCH) {
                        ConverterWorkbenchPane(
                            state = state,
                            isEnglish = isEnglish,
                            vm = vm,
                            onPickFiles = { multiFilePicker.launch(arrayOf("*/*")) },
                            onPickMedia = {
                                multiMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            onLoadTextFromFile = { textFilePicker.launch(arrayOf("text/*", "application/json", "application/xml", "*/*")) },
                            onRun = ::triggerEnqueue,
                            onOpenAdvancedTool = onOpenTool
                        )
                    } else {
                        ConverterWorkflowsPane(
                            selectedWorkflow = state.selectedWorkflow,
                            searchQuery = state.searchQuery,
                            isEnglish = isEnglish,
                            onSelectWorkflow = vm::setSelectedWorkflow,
                            onSearchQueryChange = vm::setSearchQuery,
                            onOpenWorkbench = { tool -> vm.focusToolOrWorkflow(tool.id) },
                            onOpenTool = onOpenTool
                        )
                    }
                    ExportStatusBanner(
                        exportState = state.exportState,
                        onDismiss = vm::clearExportState
                    )
                    ConverterJobQueueSection(
                        jobs = jobs,
                        onCancelJob = vm::cancelJob,
                        onRetryJob = { job -> vm.retryJob(job.jobId, isEnglish) },
                        canRetryJob = vm::canRetry,
                        onOpenOutput = { out -> openFile(out, container.storage, context) },
                        onShareOutput = { out -> shareFile(out, container.storage, context) },
                        onSaveOutput = { out ->
                            vm.exportToDefaultFolderOrRequestSaf(
                                output = out,
                                defaultTreeUri = settings.outputFolderUri,
                                onRequestSaf = { target -> safSaveLauncher.launch(target.displayName) }
                            )
                        },
                        onUploadOutput = { out ->
                            uploadToCloud(out, container, settings, scope, context, isEnglish)
                        },
                        uploadTasks = uploadTasks,
                        onRetryUpload = { task -> retryCloudUpload(task, container, scope, context, isEnglish) },
                        onClearFinished = vm::clearFinishedJobs,
                        onCopyTextOutput = ::copyTextToClipboard
                    )
                }
            }
        }
    }
}

@Composable
private fun ConverterModeSwitcher(
    mode: ConverterScreenMode,
    onSelectMode: (ConverterScreenMode) -> Unit
) {
    val accent = HariMuColors.GroupConverter
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = mode == ConverterScreenMode.WORKBENCH,
            onClick = { onSelectMode(ConverterScreenMode.WORKBENCH) },
            label = { Text(stringResource(R.string.converter_mode_workbench)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = accent.copy(alpha = 0.16f),
                selectedLabelColor = safeAccent
            ),
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("converter_mode_workbench")
        )
        FilterChip(
            selected = mode == ConverterScreenMode.WORKFLOWS,
            onClick = { onSelectMode(ConverterScreenMode.WORKFLOWS) },
            label = { Text(stringResource(R.string.converter_mode_workflows)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = accent.copy(alpha = 0.16f),
                selectedLabelColor = safeAccent
            ),
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("converter_mode_workflows")
        )
    }
}

@Composable
private fun ConverterWorkbenchPane(
    state: UniversalConverterUiState,
    isEnglish: Boolean,
    vm: UniversalConverterViewModel,
    onPickFiles: () -> Unit,
    onPickMedia: () -> Unit,
    onLoadTextFromFile: () -> Unit,
    onRun: () -> Unit,
    onOpenAdvancedTool: (Tool) -> Unit
) {
    val context = LocalContext.current
    val focusedTool = state.focusedTool
    if (focusedTool != null && !state.isTextMode && state.selectedTarget?.toolId != focusedTool.id) {
        ConverterFocusedToolBanner(
            toolId = focusedTool.id,
            toolTitle = focusedTool.title(isEnglish),
            onDismiss = vm::clearFocusedTool
        )
    }

    ConverterSourceCard(
        isTextMode = state.isTextMode,
        sources = state.sources,
        textInput = state.textInput,
        detectedTextFormat = state.detectedTextFormat,
        isEnglish = isEnglish,
        onSetTextMode = vm::setTextMode,
        onPickMedia = onPickMedia,
        onPickFiles = onPickFiles,
        onRemoveFile = vm::removeFileAt,
        onClearFiles = vm::clearFiles,
        onTextChange = vm::setTextInput,
        onLoadTextFromFile = onLoadTextFromFile,
        onLoadSample = vm::loadSample,
        sourceWarningMessage = state.sourceWarningMessage
    )

    val hasAnyInput = if (state.isTextMode) state.textInput.isNotBlank() else state.sources.isNotEmpty()
    if (!hasAnyInput) return

    ConverterTargetMatrix(
        targets = state.availableTargets,
        selectedTargetId = state.selectedTargetId,
        unsupportedReasons = state.unsupportedReasons(isEnglish),
        isEnglish = isEnglish,
        onSelectTarget = vm::selectTarget,
        mixedIncompatibleReason = state.mixedIncompatibleReason(isEnglish)
    )

    val target = state.selectedTarget
    val tool = state.selectedTool
    val validation = state.validate(isEnglish)

    if (target != null && tool != null) {
        target.limitNote(isEnglish)?.let { note ->
            ConverterCapabilityNoticeBanner(
                title = stringResource(R.string.converter_honest_limits_title),
                message = note,
                isWarning = target.isLimitedOrBasic,
                modifier = Modifier.testTag("converter_target_limit_banner")
            )
        }

        val visibleParams = state.visibleParams
        if (visibleParams.isNotEmpty()) {
            ToolParameterPanel(
                params = visibleParams,
                paramValues = state.mergedParams(),
                onParamChange = vm::setParam,
                validators = remember(tool.id, visibleParams, isEnglish) {
                    ToolWorkspaceValidator.buildParamValidators(context, tool)
                },
                isEnglish = isEnglish
            )
        }

        if (!state.isTextMode && state.sources.size > 1) {
            ConverterBatchSummaryBanner(
                fileCount = state.sources.size,
                isSingleArchiveJob = target.supportsMultiInputSingleJob
            )
        }

        validation.blockingError?.let { err ->
            HariMuStatusBanner(
                message = err,
                tone = StatusTone.ERROR,
                modifier = Modifier.testTag("converter_validation_error")
            )
        }

        state.lastEnqueueMessage?.let { msg ->
            HariMuStatusBanner(
                message = msg,
                tone = StatusTone.INFO,
                modifier = Modifier.testTag("converter_enqueue_status")
            )
        }

        val ctaText = when {
            !state.isTextMode && state.sources.size > 1 && target.supportsMultiInputSingleJob ->
                stringResource(R.string.converter_run_archive_multi, state.sources.size)
            !state.isTextMode && state.sources.size > 1 ->
                stringResource(R.string.converter_run_batch, state.sources.size)
            else ->
                stringResource(R.string.converter_run_single)
        }

        HariMuPrimaryButton(
            text = ctaText,
            onClick = onRun,
            enabled = validation.isValid && !state.isEnqueueing,
            icon = Icons.Filled.PlayArrow,
            containerColor = HariMuColors.GroupConverter,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("converter_run_btn")
        )

        OutlinedButton(
            onClick = { onOpenAdvancedTool(tool) },
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("converter_open_tool_screen_btn")
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("${stringResource(R.string.converter_open_tool_cta)} (${tool.id})")
        }
    }
}

@Composable
private fun ConverterWorkflowsPane(
    selectedWorkflow: ConverterWorkflow?,
    searchQuery: String,
    isEnglish: Boolean,
    onSelectWorkflow: (ConverterWorkflow?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenWorkbench: (Tool) -> Unit,
    onOpenTool: (Tool) -> Unit
) {
    ConverterWorkflowSearchBar(
        query = searchQuery,
        onQueryChange = onSearchQueryChange
    )

    ConverterWorkflowStrip(
        selectedWorkflow = selectedWorkflow,
        onSelectWorkflow = onSelectWorkflow,
        isEnglish = isEnglish
    )

    val allConverterTools = remember { ToolRegistry.byGroup(ToolGroup.CONVERTER) }
    val workflowsToShow = remember(selectedWorkflow) {
        selectedWorkflow?.let { listOf(it) } ?: ConverterWorkflow.entries
    }

    var totalShown = 0
    workflowsToShow.forEach { workflow ->
        val matchingTools = remember(workflow, searchQuery, isEnglish, allConverterTools) {
            val q = searchQuery.trim().lowercase()
            allConverterTools.filter { tool ->
                tool.id in workflow.toolIds && (
                    q.isBlank() ||
                        tool.id.lowercase().contains(q) ||
                        tool.title(isEnglish).lowercase().contains(q) ||
                        tool.description(isEnglish).lowercase().contains(q) ||
                        workflow.title(isEnglish).lowercase().contains(q) ||
                        workflow.subtitle(isEnglish).lowercase().contains(q) ||
                        ConverterWorkflow.searchKeywordsForTool(tool.id).contains(q)
                    )
            }
        }
        if (matchingTools.isNotEmpty()) {
            totalShown += matchingTools.size
            ConverterWorkflowSection(
                workflow = workflow,
                tools = matchingTools,
                isEnglish = isEnglish,
                onOpenWorkbench = onOpenWorkbench,
                onOpenTool = onOpenTool
            )
        }
    }

    if (totalShown == 0) {
        EmptyState(
            icon = Icons.Filled.SearchOff,
            title = stringResource(R.string.catalog_empty_title),
            subtitle = stringResource(R.string.catalog_empty_desc)
        )
    }
}

@Composable
private fun ConverterWorkflowSection(
    workflow: ConverterWorkflow,
    tools: List<Tool>,
    isEnglish: Boolean,
    onOpenWorkbench: (Tool) -> Unit,
    onOpenTool: (Tool) -> Unit
) {
    val accent = HariMuColors.GroupConverter
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("converter_workflow_section_${workflow.name}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = workflow.icon,
                contentDescription = null,
                tint = safeAccent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${workflow.title(isEnglish)} (${tools.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = workflow.subtitle(isEnglish),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        tools.forEach { tool ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("converter_tool_card_${tool.id}"),
                shape = HariMuThemeTokens.shapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolIdBadge(id = tool.id, accent = accent)
                        EngineBadge(engine = tool.engineLabel)
                        Text(
                            text = tool.title(isEnglish),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = tool.description(isEnglish),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenWorkbench(tool) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("converter_tool_workbench_${tool.id}")
                        ) {
                            Text(
                                text = stringResource(R.string.converter_open_workbench_cta),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { onOpenTool(tool) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("converter_tool_direct_${tool.id}")
                        ) {
                            Text(
                                text = stringResource(R.string.converter_open_tool_cta),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
