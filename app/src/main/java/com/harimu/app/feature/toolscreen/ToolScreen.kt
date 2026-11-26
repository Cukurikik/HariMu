package com.harimu.app.feature.toolscreen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ProgressCallbacks
import com.harimu.app.core.design.ResultActions
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.ToolFileSelector
import com.harimu.app.core.design.ToolInputPreview
import com.harimu.app.core.design.ToolParameterPanel
import com.harimu.app.core.design.ToolProgressView
import com.harimu.app.core.design.ToolProgressState
import com.harimu.app.core.design.ToolResultCard
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.audio.AudioForgeToolPreviewSection
import com.harimu.app.core.design.audio.resolveWaveformOverlaySpec
import com.harimu.app.core.design.image.ImageForgeToolPreviewSection
import com.harimu.app.core.design.toProgressState
import com.harimu.app.core.media.Id3TagHelper
import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolScreen(
    toolId: String,
    container: AppContainer,
    isEnglish: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    val scope = rememberCoroutineScope()
    var showUploadConsent by remember { mutableStateOf(false) }
    val vm: ToolViewModel = viewModel(
        key = "tool_$toolId",
        factory = ToolViewModel.Factory(toolId, container)
    )
    LaunchedEffect(context) { vm.bindContext(context) }

    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val tool = state.tool ?: return

    var pendingExportFile by remember { mutableStateOf<ToolOutputFile?>(null) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { runCatching { audioPlayer?.release() } }
    }

    // Auto-fill ID3 tags for AUD-18
    LaunchedEffect(tool.id, state.selectedUris) {
        if (tool.id == "AUD-18" && state.selectedUris.isNotEmpty()) {
            val existing = withContext(Dispatchers.IO) {
                Id3TagHelper.readTags(context, state.selectedUris.first())
            }
            if ((state.paramValues["title"]?.toString()).isNullOrBlank() && existing.title.isNotBlank()) {
                vm.setParam("title", existing.title)
            }
            if ((state.paramValues["artist"]?.toString()).isNullOrBlank() && existing.artist.isNotBlank()) {
                vm.setParam("artist", existing.artist)
            }
            if ((state.paramValues["album"]?.toString()).isNullOrBlank() && existing.album.isNotBlank()) {
                vm.setParam("album", existing.album)
            }
            if ((state.paramValues["year"]?.toString()).isNullOrBlank() && existing.year.isNotBlank()) {
                vm.setParam("year", existing.year)
            }
            if ((state.paramValues["genre"]?.toString()).isNullOrBlank() && existing.genre.isNotBlank()) {
                vm.setParam("genre", existing.genre)
            }
        }
    }

    // File pickers
    val singleImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val multiImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.setUris(uris)
    }
    val singleVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val multiVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) vm.setUris(uris)
    }
    val singleDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val multiDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.setUris(uris)
    }
    val secondaryAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setSecondaryUri(it) }
    }
    val secondaryImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setSecondaryUri(it) }
    }
    val secondarySubtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setSecondaryUri(it) }
    }
    val secondaryBgMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setSecondaryUri(it) }
    }

    val savedToast = stringResource(R.string.toast_saved)
    val saveFailedToast = stringResource(R.string.toast_save_failed)
    val copiedToast = stringResource(R.string.toast_copied)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        vm.execute(isEnglish)
    }

    fun triggerExecution() {
        if (tool.isStub && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        vm.execute(isEnglish)
    }

    val safSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        val src = pendingExportFile
        if (destUri != null && src != null) {
            val ok = container.storage.copyFileToUri(src.file, destUri)
            Toast.makeText(context, if (ok) savedToast else saveFailedToast, Toast.LENGTH_SHORT).show()
        }
    }

    // Convert ToolProgress to ToolProgressState
    val progressState = remember(state.progress) {
        state.progress?.toProgressState() ?: ToolProgressState.Idle
    }

    // Build parameter validators based on tool requirements
    val paramValidators = remember(tool.id, tool.params) {
        buildParamValidators(tool)
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tool_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(R.string.cd_toggle_favorite),
                            tint = if (isFavorite) HariMuColors.ElectricOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                // Tool header card
                ToolHeaderCard(tool = tool, isEnglish = isEnglish)

                // Stub notice
                if (tool.isStub) {
                    StubNoticeCard(tool = tool, isEnglish = isEnglish)
                }

                // Input section
                InputSection(
                    tool = tool,
                    state = state,
                    vm = vm,
                    container = container,
                    pickers = Pickers(
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
                    ),
                    isEnglish = isEnglish
                )

                // Specialized preview cards (tool-specific)
                SpecializedPreviews(
                    tool = tool,
                    state = state,
                    vm = vm,
                    container = container,
                    isEnglish = isEnglish
                )

                // Parameter panel
                if (tool.params.isNotEmpty()) {
                    ToolParameterPanel(
                        params = tool.params,
                        paramValues = state.paramValues,
                        onParamChange = { key, value -> vm.setParam(key, value) },
                        validators = paramValidators,
                        isEnglish = isEnglish
                    )
                }

                // Run button with upload consent
                RunButtonSection(
                    tool = tool,
                    state = state,
                    showUploadConsent = { showUploadConsent = true },
                    onExecute = ::triggerExecution,
                    scope = scope,
                    container = container,
                    isEnglish = isEnglish
                )

                // Upload consent dialog
                if (showUploadConsent) {
                    UploadConsentDialog(
                        onConfirm = {
                            showUploadConsent = false
                            scope.launch {
                                runCatching { container.aiClient.grantUploadConsent() }
                                triggerExecution()
                            }
                        },
                        onDismiss = { showUploadConsent = false },
                        isEnglish = isEnglish
                    )
                }

                // Progress and result
                ToolProgressView(
                    state = progressState,
                    callbacks = ProgressCallbacks(
                        onCancel = { vm.cancel() },
                        onRetry = { triggerExecution() }
                    )
                )

                // Detailed result view for Done state
                if (state.progress is ToolProgress.Done) {
                    val doneProgress = state.progress as ToolProgress.Done
                    DetailedResultView(
                        toolId = tool.id,
                        result = doneProgress.result,
                        context = context,
                        container = container,
                        settings = settings,
                        pendingExportFile = { pendingExportFile = it },
                        safSaveLauncher = safSaveLauncher,
                        savedToast = savedToast,
                        saveFailedToast = saveFailedToast,
                        copiedToast = copiedToast,
                        audioPlayer = { audioPlayer = it },
                        isPlayingAudio = { isPlayingAudio = it },
                        scope = scope,
                        isEnglish = isEnglish
                    )
                }
            }
        }
    }
}

