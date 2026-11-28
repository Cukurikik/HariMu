package com.harimu.app.feature.toolscreen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.cloud.UploadQueue
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.data.JobEntity
import com.harimu.app.core.design.CapabilityBadge
import com.harimu.app.core.design.EmptyStateCard
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuAlertDialog
import com.harimu.app.core.design.HariMuPrimaryButton
import com.harimu.app.core.design.HariMuSectionCard
import com.harimu.app.core.design.HariMuStatusBanner
import com.harimu.app.core.design.HariMuTextField
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.HariMuTopBar
import com.harimu.app.core.design.ModelDownloadDialog
import com.harimu.app.core.design.ProgressCallbacks
import com.harimu.app.core.design.ResultAction
import com.harimu.app.core.design.ResultActions
import com.harimu.app.core.design.StatusTone
import com.harimu.app.core.design.ToolFileSelector
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.ToolMultiResultCard
import com.harimu.app.core.design.ToolParameterPanel
import com.harimu.app.core.design.ToolProgressState
import com.harimu.app.core.design.ToolProgressView
import com.harimu.app.core.design.ToolResultCard
import com.harimu.app.core.design.ToolResultSummary
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.audio.AudioForgeToolPreviewSection
import com.harimu.app.core.design.image.ImageForgeToolPreviewSection
import com.harimu.app.core.design.jobs.decodeJobOutputs
import com.harimu.app.core.jobs.JobInputValidator
import com.harimu.app.core.jobs.ToolJobWorker
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputCodec
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolResult
import com.harimu.app.feature.studios.tabForPdfTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolScreen(
    toolId: String,
    container: AppContainer,
    isEnglish: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onOpenPdfStudio: (String, String) -> Unit,
    onOpenUniversalConverter: (String) -> Unit,
    initialJobId: String? = null,
    onOpenJobCenter: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = HariMuThemeTokens.spacing
    val tool = remember(toolId) { ToolRegistry.findById(toolId) }

    if (tool == null) {
        Scaffold(
            topBar = {
                HariMuTopBar(
                    title = toolId,
                    onBack = onBack,
                    backTestTag = "tool_back_button"
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(spacing.md),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    title = if (isEnglish) "Tool not found" else "Tool tidak ditemukan",
                    description = if (isEnglish) {
                        "No tool matches ID $toolId."
                    } else {
                        "Tidak ada tool dengan ID $toolId."
                    },
                    actionLabel = stringResource(R.string.cd_back),
                    onAction = onBack
                )
            }
        }
        return
    }

    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val uploadTasks by container.uploadQueue.tasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val selectedUris = remember(tool.id) { mutableStateListOf<Uri>() }
    var secondaryUri by remember(tool.id) { mutableStateOf<Uri?>(null) }
    var textInput by rememberSaveable(tool.id) { mutableStateOf("") }
    val paramValues = remember(tool.id) {
        mutableStateMapOf<String, Any>().apply {
            tool.params.forEach { param ->
                this[param.key] = defaultParamValue(param)
            }
        }
    }

    LaunchedEffect(tool.id) {
        val prefill = ToolPrefillBus.consume(tool.id)
        if (prefill != null) {
            if (prefill.uris.isNotEmpty()) {
                selectedUris.clear()
                selectedUris.addAll(prefill.uris)
            }
            if (prefill.secondaryUri != null) {
                secondaryUri = prefill.secondaryUri
            }
            if (prefill.textInput.isNotBlank()) {
                textInput = prefill.textInput
            }
            prefill.params.forEach { (k, v) ->
                paramValues[k] = v
            }
        }
    }

    var activeJobId by rememberSaveable(tool.id, initialJobId) { mutableStateOf(initialJobId) }
    val observedJob by remember(activeJobId) {
        val jid = activeJobId
        if (jid != null) container.jobManager.observeJob(jid) else flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    var localProgressState by remember(tool.id) { mutableStateOf<ToolProgressState>(ToolProgressState.Idle) }
    var localResult by remember(tool.id) { mutableStateOf<ToolResult?>(null) }
    var runningCoroutine by remember(tool.id) { mutableStateOf<Job?>(null) }
    var validationBanner by remember(tool.id) { mutableStateOf<String?>(null) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showAiConsentDialog by remember { mutableStateOf(false) }

    fun takeReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    val singleImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUris.clear()
            selectedUris.add(uri)
            validationBanner = null
        }
    }
    val multiImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            validationBanner = null
        }
    }
    val singleVideoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUris.clear()
            selectedUris.add(uri)
            validationBanner = null
        }
    }
    val multiVideoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(8)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            validationBanner = null
        }
    }
    val singleDocumentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            takeReadPermission(uri)
            selectedUris.clear()
            selectedUris.add(uri)
            validationBanner = null
        }
    }
    val multiDocumentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach(::takeReadPermission)
            selectedUris.clear()
            selectedUris.addAll(uris)
            validationBanner = null
        }
    }
    val secondaryImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) secondaryUri = uri
    }
    val secondaryMediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) secondaryUri = uri
    }
    val secondaryDocPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            takeReadPermission(uri)
            secondaryUri = uri
        }
    }

    fun buildCurrentInput(): ToolInput = ToolInput(
        uris = selectedUris.toList(),
        secondaryUri = secondaryUri,
        textInput = textInput,
        params = paramValues.toMap()
    )

    fun launchExecution(skipModelCheck: Boolean = false) {
        val input = buildCurrentInput()
        if (!JobInputValidator.canRetryStoredInput(context, tool, input)) {
            validationBanner = when (tool.inputSpec) {
                is InputSpec.TextInput ->
                    if (isEnglish) "Enter text or pick an input file first." else "Masukkan teks atau pilih berkas terlebih dahulu."
                else ->
                    if (isEnglish) "Select a valid input file first." else "Pilih berkas masukan yang valid terlebih dahulu."
            }
            return
        }
        val modelSpec = tool.requiresModel
        if (!skipModelCheck && modelSpec != null && !container.modelManager.isDownloaded(modelSpec.id)) {
            showModelDialog = true
            return
        }
        if (tool.requiresNetwork && !settings.aiUploadConsent && tool.engineLabel.contains("CL", ignoreCase = true)) {
            showAiConsentDialog = true
            return
        }

        validationBanner = null
        localResult = null

        if (tool.heavyBackground) {
            scope.launch {
                val created = container.jobManager.enqueueToolJob(tool, input, isEnglish)
                activeJobId = created.jobId
            }
        } else {
            runningCoroutine?.cancel()
            runningCoroutine = scope.launch {
                val jobEntity = container.jobManager.startInProcessJob(tool, input, isEnglish)
                activeJobId = jobEntity.jobId
                localProgressState = ToolProgressState.Preparing(context.getString(R.string.progress_preparing))
                val toolContext = ToolContext(
                    appContext = context.applicationContext,
                    storage = container.storage,
                    isEnglish = isEnglish,
                    ai = container.aiClient,
                    models = container.modelManager
                )
                try {
                    tool.run(toolContext, input).collect { progress ->
                        when (progress) {
                            ToolProgress.Queued -> {
                                val msg = context.getString(R.string.job_queued_message)
                                localProgressState = ToolProgressState.Queued(msg)
                            }
                            is ToolProgress.Running -> {
                                localProgressState = ToolProgressState.Running(progress.percent, progress.message)
                                container.jobManager.updateInProcessJob(
                                    jobId = jobEntity.jobId,
                                    status = ToolJobWorker.STATUS_RUNNING,
                                    percent = progress.percent,
                                    message = progress.message
                                )
                            }
                            is ToolProgress.Done -> {
                                val res = progress.result
                                localResult = res
                                localProgressState = ToolProgressState.Done(
                                    ToolResultSummary(
                                        summary = res.summary,
                                        outputCount = res.outputs.size,
                                        primaryMimeType = res.outputs.firstOrNull()?.mimeType,
                                        hasTextOutput = !res.textOutput.isNullOrBlank()
                                    )
                                )
                                container.jobManager.completeInProcessJob(jobEntity.jobId, res)
                                val first = res.outputs.firstOrNull()
                                val entries = res.outputs.map {
                                    ToolOutputCodec.Entry(it.file.absolutePath, it.mimeType, it.displayName)
                                }
                                container.workspace.recordHistory(
                                    toolId = tool.id,
                                    toolTitle = tool.title(isEnglish),
                                    groupName = tool.group.name,
                                    outputFile = first?.file,
                                    mimeType = first?.mimeType ?: "text/plain",
                                    summary = res.summary,
                                    success = true,
                                    jobId = jobEntity.jobId,
                                    outputsJson = ToolOutputCodec.encode(entries),
                                    textOutput = res.textOutput.orEmpty()
                                )
                            }
                            is ToolProgress.Failed -> {
                                localProgressState = ToolProgressState.Failed(progress.error, isRetryable = true)
                                container.jobManager.failInProcessJob(jobEntity.jobId, progress.error)
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    localProgressState = ToolProgressState.Cancelled(isRetryable = true)
                    container.jobManager.cancelJob(jobEntity.jobId)
                } catch (t: Throwable) {
                    val msg = t.localizedMessage ?: "Processing failed"
                    localProgressState = ToolProgressState.Failed(msg, isRetryable = true)
                    container.jobManager.failInProcessJob(jobEntity.jobId, msg)
                }
            }
        }
    }

    val effectiveProgressState = remember(observedJob, localProgressState, tool.heavyBackground) {
        val job = observedJob
        if (job != null && (tool.heavyBackground || initialJobId != null)) {
            jobEntityToProgressState(job)
        } else if (localProgressState != ToolProgressState.Idle) {
            localProgressState
        } else if (job != null) {
            jobEntityToProgressState(job)
        } else {
            ToolProgressState.Idle
        }
    }

    val effectiveResult = remember(observedJob, localResult) {
        localResult ?: observedJob?.takeIf { it.status == ToolJobWorker.STATUS_SUCCEEDED }?.let { job ->
            ToolResult(
                outputs = decodeJobOutputs(job),
                summary = job.summary.ifBlank { job.toolTitle },
                textOutput = job.textOutput.takeIf { it.isNotBlank() }
            )
        }
    }

    val resultActions = remember(container, settings, isEnglish) {
        ResultActions(
            onOpen = { out ->
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            container.storage.buildOpenIntent(out.file, out.mimeType),
                            out.displayName
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure {
                    Toast.makeText(
                        context,
                        if (isEnglish) "No app can open this file." else "Tidak ada aplikasi untuk membuka berkas.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onShare = { out ->
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            container.storage.buildShareIntent(out.file, out.mimeType),
                            out.displayName
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            onSave = { out ->
                scope.launch(Dispatchers.IO) {
                    val saved = container.storage.saveToDefaultDestination(
                        source = out.file,
                        mimeType = out.mimeType,
                        configuredTreeUri = settings.outputFolderUri
                    )
                    withContext(Dispatchers.Main) {
                        val msg = if (saved != null) {
                            if (isEnglish) "Saved ${out.displayName}" else "Tersimpan: ${out.displayName}"
                        } else {
                            if (isEnglish) "Failed to save file" else "Gagal menyimpan berkas"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = { out ->
                runCatching { out.file.delete() }
                localResult = localResult?.let { prev ->
                    prev.copy(outputs = prev.outputs.filterNot { it.file.absolutePath == out.file.absolutePath })
                }
            },
            onUploadToCloud = { out ->
                scope.launch {
                    val cfg = container.cloudStore.load()
                    if (!cfg.isConfigured()) {
                        Toast.makeText(
                            context,
                            if (isEnglish) "Configure cloud storage in Settings first." else "Atur koneksi cloud di Pengaturan dahulu.",
                            Toast.LENGTH_SHORT
                        ).show()
                        onOpenSettings()
                        return@launch
                    }
                    container.uploadQueue.enqueue(out.file, out.mimeType, cfg.provider)
                    Toast.makeText(
                        context,
                        if (isEnglish) "Queued upload for ${out.displayName}" else "Diantrekan untuk diunggah: ${out.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    container.uploadQueue.processPending(
                        configProvider = { container.cloudStore.load() },
                        persistConfig = { container.cloudStore.save(it) }
                    )
                }
            },
            onCopyText = { txt ->
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("HariMu", txt))
                Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
            }
        )
    }

    val accent = tool.group.accentColor()
    val primaryFilePath = effectiveResult?.outputs?.firstOrNull()?.file?.absolutePath.orEmpty()
    val primaryUploadTask = remember(uploadTasks, primaryFilePath) {
        UploadQueue.findLatestForFile(primaryFilePath, uploadTasks)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HariMuTopBar(
                title = tool.title(isEnglish),
                subtitle = "${tool.id} • ${tool.engineLabel}",
                onBack = onBack,
                backTestTag = "tool_back_button",
                actions = {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tool_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(
                                if (isFavorite) R.string.cd_remove_favorite else R.string.cd_add_favorite,
                                tool.title(isEnglish)
                            ),
                            tint = if (isFavorite) HariMuThemeTokens.semantic.favoriteAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = HariMuThemeTokens.layout.formMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                // Header card
                HariMuSectionCard {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ToolIdBadge(id = tool.id, accent = accent)
                        EngineBadge(engine = tool.engineLabel)
                        if (tool.requiresNetwork) {
                            CapabilityBadge(label = stringResource(R.string.badge_needs_internet))
                        }
                        tool.requiresModel?.let { spec ->
                            CapabilityBadge(label = stringResource(R.string.badge_needs_model, spec.name))
                        }
                    }
                    Text(
                        text = tool.description(isEnglish),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tool.group == ToolGroup.PDF) {
                        OutlinedButton(
                            onClick = { onOpenPdfStudio(tabForPdfTool(tool.id).name, tool.id) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(stringResource(R.string.pdf_vault_open_studio))
                        }
                    } else if (tool.group == ToolGroup.CONVERTER) {
                        OutlinedButton(
                            onClick = { onOpenUniversalConverter(tool.id) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(stringResource(R.string.converter_hub_title))
                        }
                    }
                }

                // Network gateway notice if cloud gateway URL isn't configured
                if (tool.requiresNetwork && tool.engineLabel.contains("CL", ignoreCase = true) && settings.aiGatewayUrl.isBlank()) {
                    HariMuStatusBanner(
                        message = if (isEnglish) {
                            "Cloud AI Gateway URL is not configured yet. Set it in Settings before running cloud AI tools."
                        } else {
                            "URL Cloudflare AI Gateway belum diatur. Atur di Pengaturan sebelum menjalankan tool AI cloud."
                        },
                        tone = StatusTone.WARNING
                    )
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.nav_settings))
                    }
                }

                // Input picker / TextInput
                when (val spec = tool.inputSpec) {
                    InputSpec.None -> Unit
                    is InputSpec.TextInput -> {
                        HariMuSectionCard {
                            HariMuTextField(
                                value = textInput,
                                onValueChange = {
                                    textInput = it
                                    validationBanner = null
                                },
                                label = if (isEnglish) spec.hintEn else spec.hintId,
                                singleLine = false,
                                modifier = Modifier.testTag("tool_text_input")
                            )
                        }
                    }
                    else -> {
                        ToolFileSelector(
                            inputSpec = spec,
                            selectedUris = selectedUris,
                            secondaryUri = secondaryUri,
                            onUrisSelected = {
                                selectedUris.clear()
                                selectedUris.addAll(it)
                            },
                            onSecondaryUriSelected = { secondaryUri = it },
                            storage = container.storage,
                            isEnglish = isEnglish,
                            onPickSingleImage = {
                                singleImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onPickMultipleImages = {
                                multiImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onPickSingleVideo = {
                                singleVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            onPickMultipleVideos = {
                                multiVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            onPickSingleDocument = { mimes -> singleDocumentPicker.launch(mimes) },
                            onPickMultipleDocuments = { mimes -> multiDocumentPicker.launch(mimes) },
                            onPickSecondaryAudio = { secondaryDocPicker.launch(arrayOf("audio/*")) },
                            onPickSecondaryImage = {
                                secondaryImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onPickSecondaryDocument = { mimes -> secondaryDocPicker.launch(mimes) },
                            onPickSecondaryMedia = {
                                secondaryMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }
                        )
                    }
                }

                // Visual previews
                if (selectedUris.isNotEmpty() || secondaryUri != null || textInput.isNotBlank()) {
                    val previewParams = remember(paramValues.toMap(), textInput) {
                        paramValues.toMap() + mapOf("textInput" to textInput)
                    }
                    ToolInputPreview(
                        inputSpec = tool.inputSpec,
                        selectedUris = selectedUris,
                        secondaryUri = secondaryUri,
                        params = previewParams,
                        storage = container.storage,
                        isEnglish = isEnglish
                    )
                }
                if (tool.id.startsWith("AUD-")) {
                    AudioForgeToolPreviewSection(
                        toolId = tool.id,
                        params = paramValues,
                        secondaryUri = secondaryUri,
                        isEnglish = isEnglish
                    )
                } else if (tool.id.startsWith("IMG-")) {
                    ImageForgeToolPreviewSection(
                        toolId = tool.id,
                        selectedUris = selectedUris,
                        textInput = textInput,
                        params = paramValues,
                        onParamChange = { k, v -> paramValues[k] = v },
                        isEnglish = isEnglish
                    )
                }

                // Parameters
                ToolParameterPanel(
                    params = tool.params,
                    paramValues = paramValues,
                    onParamChange = { key, value -> paramValues[key] = value },
                    isEnglish = isEnglish
                )

                validationBanner?.let { msg ->
                    HariMuStatusBanner(message = msg, tone = StatusTone.ERROR)
                }

                // Run CTA
                val isRunning = effectiveProgressState is ToolProgressState.Queued ||
                    effectiveProgressState is ToolProgressState.Preparing ||
                    effectiveProgressState is ToolProgressState.Running
                HariMuPrimaryButton(
                    text = if (isRunning) {
                        stringResource(R.string.progress_running_title)
                    } else if (isEnglish) {
                        "Run Tool"
                    } else {
                        "Jalankan Tool"
                    },
                    onClick = { launchExecution() },
                    enabled = !isRunning,
                    icon = Icons.Filled.PlayArrow,
                    containerColor = accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tool_run_button")
                )

                // Live progress view
                ToolProgressView(
                    state = effectiveProgressState,
                    callbacks = ProgressCallbacks(
                        onCancel = {
                            runningCoroutine?.cancel()
                            activeJobId?.let { jid ->
                                scope.launch { container.jobManager.cancelJob(jid) }
                            }
                        },
                        onRetry = {
                            val jid = activeJobId
                            if (tool.heavyBackground && jid != null) {
                                scope.launch {
                                    container.jobManager.retryJob(jid, isEnglish)?.let { retried ->
                                        activeJobId = retried.jobId
                                    }
                                }
                            } else {
                                launchExecution()
                            }
                        },
                        jobId = activeJobId,
                        onViewJobCenter = onOpenJobCenter
                    )
                )

                // Result display
                effectiveResult?.let { res ->
                    if (!res.textOutput.isNullOrBlank()) {
                        HariMuSectionCard {
                            Text(
                                text = stringResource(R.string.preview_text_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = res.textOutput,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    ToolResultCard(
                        result = res,
                        storage = container.storage,
                        actions = resultActions,
                        uploadTask = primaryUploadTask,
                        onRetryUpload = { task ->
                            scope.launch {
                                container.uploadQueue.retryTask(
                                    taskId = task.id,
                                    configProvider = { container.cloudStore.load() },
                                    persistConfig = { container.cloudStore.save(it) }
                                )
                            }
                        },
                        onViewJobCenter = onOpenJobCenter
                    )
                    if (res.outputs.size > 1) {
                        ToolMultiResultCard(
                            outputs = res.outputs,
                            storage = container.storage,
                            onItemClick = { out -> resultActions.onOpen?.invoke(out) },
                            onItemAction = { out, action ->
                                when (action) {
                                    ResultAction.Open -> resultActions.onOpen?.invoke(out)
                                    ResultAction.Share -> resultActions.onShare?.invoke(out)
                                    ResultAction.Save -> resultActions.onSave?.invoke(out)
                                    ResultAction.Delete -> resultActions.onDelete?.invoke(out)
                                    ResultAction.Details -> Unit
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.lg))
            }
        }
    }

    if (showModelDialog) {
        val spec = tool.requiresModel
        if (spec != null) {
            ModelDownloadDialog(
                modelName = spec.name,
                modelSizeLabel = spec.sizeLabel,
                onConfirmDownload = {
                    showModelDialog = false
                    scope.launch {
                        runCatching {
                            container.modelManager.downloadModel(spec)
                        }.onSuccess {
                            launchExecution(skipModelCheck = true)
                        }.onFailure { err ->
                            Toast.makeText(context, err.localizedMessage ?: "Download unavailable", Toast.LENGTH_LONG).show()
                            launchExecution(skipModelCheck = true)
                        }
                    }
                },
                onDismiss = {
                    showModelDialog = false
                    launchExecution(skipModelCheck = true)
                }
            )
        }
    }

    if (showAiConsentDialog) {
        HariMuAlertDialog(
            title = if (isEnglish) "Send input to AI Gateway?" else "Kirim masukan ke AI Gateway?",
            body = if (isEnglish) {
                "This cloud AI tool sends your prompt or selected media to your configured Cloudflare AI Gateway."
            } else {
                "Tool AI cloud ini akan mengirim teks atau media terpilih ke Cloudflare AI Gateway yang Anda konfigurasi."
            },
            confirmLabel = if (isEnglish) "Allow & Run" else "Izinkan & Jalankan",
            dismissLabel = stringResource(R.string.action_cancel),
            icon = Icons.Filled.Cloud,
            onConfirm = {
                showAiConsentDialog = false
                scope.launch {
                    container.settings.setAiUploadConsent(true)
                    launchExecution()
                }
            },
            onDismiss = { showAiConsentDialog = false }
        )
    }
}

private fun defaultParamValue(param: ToolParam): Any = when (param) {
    is ToolParam.SliderParam -> param.defaultValue
    is ToolParam.DropdownParam -> param.defaultOption
    is ToolParam.ToggleParam -> param.defaultValue
    is ToolParam.TextParam -> param.defaultValue
}

private fun jobEntityToProgressState(job: JobEntity): ToolProgressState = when (job.status) {
    ToolJobWorker.STATUS_QUEUED -> ToolProgressState.Queued(job.progressMessage)
    ToolJobWorker.STATUS_PREPARING -> ToolProgressState.Preparing(job.progressMessage)
    ToolJobWorker.STATUS_RUNNING -> ToolProgressState.Running(job.progressPercent, job.progressMessage)
    ToolJobWorker.STATUS_SUCCEEDED -> {
        val outputs = decodeJobOutputs(job)
        ToolProgressState.Done(
            ToolResultSummary(
                summary = job.summary.ifBlank { job.progressMessage },
                outputCount = outputs.size,
                primaryMimeType = outputs.firstOrNull()?.mimeType,
                hasTextOutput = job.textOutput.isNotBlank()
            )
        )
    }
    ToolJobWorker.STATUS_FAILED -> ToolProgressState.Failed(job.errorMessage.ifBlank { job.progressMessage }, isRetryable = job.isRetryable)
    ToolJobWorker.STATUS_INTERRUPTED -> ToolProgressState.Interrupted(job.errorMessage.ifBlank { job.progressMessage }, isRetryable = job.isRetryable)
    ToolJobWorker.STATUS_CANCELLED -> ToolProgressState.Cancelled(isRetryable = job.isRetryable)
    else -> ToolProgressState.Idle
}
