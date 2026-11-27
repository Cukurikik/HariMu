package com.harimu.app.feature.toolscreen

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * IMG-06 interactive preview: drag the four corners of the document on the photo.
 * Values are percentages of the displayed image, matching the tool's `tlX..blY` params.
 */
@Composable
fun PerspectivePointsCard(
    imageUri: Uri,
    points: List<Float>,
    onPointChange: (index: Int, value: Float) -> Unit
) {
    var aspectRatio by remember { mutableStateOf(1f) }
    var localPoints by remember { mutableStateOf(points) }
    var activeHandle by remember { mutableStateOf(-1) }

    LaunchedEffect(points) { localPoints = points }

    fun safe(index: Int): Float = localPoints.getOrNull(index) ?: 0f
    val hitRadiusPx = with(LocalDensity.current) { 36.dp.toPx() }
    val canvasCd = stringResource(R.string.cd_perspective_preview)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
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
                    text = stringResource(R.string.perspective_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val defaults = listOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
                        localPoints = defaults
                        defaults.forEachIndexed { idx, value -> onPointChange(idx, value) }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("perspective_reset_corners_btn")
                ) {
                    Text(stringResource(R.string.perspective_reset_corners))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.coerceIn(0.4f, 3f))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = canvasCd,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onState = { state ->
                        val size = state.painter?.intrinsicSize
                        if (size != null && size.width > 0f && size.height > 0f &&
                            size.width.isFinite() && size.height.isFinite()
                        ) {
                            aspectRatio = size.width / size.height
                        }
                    }
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = canvasCd }
                        .pointerInput(aspectRatio, hitRadiusPx) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    activeHandle = nearestHandle(
                                        offset,
                                        localPoints,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        hitRadiusPx
                                    )
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val index = activeHandle
                                    if (index < 0) return@detectDragGestures
                                    val width = size.width.toFloat().coerceAtLeast(1f)
                                    val height = size.height.toFloat().coerceAtLeast(1f)
                                    val axisX = index * 2
                                    val axisY = axisX + 1
                                    val newX = ((safe(axisX) / 100f * width + dragAmount.x)
                                        .coerceIn(0f, width) / width) * 100f
                                    val newY = ((safe(axisY) / 100f * height + dragAmount.y)
                                        .coerceIn(0f, height) / height) * 100f
                                    localPoints = localPoints.toMutableList().also {
                                        it[axisX] = newX
                                        it[axisY] = newY
                                    }
                                    onPointChange(axisX, newX)
                                    onPointChange(axisY, newY)
                                },
                                onDragEnd = { activeHandle = -1 },
                                onDragCancel = { activeHandle = -1 }
                            )
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val offsets = (0 until 4).map { index ->
                        Offset(
                            safe(index * 2) / 100f * width,
                            safe(index * 2 + 1) / 100f * height
                        )
                    }
                    val path = Path().apply {
                        moveTo(offsets[0].x, offsets[0].y)
                        lineTo(offsets[1].x, offsets[1].y)
                        lineTo(offsets[2].x, offsets[2].y)
                        lineTo(offsets[3].x, offsets[3].y)
                        close()
                    }
                    drawPath(path, HariMuColors.TealEmerald, style = Stroke(width = 1.5.dp.toPx()))
                    val haloRadius = 14.dp.toPx()
                    val outerRadius = 7.dp.toPx()
                    val innerRadius = 4.dp.toPx()
                    offsets.forEachIndexed { index, offset ->
                        drawCircle(
                            HariMuColors.TealEmerald.copy(alpha = if (index == activeHandle) 0.35f else 0.18f),
                            radius = haloRadius,
                            center = offset
                        )
                        drawCircle(HariMuColors.TealEmerald, radius = outerRadius, center = offset)
                        drawCircle(
                            if (index == activeHandle) HariMuColors.CrispWhite else HariMuColors.CanvasHandleDark,
                            radius = innerRadius,
                            center = offset
                        )
                    }
                }
            }
            Text(
                text = stringResource(
                    R.string.perspective_coords_summary,
                    safe(0).roundToInt(), safe(1).roundToInt(),
                    safe(4).roundToInt(), safe(5).roundToInt()
                ),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.perspective_preview_hint),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun nearestHandle(
    offset: Offset,
    points: List<Float>,
    width: Float,
    height: Float,
    maxHitRadiusPx: Float
): Int {
    var best = -1
    var bestDistance = maxHitRadiusPx
    for (index in 0 until 4) {
        val x = (points.getOrNull(index * 2) ?: return -1) / 100f * width
        val y = (points.getOrNull(index * 2 + 1) ?: return -1) / 100f * height
        val dx = offset.x - x
        val dy = offset.y - y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance <= bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}