// ========================================================================
// Supporting data classes and components
// ========================================================================

data class Pickers(
    val singleImagePicker: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    val multiImagePicker: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    val singleVideoPicker: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    val multiVideoPicker: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    val singleDocPicker: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    val multiDocPicker: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    val secondaryAudioPicker: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    val secondaryImagePicker: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    val secondarySubtitlePicker: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    val secondaryBgMediaPicker: androidx.activity.result.ActivityResultLauncher<Array<String>>
)

@Composable
fun ToolHeaderCard(tool: com.harimu.app.core.toolapi.Tool, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.xs)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.xxs)
            ) {
                ToolIdBadge(id = tool.id, accent = tool.group.accentColor())
                EngineBadge(engine = tool.engineLabel)
                if (tool.isStub) EngineBadge(engine = stringResource(R.string.badge_coming_soon))
                if (tool.requiresNetwork) EngineBadge(engine = stringResource(R.string.badge_needs_internet))
                tool.requiresModel?.let { EngineBadge(engine = stringResource(R.string.badge_needs_model, it.sizeLabel)) }
            }
            Text(text = tool.description(isEnglish), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StubNoticeCard(tool: com.harimu.app.core.toolapi.Tool, isEnglish: Boolean) {
    Card(
        modifier = Modifier.testTag("stub_notice_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(text = stringResource(R.string.stub_notice_title, tool.plannedPhase.ifBlank { "-" }), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(text = stringResource(R.string.stub_notice_body, tool.plannedPhase.ifBlank { "-" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun InputSection(
    tool: com.harimu.app.core.toolapi.Tool,
    state: ToolUiState,
    vm: ToolViewModel,
    container: AppContainer,
    pickers: Pickers,
    isEnglish: Boolean
) {
    val spec = tool.inputSpec
    when (spec) {
        InputSpec.None -> Unit
        is InputSpec.TextInput -> {
            OutlinedTextField(
                value = state.textInput,
                onValueChange = vm::setTextInput,
                modifier = Modifier.fillMaxWidth().height(180.dp).testTag("tool_text_input"),
                label = { Text(if (isEnglish) spec.hintEn else spec.hintId) },
                textStyle = MonoTextStyle
            )
            ToolInputPreview(inputSpec = spec, selectedUris = state.selectedUris, secondaryUri = state.secondaryUri, params = state.paramValues, storage = container.storage, isEnglish = isEnglish)
        }
        else -> {
            ToolFileSelector(
                inputSpec = spec,
                selectedUris = state.selectedUris,
                secondaryUri = state.secondaryUri,
                onUrisSelected = vm::setUris,
                onSecondaryUriSelected = vm::setSecondaryUri,
                storage = container.storage,
                isEnglish = isEnglish,
                onPickSingleImage = { pickers.singleImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onPickMultipleImages = { pickers.multiImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onPickSingleVideo = { pickers.singleVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                onPickMultipleVideos = { pickers.multiVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                onPickSingleDocument = { mimeTypes -> pickers.singleDocPicker.launch(mimeTypes) },
                onPickMultipleDocuments = { mimeTypes -> pickers.multiDocPicker.launch(mimeTypes) },
                onPickSecondaryAudio = { pickers.secondaryAudioPicker.launch(arrayOf("audio/*")) },
                onPickSecondaryImage = { pickers.secondaryImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onPickSecondaryDocument = { mimeTypes -> pickers.secondarySubtitlePicker.launch(mimeTypes) },
                onPickSecondaryMedia = { pickers.secondaryBgMediaPicker.launch(arrayOf("image/*", "video/*")) }
            )
            ToolInputPreview(inputSpec = spec, selectedUris = state.selectedUris, secondaryUri = state.secondaryUri, params = state.paramValues, storage = container.storage, isEnglish = isEnglish)
        }
    }
}

@Composable
fun SpecializedPreviews(
    tool: com.harimu.app.core.toolapi.Tool,
    state: ToolUiState,
    vm: ToolViewModel,
    container: AppContainer,
    isEnglish: Boolean
) {
    // Video safety warnings
    if (tool.id == "VID-12" || tool.id == "VID-19") {
        VideoSafetyWarningCard(isReverse = tool.id == "VID-19")
    }

    // Video trim/preview cards
    val isSingleVideoSpec = tool.inputSpec == InputSpec.SingleVideo ||
        tool.inputSpec == InputSpec.VideoAndAudio ||
        tool.inputSpec == InputSpec.VideoAndImage ||
        tool.inputSpec == InputSpec.VideoAndSubtitle ||
        tool.inputSpec == InputSpec.VideoAndMedia

    if (isSingleVideoSpec && state.selectedUris.isNotEmpty()) {
        val videoUri = state.selectedUris.first()
        when (tool.id) {
            "VID-14" -> RoiLogoPreviewCard(
                videoUri = videoUri,
                xPct = (state.paramValues["xPct"] as? Number)?.toFloat() ?: 70f,
                yPct = (state.paramValues["yPct"] as? Number)?.toFloat() ?: 5f,
                wPct = (state.paramValues["wPct"] as? Number)?.toFloat() ?: 22f,
                hPct = (state.paramValues["hPct"] as? Number)?.toFloat() ?: 14f
            )
            "VID-25" -> FaceBlurPreviewCard(videoUri = videoUri)
            else -> {
                val showTrim = tool.id == "VID-01" || tool.id == "VID-21"
                val startVal = (state.paramValues["startSec"] as? Number)?.toFloat() ?: 0f
                val endVal = (state.paramValues["endSec"] as? Number)?.toFloat() ?: 0f
                VideoTrimPreviewCard(
                    videoUri = videoUri,
                    showTrimStrip = showTrim,
                    startSec = startVal,
                    endSec = endVal,
                    onRangeChange = { newStart, newEnd ->
                        vm.setParam("startSec", newStart)
                        vm.setParam("endSec", newEnd)
                        if (tool.id == "VID-21") {
                            vm.setParam("mode", "Rentang Pilihan")
                        }
                    }
                )
            }
        }
    }

    // Perspective correction preview
    if (tool.id == "IMG-06" && state.selectedUris.isNotEmpty()) {
        val keys = listOf("tlX", "tlY", "trX", "trY", "brX", "brY", "blX", "blY")
        val defaults = listOf(8f, 8f, 92f, 8f, 92f, 92f, 8f, 92f)
        PerspectivePointsCard(
            imageUri = state.selectedUris.first(),
            points = keys.mapIndexed { index, key -> (state.paramValues[key] as? Number)?.toFloat() ?: defaults[index] },
            onPointChange = { index, value -> vm.setParam(keys[index], value) }
        )
    }

    // Filter preview
    if (tool.id == "IMG-33" && state.selectedUris.isNotEmpty()) {
        FilterPreviewCard(
            imageUri = state.selectedUris.first(),
            preset = state.paramValues["preset"]?.toString() ?: "Vintage",
            brightness = (state.paramValues["brightness"] as? Number)?.toFloat() ?: 0f,
            contrast = (state.paramValues["contrast"] as? Number)?.toFloat() ?: 1.05f,
            saturation = (state.paramValues["saturation"] as? Number)?.toFloat() ?: 1f,
            temperature = (state.paramValues["temperature"] as? Number)?.toFloat() ?: 0f,
            blurRadius = (state.paramValues["blur"] as? Number)?.toInt() ?: 0,
            vignette = (state.paramValues["vignette"] as? Number)?.toInt() ?: 0
        )
    }

    // Visual previews for Image Forge tools (crop, canvas expander, straighten, bokeh, flare, overlays, LUT, format support)
    if (tool.group == ToolGroup.IMAGE && !tool.isStub) {
        ImageForgeToolPreviewSection(
            toolId = tool.id,
            selectedUris = state.selectedUris,
            textInput = state.textInput,
            params = state.paramValues,
            onParamChange = { key, value -> vm.setParam(key, value) },
            isEnglish = isEnglish
        )
    }

    // PDF page picker
    if (tool.group == ToolGroup.PDF && !tool.isStub &&
        state.selectedUris.isNotEmpty() &&
        tool.inputSpec != InputSpec.MultiplePdfs &&
        tool.inputSpec != InputSpec.MultipleImages
    ) {
        val pdfUri = state.selectedUris.first()
        if (tool.id == "PDF-04") {
            PdfPagePickerCard(
                pdfUri = pdfUri,
                storage = container.storage,
                isEnglish = isEnglish,
                reorderMode = true,
                pages = PdfVaultEngine.parsePagesLoose(state.paramValues["order"]?.toString() ?: ""),
                onPagesChange = { order -> vm.setParam("order", order.joinToString(",")) }
            )
        } else if (tool.params.any { it.key == "pages" }) {
            PdfPagePickerCard(
                pdfUri = pdfUri,
                storage = container.storage,
                isEnglish = isEnglish,
                reorderMode = false,
                pages = PdfVaultEngine.parsePagesLoose(state.paramValues["pages"]?.toString() ?: ""),
                onPagesChange = { pages -> vm.setParam("pages", PdfVaultEngine.formatPageRange(pages)) }
            )
        }
    }

    // Audio preview + Audio Forge visual cards
    val isSingleAudioSpec = tool.inputSpec == InputSpec.SingleAudio ||
        tool.inputSpec == InputSpec.AudioAndImage ||
        tool.inputSpec == InputSpec.AudioAndWatermarkAudio
    val doneAudioOutput = (state.progress as? ToolProgress.Done)
        ?.result
        ?.outputs
        ?.firstOrNull { it.mimeType.startsWith("audio/") }
    val audioOverlaySpec = remember(tool.id, state.paramValues) {
        resolveWaveformOverlaySpec(tool.id, state.paramValues)
    }
    val showAudioCard = !tool.isStub && (
        (isSingleAudioSpec && state.selectedUris.isNotEmpty()) ||
            (tool.group == ToolGroup.AUDIO && doneAudioOutput != null)
    )
    if (showAudioCard) {
        AudioBeforeAfterPreviewCard(
            inputUri = if (isSingleAudioSpec) state.selectedUris.firstOrNull() else null,
            outputFile = doneAudioOutput?.file,
            showTrimControls = tool.id == "AUD-09",
            startSec = (state.paramValues["startSec"] as? Number)?.toFloat() ?: 0f,
            endSec = (state.paramValues["endSec"] as? Number)?.toFloat() ?: 15f,
            overlaySpec = audioOverlaySpec,
            onRangeChange = { newStart, newEnd ->
                vm.setParam("startSec", newStart)
                vm.setParam("endSec", newEnd)
            }
        )
    }
    if (tool.group == ToolGroup.AUDIO && !tool.isStub) {
        AudioForgeToolPreviewSection(
            toolId = tool.id,
            params = state.paramValues,
            secondaryUri = state.secondaryUri,
            isEnglish = isEnglish
        )
    }
}

@Composable
fun RunButtonSection(
    tool: com.harimu.app.core.toolapi.Tool,
    state: ToolUiState,
    showUploadConsent: () -> Unit,
    onExecute: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    isEnglish: Boolean
) {
    Button(
        onClick = {
            if (tool.requiresNetwork && !state.isRunning) {
                scope.launch {
                    val consent = runCatching { container.aiClient.hasUploadConsent() }.getOrDefault(false)
                    if (consent) onExecute() else showUploadConsent()
                }
            } else {
                onExecute()
            }
        },
        enabled = !state.isRunning,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("run_tool_button")
    ) {
        Text(
            text = when {
                state.isRunning -> stringResource(R.string.action_processing)
                tool.isStub -> stringResource(R.string.action_run_dummy_job)
                else -> stringResource(R.string.action_run_tool)
            },
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
fun UploadConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isEnglish: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEnglish) "Allow cloud upload?" else "Izinkan unggahan cloud?") },
        text = {
            Text(if (isEnglish) {
                "This tool sends your file to the AI gateway configured in Settings. " +
                    "Files are processed for this request only and the gateway keeps no copy " +
                    "beyond the job lifetime. Continue?"
            } else {
                "Tool ini mengirim berkas Anda ke AI Gateway yang diatur di Pengaturan. " +
                    "Berkas hanya diproses untuk permintaan ini dan gateway tidak menyimpan " +
                    "salinan melebihi masa hidup job. Lanjutkan?"
            })
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (isEnglish) "Allow upload" else "Izinkan unggah") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isEnglish) "Cancel" else "Batal") }
        }
    )
}

@Composable
fun DetailedResultView(
    toolId: String,
    result: ToolResult,
    context: Context,
    container: AppContainer,
    settings: AppSettings,
    pendingExportFile: (ToolOutputFile) -> Unit,
    safSaveLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    savedToast: String,
    saveFailedToast: String,
    copiedToast: String,
    audioPlayer: (MediaPlayer?) -> Unit,
    isPlayingAudio: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    isEnglish: Boolean
) {
    val firstOut = result.outputs.firstOrNull()

    ToolResultCard(
        result = result,
        storage = container.storage,
        actions = ResultActions(
            onOpen = firstOut?.let { out -> { openFile(out, container.storage, context) } },
            onShare = firstOut?.let { out -> { shareFile(out, container.storage, context) } },
            onSave = firstOut?.let { out -> { saveFile(out, container.storage, settings, scope, context, savedToast, saveFailedToast, pendingExportFile, safSaveLauncher) } },
            onUploadToCloud = firstOut?.let { out -> { uploadToCloud(out, container, settings, scope, context, isEnglish) } },
            onCopyText = { txt -> copyText(txt, context, copiedToast) }
        ),
        showPreview = false // We'll show custom previews below
    )

    // Additional previews for all outputs
    if (result.outputs.isNotEmpty()) {
        result.outputs.forEach { out ->
            when {
                out.mimeType.startsWith("video/") && toolId == "AUD-28" -> {
                    VideoTrimPreviewCard(
                        videoUri = out.file.toUri(),
                        showTrimStrip = false,
                        startSec = 0f,
                        endSec = 0f,
                        onRangeChange = { _, _ -> }
                    )
                }
                out.mimeType.startsWith("image/") -> {
                    AsyncImage(
                        model = out.file,
                        contentDescription = out.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                out.mimeType.startsWith("audio/") -> {
                    AudioPlaybackButton(
                        file = out.file,
                        audioPlayer = audioPlayer,
                        isPlayingAudio = isPlayingAudio,
                        copiedToast = copiedToast,
                        container = container
                    )
                }
            }
        }
    }

    // Text output
    result.textOutput?.let { txt ->
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_output), style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { copyText(txt, context, copiedToast) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy), modifier = Modifier.size(18.dp))
                    }
                }
                Text(txt, style = MonoTextStyle)
            }
        }
    }
}

