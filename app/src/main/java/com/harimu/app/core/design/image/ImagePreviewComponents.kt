package com.harimu.app.core.design.image

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.engines.FormatSupportLevel
import com.harimu.app.core.engines.ImageEngine
import com.harimu.app.core.engines.ImageFormatMatrix
import com.harimu.app.core.engines.ImageGeometryMath
import com.harimu.app.core.engines.ImageIO
import com.harimu.app.core.engines.ImageLutEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Dispatcher for specialized live visual previews on [com.harimu.app.feature.toolscreen.ToolScreen]
 * across Image Forge tools. Every math overlay delegates to actual engine helpers so preview geometry
 * matches export behavior.
 */
@Composable
fun ImageForgeToolPreviewSection(
    toolId: String,
    selectedUris: List<Uri>,
    textInput: String,
    params: Map<String, Any>,
    onParamChange: (String, Any) -> Unit,
    isEnglish: Boolean
) {
    val firstUri = selectedUris.firstOrNull()
    when (toolId) {
        "IMG-01" -> if (firstUri != null) {
            ImageCropOverlayPreviewCard(
                imageUri = firstUri,
                ratioLabel = params["ratio"]?.toString() ?: "1:1",
                zoomPct = (params["zoom"] as? Number)?.toFloat() ?: 100f,
                offsetXPct = (params["offsetX"] as? Number)?.toFloat() ?: 0f,
                offsetYPct = (params["offsetY"] as? Number)?.toFloat() ?: 0f,
                onOffsetChange = { nx, ny ->
                    onParamChange("offsetX", nx)
                    onParamChange("offsetY", ny)
                }
            )
        }

        "IMG-05" -> if (firstUri != null) {
            ImageCanvasExpanderPreviewCard(
                imageUri = firstUri,
                aspectLabel = params["aspect"]?.toString() ?: "1:1",
                mode = params["mode"]?.toString() ?: "Blur latar",
                bgHex = params["bgHex"]?.toString() ?: "#13161F"
            )
        }

        "IMG-07" -> if (firstUri != null) {
            ImageStraightenPreviewCard(
                imageUri = firstUri,
                angleDeg = (params["angle"] as? Number)?.toFloat() ?: 3f,
                fillMode = params["fill"]?.toString() ?: "Potong otomatis",
                bgHex = params["bgHex"]?.toString() ?: "#13161F"
            )
        }

        "IMG-19" -> if (firstUri != null) {
            ImageRadialFocusPreviewCard(
                imageUri = firstUri,
                focusXPct = (params["focusX"] as? Number)?.toFloat() ?: 50f,
                focusYPct = (params["focusY"] as? Number)?.toFloat() ?: 45f,
                radiusPct = (params["radius"] as? Number)?.toFloat() ?: 42f,
                onFocusChange = { fx, fy ->
                    onParamChange("focusX", fx)
                    onParamChange("focusY", fy)
                }
            )
        }

        "IMG-23" -> {
            ImageFormatSupportCard(
                formatLabel = params["format"]?.toString() ?: "WEBP",
                isEnglish = isEnglish
            )
        }

        "IMG-31" -> if (firstUri != null) {
            ImageWatermarkOverlayPreviewCard(
                imageUri = firstUri,
                text = params["text"]?.toString() ?: "© HariMu Studio",
                opacityPct = (params["opacity"] as? Number)?.toFloat() ?: 45f,
                position = params["pos"]?.toString() ?: "Kanan Bawah",
                tiled = (params["tiled"] as? Boolean) ?: false
            )
        }

        "IMG-34" -> if (firstUri != null) {
            ImageBorderFramePreviewCard(
                imageUri = firstUri,
                padPct = (params["pad"] as? Number)?.toFloat() ?: 6f,
                colorHex = params["color"]?.toString() ?: "#FFFFFF",
                polaroid = (params["polaroid"] as? Boolean) ?: true
            )
        }

        "IMG-35" -> {
            ImagePosterPreviewCard(
                quoteText = textInput,
                style = params["style"]?.toString() ?: "Sunset Amber"
            )
        }

        "IMG-36" -> if (firstUri != null) {
            ImageGradientOverlayPreviewCard(
                imageUri = firstUri,
                startHex = params["startHex"]?.toString() ?: "#FF6B2C",
                endHex = params["endHex"]?.toString() ?: "#00BFA5",
                opacityPct = (params["opacity"] as? Number)?.toFloat() ?: 35f
            )
        }

        "IMG-38" -> if (firstUri != null) {
            ImageStickerOverlayPreviewCard(
                imageUri = firstUri,
                emoji = params["emoji"]?.toString().orEmpty().ifBlank { "⭐" },
                xPct = (params["xPct"] as? Number)?.toFloat() ?: 75f,
                yPct = (params["yPct"] as? Number)?.toFloat() ?: 25f,
                sizePct = (params["sizePct"] as? Number)?.toFloat() ?: 18f,
                rotationDeg = (params["rotation"] as? Number)?.toFloat() ?: 0f,
                opacityPct = (params["opacity"] as? Number)?.toFloat() ?: 100f,
                onMove = { nx, ny ->
                    onParamChange("xPct", nx)
                    onParamChange("yPct", ny)
                }
            )
        }

        "IMG-39" -> {
            ImageLutPreviewCard(
                mode = params["mode"]?.toString() ?: "Preset bawaan",
                preset = params["preset"]?.toString() ?: "Teal & Orange",
                intensityPct = (params["intensity"] as? Number)?.toInt() ?: 85,
                isEnglish = isEnglish,
                imageUri = firstUri,
                cubeText = params["cubeText"]?.toString() ?: ""
            )
        }

        "IMG-41" -> if (firstUri != null) {
            ImageLensFlarePreviewCard(
                imageUri = firstUri,
                xPct = (params["xPct"] as? Number)?.toFloat() ?: 78f,
                yPct = (params["yPct"] as? Number)?.toFloat() ?: 22f,
                intensityPct = (params["intensity"] as? Number)?.toFloat() ?: 60f,
                warmHex = params["warm"]?.toString() ?: "#FFD9A0",
                onMove = { nx, ny ->
                    onParamChange("xPct", nx)
                    onParamChange("yPct", ny)
                }
            )
        }
    }
}

