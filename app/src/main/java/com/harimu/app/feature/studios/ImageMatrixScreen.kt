package com.harimu.app.feature.studios

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.image.ImageMatrixCanvasInspector
import com.harimu.app.core.design.image.ImageMatrixCheckerboardBox
import com.harimu.app.core.design.image.ImageMatrixExportDialog
import com.harimu.app.core.design.image.ImageMatrixLayerRow
import com.harimu.app.core.design.image.ImageMatrixTransformInspector
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.feature.toolscreen.openFile
import com.harimu.app.feature.toolscreen.saveFile
import com.harimu.app.feature.toolscreen.shareFile
import com.harimu.app.feature.toolscreen.uploadToCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class MatrixInspectorTab {
    TRANSFORM, LAYERS, CANVAS
}

/**
 * IMG-47 Image Matrix Studio: adaptive multilayer workspace with transparency checkerboard,
 * viewport zoom, direct drag-to-move layer positioning, undo/redo stack, ML Kit / Basic background
 * removal, and PNG/JPG/WEBP export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMatrixScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: ImageMatrixViewModel = viewModel(
        factory = ImageMatrixViewModel.Factory(container, context.applicationContext)
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())

    LaunchedEffect(Unit) { vm.initialize() }
    LaunchedEffect(isEnglish) { vm.setLanguage(isEnglish) }
    LaunchedEffect(state.lastMessage) {
        val message = state.lastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        vm.consumeMessage()
    }

    var activeTab by remember { mutableStateOf(MatrixInspectorTab.TRANSFORM) }
    var viewportZoom by remember { mutableFloatStateOf(1f) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showAddStickerDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<ToolOutputFile?>(null) }

    val savedToast = stringResource(R.string.toast_saved)
    val saveFailedToast = stringResource(R.string.toast_save_failed)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addImage(uri)
    }

    val safSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { destUri ->
        val src = pendingSaveFile
        if (destUri != null && src != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    container.storage.copyFileToUri(src.file, destUri)
                }
                Toast.makeText(context, if (ok) savedToast else saveFailedToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val canvasW = state.canvasSize.first
    val canvasH = state.canvasSize.second

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.image_matrix_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.image_matrix_meta,
                                state.layers.size,
                                canvasW,
                                canvasH,
                                (viewportZoom * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.undo() },
                        enabled = state.canUndo,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_undo_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.cd_undo))
                    }
                    IconButton(
                        onClick = { vm.redo() },
                        enabled = state.canRedo,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_redo_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.cd_redo))
                    }
                    FilledTonalButton(
                        onClick = { showExportDialog = true },
                        enabled = state.layers.isNotEmpty() && !state.isExporting,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_top_export_btn")
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.image_matrix_export_short))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { inner ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWide = maxWidth >= 720.dp

            val canvasPane: @Composable (Modifier) -> Unit = { paneModifier ->
                Column(
                    modifier = paneModifier,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ImageMatrixCheckerboardBox(
                        preview = state.preview,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        selectedLayer = state.selectedLayer,
                        isRendering = state.isRendering,
                        zoom = viewportZoom,
                        onZoomChange = { viewportZoom = it },
                        onDragSelectedLayer = { dx, dy -> vm.moveSelectedBy(dx, dy) },
                        onDragSelectedLayerEnd = { vm.commitLayers() }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HariMuColors.GroupImage,
                                contentColor = Color(0xFF062026)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("matrix_add_image_btn")
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.image_matrix_add_image),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { showAddTextDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("matrix_add_text_btn")
                        ) {
                            Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.image_matrix_add_text),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { showAddStickerDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("matrix_add_sticker_btn")
                        ) {
                            Icon(Icons.Filled.EmojiEmotions, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.image_matrix_add_sticker),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            val inspectorPane: @Composable (Modifier) -> Unit = { paneModifier ->
                Column(
                    modifier = paneModifier,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = activeTab == MatrixInspectorTab.TRANSFORM,
                            onClick = { activeTab = MatrixInspectorTab.TRANSFORM },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("matrix_tab_transform")
                        ) {
                            Text(stringResource(R.string.image_matrix_tab_transform), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SegmentedButton(
                            selected = activeTab == MatrixInspectorTab.LAYERS,
                            onClick = { activeTab = MatrixInspectorTab.LAYERS },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("matrix_tab_layers")
                        ) {
                            Text(stringResource(R.string.image_matrix_tab_layers, state.layers.size), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SegmentedButton(
                            selected = activeTab == MatrixInspectorTab.CANVAS,
                            onClick = { activeTab = MatrixInspectorTab.CANVAS },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("matrix_tab_canvas")
                        ) {
                            Text(stringResource(R.string.image_matrix_tab_canvas), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    when (activeTab) {
                        MatrixInspectorTab.TRANSFORM -> {
                            ImageMatrixTransformInspector(
                                layer = state.selectedLayer,
                                blendModes = vm.blendModes,
                                isRendering = state.isRendering,
                                onRemoveBackground = vm::removeBackgroundOfLayer,
                                onUpdateText = vm::updateText,
                                onSetColor = vm::setLayerColor,
                                onPreviewLayer = vm::previewLayer,
                                onCommitLayers = vm::commitLayers,
                                onUpdateLayer = vm::updateLayer,
                                onCenterLayer = vm::centerLayer,
                                onResetTransform = vm::resetTransform,
                                isEnglish = isEnglish
                            )
                        }

                        MatrixInspectorTab.LAYERS -> {
                            if (state.layers.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Filled.Layers,
                                    title = stringResource(R.string.image_matrix_layers_empty_title),
                                    subtitle = stringResource(R.string.image_matrix_layers_empty_subtitle),
                                    modifier = Modifier.testTag("matrix_layers_empty")
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.layers.asReversed().forEachIndexed { reverseIndex, layer ->
                                        val index = state.layers.lastIndex - reverseIndex
                                        val selected = layer.id == state.selectedLayer?.id
                                        ImageMatrixLayerRow(
                                            index = index,
                                            layer = layer,
                                            kindLabel = vm.layerKindLabel(layer.kind, isEnglish),
                                            selected = selected,
                                            canMoveUp = index < state.layers.lastIndex,
                                            canMoveDown = index > 0,
                                            onSelect = { vm.selectLayer(layer.id) },
                                            onToggleVisible = { vm.toggleVisibility(layer.id) },
                                            onMoveUp = { vm.moveLayer(layer.id, 1) },
                                            onMoveDown = { vm.moveLayer(layer.id, -1) },
                                            onDuplicate = { vm.duplicateLayer(layer.id) },
                                            onDelete = { vm.deleteLayer(layer.id) },
                                            isEnglish = isEnglish
                                        )
                                    }
                                }
                            }
                        }

                        MatrixInspectorTab.CANVAS -> {
                            ImageMatrixCanvasInspector(
                                presets = vm.canvasPresets,
                                selectedPresetIndex = state.canvasPresetIndex,
                                backgroundHex = state.backgroundHex,
                                hasLayers = state.layers.isNotEmpty(),
                                onSelectPreset = vm::setCanvasPreset,
                                onApplyBackground = vm::setBackground,
                                onClearCanvas = vm::clearCanvas
                            )
                        }
                    }
                }
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1140.dp)
                        .padding(16.dp)
                        .testTag("matrix_wide_workspace"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    canvasPane(
                        Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    )
                    inspectorPane(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .testTag("matrix_compact_workspace"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    canvasPane(Modifier.fillMaxWidth())
                    inspectorPane(Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showAddTextDialog) {
        AddTextLayerDialog(
            onConfirm = { txt ->
                vm.addText(txt)
                showAddTextDialog = false
                activeTab = MatrixInspectorTab.TRANSFORM
            },
            onDismiss = { showAddTextDialog = false }
        )
    }

    if (showAddStickerDialog) {
        AddStickerLayerDialog(
            onConfirm = { emoji ->
                vm.addSticker(emoji)
                showAddStickerDialog = false
                activeTab = MatrixInspectorTab.TRANSFORM
            },
            onDismiss = { showAddStickerDialog = false }
        )
    }

    val doneOutput = (state.exportProgress as? ToolProgress.Done)?.result?.outputs?.firstOrNull()
    ImageMatrixExportDialog(
        isOpen = showExportDialog,
        layers = state.layers,
        canvasWidth = canvasW,
        canvasHeight = canvasH,
        progress = state.exportProgress,
        onExport = { fmt, q ->
            vm.exportComposition(format = fmt, quality = q, trackExportDialog = true)
        },
        onDismiss = {
            showExportDialog = false
            vm.resetExportProgress()
        },
        onOpenResult = doneOutput?.let { out -> { openFile(out, container.storage, context) } },
        onShareResult = doneOutput?.let { out -> { shareFile(out, container.storage, context) } },
        onSaveResult = doneOutput?.let { out ->
            {
                saveFile(
                    output = out,
                    storage = container.storage,
                    settings = settings,
                    scope = scope,
                    context = context,
                    savedToast = savedToast,
                    saveFailedToast = saveFailedToast,
                    pendingExportFile = { pendingSaveFile = it },
                    safSaveLauncher = safSaveLauncher
                )
            }
        },
        onUploadResult = doneOutput?.let { out ->
            { uploadToCloud(out, container, settings, scope, context, isEnglish) }
        },
        onCancelExport = vm::cancelExport
    )
}

@Composable
private fun AddTextLayerDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_matrix_add_text)) },
        text = {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text(stringResource(R.string.image_matrix_new_text_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("matrix_text_dialog_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(textInput) },
                enabled = textInput.isNotBlank(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("matrix_text_dialog_confirm")
            ) {
                Text(stringResource(R.string.image_matrix_add_text))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text(stringResource(R.string.cd_image_close))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddStickerLayerDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var emojiInput by remember { mutableStateOf("⭐") }
    val presets = listOf("⭐", "🔥", "❤️", "✨", "📷", "✅")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.image_matrix_add_sticker)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        OutlinedButton(
                            onClick = { emojiInput = preset },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .minimumInteractiveComponentSize()
                        ) {
                            Text(preset)
                        }
                    }
                }
                OutlinedTextField(
                    value = emojiInput,
                    onValueChange = { emojiInput = it },
                    label = { Text(stringResource(R.string.image_matrix_sticker_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("matrix_sticker_dialog_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(emojiInput) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("matrix_sticker_dialog_confirm")
            ) {
                Text(stringResource(R.string.image_matrix_add_sticker))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text(stringResource(R.string.cd_image_close))
            }
        }
    )
}
