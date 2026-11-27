package com.harimu.app.feature.studios

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ProgressCallbacks
import com.harimu.app.core.design.ResultAction
import com.harimu.app.core.design.ResultActions
import com.harimu.app.core.design.ToolMultiResultCard
import com.harimu.app.core.design.ToolProgressView
import com.harimu.app.core.design.ToolResultCard
import com.harimu.app.core.design.pdf.PdfDocumentPreviewCard
import com.harimu.app.core.design.pdf.PdfMergeQueueCard
import com.harimu.app.core.design.pdf.PdfPageStripOrganizer
import com.harimu.app.core.design.pdf.PdfRedactionOverlaySpec
import com.harimu.app.core.design.pdf.PdfWatermarkPreviewSpec
import com.harimu.app.core.design.toProgressState
import com.harimu.app.core.pdf.PdfOcrEngine
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.feature.toolscreen.UploadConsentDialog
import com.harimu.app.feature.toolscreen.copyText
import com.harimu.app.feature.toolscreen.openFile
import com.harimu.app.feature.toolscreen.saveFile
import com.harimu.app.feature.toolscreen.shareFile
import com.harimu.app.feature.toolscreen.uploadToCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfStudioScreen(
    container: AppContainer,
    isEnglish: Boolean,
    initialToolId: String? = null,
    initialTabName: String? = null,
    onOpenTool: ((String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    val initialTab = remember(initialTabName, initialToolId) {
        initialTabName?.let { name -> runCatching { PdfStudioTab.valueOf(name) }.getOrNull() }
            ?: tabForPdfTool(initialToolId)
    }

    val vm: PdfStudioViewModel = viewModel(
        key = "pdf_studio_${initialTab.name}_${initialToolId ?: "none"}",
        factory = PdfStudioViewModel.Factory(container, context, initialTab)
    )
    val state by vm.state.collectAsStateWithLifecycle()

    var pendingExportFile by remember { mutableStateOf<ToolOutputFile?>(null) }
    var showUploadConsent by remember { mutableStateOf(false) }

    val savedToast = stringResource(R.string.toast_saved)
    val saveFailedToast = stringResource(R.string.toast_save_failed)
    val copiedToast = stringResource(R.string.toast_copied)

    val primaryPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.selectPrimaryPdf(it) }
    }
    val mergePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addMergePdfs(uris)
    }
    val imagesToPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(40)) { uris ->
        if (uris.isNotEmpty()) vm.addImages(uris)
    }
    val watermarkImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        vm.setWatermarkImageUri(uri)
    }
    val compareAPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setCompareDocA(it) }
    }
    val compareBPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setCompareDocB(it) }
    }
    val safSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        val src = pendingExportFile
        if (destUri != null && src != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    container.storage.copyFileToUri(src.file, destUri)
                }
                Toast.makeText(context, if (ok) savedToast else saveFailedToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val liveWatermark = remember(
        state.activeTab,
        state.watermarkIsImage,
        state.watermarkText,
        state.watermarkImageUri,
        state.watermarkOpacityPct,
        state.watermarkAngleDeg,
        state.watermarkSizePct
    ) {
        if (state.activeTab == PdfStudioTab.SECURITY_REDACT) {
            PdfWatermarkPreviewSpec(
                isImage = state.watermarkIsImage,
                text = state.watermarkText,
                imageUri = state.watermarkImageUri,
                opacityPct = state.watermarkOpacityPct,
                angleDeg = state.watermarkAngleDeg,
                sizePct = state.watermarkSizePct
            )
        } else null
    }

    val liveRedactionOverlay = remember(state.activeTab, state.redactPreview) {
        val preview = state.redactPreview
        if (state.activeTab == PdfStudioTab.SECURITY_REDACT && preview != null && preview.totalBoxes > 0) {
            PdfRedactionOverlaySpec(
                pageHits = preview.pageHits,
                pageSizes = preview.pageSizes
            )
        } else null
    }

    fun exportOutput(out: ToolOutputFile) {
        saveFile(
            output = out,
            storage = container.storage,
            settings = settings,
            scope = scope,
            context = context,
            savedToast = savedToast,
            saveFailedToast = saveFailedToast,
            pendingExportFile = { pendingExportFile = it },
            safSaveLauncher = safSaveLauncher
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.pdf_vault_studio_title),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_studio_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { primaryPdfPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_studio_pick_primary_btn")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.primaryUri == null) {
                                stringResource(R.string.pdf_studio_pick_primary)
                            } else {
                                stringResource(R.string.pdf_studio_change_primary)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                .padding(padding)
        ) {
            val isWide = maxWidth >= 720.dp

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("pdf_studio_wide_workspace"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StudioDocumentPreviewPane(
                            state = state,
                            container = container,
                            isEnglish = isEnglish,
                            watermarkSpec = liveWatermark,
                            redactionOverlay = liveRedactionOverlay,
                            onPickPrimary = { primaryPdfPicker.launch(arrayOf("application/pdf")) },
                            onPageChange = vm::setPreviewPage,
                            onUnlockPasswordChange = vm::setUnlockPassword,
                            onApplyUnlock = vm::inspectPrimary
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StudioTabBar(
                            activeTab = state.activeTab,
                            onSelectTab = vm::setTab
                        )
                        StudioActiveTabContent(
                            state = state,
                            vm = vm,
                            container = container,
                            isEnglish = isEnglish,
                            onPickPrimary = { primaryPdfPicker.launch(arrayOf("application/pdf")) },
                            onPickMergePdfs = { mergePdfPicker.launch(arrayOf("application/pdf")) },
                            onPickImages = { imagesToPdfPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onPickWatermarkImage = { watermarkImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onPickCompareA = { compareAPicker.launch(arrayOf("application/pdf")) },
                            onPickCompareB = { compareBPicker.launch(arrayOf("application/pdf")) },
                            onRequestAiStructure = {
                                scope.launch {
                                    val consent = runCatching { container.aiClient.hasUploadConsent() }.getOrDefault(false)
                                    if (consent) vm.runAiStructureAnalysis(isEnglish) else showUploadConsent = true
                                }
                            },
                            onOpenTool = onOpenTool
                        )
                        StudioResultAndProgressPane(
                            state = state,
                            container = container,
                            isEnglish = isEnglish,
                            onCancel = vm::cancelOperation,
                            onOpen = { openFile(it, container.storage, context) },
                            onShare = { shareFile(it, container.storage, context) },
                            onSave = { exportOutput(it) },
                            onDelete = vm::deleteOutputFile,
                            onUpload = { uploadToCloud(it, container, settings, scope, context, isEnglish) },
                            onCopy = { copyText(it, context, copiedToast) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .testTag("pdf_studio_compact_workspace"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioTabBar(
                        activeTab = state.activeTab,
                        onSelectTab = vm::setTab
                    )
                    if (state.activeTab != PdfStudioTab.COMPARE) {
                        StudioDocumentPreviewPane(
                            state = state,
                            container = container,
                            isEnglish = isEnglish,
                            watermarkSpec = liveWatermark,
                            redactionOverlay = liveRedactionOverlay,
                            onPickPrimary = { primaryPdfPicker.launch(arrayOf("application/pdf")) },
                            onPageChange = vm::setPreviewPage,
                            onUnlockPasswordChange = vm::setUnlockPassword,
                            onApplyUnlock = vm::inspectPrimary
                        )
                    }
                    StudioActiveTabContent(
                        state = state,
                        vm = vm,
                        container = container,
                        isEnglish = isEnglish,
                        onPickPrimary = { primaryPdfPicker.launch(arrayOf("application/pdf")) },
                        onPickMergePdfs = { mergePdfPicker.launch(arrayOf("application/pdf")) },
                        onPickImages = { imagesToPdfPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onPickWatermarkImage = { watermarkImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onPickCompareA = { compareAPicker.launch(arrayOf("application/pdf")) },
                        onPickCompareB = { compareBPicker.launch(arrayOf("application/pdf")) },
                        onRequestAiStructure = {
                            scope.launch {
                                val consent = runCatching { container.aiClient.hasUploadConsent() }.getOrDefault(false)
                                if (consent) vm.runAiStructureAnalysis(isEnglish) else showUploadConsent = true
                            }
                        },
                        onOpenTool = onOpenTool
                    )
                    StudioResultAndProgressPane(
                        state = state,
                        container = container,
                        isEnglish = isEnglish,
                        onCancel = vm::cancelOperation,
                        onOpen = { openFile(it, container.storage, context) },
                        onShare = { shareFile(it, container.storage, context) },
                        onSave = { exportOutput(it) },
                        onDelete = vm::deleteOutputFile,
                        onUpload = { uploadToCloud(it, container, settings, scope, context, isEnglish) },
                        onCopy = { copyText(it, context, copiedToast) }
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
                    vm.runAiStructureAnalysis(isEnglish)
                }
            },
            onDismiss = { showUploadConsent = false },
            isEnglish = isEnglish
        )
    }
}

@Composable
private fun StudioTabBar(
    activeTab: PdfStudioTab,
    onSelectTab: (PdfStudioTab) -> Unit
) {
    val selectedIdx = PdfStudioTab.entries.indexOf(activeTab).coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIdx,
        edgePadding = 4.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        PdfStudioTab.entries.forEach { tab ->
            Tab(
                selected = activeTab == tab,
                onClick = { onSelectTab(tab) },
                text = {
                    Text(
                        text = stringResource(tab.titleRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("pdf_studio_tab_${tab.name}")
            )
        }
    }
}

@Composable
private fun StudioDocumentPreviewPane(
    state: PdfStudioUiState,
    container: AppContainer,
    isEnglish: Boolean,
    watermarkSpec: PdfWatermarkPreviewSpec?,
    redactionOverlay: PdfRedactionOverlaySpec?,
    onPickPrimary: () -> Unit,
    onPageChange: (Int) -> Unit,
    onUnlockPasswordChange: (String) -> Unit,
    onApplyUnlock: () -> Unit
) {
    val uri = state.primaryUri
    val accent = HariMuColors.GroupPdf
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    if (uri == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pdf_studio_empty_preview")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = safeAccent, modifier = Modifier.size(30.dp))
                }
                Text(
                    text = stringResource(R.string.pdf_studio_empty_doc_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.pdf_studio_empty_doc_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onPickPrimary,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pdf_studio_pick_primary))
                }
            }
        }
        return
    }

    PdfDocumentPreviewCard(
        uri = uri,
        storage = container.storage,
        isEnglish = isEnglish,
        password = state.unlockPassword,
        inspection = state.inspection,
        pageIndex = state.previewPageIndex,
        onPageChange = onPageChange,
        previewRotationDeg = if (state.activeTab == PdfStudioTab.PAGES_MERGE) state.previewRotationDeg else 0,
        watermarkSpec = watermarkSpec,
        redactionOverlay = redactionOverlay,
        maxHeightDp = 300.dp,
        testTag = "pdf_studio_document_preview"
    )

    val needsPassword = state.inspection?.requiresPassword == true ||
        state.inspection?.isEncrypted == true ||
        state.unlockPassword.isNotEmpty()
    if (needsPassword) {
        var pwdVisible by remember { mutableStateOf(false) }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.unlockPassword,
                    onValueChange = onUnlockPasswordChange,
                    label = { Text(stringResource(R.string.pdf_studio_unlock_hint)) },
                    singleLine = true,
                    visualTransformation = if (pwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pwdVisible = !pwdVisible }) {
                            Icon(
                                imageVector = if (pwdVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.cd_toggle_password)
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pdf_studio_unlock_input")
                )
                Button(
                    onClick = onApplyUnlock,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.pdf_studio_unlock_apply))
                }
            }
        }
    }

    state.docError?.let { err ->
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
private fun StudioActiveTabContent(
    state: PdfStudioUiState,
    vm: PdfStudioViewModel,
    container: AppContainer,
    isEnglish: Boolean,
    onPickPrimary: () -> Unit,
    onPickMergePdfs: () -> Unit,
    onPickImages: () -> Unit,
    onPickWatermarkImage: () -> Unit,
    onPickCompareA: () -> Unit,
    onPickCompareB: () -> Unit,
    onRequestAiStructure: () -> Unit,
    onOpenTool: ((String) -> Unit)?
) {
    when (state.activeTab) {
        PdfStudioTab.INSPECT -> InspectAndOcrWorkspace(
            state = state,
            vm = vm,
            isEnglish = isEnglish,
            onPickPrimary = onPickPrimary,
            onRequestAiStructure = onRequestAiStructure,
            onOpenTool = onOpenTool
        )

        PdfStudioTab.PAGES_MERGE -> PagesAndMergeWorkspace(
            state = state,
            vm = vm,
            container = container,
            isEnglish = isEnglish,
            onPickMergePdfs = onPickMergePdfs,
            onPickImages = onPickImages
        )

        PdfStudioTab.SECURITY_REDACT -> SecurityAndRedactWorkspace(
            state = state,
            vm = vm,
            isEnglish = isEnglish,
            onPickWatermarkImage = onPickWatermarkImage
        )

        PdfStudioTab.FORMS_META -> FormsAndMetadataWorkspace(
            state = state,
            vm = vm,
            isEnglish = isEnglish
        )

        PdfStudioTab.COMPARE -> CompareAbWorkspace(
            state = state,
            vm = vm,
            container = container,
            isEnglish = isEnglish,
            onPickCompareA = onPickCompareA,
            onPickCompareB = onPickCompareB
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectAndOcrWorkspace(
    state: PdfStudioUiState,
    vm: PdfStudioViewModel,
    isEnglish: Boolean,
    onPickPrimary: () -> Unit,
    onRequestAiStructure: () -> Unit,
    onOpenTool: ((String) -> Unit)?
) {
    val hasDoc = state.primaryUri != null && state.inspection?.requiresPassword != true

    // Text snippet + AI + OCR card
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_studio_inspect_workspace")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.pdf_studio_inspect_text_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (state.primaryUri == null) {
                OutlinedButton(onClick = onPickPrimary, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pdf_studio_pick_primary))
                }
            } else if (state.extractedText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.extractedText.take(1200),
                        style = MonoTextStyle,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.pdf_studio_inspect_no_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // OCR controls
            Text(
                text = stringResource(R.string.pdf_studio_ocr_lang),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PdfOcrEngine.LANGUAGES.forEach { lang ->
                    val label = lang.labelId
                    FilterChip(
                        selected = state.ocrLanguageLabel == label,
                        onClick = { vm.setOcrLanguage(label) },
                        label = { Text(if (isEnglish) lang.labelEn else lang.labelId) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }
            Text(
                text = if (state.ocrDataInstalled) {
                    stringResource(R.string.pdf_studio_ocr_ready)
                } else {
                    stringResource(R.string.pdf_studio_ocr_need_download)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.ocrDataInstalled) HariMuThemeTokens.semantic.success else HariMuThemeTokens.semantic.warning
            )
            Text(
                text = stringResource(R.string.pdf_studio_ocr_limit_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.pdf_honest_ai_gateway_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { vm.runOcrExtract(isEnglish) },
                    enabled = hasDoc && !state.isRunning,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pdf_studio_run_ocr_btn")
                ) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pdf_studio_run_ocr))
                }
                OutlinedButton(
                    onClick = onRequestAiStructure,
                    enabled = hasDoc && !state.isRunning,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pdf_studio_run_ai_btn")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pdf_studio_run_ai_structure))
                }
            }
        }
    }

    // Compress card
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Compress, contentDescription = null, tint = HariMuColors.GroupPdf)
                Text(
                    text = stringResource(R.string.pdf_studio_compress_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.pdf_studio_compress_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(stringResource(R.string.pdf_studio_compress_dpi_fmt, state.compressDpi), style = MonoTextStyle)
            Slider(
                value = state.compressDpi.toFloat(),
                onValueChange = { vm.setCompressDpi(it.roundToInt()) },
                valueRange = 72f..200f
            )
            Text(stringResource(R.string.pdf_studio_compress_quality_fmt, state.compressQuality), style = MonoTextStyle)
            Slider(
                value = state.compressQuality.toFloat(),
                onValueChange = { vm.setCompressQuality(it.roundToInt()) },
                valueRange = 30f..95f
            )
            Button(
                onClick = { vm.compressPrimaryPdf(isEnglish) },
                enabled = hasDoc && !state.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("pdf_studio_compress_btn")
            ) {
                Text(stringResource(R.string.pdf_studio_run_compress))
            }
            if (onOpenTool != null) {
                OutlinedButton(
                    onClick = { onOpenTool("PDF-06") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isEnglish) "Open Office Converter (PDF-06)" else "Buka Konverter Office (PDF-06)")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PagesAndMergeWorkspace(
    state: PdfStudioUiState,
    vm: PdfStudioViewModel,
    container: AppContainer,
    isEnglish: Boolean,
    onPickMergePdfs: () -> Unit,
    onPickImages: () -> Unit
) {
    var reorderMode by remember { mutableStateOf(false) }
    val hasDoc = state.primaryUri != null && state.inspection?.requiresPassword != true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_studio_pages_workspace"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.primaryUri != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.pdf_studio_pages_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (isEnglish) "Reorder" else "Susun", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = reorderMode, onCheckedChange = { reorderMode = it })
                    }
                }

                PdfPageStripOrganizer(
                    pdfUri = state.primaryUri,
                    storage = container.storage,
                    selectedPages = state.selectedPages,
                    pageOrder = state.pageOrder,
                    reorderMode = reorderMode,
                    onSelectedPagesChange = vm::setSelectedPages,
                    onPageOrderChange = vm::setPageOrder,
                    password = state.unlockPassword,
                    previewRotationDeg = state.previewRotationDeg
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (reorderMode) {
                        Button(
                            onClick = { vm.applyReorderPages(isEnglish) },
                            enabled = hasDoc && !state.isRunning,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pdf_studio_reorder_run_btn")
                        ) {
                            Text(stringResource(R.string.pdf_studio_pages_apply_reorder))
                        }
                    } else {
                        Button(
                            onClick = { vm.extractSelectedPages(isEnglish) },
                            enabled = hasDoc && !state.isRunning,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pdf_studio_extract_run_btn")
                        ) {
                            Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.pdf_studio_pages_extract))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            vm.bumpRotationPreview(90)
                            vm.rotatePages(90, applySelectedOnly = !reorderMode && state.selectedPages.isNotEmpty(), isEnglish = isEnglish)
                        },
                        enabled = hasDoc && !state.isRunning,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.pdf_studio_pages_rotate_90))
                    }
                    OutlinedButton(
                        onClick = {
                            vm.bumpRotationPreview(180)
                            vm.rotatePages(180, applySelectedOnly = !reorderMode && state.selectedPages.isNotEmpty(), isEnglish = isEnglish)
                        },
                        enabled = hasDoc && !state.isRunning,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.pdf_studio_pages_rotate_180))
                    }
                    OutlinedButton(
                        onClick = {
                            vm.bumpRotationPreview(270)
                            vm.rotatePages(270, applySelectedOnly = !reorderMode && state.selectedPages.isNotEmpty(), isEnglish = isEnglish)
                        },
                        enabled = hasDoc && !state.isRunning,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.pdf_studio_pages_rotate_270))
                    }
                }

                // Split per N pages
                Text(
                    text = "${stringResource(R.string.pdf_studio_split_per_file)}: ${state.splitPerFile}",
                    style = MonoTextStyle
                )
                Slider(
                    value = state.splitPerFile.toFloat(),
                    onValueChange = { vm.setSplitPerFile(it.roundToInt()) },
                    valueRange = 1f..30f
                )
                OutlinedButton(
                    onClick = { vm.splitPrimaryPdf(isEnglish) },
                    enabled = hasDoc && !state.isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pdf_studio_run_split))
                }
            }
        }
    }

    // Merge queue (PDF-01)
    PdfMergeQueueCard(
        items = state.mergeQueue,
        storage = container.storage,
        onAddClick = onPickMergePdfs,
        onMoveUp = { idx -> vm.moveMergeItem(idx, -1) },
        onMoveDown = { idx -> vm.moveMergeItem(idx, 1) },
        onRemove = vm::removeMergeItem
    )
    Button(
        onClick = { vm.mergeQueuedPdfs(isEnglish) },
        enabled = state.mergeQueue.size >= 2 && !state.isRunning,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("pdf_studio_merge_run_btn")
    ) {
        Text(stringResource(R.string.pdf_studio_merge_run, state.mergeQueue.size))
    }

    // Images to PDF (PDF-08)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.pdf_studio_img2pdf_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Fit" to (if (isEnglish) "Fit Image" else "Sesuai Gambar"), "A4" to "A4", "Letter" to "Letter").forEach { (key, label) ->
                    FilterChip(
                        selected = state.imagePageSizeMode == key,
                        onClick = { vm.setImagePageSizeMode(key) },
                        label = { Text(label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickImages,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pdf_studio_img2pdf_add))
                }
                if (state.imageQueue.isNotEmpty()) {
                    OutlinedButton(onClick = vm::clearImages, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Text(stringResource(R.string.pdf_picker_clear_pages))
                    }
                }
            }
            Button(
                onClick = { vm.createPdfFromImages(isEnglish) },
                enabled = state.imageQueue.isNotEmpty() && !state.isRunning,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.pdf_studio_img2pdf_run, state.imageQueue.size))
            }
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecurityAndRedactWorkspace(
    state: PdfStudioUiState,
    vm: PdfStudioViewModel,
    isEnglish: Boolean,
    onPickWatermarkImage: () -> Unit
) {
    val hasDoc = state.primaryUri != null
    val docUnlocked = hasDoc && state.inspection?.requiresPassword != true
    var showUserPwd by remember { mutableStateOf(false) }
    var showOwnerPwd by remember { mutableStateOf(false) }

    // Encrypt / Decrypt (PDF-10 & PDF-11)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_studio_security_workspace")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = HariMuColors.GroupPdf)
                Text(
                    text = stringResource(R.string.pdf_studio_security_enc_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedTextField(
                value = state.encryptUserPwd,
                onValueChange = vm::setEncryptUserPwd,
                label = { Text(stringResource(R.string.pdf_studio_user_pwd)) },
                singleLine = true,
                visualTransformation = if (showUserPwd) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showUserPwd = !showUserPwd }) {
                        Icon(
                            imageVector = if (showUserPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.cd_toggle_password)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.encryptOwnerPwd,
                onValueChange = vm::setEncryptOwnerPwd,
                label = { Text(stringResource(R.string.pdf_studio_owner_pwd)) },
                singleLine = true,
                visualTransformation = if (showOwnerPwd) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showOwnerPwd = !showOwnerPwd }) {
                        Icon(
                            imageVector = if (showOwnerPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.cd_toggle_password)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AES-256", "AES-128", "RC4-128").forEach { algo ->
                    FilterChip(
                        selected = state.encryptKeyAlgo == algo,
                        onClick = { vm.setEncryptKeyAlgo(algo) },
                        label = { Text(algo) }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.encryptPrimaryPdf(isEnglish) },
                    enabled = docUnlocked && !state.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("pdf_studio_encrypt_btn")
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pdf_studio_run_encrypt), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { vm.decryptPrimaryPdf(isEnglish) },
                    enabled = hasDoc && !state.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("pdf_studio_decrypt_btn")
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pdf_studio_run_decrypt), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    // Live Watermark (PDF-12 & PDF-13)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = HariMuColors.GroupPdf)
                Text(
                    text = stringResource(R.string.pdf_studio_watermark_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.watermarkIsImage,
                    onClick = { vm.setWatermarkIsImage(false) },
                    label = { Text(if (isEnglish) "Text" else "Teks") }
                )
                FilterChip(
                    selected = state.watermarkIsImage,
                    onClick = { vm.setWatermarkIsImage(true) },
                    label = { Text(if (isEnglish) "Image" else "Gambar") }
                )
            }
            if (state.watermarkIsImage) {
                OutlinedButton(onClick = onPickWatermarkImage, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pdf_studio_watermark_pick_img))
                }
            } else {
                OutlinedTextField(
                    value = state.watermarkText,
                    onValueChange = vm::setWatermarkText,
                    label = { Text(if (isEnglish) "Watermark Text" else "Teks Watermark") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                stringResource(
                    R.string.pdf_studio_watermark_summary_fmt,
                    state.watermarkOpacityPct,
                    state.watermarkAngleDeg,
                    state.watermarkSizePct
                ),
                style = MonoTextStyle
            )
            Slider(value = state.watermarkOpacityPct.toFloat(), onValueChange = { vm.setWatermarkOpacity(it.roundToInt()) }, valueRange = 5f..100f)
            if (!state.watermarkIsImage) {
                Slider(value = state.watermarkAngleDeg.toFloat(), onValueChange = { vm.setWatermarkAngle(it.roundToInt()) }, valueRange = -90f..90f)
            }
            Slider(value = state.watermarkSizePct.toFloat(), onValueChange = { vm.setWatermarkSize(it.roundToInt()) }, valueRange = 4f..30f)

            Button(
                onClick = { vm.applyWatermark(isEnglish) },
                enabled = docUnlocked && !state.isRunning,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.pdf_studio_watermark_apply))
            }

            Text(
                text = stringResource(R.string.pdf_studio_watermark_remove_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { vm.removeObjectWatermark(isEnglish) },
                enabled = docUnlocked && !state.isRunning,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pdf_studio_watermark_remove))
            }
        }
    }

    // Permanent Redaction (PDF-14)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = stringResource(R.string.pdf_studio_redact_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.pdf_studio_redact_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.redactTermsText,
                onValueChange = vm::setRedactTerms,
                label = { Text(stringResource(R.string.pdf_studio_redact_terms)) },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf_studio_redact_terms_input")
            )
            Text(stringResource(R.string.pdf_studio_compress_dpi_fmt, state.redactDpi), style = MonoTextStyle)
            Slider(value = state.redactDpi.toFloat(), onValueChange = { vm.setRedactDpi(it.roundToInt()) }, valueRange = 100f..250f)

            state.redactPreview?.let { preview ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (preview.totalBoxes > 0) HariMuThemeTokens.semantic.warningContainer else MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = if (preview.totalBoxes > 0) {
                            stringResource(R.string.pdf_studio_redact_hits_found, preview.totalBoxes, preview.pagesWithHits.size)
                        } else {
                            stringResource(R.string.pdf_studio_redact_hits_none)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = vm::scanRedactionMatches,
                    enabled = docUnlocked && !state.isScanningRedact && state.redactTermsText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("pdf_studio_redact_scan_btn")
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.pdf_studio_redact_scan), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = { vm.runPermanentRedaction(isEnglish) },
                    enabled = docUnlocked && !state.isRunning && state.redactTermsText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("pdf_studio_redact_apply_btn")
                ) {
                    Text(stringResource(R.string.pdf_studio_redact_apply), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormsAndMetadataWorkspace(
    state: PdfStudioUiState,
    vm: PdfStudioViewModel,
    isEnglish: Boolean
) {
    val docUnlocked = state.primaryUri != null && state.inspection?.requiresPassword != true

    // Metadata Editor (PDF-19)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_studio_forms_workspace")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.EditNote, contentDescription = null, tint = HariMuColors.GroupPdf)
                Text(stringResource(R.string.pdf_studio_meta_section), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(value = state.metaTitle, onValueChange = vm::setMetaTitle, label = { Text(if (isEnglish) "Title" else "Judul") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.metaAuthor, onValueChange = vm::setMetaAuthor, label = { Text(if (isEnglish) "Author" else "Penulis") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.metaSubject, onValueChange = vm::setMetaSubject, label = { Text(if (isEnglish) "Subject" else "Subjek") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.metaKeywords, onValueChange = vm::setMetaKeywords, label = { Text(if (isEnglish) "Keywords" else "Kata Kunci") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { vm.saveMetadata(isEnglish) },
                enabled = docUnlocked && !state.isRunning,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.pdf_studio_meta_apply))
            }
        }
    }

    // Page Numbering (PDF-18)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.FormatListNumbered, contentDescription = null, tint = HariMuColors.GroupPdf)
                Text(stringResource(R.string.pdf_studio_pagenum_section), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = state.pageNumPattern,
                onValueChange = vm::setPageNumPattern,
                label = { Text(if (isEnglish) "Number Pattern ({n} / {total})" else "Pola Nomor ({n} / {total})") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Tengah Bawah" to (if (isEnglish) "Bottom Center" else "Tengah Bawah"),
                    "Kanan Bawah" to (if (isEnglish) "Bottom Right" else "Kanan Bawah"),
                    "Kiri Bawah" to (if (isEnglish) "Bottom Left" else "Kiri Bawah"),
                    "Kanan Atas" to (if (isEnglish) "Top Right" else "Kanan Atas")
                ).forEach { (posKey, label) ->
                    FilterChip(
                        selected = state.pageNumPosition == posKey,
                        onClick = { vm.setPageNumPosition(posKey) },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                stringResource(R.string.pdf_studio_pagenum_summary_fmt, state.pageNumStart, state.pageNumSizePct),
                style = MonoTextStyle
            )
            Slider(value = state.pageNumStart.toFloat(), onValueChange = { vm.setPageNumStart(it.roundToInt()) }, valueRange = 1f..50f)
            Slider(value = state.pageNumSizePct.toFloat(), onValueChange = { vm.setPageNumSize(it.roundToInt()) }, valueRange = 2f..8f)
            Button(
                onClick = { vm.applyPageNumbers(isEnglish) },
                enabled = docUnlocked && !state.isRunning,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.pdf_studio_pagenum_apply))
            }
        }
    }

    // AcroForm Filler (PDF-21)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pdf_studio_form_section), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (state.formValues.isEmpty()) {
                Text(
                    text = stringResource(R.string.pdf_studio_form_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.formValues.forEach { (key, value) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { vm.setFormValue(key, it) },
                        label = { Text(key) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = { vm.fillAcroForm(isEnglish) },
                    enabled = docUnlocked && !state.isRunning,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.pdf_studio_form_apply))
                }
            }
        }
    }
}