@Composable
internal fun rememberImageDisplayDimensions(imageUri: Uri): Pair<Int, Int> {
    val context = LocalContext.current
    var dims by remember(imageUri) { mutableStateOf(1000 to 750) }
    LaunchedEffect(imageUri) {
        dims = withContext(Dispatchers.IO) {
            runCatching {
                val info = ImageIO.readSourceInfo(context, imageUri, null)
                info.displayWidth.coerceAtLeast(1) to info.displayHeight.coerceAtLeast(1)
            }.getOrDefault(1000 to 750)
        }
    }
    return dims
}

/**
 * Fits [imageUri] strictly to its real EXIF-adjusted aspect ratio within [maxHeightDp] so
 * interactive overlays, crop frames, and drag coordinates align 1:1 with the rendered photo bounds.
 */
@Composable
fun FittedImageBox(
    imageUri: Uri,
    contentDescription: String,
    modifier: Modifier = Modifier,
    maxHeightDp: Dp = 240.dp,
    colorFilter: ColorFilter? = null,
    stageModifier: Modifier = Modifier,
    overlay: @Composable BoxScope.(srcWidth: Int, srcHeight: Int) -> Unit = { _, _ -> }
) {
    val dims = rememberImageDisplayDimensions(imageUri)
    val aspect = (dims.first.toFloat() / dims.second.toFloat()).coerceIn(0.25f, 4.0f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeightDp),
        contentAlignment = Alignment.Center
    ) {
        val availW = maxWidth
        val availH = if (maxHeight != Dp.Infinity) minOf(maxHeight, maxHeightDp) else maxHeightDp
        val (boxW, boxH) = if (availW / availH > aspect) {
            (availH * aspect) to availH
        } else {
            availW to (availW / aspect)
        }
        Box(
            modifier = Modifier
                .size(boxW, boxH)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(stageModifier)
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
            overlay(dims.first, dims.second)
        }
    }
}