// Helper functions
fun openFile(output: ToolOutputFile, storage: com.harimu.app.core.files.FileStorageManager, context: Context) {
    runCatching {
        context.startActivity(Intent.createChooser(storage.buildOpenIntent(output.file, output.mimeType), output.displayName))
    }
}

fun shareFile(output: ToolOutputFile, storage: com.harimu.app.core.files.FileStorageManager, context: Context) {
    context.startActivity(Intent.createChooser(storage.buildShareIntent(output.file, output.mimeType), output.displayName))
}

fun saveFile(
    output: ToolOutputFile,
    storage: com.harimu.app.core.files.FileStorageManager,
    settings: AppSettings,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    savedToast: String,
    saveFailedToast: String,
    pendingExportFile: (ToolOutputFile) -> Unit,
    safSaveLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val defaultTreeUri = settings.outputFolderUri
    if (defaultTreeUri.isNotBlank()) {
        scope.launch(Dispatchers.IO) {
            val savedUri = storage.saveToTreeUri(source = output.file, treeUri = defaultTreeUri.toUri(), mimeType = output.mimeType)
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
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    isEnglish: Boolean
) {
    scope.launch(Dispatchers.IO) {
        val config = container.cloudStore.load()
        if (!config.isConfigured()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (isEnglish) "Configure a cloud connection in Settings first" else "Atur koneksi cloud di Pengaturan dulu", Toast.LENGTH_LONG).show()
            }
        } else {
            container.uploadQueue.enqueue(output.file, output.mimeType, config.provider)
            val uploaded = container.uploadQueue.processPending(
                configProvider = { container.cloudStore.load() },
                persistConfig = { container.cloudStore.save(it) }
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (uploaded > 0) {
                    if (settings.isEnglish) "Uploaded to cloud" else "Terunggah ke cloud"
                } else {
                    if (settings.isEnglish) "Queued — will retry" else "Masuk antrean — akan dicoba ulang"
                }, Toast.LENGTH_LONG).show()
            }
        }
    }
}

