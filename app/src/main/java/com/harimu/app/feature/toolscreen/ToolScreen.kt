package com.harimu.app.feature.toolscreen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.ProgressCallbacks
import com.harimu.app.core.design.ToolParameterPanel
import com.harimu.app.core.design.ToolProgressView
import com.harimu.app.core.media.Id3TagHelper
import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(
    toolId: String,
    container: AppContainer,
    isEnglish: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onOpenPdfStudio: ((initialTab: String, toolId: String) -> Unit)? = null,
    onOpenUniversalConverter: ((toolId: String) -> Unit)? = null,
    initialJobId: String? = null,
    onOpenJobCenter: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    val scope = rememberCoroutineScope()
    var showUploadConsent by rememberSaveable { mutableStateOf(false) }
    var notificationDeniedOnce by rememberSaveable { mutableStateOf(false) }

    val vm: ToolViewModel = viewModel(
        key = "tool_${toolId}_${initialJobId ?: "latest"}",
        factory = ToolViewModel.Factory(toolId, container, initialJobId)
    )
    LaunchedEffect(context) { vm.bindContext(context) }

    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val tool = state.tool ?: return

    // Auto-fill ID3 tags (AUD-18), PDF metadata (PDF-19), and AcroForm fields (PDF-21)
    var lastAutoFilledPdfUri by remember(tool.id) { mutableStateOf<android.net.Uri?>(null) }
    LaunchedEffect(tool.id, state.selectedUris) {
        val firstUri = state.selectedUris.firstOrNull()
        if (firstUri == null) {
            if (lastAutoFilledPdfUri != null) {
                lastAutoFilledPdfUri = null
            }
            return@LaunchedEffect
        }
        if (tool.id == "AUD-18") {
            val existing = withContext(Dispatchers.IO) {
                Id3TagHelper.readTags(context, firstUri)
            }
            if ((state.paramValues["title"]?.toString()).isNullOrBlank() && existing.title.isNotBlank()) vm.setParam("title", existing.title)
            if ((state.paramValues["artist"]?.toString()).isNullOrBlank() && existing.artist.isNotBlank()) vm.setParam("artist", existing.artist)
            if ((state.paramValues["album"]?.toString()).isNullOrBlank() && existing.album.isNotBlank()) vm.setParam("album", existing.album)
            if ((state.paramValues["year"]?.toString()).isNullOrBlank() && existing.year.isNotBlank()) vm.setParam("year", existing.year)
            if ((state.paramValues["genre"]?.toString()).isNullOrBlank() && existing.genre.isNotBlank()) vm.setParam("genre", existing.genre)
        } else if (tool.id == "PDF-19" && lastAutoFilledPdfUri != firstUri) {
            val password = state.paramValues["password"]?.toString().orEmpty()
            val existing = withContext(Dispatchers.IO) {
                runCatching { PdfVaultEngine.readMetadata(context, container.storage, firstUri, password) }
                    .getOrNull()
            }
            if (existing != null) {
                lastAutoFilledPdfUri = firstUri
                listOf("title", "author", "subject", "keywords").forEach { key ->
                    vm.setParam(key, existing[key].orEmpty())
                }
            }
        } else if (tool.id == "PDF-21" && lastAutoFilledPdfUri != firstUri) {
            val password = state.paramValues["password"]?.toString().orEmpty()
            val fields = withContext(Dispatchers.IO) {
                runCatching { PdfVaultEngine.listFormFields(context, container.storage, firstUri, password) }
                    .getOrNull()
            }
            if (fields != null) {
                lastAutoFilledPdfUri = firstUri
                vm.setParam("values", fields.joinToString("|") { "${it.first}=${it.second}" })
            }
        }
    }

    val singleImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.setUris(listOf(it)) } }
    val multiImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris -> if (uris.isNotEmpty()) vm.setUris(uris) }
    val singleVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.setUris(listOf(it)) } }
    val multiVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris -> if (uris.isNotEmpty()) vm.setUris(uris) }
    val singleDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.setUris(listOf(it)) } }
    val multiDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.setUris(uris) }
    val secondaryAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.setSecondaryUri(it) } }
    val secondaryImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.setSecondaryUri(it) } }
    val secondarySubtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.setSecondaryUri(it) } }
    val secondaryBgMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.setSecondaryUri(it) } }

    val pickers = remember(
        singleImagePicker, multiImagePicker, singleVideoPicker, multiVideoPicker,
        singleDocPicker, multiDocPicker, secondaryAudioPicker, secondaryImagePicker,
        secondarySubtitlePicker, secondaryBgMediaPicker
    ) {
        Pickers(
            singleImagePicker = singleImagePicker,
            multiImagePicker = multiImagePicker,
            singleVideoPicker = singleVideoPicker,
            multiVideoPicker = multiVideoPicker,
            singleDocPicker = singleDocPicker,
            multiDocPicker = multiDocPicker,
            secondaryAudioPicker = secondaryAudioPicker,
            secondaryImagePicker = secondaryImagePicker,
            secondarySubtitlePicker = secondarySubtitlePicker,
            secondaryBgMediaPicker = secondaryBgMediaPicker
        )
    }

    val copiedToast = stringResource(R.string.toast_copied)
    val savedToast = stringResource(R.string.toast_saved)
    val saveFailedToast = stringResource(R.string.toast_save_failed)

    LaunchedEffect(state.exportState) {
        when (state.exportState) {
            is ExportState.Succeeded -> Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
            is ExportState.Failed -> Toast.makeText(context, saveFailedToast, Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestNotificationOnlyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (!granted) notificationDeniedOnce = true
    }

    val notificationBeforeRunLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (!granted) notificationDeniedOnce = true
        vm.execute(isEnglish)
    }

    val usesWorker = remember(tool.id) { vm.usesBackgroundWorker(tool) }

    fun triggerExecution() {
        val shouldRequestNotifications = (tool.isStub || usesWorker) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission &&
            !notificationDeniedOnce
        if (shouldRequestNotifications) {
            notificationBeforeRunLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        vm.execute(isEnglish)
    }

    val safSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        vm.completeSafExport(destUri)
    }

    var hasUploadConsent by remember(tool.id) { mutableStateOf(!tool.requiresNetwork) }
    LaunchedEffect(tool.id, tool.requiresNetwork, showUploadConsent) {
        hasUploadConsent = if (tool.requiresNetwork) {
            runCatching { container.aiClient.hasUploadConsent() }.getOrDefault(false)
        } else {
            true
        }
    }

    val isModelDownloaded = remember(tool.requiresModel) {
        tool.requiresModel?.let { container.modelManager.isDownloaded(it.id) } ?: true
    }

    val readiness = remember(
        tool.id,
        state.selectedUris,
        state.secondaryUri,
        state.textInput,
        state.paramValues,
        usesWorker,
        hasNotificationPermission,
        hasUploadConsent,
        isModelDownloaded
    ) {
        ToolWorkspaceValidator.evaluateReadiness(
            context = context,
            tool = tool,
            selectedUris = state.selectedUris,
            secondaryUri = state.secondaryUri,
            textInput = state.textInput,
            paramValues = state.paramValues,
            usesWorker = usesWorker,
            hasNotificationPermission = hasNotificationPermission,
            hasUploadConsent = hasUploadConsent,
            isModelDownloaded = isModelDownloaded,
            checkUriReadability = true
        )
    }

    val progressState = remember(
        state.progress,
        state.isInterrupted,
        state.isCancelled,
        state.isRetryable,
        state.jobErrorMessage
    ) {
        state.toUiProgressState()
    }

    val paramValidators = remember(tool.id, tool.params, isEnglish) {
        ToolWorkspaceValidator.buildParamValidators(context, tool)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = tool.title(isEnglish),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("tool_back_button")
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .testTag("fav_button_${tool.id}")
                            .semantics { selected = isFavorite }
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
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
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isExpandedTwoPane = maxWidth >= 840.dp
            val showReadinessCard = !state.isRunning &&
                (state.progress == null || !readiness.isReadyToRun || state.validationError.isNotBlank())

            if (isExpandedTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.md, vertical = spacing.md)
                        .testTag("tool_two_pane_layout"),
                    horizontalArrangement = Arrangement.spacedBy(spacing.lg)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.workspace_pane_input_preview),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ToolHeaderCard(tool = tool, isEnglish = isEnglish)
                        if (tool.isStub) StubNoticeCard(tool = tool)
                        PdfToolCapabilityCards(tool.id, tool.group, state.paramValues, onOpenPdfStudio)
                        ConverterToolCapabilityCards(tool.id, tool.group, state.selectedUris, container, isEnglish, onOpenUniversalConverter)
                        InputSection(tool, state, vm, container, pickers, isEnglish)
                        SpecializedPreviews(tool, state, vm, container, isEnglish)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.workspace_pane_config_run),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (showReadinessCard) {
                            WorkspaceReadinessCard(
                                readiness = readiness,
                                notificationDeniedOnce = notificationDeniedOnce,
                                onRequestNotifications = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestNotificationOnlyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }
                        if (tool.params.isNotEmpty()) {
                            ToolParameterPanel(
                                params = tool.params,
                                paramValues = state.paramValues,
                                onParamChange = vm::setParam,
                                validators = paramValidators,
                                isEnglish = isEnglish
                            )
                        }
                        RunButtonSection(
                            tool = tool,
                            state = state,
                            readiness = readiness,
                            showUploadConsent = { showUploadConsent = true },
                            onExecute = ::triggerExecution,
                            scope = scope,
                            container = container
                        )
                        ToolProgressView(
                            state = progressState,
                            callbacks = ProgressCallbacks(
                                onCancel = vm::cancel,
                                onRetry = { vm.retry(isEnglish) },
                                jobId = state.activeJobId ?: state.lastJobId,
                                onViewJobCenter = onOpenJobCenter
                            )
                        )
                        val doneProgress = state.progress as? ToolProgress.Done
                        if (doneProgress != null) {
                            DetailedResultView(
                                toolId = tool.id,
                                toolGroup = tool.group,
                                result = doneProgress.result,
                                state = state,
                                vm = vm,
                                context = context,
                                container = container,
                                settings = settings,
                                safSaveLauncher = safSaveLauncher,
                                copiedToast = copiedToast,
                                scope = scope,
                                onOpenJobCenter = onOpenJobCenter
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = layout.formMaxWidth)
                            .verticalScroll(rememberScrollState())
                            .padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        ToolHeaderCard(tool = tool, isEnglish = isEnglish)
                        if (tool.isStub) StubNoticeCard(tool = tool)
                        PdfToolCapabilityCards(tool.id, tool.group, state.paramValues, onOpenPdfStudio)
                        ConverterToolCapabilityCards(tool.id, tool.group, state.selectedUris, container, isEnglish, onOpenUniversalConverter)
                        InputSection(tool, state, vm, container, pickers, isEnglish)
                        if (showReadinessCard) {
                            WorkspaceReadinessCard(
                                readiness = readiness,
                                notificationDeniedOnce = notificationDeniedOnce,
                                onRequestNotifications = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestNotificationOnlyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }
                        SpecializedPreviews(tool, state, vm, container, isEnglish)
                        if (tool.params.isNotEmpty()) {
                            ToolParameterPanel(
                                params = tool.params,
                                paramValues = state.paramValues,
                                onParamChange = vm::setParam,
                                validators = paramValidators,
                                isEnglish = isEnglish
                            )
                        }
                        RunButtonSection(
                            tool = tool,
                            state = state,
                            readiness = readiness,
                            showUploadConsent = { showUploadConsent = true },
                            onExecute = ::triggerExecution,
                            scope = scope,
                            container = container
                        )
                        ToolProgressView(
                            state = progressState,
                            callbacks = ProgressCallbacks(
                                onCancel = vm::cancel,
                                onRetry = { vm.retry(isEnglish) },
                                jobId = state.activeJobId ?: state.lastJobId,
                                onViewJobCenter = onOpenJobCenter
                            )
                        )
                        val doneProgress = state.progress as? ToolProgress.Done
                        if (doneProgress != null) {
                            DetailedResultView(
                                toolId = tool.id,
                                toolGroup = tool.group,
                                result = doneProgress.result,
                                state = state,
                                vm = vm,
                                context = context,
                                container = container,
                                settings = settings,
                                safSaveLauncher = safSaveLauncher,
                                copiedToast = copiedToast,
                                scope = scope,
                                onOpenJobCenter = onOpenJobCenter
                            )
                        }
                    }
                }
            }

            if (showUploadConsent) {
                UploadConsentDialog(
                    onConfirm = {
                        showUploadConsent = false
                        scope.launch {
                            runCatching { container.aiClient.grantUploadConsent() }
                            hasUploadConsent = true
                            triggerExecution()
                        }
                    },
                    onDismiss = { showUploadConsent = false }
                )
            }
        }
    }
}