@Composable
fun ImageCropOverlayPreviewCard(
    imageUri: Uri,
    ratioLabel: String,
    zoomPct: Float,
    offsetXPct: Float,
    offsetYPct: Float,
    onOffsetChange: (Float, Float) -> Unit
) {
    val dims = rememberImageDisplayDimensions(imageUri)
    val srcW = dims.first
    val srcH = dims.second
    val (rw, rh) = remember(ratioLabel, srcW, srcH) {
        if (ratioLabel == "Bebas") {
            srcW.toFloat() to srcH.toFloat()
        } else {
            val parts = ratioLabel.split(':')
            (parts.getOrNull(0)?.toFloatOrNull() ?: 1f) to (parts.getOrNull(1)?.toFloatOrNull() ?: 1f)
        }
    }
    val cropRect = remember(srcW, srcH, rw, rh, zoomPct, offsetXPct, offsetYPct) {
        ImageGeometryMath.cropRectForRatio(srcW, srcH, rw, rh, zoomPct, offsetXPct, offsetYPct)
    }
    val currentOffsetX by rememberUpdatedState(offsetXPct)
    val currentOffsetY by rememberUpdatedState(offsetYPct)
    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_crop_title, ratioLabel),
        testTag = "img_crop_preview_card"
    ) {
        FittedImageBox(
            imageUri = imageUri,
            contentDescription = stringResource(R.string.image_preview_crop_title, ratioLabel),
            maxHeightDp = 260.dp,
            stageModifier = Modifier.pointerInput(imageUri) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val nx = (currentOffsetX + dragAmount.x / w * 200f).coerceIn(-100f, 100f)
                    val ny = (currentOffsetY + dragAmount.y / h * 200f).coerceIn(-100f, 100f)
                    currentOnOffsetChange(nx.roundToInt().toFloat(), ny.roundToInt().toFloat())
                }
            }
        ) { wSrc, hSrc ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = cropRect.left.toFloat() / wSrc * size.width
                val top = cropRect.top.toFloat() / hSrc * size.height
                val w = cropRect.width.toFloat() / wSrc * size.width
                val h = cropRect.height.toFloat() / hSrc * size.height

                // Dim outside crop rectangle
                drawRect(Color.Black.copy(alpha = 0.52f), Offset.Zero, Size(size.width, top))
                drawRect(Color.Black.copy(alpha = 0.52f), Offset(0f, top + h), Size(size.width, (size.height - top - h).coerceAtLeast(0f)))
                drawRect(Color.Black.copy(alpha = 0.52f), Offset(0f, top), Size(left, h))
                drawRect(Color.Black.copy(alpha = 0.52f), Offset(left + w, top), Size((size.width - left - w).coerceAtLeast(0f), h))

                // Rule-of-thirds grid
                val gridColor = Color.White.copy(alpha = 0.55f)
                for (i in 1..2) {
                    val x = left + w * i / 3f
                    val y = top + h * i / 3f
                    drawLine(gridColor, Offset(x, top), Offset(x, top + h), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(left, y), Offset(left + w, y), strokeWidth = 1.dp.toPx())
                }

                // Active crop border
                drawRect(
                    color = HariMuColors.GroupImage,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "${cropRect.width}×${cropRect.height} px",
                    style = MonoTextStyle,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.image_preview_crop_caption, zoomPct, offsetXPct, offsetYPct),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ImageStraightenPreviewCard(
    imageUri: Uri,
    angleDeg: Float,
    fillMode: String,
    bgHex: String
) {
    val dims = rememberImageDisplayDimensions(imageUri)
    val bgColor = remember(bgHex, fillMode) {
        if (fillMode == "Warna latar") {
            Color(ImageEngine.parseColorSafe(bgHex, android.graphics.Color.parseColor("#13161F")))
        } else {
            Color(0xFF13161F)
        }
    }
    val safeRect = remember(angleDeg, dims) {
        ImageGeometryMath.straightenSafeRect(dims.first, dims.second, angleDeg)
    }
    val bounds = remember(angleDeg, dims) {
        ImageGeometryMath.rotatedBounds(dims.first, dims.second, angleDeg)
    }
    val boundsAspect = (bounds.first.toFloat() / bounds.second.coerceAtLeast(1).toFloat()).coerceIn(0.25f, 4f)

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_straighten_title, angleDeg),
        testTag = "img_straighten_preview_card"
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val (boxW, boxH) = if (maxWidth / maxHeight > boundsAspect) {
                (maxHeight * boundsAspect) to maxHeight
            } else {
                maxWidth to (maxWidth / boundsAspect)
            }
            val unrotatedW = boxW * (dims.first.toFloat() / bounds.first.coerceAtLeast(1).toFloat())
            val unrotatedH = boxH * (dims.second.toFloat() / bounds.second.coerceAtLeast(1).toFloat())

            Box(
                modifier = Modifier.size(boxW, boxH),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = stringResource(R.string.image_preview_straighten_title, angleDeg),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(unrotatedW, unrotatedH)
                        .rotate(angleDeg)
                )
                if (fillMode == "Potong otomatis") {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val left = safeRect.left.toFloat() / bounds.first.coerceAtLeast(1) * size.width
                        val top = safeRect.top.toFloat() / bounds.second.coerceAtLeast(1) * size.height
                        val w = safeRect.width.toFloat() / bounds.first.coerceAtLeast(1) * size.width
                        val h = safeRect.height.toFloat() / bounds.second.coerceAtLeast(1) * size.height
                        drawRect(
                            color = HariMuColors.TealEmerald,
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
        Text(
            text = if (fillMode == "Potong otomatis") {
                stringResource(R.string.image_preview_straighten_auto)
            } else {
                stringResource(R.string.image_preview_straighten_fill, bgHex)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ImageCanvasExpanderPreviewCard(
    imageUri: Uri,
    aspectLabel: String,
    mode: String,
    bgHex: String
) {
    val ratio = remember(aspectLabel) {
        when (aspectLabel) {
            "4:5" -> 4f / 5f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "3:4" -> 3f / 4f
            else -> 1f
        }
    }
    val solidBg = remember(bgHex) {
        Color(ImageEngine.parseColorSafe(bgHex, android.graphics.Color.parseColor("#13161F")))
    }

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_canvas_title, aspectLabel),
        testTag = "img_canvas_expander_preview_card"
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            val (boxW, boxH) = if (maxWidth / maxHeight > ratio) {
                (maxHeight * ratio) to maxHeight
            } else {
                maxWidth to (maxWidth / ratio)
            }
            Box(
                modifier = Modifier
                    .size(boxW, boxH)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (mode == "Blur latar") {
                            solidBg.copy(alpha = 0.85f)
                        } else {
                            solidBg
                        }
                    )
                    .border(1.dp, HariMuColors.GroupImage.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (mode == "Blur latar") {
                    val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(8.dp)
                    } else {
                        Modifier
                    }
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.35f,
                        modifier = Modifier.fillMaxSize().then(blurMod)
                    )
                }
                AsyncImage(
                    model = imageUri,
                    contentDescription = stringResource(R.string.image_preview_canvas_title, aspectLabel),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                )
            }
        }
        Text(
            text = if (mode == "Blur latar") {
                stringResource(R.string.image_preview_canvas_blur)
            } else {
                stringResource(R.string.image_preview_canvas_solid, bgHex)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ImageRadialFocusPreviewCard(
    imageUri: Uri,
    focusXPct: Float,
    focusYPct: Float,
    radiusPct: Float,
    onFocusChange: (Float, Float) -> Unit
) {
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_bokeh_title),
        testTag = "img_bokeh_preview_card"
    ) {
        FittedImageBox(
            imageUri = imageUri,
            contentDescription = stringResource(R.string.image_preview_bokeh_title),
            maxHeightDp = 220.dp,
            stageModifier = Modifier.pointerInput(imageUri) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val nx = (change.position.x / w * 100f).coerceIn(0f, 100f)
                    val ny = (change.position.y / h * 100f).coerceIn(0f, 100f)
                    currentOnFocusChange(nx.roundToInt().toFloat(), ny.roundToInt().toFloat())
                }
            }
        ) { _, _ ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = focusXPct.coerceIn(0f, 100f) / 100f * size.width
                val cy = focusYPct.coerceIn(0f, 100f) / 100f * size.height
                val r = (radiusPct.coerceIn(10f, 90f) / 100f) * minOf(size.width, size.height) * 0.5f
                drawCircle(
                    color = HariMuColors.GroupImage.copy(alpha = 0.25f),
                    radius = r,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = HariMuColors.GroupImage,
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }
        Text(
            text = stringResource(R.string.image_preview_bokeh_caption, focusXPct, focusYPct, radiusPct),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ImageLensFlarePreviewCard(
    imageUri: Uri,
    xPct: Float,
    yPct: Float,
    intensityPct: Float,
    warmHex: String,
    onMove: (Float, Float) -> Unit
) {
    val flareColor = remember(warmHex) {
        Color(ImageEngine.parseColorSafe(warmHex, android.graphics.Color.parseColor("#FFD9A0")))
    }
    val currentOnMove by rememberUpdatedState(onMove)

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_flare_title),
        testTag = "img_flare_preview_card"
    ) {
        FittedImageBox(
            imageUri = imageUri,
            contentDescription = stringResource(R.string.image_preview_flare_title),
            maxHeightDp = 220.dp,
            stageModifier = Modifier.pointerInput(imageUri) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val nx = (change.position.x / w * 100f).coerceIn(0f, 100f)
                    val ny = (change.position.y / h * 100f).coerceIn(0f, 100f)
                    currentOnMove(nx.roundToInt().toFloat(), ny.roundToInt().toFloat())
                }
            }
        ) { _, _ ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = xPct.coerceIn(0f, 100f) / 100f * size.width
                val cy = yPct.coerceIn(0f, 100f) / 100f * size.height
                val alpha = (intensityPct / 100f).coerceIn(0.15f, 1f)
                drawLine(
                    color = flareColor.copy(alpha = alpha * 0.7f),
                    start = Offset((cx - size.width * 0.35f).coerceAtLeast(0f), cy),
                    end = Offset((cx + size.width * 0.35f).coerceAtMost(size.width), cy),
                    strokeWidth = 3.dp.toPx()
                )
                drawCircle(
                    color = flareColor.copy(alpha = alpha * 0.5f),
                    radius = 28.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }
        Text(
            text = stringResource(R.string.image_preview_flare_caption, xPct, yPct, intensityPct),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ImageStickerOverlayPreviewCard(
    imageUri: Uri,
    emoji: String,
    xPct: Float,
    yPct: Float,
    sizePct: Float,
    rotationDeg: Float,
    opacityPct: Float,
    onMove: (Float, Float) -> Unit
) {
    val currentOnMove by rememberUpdatedState(onMove)

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_sticker_title),
        testTag = "img_sticker_preview_card"
    ) {
        FittedImageBox(
            imageUri = imageUri,
            contentDescription = stringResource(R.string.image_preview_sticker_title),
            maxHeightDp = 220.dp,
            stageModifier = Modifier.pointerInput(imageUri) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val nx = (change.position.x / w * 100f).coerceIn(0f, 100f)
                    val ny = (change.position.y / h * 100f).coerceIn(0f, 100f)
                    currentOnMove(nx.roundToInt().toFloat(), ny.roundToInt().toFloat())
                }
            }
        ) { _, _ ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val dx = (maxWidth * (xPct.coerceIn(0f, 100f) / 100f - 0.5f))
                val dy = (maxHeight * (yPct.coerceIn(0f, 100f) / 100f - 0.5f))
                Text(
                    text = emoji,
                    fontSize = (sizePct.coerceIn(4f, 60f) * 1.4f).sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = if (dx >= 0.dp) dx else 0.dp, end = if (dx < 0.dp) -dx else 0.dp)
                        .padding(top = if (dy >= 0.dp) dy else 0.dp, bottom = if (dy < 0.dp) -dy else 0.dp)
                        .rotate(rotationDeg)
                        .graphicsLayer { alpha = (opacityPct / 100f).coerceIn(0.1f, 1f) }
                )
            }
        }
        Text(
            text = stringResource(R.string.image_preview_sticker_caption, xPct, yPct, sizePct),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ImageWatermarkOverlayPreviewCard(
    imageUri: Uri,
    text: String,
    opacityPct: Float,
    position: String,
    tiled: Boolean
) {
    val alpha = (opacityPct / 100f).coerceIn(0.1f, 1f)
    val alignment = when (position) {
        "Kiri Bawah" -> Alignment.BottomStart
        "Kanan Atas" -> Alignment.TopEnd
        "Kiri Atas" -> Alignment.TopStart
        "Tengah" -> Alignment.Center
        else -> Alignment.BottomEnd
    }

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_watermark_title),
        testTag = "img_watermark_preview_card"
    ) {
        FittedImageBox(
            imageUri = imageUri,
            contentDescription = stringResource(R.string.image_preview_watermark_title),
            maxHeightDp = 220.dp
        ) { _, _ ->
            if (tiled) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(-22f),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            repeat(2) {
                                Text(
                                    text = text.ifBlank { "© HariMu" },
                                    color = Color.White.copy(alpha = alpha),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = text.ifBlank { "© HariMu" },
                    color = Color.White.copy(alpha = alpha),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(alignment)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.25f * alpha), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ImageBorderFramePreviewCard(
    imageUri: Uri,
    padPct: Float,
    colorHex: String,
    polaroid: Boolean
) {
    val frameColor = remember(colorHex) {
        Color(ImageEngine.parseColorSafe(colorHex, android.graphics.Color.WHITE))
    }
    val padDp = (padPct * 1.4f).coerceIn(4f, 28f).dp
    val bottomPad = if (polaroid) padDp * 2.6f else padDp

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_border_title),
        testTag = "img_border_preview_card"
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(frameColor)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(start = padDp, end = padDp, top = padDp, bottom = bottomPad)
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = stringResource(R.string.image_preview_border_title),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(145.dp)
                )
            }
        }
    }
}

