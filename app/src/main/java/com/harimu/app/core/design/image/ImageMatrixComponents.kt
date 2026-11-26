package com.harimu.app.core.design.image

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.harimu.app.R
import com.harimu.app.core.ai.SegmentationMode
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.ErrorState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
import com.harimu.app.core.engines.ImageEngine
import com.harimu.app.core.engines.MatrixLayer
import com.harimu.app.core.engines.MatrixLayerKind
import com.harimu.app.core.engines.MatrixLayerMath
import com.harimu.app.core.toolapi.ToolProgress
import kotlin.math.roundToInt

private val quickColorHexes = listOf(
    "#FFFFFF", "#11141C", "#000000", "#06B6D4", "#FF6B2C", "#10B981", "#FBBF24", "#EF4444"
)

@Composable
fun ImageMatrixCheckerboardBox(
    preview: Bitmap?,
    canvasW: Int,
    canvasH: Int,
    selectedLayer: MatrixLayer?,
    isRendering: Boolean,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onDragSelectedLayer: (dxPct: Float, dyPct: Float) -> Unit,
    onDragSelectedLayerEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupImage
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${canvasW}×${canvasH} px",
                    style = MonoTextStyle,
                    color = safeAccent
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { onZoomChange((zoom - 0.25f).coerceIn(1f, 3f)) },
                        enabled = zoom > 1.01f,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_zoom_out_btn")
                    ) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = stringResource(R.string.cd_image_zoom_out))
                    }
                    TextButton(
                        onClick = { onZoomChange(1f) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_zoom_reset_btn")
                    ) {
                        Text(
                            text = "${(zoom * 100).roundToInt()}%",
                            style = MonoTextStyle
                        )
                    }
                    IconButton(
                        onClick = { onZoomChange((zoom + 0.25f).coerceIn(1f, 3f)) },
                        enabled = zoom < 2.99f,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_zoom_in_btn")
                    ) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = stringResource(R.string.cd_image_zoom_in))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .aspectRatio((canvasW.toFloat() / canvasH.toFloat()).coerceIn(0.45f, 2.2f))
                    .clip(RoundedCornerShape(12.dp))
                    .clipToBounds()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .pointerInput(selectedLayer?.id, zoom) {
                        if (selectedLayer != null) {
                            detectDragGestures(
                                onDragEnd = onDragSelectedLayerEnd,
                                onDragCancel = onDragSelectedLayerEnd
                            ) { change, dragAmount ->
                                change.consume()
                                val dxPct = (dragAmount.x / (size.width * zoom)) * 100f
                                val dyPct = (dragAmount.y / (size.height * zoom)) * 100f
                                onDragSelectedLayer(dxPct, dyPct)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Checkerboard transparency backdrop
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cell = 14.dp.toPx()
                    val cols = (size.width / cell).toInt() + 1
                    val rows = (size.height / cell).toInt() + 1
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            drawRect(
                                color = if ((r + c) % 2 == 0) Color(0xFF1F2430) else Color(0xFF282E3D),
                                topLeft = Offset(c * cell, r * cell),
                                size = Size(cell, cell)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                        },
                    contentAlignment = Alignment.Center
                ) {
                    preview?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_image_matrix_canvas),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    // Crosshair center indicator for active layer
                    if (selectedLayer != null && selectedLayer.visible) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = selectedLayer.xPct.coerceIn(0f, 100f) / 100f * size.width
                            val cy = selectedLayer.yPct.coerceIn(0f, 100f) / 100f * size.height
                            drawCircle(
                                color = accent,
                                radius = 10.dp.toPx(),
                                center = Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }

                if (isRendering) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.TopCenter),
                        color = accent
                    )
                }
            }

            if (selectedLayer != null) {
                Text(
                    text = stringResource(R.string.image_matrix_drag_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ImageMatrixLayerRow(
    index: Int,
    layer: MatrixLayer,
    kindLabel: String,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupImage
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    val kindIcon = when (layer.kind) {
        MatrixLayerKind.IMAGE -> Icons.Filled.Image
        MatrixLayerKind.TEXT -> Icons.Filled.TextFields
        MatrixLayerKind.STICKER -> Icons.Filled.EmojiEmotions
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("matrix_layer_card_${layer.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) {
            BorderStroke(2.dp, accent)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(kindIcon, contentDescription = null, tint = safeAccent, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${layer.title()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$kindLabel • ${layer.opacityPct}% • ${layer.blend}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = onToggleVisible,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("matrix_layer_vis_${layer.id}")
                ) {
                    Icon(
                        if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = stringResource(R.string.cd_image_layer_visible)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.cd_image_layer_up))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.cd_image_layer_down))
                }
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.cd_image_layer_duplicate))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("matrix_layer_del_${layer.id}")
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_image_layer_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageMatrixTransformInspector(
    layer: MatrixLayer?,
    blendModes: List<String>,
    isRendering: Boolean,
    onRemoveBackground: (String, SegmentationMode) -> Unit,
    onUpdateText: (String, String) -> Unit,
    onSetColor: (String, String) -> Unit,
    onPreviewLayer: (String, (MatrixLayer) -> MatrixLayer) -> Unit,
    onCommitLayers: () -> Unit,
    onUpdateLayer: (String, (MatrixLayer) -> MatrixLayer) -> Unit,
    onCenterLayer: (String) -> Unit,
    onResetTransform: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (layer == null) {
        EmptyState(
            icon = Icons.Filled.Layers,
            title = stringResource(R.string.image_matrix_no_selection_title),
            subtitle = stringResource(R.string.image_matrix_no_selection_subtitle),
            modifier = modifier
        )
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("matrix_transform_inspector"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.image_matrix_active_layer, layer.title()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { onCenterLayer(layer.id) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_center_layer_btn")
                    ) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.image_matrix_center_layer))
                    }
                    OutlinedButton(
                        onClick = { onResetTransform(layer.id) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_reset_transform_btn")
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (layer.kind == MatrixLayerKind.IMAGE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRemoveBackground(layer.id, SegmentationMode.FULL) },
                        enabled = !isRendering,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_remove_bg_mlkit_btn")
                    ) {
                        Text(stringResource(R.string.image_matrix_remove_bg_mlkit))
                    }
                    OutlinedButton(
                        onClick = { onRemoveBackground(layer.id, SegmentationMode.BASIC) },
                        enabled = !isRendering,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_remove_bg_basic_btn")
                    ) {
                        Text(stringResource(R.string.image_matrix_remove_bg_basic))
                    }
                }
            }

            if (layer.kind != MatrixLayerKind.IMAGE) {
                OutlinedTextField(
                    value = layer.text,
                    onValueChange = { onUpdateText(layer.id, it) },
                    label = { Text(stringResource(R.string.image_matrix_layer_text_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = layer.colorHex,
                    onValueChange = { onSetColor(layer.id, it) },
                    label = { Text(stringResource(R.string.image_matrix_layer_color_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ColorSwatchStrip(
                    selectedHex = layer.colorHex,
                    onSelect = { hex -> onSetColor(layer.id, hex) }
                )
                MatrixTransformSlider(
                    label = stringResource(R.string.image_matrix_font_size_pct),
                    value = layer.fontSizePct,
                    range = 1f..40f,
                    onValue = { v -> onPreviewLayer(layer.id) { it.copy(fontSizePct = v) } },
                    onCommit = onCommitLayers
                )
            }

            MatrixTransformSlider(
                label = stringResource(R.string.image_matrix_pos_x_pct),
                value = layer.xPct,
                range = 0f..100f,
                onValue = { v -> onPreviewLayer(layer.id) { it.copy(xPct = v) } },
                onCommit = onCommitLayers
            )
            MatrixTransformSlider(
                label = stringResource(R.string.image_matrix_pos_y_pct),
                value = layer.yPct,
                range = 0f..100f,
                onValue = { v -> onPreviewLayer(layer.id) { it.copy(yPct = v) } },
                onCommit = onCommitLayers
            )
            MatrixTransformSlider(
                label = stringResource(R.string.image_matrix_scale_pct),
                value = layer.scalePct,
                range = 10f..300f,
                onValue = { v -> onPreviewLayer(layer.id) { it.copy(scalePct = v) } },
                onCommit = onCommitLayers
            )
            MatrixTransformSlider(
                label = stringResource(R.string.image_matrix_rotation_deg),
                value = layer.rotationDeg,
                range = -180f..180f,
                onValue = { v -> onPreviewLayer(layer.id) { it.copy(rotationDeg = v) } },
                onCommit = onCommitLayers
            )
            MatrixTransformSlider(
                label = stringResource(R.string.image_matrix_opacity_pct),
                value = layer.opacityPct.toFloat(),
                range = 10f..100f,
                onValue = { v -> onPreviewLayer(layer.id) { it.copy(opacityPct = v.roundToInt()) } },
                onCommit = onCommitLayers
            )

            Text(stringResource(R.string.image_matrix_blend_mode), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                blendModes.forEach { mode ->
                    FilterChip(
                        selected = layer.blend == mode,
                        onClick = { onUpdateLayer(layer.id) { it.copy(blend = mode) } },
                        label = { Text(mode) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }
        }
    }
}

@Composable
fun ImageMatrixCanvasInspector(
    presets: List<Pair<String, Pair<Int, Int>>>,
    selectedPresetIndex: Int,
    backgroundHex: String,
    hasLayers: Boolean,
    onSelectPreset: (Int) -> Unit,
    onApplyBackground: (String) -> Unit,
    onClearCanvas: () -> Unit,
    modifier: Modifier = Modifier
) {
    var backgroundInput by remember(backgroundHex) { mutableStateOf(backgroundHex) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("matrix_canvas_inspector"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.image_matrix_canvas_presets),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEachIndexed { index, preset ->
                    FilterChip(
                        selected = selectedPresetIndex == index,
                        onClick = { onSelectPreset(index) },
                        label = { Text(preset.first) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_preset_$index")
                    )
                }
            }

            ColorSwatchStrip(
                selectedHex = backgroundInput,
                onSelect = { hex ->
                    backgroundInput = hex
                    onApplyBackground(hex)
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = backgroundInput,
                    onValueChange = { backgroundInput = it },
                    label = { Text(stringResource(R.string.image_matrix_canvas_bg_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onApplyBackground(backgroundInput) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.image_matrix_apply_bg))
                }
            }

            if (hasLayers) {
                OutlinedButton(
                    onClick = onClearCanvas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("matrix_clear_canvas_btn")
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.image_matrix_clear_canvas))
                }
            }
        }
    }
}

@Composable
fun ImageMatrixExportDialog(
    isOpen: Boolean,
    layers: List<MatrixLayer>,
    canvasWidth: Int,
    canvasHeight: Int,
    progress: ToolProgress?,
    onExport: (format: String, quality: Int) -> Unit,
    onDismiss: () -> Unit,
    onOpenResult: (() -> Unit)?,
    onShareResult: (() -> Unit)?,
    onSaveResult: (() -> Unit)?,
    onUploadResult: (() -> Unit)?
) {
    val activeOverlay = progress != null
    if (!isOpen && !activeOverlay) return

    var selectedFormat by remember { mutableStateOf("PNG") }
    var quality by remember { mutableFloatStateOf(95f) }
    val visibleCount = remember(layers) { layers.count { it.visible } }

    AlertDialog(
        onDismissRequest = {
            if (progress !is ToolProgress.Running && progress !is ToolProgress.Queued) {
                onDismiss()
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.image_matrix_export_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (progress !is ToolProgress.Running && progress !is ToolProgress.Queued) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("matrix_export_close_btn")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_image_close))
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (progress) {
                    is ToolProgress.Queued -> ProgressSheet(
                        percent = null,
                        message = stringResource(R.string.progress_queued),
                        onCancel = {}
                    )

                    is ToolProgress.Running -> ProgressSheet(
                        percent = progress.percent,
                        message = progress.message,
                        onCancel = {}
                    )

                    is ToolProgress.Failed -> ErrorState(
                        message = progress.error,
                        onRetry = { onExport(selectedFormat, quality.roundToInt()) }
                    )

                    is ToolProgress.Done -> ResultCard(
                        summary = progress.result.summary,
                        onOpen = onOpenResult,
                        onShare = onShareResult,
                        onSave = onSaveResult,
                        onUploadToCloud = onUploadResult
                    )

                    null -> {
                        Text(
                            text = stringResource(R.string.image_matrix_export_format),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("PNG", "JPG", "WEBP").forEach { fmt ->
                                FilterChip(
                                    selected = selectedFormat == fmt,
                                    onClick = { selectedFormat = fmt },
                                    label = { Text(fmt) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("matrix_export_fmt_$fmt"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = HariMuColors.GroupImage.copy(alpha = 0.2f),
                                        selectedLabelColor = HariMuThemeTokens.semantic.contrastSafeAccent(HariMuColors.GroupImage)
                                    )
                                )
                            }
                        }

                        if (selectedFormat != "PNG") {
                            MatrixTransformSlider(
                                label = stringResource(R.string.image_matrix_export_quality, quality.roundToInt()),
                                value = quality,
                                range = 50f..100f,
                                onValue = { quality = it },
                                onCommit = {}
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.image_matrix_export_summary,
                                    layers.size,
                                    visibleCount,
                                    canvasWidth,
                                    canvasHeight
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        if (layers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.image_matrix_export_need_layer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Button(
                                onClick = { onExport(selectedFormat, quality.roundToInt()) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("matrix_export_start_btn")
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.image_matrix_export_start))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (progress == null || progress is ToolProgress.Done || progress is ToolProgress.Failed) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.cd_image_close))
                }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = progress !is ToolProgress.Running && progress !is ToolProgress.Queued,
            dismissOnClickOutside = progress !is ToolProgress.Running && progress !is ToolProgress.Queued
        )
    )
}

@Composable
private fun ColorSwatchStrip(
    selectedHex: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        quickColorHexes.forEach { hex ->
            val color = Color(ImageEngine.parseColorSafe(hex, android.graphics.Color.WHITE))
            val selected = selectedHex.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color = if (selected) HariMuColors.GroupImage else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun MatrixTransformSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    onCommit: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value.roundToInt().toString(), style = MonoTextStyle)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range,
            onValueChangeFinished = onCommit
        )
    }
}
