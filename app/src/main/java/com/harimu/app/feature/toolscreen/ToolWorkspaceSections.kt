package com.harimu.app.feature.toolscreen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.converter.ConverterFormatMatrix
import com.harimu.app.core.converter.DetectedSource
import com.harimu.app.core.converter.SourceFormatDetector
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ToolFileSelector
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.ToolInputPreview
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.audio.AudioForgeToolPreviewSection
import com.harimu.app.core.design.audio.resolveWaveformOverlaySpec
import com.harimu.app.core.design.image.ImageForgeToolPreviewSection
import com.harimu.app.core.pdf.PdfOcrEngine
import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.feature.studios.tabForPdfTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Pickers(
    val singleImagePicker: ActivityResultLauncher<PickVisualMediaRequest>,
    val multiImagePicker: ActivityResultLauncher<PickVisualMediaRequest>,
    val singleVideoPicker: ActivityResultLauncher<PickVisualMediaRequest>,
    val multiVideoPicker: ActivityResultLauncher<PickVisualMediaRequest>,
    val singleDocPicker: ActivityResultLauncher<Array<String>>,
    val multiDocPicker: ActivityResultLauncher<Array<String>>,
    val secondaryAudioPicker: ActivityResultLauncher<Array<String>>,
    val secondaryImagePicker: ActivityResultLauncher<PickVisualMediaRequest>,
    val secondarySubtitlePicker: ActivityResultLauncher<Array<String>>,
    val secondaryBgMediaPicker: ActivityResultLauncher<Array<String>>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolHeaderCard(tool: Tool, isEnglish: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tool_card_${tool.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.xs)
        ) {
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
            Text(
                text = tool.description(isEnglish),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StubNoticeCard(tool: Tool, isEnglish: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stub_notice_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.stub_notice_title, tool.plannedPhase.ifBlank { "-" }),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = stringResource(R.string.stub_notice_body, tool.plannedPhase.ifBlank { "-" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun WorkspaceReadinessCard(
    readiness: WorkspaceReadiness,
    notificationDeniedOnce: Boolean,
    onRequestNotifications: () -> Unit
) {
    val context = LocalContext.current
    val semantic = HariMuThemeTokens.semantic
    val (containerColor, borderColor, contentColor, icon) = when (readiness.stage) {
        WorkspaceStage.INPUT_MISSING -> Quadruple(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.onSurface,
            Icons.Filled.HourglassEmpty
        )
        WorkspaceStage.INVALID_INPUT -> Quadruple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.ErrorOutline
        )
        WorkspaceStage.READY -> Quadruple(
            semantic.successContainer.copy(alpha = 0.45f),
            semantic.successBorder.copy(alpha = 0.55f),
            MaterialTheme.colorScheme.onSurface,
            Icons.Filled.CheckCircle
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workspace_readiness_card"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (readiness.stage == WorkspaceStage.READY) semantic.success else contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = readiness.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
            Text(
                text = readiness.detail,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            readiness.missingModel?.let { model ->
                Text(
                    text = stringResource(R.string.workspace_model_required_missing, model.name, model.sizeLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.onWarningContainer
                )
            }
            if (readiness.needsNotificationPermission) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = semantic.infoContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = semantic.onInfoContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.workspace_perm_notifications_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = semantic.onInfoContainer
                            )
                        }
                        Text(
                            text = stringResource(R.string.workspace_perm_notifications_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.onInfoContainer
                        )
                        TextButton(
                            onClick = {
                                if (notificationDeniedOnce) {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    runCatching { context.startActivity(intent) }
                                } else {
                                    onRequestNotifications()
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("workspace_notification_permission_btn")
                        ) {
                            Text(
                                stringResource(
                                    if (notificationDeniedOnce) R.string.workspace_perm_notifications_settings
                                    else R.string.workspace_perm_notifications_action
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun InputSection(
    tool: Tool,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .testTag("tool_text_input"),
                label = { Text(if (isEnglish) spec.hintEn else spec.hintId) },
                isError = !tool.isStub && state.validationError.isNotBlank() && state.textInput.isBlank(),
                textStyle = MonoTextStyle
            )
            ToolInputPreview(
                inputSpec = spec,
                selectedUris = state.selectedUris,
                secondaryUri = state.secondaryUri,
                params = state.paramValues + ("textInput" to state.textInput),
                storage = container.storage,
                isEnglish = isEnglish
            )
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
            ToolInputPreview(
                inputSpec = spec,
                selectedUris = state.selectedUris,
                secondaryUri = state.secondaryUri,
                params = state.paramValues,
                storage = container.storage,
                isEnglish = isEnglish
            )
        }
    }
}

@Composable
fun SpecializedPreviews(
    tool: Tool,
    state: ToolUiState,
    vm: ToolViewModel,
    container: AppContainer,
    isEnglish: Boolean
) {
    if (tool.id == "VID-12" || tool.id == "VID-19") {
        VideoSafetyWarningCard(isReverse = tool.id == "VID-19")
    }

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

    if (tool.id == "IMG-06" && state.selectedUris.isNotEmpty()) {
        val keys = listOf("tlX", "tlY", "trX", "trY", "brX", "brY", "blX", "blY")
        val defaults = listOf(8f, 8f, 92f, 8f, 92f, 92f, 8f, 92f)
        PerspectivePointsCard(
            imageUri = state.selectedUris.first(),
            points = keys.mapIndexed { index, key -> (state.paramValues[key] as? Number)?.toFloat() ?: defaults[index] },
            onPointChange = { index, value -> vm.setParam(keys[index], value) }
        )
    }

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

    if (tool.group == ToolGroup.PDF && !tool.isStub &&
        state.selectedUris.isNotEmpty() &&
        tool.inputSpec != InputSpec.MultiplePdfs &&
        tool.inputSpec != InputSpec.MultipleImages
    ) {
        val pdfUri = state.selectedUris.first()
        val pdfPassword = state.paramValues["password"]?.toString()
            ?: state.paramValues["userPwd"]?.toString()
            ?: ""
        if (tool.id == "PDF-04") {
            PdfPagePickerCard(
                pdfUri = pdfUri,
                storage = container.storage,
                isEnglish = isEnglish,
                reorderMode = true,
                pages = PdfVaultEngine.parsePagesLoose(state.paramValues["order"]?.toString() ?: ""),
                onPagesChange = { order -> vm.setParam("order", order.joinToString(",")) },
                password = pdfPassword
            )
        } else if (tool.params.any { it.key == "pages" }) {
            PdfPagePickerCard(
                pdfUri = pdfUri,
                storage = container.storage,
                isEnglish = isEnglish,
                reorderMode = false,
                pages = PdfVaultEngine.parsePagesLoose(state.paramValues["pages"]?.toString() ?: ""),
                onPagesChange = { pages -> vm.setParam("pages", PdfVaultEngine.formatPageRange(pages)) },
                password = pdfPassword,
                previewRotationDeg = if (tool.id == "PDF-03") {
                    state.paramValues["angle"]?.toString()?.toIntOrNull() ?: 0
                } else 0
            )
        }
    }

    val isSingleAudioSpec = tool.inputSpec == InputSpec.SingleAudio ||
        tool.inputSpec == InputSpec.AudioAndImage ||
        tool.inputSpec == InputSpec.AudioAndWatermarkAudio
    val doneAudioOutput = (state.progress as? ToolProgress.Done)
        ?.result
        ?.outputs
        ?.firstOrNull { it.mimeType.startsWith("audio/") && it.file.absolutePath !in state.deletedOutputPaths }
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
    tool: Tool,
    state: ToolUiState,
    readiness: WorkspaceReadiness,
    showUploadConsent: () -> Unit,
    onExecute: () -> Unit,
    scope: CoroutineScope,
    container: AppContainer
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.validationError.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workspace_validation_error"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = state.validationError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
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
            enabled = !state.isRunning && (tool.isStub || readiness.isReadyToRun),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("run_tool_button")
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
}

@Composable
fun UploadConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isEnglish: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upload_consent_title)) },
        text = { Text(stringResource(R.string.upload_consent_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.upload_consent_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.upload_consent_cancel)) }
        }
    )
}

@Composable
fun PdfToolCapabilityCards(
    toolId: String,
    group: ToolGroup,
    paramValues: Map<String, Any>,
    onOpenPdfStudio: ((initialTab: String, toolId: String) -> Unit)?
) {
    val context = LocalContext.current
    val isPdfOrBridge = group == ToolGroup.PDF || toolId in setOf("CNV-13", "CNV-14", "CNV-15")

    if (isPdfOrBridge && onOpenPdfStudio != null) {
        OutlinedButton(
            onClick = { onOpenPdfStudio(tabForPdfTool(toolId).name, toolId) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tool_open_pdf_studio_btn")
        ) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.pdf_studio_open_in_tool),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    val honestBodyRes = when (toolId) {
        "PDF-06", "PDF-07", "CNV-13" -> R.string.pdf_honest_basic_conversion_body
        "PDF-13" -> R.string.pdf_honest_unwatermark_body
        "PDF-09", "PDF-14" -> R.string.pdf_honest_raster_body
        "PDF-17" -> R.string.pdf_honest_ai_gateway_body
        "PDF-20" -> R.string.pdf_honest_compare_body
        else -> null
    }
    val honestTitleRes = when (toolId) {
        "PDF-06", "PDF-07", "CNV-13" -> R.string.pdf_honest_basic_conversion_title
        else -> R.string.pdf_honest_limits_title
    }

    if (honestBodyRes != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pdf_honest_notice_card"),
            colors = CardDefaults.cardColors(containerColor = HariMuThemeTokens.semantic.warningContainer),
            border = BorderStroke(1.dp, HariMuThemeTokens.semantic.warning.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = HariMuThemeTokens.semantic.onWarningContainer,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(honestTitleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = HariMuThemeTokens.semantic.onWarningContainer
                    )
                    Text(
                        text = stringResource(honestBodyRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = HariMuThemeTokens.semantic.onWarningContainer
                    )
                }
            }
        }
    }

    if (toolId == "PDF-15" || toolId == "PDF-16" || toolId == "CNV-15") {
        val rawLang = paramValues["language"]?.toString() ?: "Indonesia"
        val codes = rawLang.split("+").map { PdfOcrEngine.codeFromLabel(it.trim()) }.distinct()
        val missing = remember(codes, context) { PdfOcrEngine.missing(context, codes) }
        val ready = missing.isEmpty()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pdf_ocr_readiness_card"),
            shape = RoundedCornerShape(10.dp),
            color = if (ready) HariMuThemeTokens.semantic.successContainer else HariMuThemeTokens.semantic.infoContainer
        ) {
            Text(
                text = if (ready) {
                    stringResource(R.string.pdf_studio_ocr_ready) + " (${codes.joinToString("+")})"
                } else {
                    stringResource(R.string.pdf_studio_ocr_need_download) + " (${missing.joinToString("+")})"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ready) HariMuThemeTokens.semantic.onSuccessContainer else HariMuThemeTokens.semantic.onInfoContainer,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
fun ConverterToolCapabilityCards(
    toolId: String,
    group: ToolGroup,
    selectedUris: List<Uri>,
    container: AppContainer,
    isEnglish: Boolean,
    onOpenUniversalConverter: ((toolId: String) -> Unit)?
) {
    if (group != ToolGroup.CONVERTER) return
    val context = LocalContext.current

    if (onOpenUniversalConverter != null) {
        OutlinedButton(
            onClick = { onOpenUniversalConverter(toolId) },
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("tool_open_converter_btn")
        ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.converter_open_workbench_cta),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    var detectedSources by remember(toolId, selectedUris) { mutableStateOf<List<DetectedSource>>(emptyList()) }
    LaunchedEffect(toolId, selectedUris) {
        detectedSources = if (selectedUris.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                selectedUris.map { uri -> SourceFormatDetector.detectFromUri(context, container.storage, uri) }
            }
        }
    }

    if (detectedSources.isNotEmpty()) {
        val summaryLine = detectedSources.joinToString(", ") { src ->
            "${src.displayName} → ${src.format.badge} (${src.detectionMethod})"
        }
        val notes = detectedSources.mapNotNull { src ->
            src.note(isEnglish) ?: ConverterFormatMatrix.unsupportedReason(src.format, isEnglish)
        }.distinct()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("converter_detected_format_card"),
            shape = RoundedCornerShape(10.dp),
            color = if (notes.isEmpty()) HariMuThemeTokens.semantic.infoContainer else HariMuThemeTokens.semantic.warningContainer
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summaryLine,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (notes.isEmpty()) HariMuThemeTokens.semantic.onInfoContainer else HariMuThemeTokens.semantic.onWarningContainer
                )
                notes.forEach { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = HariMuThemeTokens.semantic.onWarningContainer
                    )
                }
            }
        }
    }

    val honestNoteRes = when (toolId) {
        "CNV-03" -> R.string.converter_honest_cnv03
        "CNV-04" -> R.string.converter_honest_cnv04
        "CNV-06" -> R.string.converter_honest_cnv06
        "CNV-10" -> R.string.converter_honest_cnv10
        "CNV-17" -> R.string.converter_honest_cnv17
        "CNV-18" -> R.string.converter_honest_cnv18
        "CNV-19" -> R.string.converter_honest_cnv19
        "CNV-20" -> R.string.converter_honest_cnv20
        "CNV-21" -> R.string.converter_honest_cnv21
        "CNV-22" -> R.string.converter_honest_cnv22
        else -> null
    }

    if (honestNoteRes != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("converter_honest_notice_card"),
            colors = CardDefaults.cardColors(containerColor = HariMuThemeTokens.semantic.warningContainer),
            border = BorderStroke(1.dp, HariMuThemeTokens.semantic.warning.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = HariMuThemeTokens.semantic.onWarningContainer,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.converter_honest_limits_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = HariMuThemeTokens.semantic.onWarningContainer
                    )
                    Text(
                        text = stringResource(honestNoteRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = HariMuThemeTokens.semantic.onWarningContainer
                    )
                }
            }
        }
    }
}