@Composable
fun ImageGradientOverlayPreviewCard(
    imageUri: Uri,
    startHex: String,
    endHex: String,
    opacityPct: Float
) {
    val startColor = remember(startHex) { Color(ImageEngine.parseColorSafe(startHex, android.graphics.Color.parseColor("#FF6B2C"))) }
    val endColor = remember(endHex) { Color(ImageEngine.parseColorSafe(endHex, android.graphics.Color.parseColor("#00BFA5"))) }
    val alpha = (opacityPct / 100f).coerceIn(0.05f, 0.9f)

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_gradient_title),
        testTag = "img_gradient_preview_card"
    ) {
        FittedImageBox(
            imageUri = imageUri,
            contentDescription = stringResource(R.string.image_preview_gradient_title),
            maxHeightDp = 220.dp
        ) { _, _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(startColor.copy(alpha = alpha), endColor.copy(alpha = alpha))
                        )
                    )
            )
        }
    }
}

@Composable
fun ImageLutPreviewCard(
    mode: String,
    preset: String,
    intensityPct: Int,
    isEnglish: Boolean,
    imageUri: Uri? = null,
    cubeText: String = ""
) {
    val context = LocalContext.current
    val lut = remember(mode, preset, cubeText) {
        if (mode.startsWith("File") && cubeText.isNotBlank()) {
            runCatching { ImageLutEngine.parseCube(cubeText, "Cube tempel") }
                .getOrElse { ImageLutEngine.preset(preset) }
        } else {
            ImageLutEngine.preset(preset)
        }
    }
    val sampleStops = remember(lut, intensityPct) {
        val baseStops = listOf(
            floatArrayOf(0.10f, 0.14f, 0.22f),
            floatArrayOf(0.28f, 0.35f, 0.45f),
            floatArrayOf(0.55f, 0.52f, 0.48f),
            floatArrayOf(0.82f, 0.55f, 0.38f),
            floatArrayOf(0.22f, 0.65f, 0.60f),
            floatArrayOf(0.92f, 0.88f, 0.80f)
        )
        val t = intensityPct.coerceIn(0, 100) / 100f
        baseStops.map { rgb ->
            val mapped = lut.lookupNormalized(rgb[0], rgb[1], rgb[2])
            val orig = Color(rgb[0], rgb[1], rgb[2])
            val out = Color(
                (rgb[0] + (mapped[0] - rgb[0]) * t).coerceIn(0f, 1f),
                (rgb[1] + (mapped[1] - rgb[1]) * t).coerceIn(0f, 1f),
                (rgb[2] + (mapped[2] - rgb[2]) * t).coerceIn(0f, 1f)
            )
            orig to out
        }
    }

    var showOriginal by remember(imageUri) { mutableStateOf(false) }
    var sourceThumb by remember { mutableStateOf<Bitmap?>(null) }
    var gradedThumb by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        val oldSrc = sourceThumb
        val oldGraded = gradedThumb
        sourceThumb = null
        gradedThumb = null
        if (oldGraded != null && oldGraded !== oldSrc && !oldGraded.isRecycled) oldGraded.recycle()
        if (oldSrc != null && !oldSrc.isRecycled) oldSrc.recycle()
        if (imageUri != null) {
            sourceThumb = withContext(Dispatchers.IO) {
                runCatching { ImageIO.decodePreview(context, imageUri, 320) }.getOrNull()
            }
        }
    }

    LaunchedEffect(sourceThumb, lut, intensityPct) {
        val src = sourceThumb ?: return@LaunchedEffect
        if (src.isRecycled) return@LaunchedEffect
        val fresh = withContext(Dispatchers.Default) {
            try {
                ImageLutEngine.apply(src, lut, intensityPct)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
        }
        val prev = gradedThumb
        gradedThumb = fresh
        if (prev != null && prev !== fresh && prev !== src && !prev.isRecycled) {
            prev.recycle()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val g = gradedThumb
            val s = sourceThumb
            gradedThumb = null
            sourceThumb = null
            if (g != null && g !== s && !g.isRecycled) g.recycle()
            if (s != null && !s.isRecycled) s.recycle()
        }
    }

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_lut_title, if (mode.startsWith("Preset")) preset else mode),
        testTag = "img_lut_preview_card"
    ) {
        if (imageUri != null) {
            val activeBitmap = if (showOriginal) sourceThumb else (gradedThumb ?: sourceThumb)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lut_before_after_toggle"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showOriginal,
                    onClick = { showOriginal = false },
                    label = { Text(stringResource(R.string.image_compare_lut)) },
                    modifier = Modifier.testTag("lut_compare_after")
                )
                FilterChip(
                    selected = showOriginal,
                    onClick = { showOriginal = true },
                    label = { Text(stringResource(R.string.image_compare_original)) },
                    modifier = Modifier.testTag("lut_compare_before")
                )
            }
            if (activeBitmap != null && !activeBitmap.isRecycled) {
                val aspect = (activeBitmap.width.toFloat() / activeBitmap.height.coerceAtLeast(1).toFloat())
                    .coerceIn(0.25f, 4f)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val availH = if (maxHeight != Dp.Infinity) minOf(maxHeight, 210.dp) else 210.dp
                    val (boxW, boxH) = if (maxWidth / availH > aspect) {
                        (availH * aspect) to availH
                    } else {
                        maxWidth to (maxWidth / aspect)
                    }
                    Image(
                        bitmap = activeBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.image_preview_lut_title, lut.title),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(boxW, boxH)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            sampleStops.forEach { (orig, graded) ->
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().background(orig))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().background(graded))
                }
            }
        }
        Text(
            text = if (isEnglish) {
                "Top row: reference tones • Bottom row: graded by ${lut.title} (${lut.size}³) at ${intensityPct}%"
            } else {
                "Baris atas: warna acuan • Baris bawah: hasil ${lut.title} (${lut.size}³) intensitas ${intensityPct}%"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ImagePosterPreviewCard(
    quoteText: String,
    style: String
) {
    val (bgBrush, textColor, accentColor) = remember(style) {
        when (style) {
            "Teal Obsidian" -> Triple(
                Brush.verticalGradient(listOf(Color(0xFF0E141B), Color(0xFF062C2B))),
                Color(0xFFF1F5F9),
                Color(0xFF00BFA5)
            )
            "Editorial Paper" -> Triple(
                Brush.verticalGradient(listOf(Color(0xFFF6F1E7), Color(0xFFEAE1CF))),
                Color(0xFF1E2022),
                Color(0xFFC8430C)
            )
            else -> Triple(
                Brush.verticalGradient(listOf(Color(0xFF28120A), Color(0xFF5D210B))),
                Color(0xFFFFF7ED),
                Color(0xFFFF6B2C)
            )
        }
    }

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_poster_title),
        testTag = "img_poster_preview_card"
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1080f / 1350f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgBrush)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = quoteText.ifBlank { "Kreativitas Tanpa Batas — HariMu" },
                        color = textColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ImageFormatSupportCard(
    formatLabel: String,
    isEnglish: Boolean
) {
    val support = remember(formatLabel) { ImageFormatMatrix.byLabel(formatLabel) } ?: return
    val semantic = HariMuThemeTokens.semantic
    val encodeColor = when (support.encode) {
        FormatSupportLevel.FULL -> semantic.success
        FormatSupportLevel.LIMITED -> semantic.warning
        FormatSupportLevel.NONE -> MaterialTheme.colorScheme.error
    }

    PreviewCardContainer(
        title = stringResource(R.string.image_preview_format_title, support.id),
        testTag = "img_format_support_card"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.image_preview_format_decode_encode,
                    if (isEnglish) support.decode.labelEn else support.decode.labelId,
                    if (isEnglish) support.encode.labelEn else support.encode.labelId
                ),
                style = MaterialTheme.typography.labelLarge,
                color = encodeColor
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = support.extensions.joinToString("/"),
                    style = MonoTextStyle,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            text = if (isEnglish) support.noteEn else support.noteId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewCardContainer(
    title: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, HariMuColors.GroupImage.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = HariMuThemeTokens.semantic.contrastSafeAccent(HariMuColors.GroupImage)
            )
            content()
        }
    }
}