@Composable
private fun CompareAbWorkspace(
    state: PdfStudioUiState,
    vm: PdfStudioViewModel,
    container: AppContainer,
    isEnglish: Boolean,
    onPickCompareA: () -> Unit,
    onPickCompareB: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_studio_compare_workspace")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Compare, contentDescription = null, tint = HariMuColors.GroupPdf)
                Text(
                    text = stringResource(R.string.pdf_studio_compare_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.pdf_honest_compare_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickCompareA,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = state.compareNameA.ifBlank { stringResource(R.string.pdf_studio_compare_pick_a) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onPickCompareB,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = state.compareNameB.ifBlank { stringResource(R.string.pdf_studio_compare_pick_b) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            state.compareError?.let { compareErr ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pdf_studio_compare_error")
                ) {
                    Text(
                        text = compareErr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (state.compareUriA != null && state.compareUriB != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { vm.refreshComparePagePreview((state.comparePageIndex - 1).coerceAtLeast(0)) },
                        enabled = state.comparePageIndex > 0 && !state.isLoadingComparePreview,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.pdf_studio_compare_prev))
                    }
                    Button(
                        onClick = { vm.refreshComparePagePreview(state.comparePageIndex) },
                        enabled = !state.isLoadingComparePreview,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.pdf_studio_compare_preview_btn, state.comparePageIndex + 1))
                    }
                    OutlinedButton(
                        onClick = {
                            val maxPages = state.comparePreview?.let { maxOf(it.pagesA, it.pagesB) } ?: Int.MAX_VALUE
                            if (state.comparePageIndex + 1 < maxPages) {
                                vm.refreshComparePagePreview(state.comparePageIndex + 1)
                            }
                        },
                        enabled = !state.isLoadingComparePreview,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.pdf_studio_compare_next))
                    }
                }

                state.comparePreview?.let { preview ->
                    val statusLabel = stringResource(
                        if (preview.changed) R.string.pdf_studio_compare_diff_yes
                        else R.string.pdf_studio_compare_diff_no
                    )
                    Text(
                        text = stringResource(
                            R.string.pdf_studio_compare_page_status,
                            state.comparePageIndex + 1,
                            preview.pagesA,
                            preview.pagesB,
                            statusLabel
                        ),
                        style = MonoTextStyle,
                        color = if (preview.changed) HariMuColors.GroupPdf else HariMuThemeTokens.semantic.success
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        preview.bitmapA?.takeIf { !it.isRecycled }?.let { bmpA ->
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.pdf_studio_doc_a_label), style = MaterialTheme.typography.labelSmall)
                                Image(
                                    bitmap = bmpA.asImageBitmap(),
                                    contentDescription = stringResource(
                                        R.string.pdf_studio_compare_cd_doc_a,
                                        state.comparePageIndex + 1
                                    ),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(bmpA.width.toFloat() / bmpA.height.coerceAtLeast(1))
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                        preview.bitmapB?.takeIf { !it.isRecycled }?.let { bmpB ->
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.pdf_studio_doc_b_label), style = MaterialTheme.typography.labelSmall)
                                Image(
                                    bitmap = bmpB.asImageBitmap(),
                                    contentDescription = stringResource(
                                        R.string.pdf_studio_compare_cd_doc_b,
                                        state.comparePageIndex + 1
                                    ),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(bmpB.width.toFloat() / bmpB.height.coerceAtLeast(1))
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                    preview.diffBitmap?.takeIf { !it.isRecycled }?.let { diffBmp ->
                        Text(
                            stringResource(R.string.pdf_studio_compare_diff_overlay),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Image(
                            bitmap = diffBmp.asImageBitmap(),
                            contentDescription = stringResource(
                                R.string.pdf_studio_compare_cd_diff,
                                state.comparePageIndex + 1
                            ),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(diffBmp.width.toFloat() / diffBmp.height.coerceAtLeast(1))
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            Button(
                onClick = { vm.runFullComparison(isEnglish) },
                enabled = state.compareUriA != null && state.compareUriB != null && !state.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("pdf_studio_compare_run_btn")
            ) {
                Text(stringResource(R.string.pdf_studio_compare_run))
            }
        }
    }
}

@Composable
private fun StudioResultAndProgressPane(
    state: PdfStudioUiState,
    container: AppContainer,
    isEnglish: Boolean,
    onCancel: () -> Unit,
    onOpen: (ToolOutputFile) -> Unit,
    onShare: (ToolOutputFile) -> Unit,
    onSave: (ToolOutputFile) -> Unit,
    onDelete: (ToolOutputFile) -> Unit,
    onUpload: (ToolOutputFile) -> Unit,
    onCopy: (String) -> Unit
) {
    val progressState = remember(state.progress) {
        state.progress?.toProgressState() ?: com.harimu.app.core.design.ToolProgressState.Idle
    }
    ToolProgressView(
        state = progressState,
        callbacks = ProgressCallbacks(onCancel = onCancel)
    )

    val done = state.progress as? ToolProgress.Done ?: return
    val visibleOutputs = done.result.outputs.filter {
        it.file.absolutePath !in state.deletedOutputPaths && it.file.exists()
    }
    val effectiveResult = done.result.copy(outputs = visibleOutputs)
    val firstOut = visibleOutputs.firstOrNull()

    ToolResultCard(
        result = effectiveResult,
        storage = container.storage,
        actions = ResultActions(
            onOpen = firstOut?.let { _ -> onOpen },
            onShare = firstOut?.let { _ -> onShare },
            onSave = firstOut?.let { _ -> onSave },
            onDelete = if (visibleOutputs.size == 1 && firstOut != null) {
                { onDelete(firstOut) }
            } else null,
            onUploadToCloud = firstOut?.let { _ -> onUpload },
            onCopyText = onCopy
        ),
        showPreview = true
    )

    if (visibleOutputs.size > 1) {
        ToolMultiResultCard(
            outputs = visibleOutputs,
            storage = container.storage,
            onItemClick = onOpen,
            onItemAction = { file, action ->
                when (action) {
                    ResultAction.Open -> onOpen(file)
                    ResultAction.Share -> onShare(file)
                    ResultAction.Save -> onSave(file)
                    ResultAction.Delete -> onDelete(file)
                    ResultAction.Details -> onOpen(file)
                }
            }
        )
    }

    done.result.textOutput?.let { txt ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.label_output), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onCopy(txt) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                    }
                }
                Text(text = txt, style = MonoTextStyle)
            }
        }
    }
}