fun copyText(text: String, context: Context, copiedToast: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("HariMu", text))
    Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
}

@Composable
fun AudioPlaybackButton(
    file: java.io.File,
    audioPlayer: (MediaPlayer?) -> Unit,
    isPlayingAudio: (Boolean) -> Unit,
    copiedToast: String,
    container: AppContainer
) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { runCatching { player?.release() } }
    }

    OutlinedButton(
        onClick = {
            if (playing) {
                runCatching { player?.stop(); player?.release() }
                player = null
                playing = false
                isPlayingAudio(false)
                audioPlayer(null)
            } else {
                runCatching {
                    player?.release()
                    player = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        setOnCompletionListener { playing = false; isPlayingAudio(false); audioPlayer(null) }
                        start()
                    }
                    playing = true
                    isPlayingAudio(true)
                    audioPlayer(player)
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (playing) stringResource(R.string.action_stop_audio) else stringResource(R.string.action_play_audio))
    }
}

// Parameter validators
fun buildParamValidators(tool: com.harimu.app.core.toolapi.Tool): Map<String, (Any) -> com.harimu.app.core.design.ParamValidation> {
    val validators = mutableMapOf<String, (Any) -> com.harimu.app.core.design.ParamValidation>()

    tool.params.forEach { param ->
        when (param) {
            is ToolParam.SliderParam -> {
                validators[param.key] = { value ->
                    if (value is Number) {
                        val v = value.toFloat()
                        if (v < param.min || v > param.max) {
                            com.harimu.app.core.design.ParamValidation.Invalid("Value must be between ${param.min} and ${param.max}")
                        } else {
                            com.harimu.app.core.design.ParamValidation.Valid
                        }
                    } else com.harimu.app.core.design.ParamValidation.Valid
                }
            }
            is ToolParam.TextParam -> {
                if (param.key == "password" || param.key.contains("pass")) {
                    validators[param.key] = com.harimu.app.core.design.ParamValidators.minLength(4, "Password must be at least 4 characters")
                } else if (param.key == "color" || param.key.contains("Hex")) {
                    validators[param.key] = com.harimu.app.core.design.ParamValidators.hexColor()
                } else if (param.key == "email") {
                    validators[param.key] = com.harimu.app.core.design.ParamValidators.email()
                }
            }
            is ToolParam.DropdownParam -> {
                validators[param.key] = { value ->
                    if (value !in param.options) {
                        com.harimu.app.core.design.ParamValidation.Invalid("Invalid option selected")
                    } else {
                        com.harimu.app.core.design.ParamValidation.Valid
                    }
                }
            }
            is ToolParam.ToggleParam -> Unit
        }
    }

    return validators
}