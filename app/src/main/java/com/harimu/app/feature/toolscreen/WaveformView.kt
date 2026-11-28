package com.harimu.app.feature.toolscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.audio.WaveformOverlaySpec
import kotlin.math.pow

@Composable
fun WaveformView(
    peaks: List<Float>,
    playheadFraction: Float = 0f,
    modifier: Modifier = Modifier,
    playedColor: Color = HariMuColors.GroupAudio,
    activeColor: Color = MaterialTheme.colorScheme.outlineVariant,
    overlaySpec: WaveformOverlaySpec = WaveformOverlaySpec.None,
    onSeekFraction: ((Float) -> Unit)? = null
) {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerLow
    val markerColor = HariMuColors.ElectricOrange
    val thresholdColor = HariMuColors.TealEmerald

    val gestureModifier = if (onSeekFraction != null) {
        modifier.pointerInput(onSeekFraction) {
            detectTapGestures { offset ->
                if (size.width > 0) {
                    onSeekFraction((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
        }
    } else {
        modifier
    }

    Canvas(
        modifier = gestureModifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
    ) {
        val barList = if (peaks.isEmpty()) List(48) { 0.18f } else peaks
        val count = barList.size.coerceAtLeast(1)
        val slotW = size.width / count.toFloat()
        val barW = (slotW * 0.62f).coerceAtLeast(2.dp.toPx())
        val midY = size.height / 2f
        val clampedPlayhead = playheadFraction.coerceIn(0f, 1f)

        barList.forEachIndexed { idx, rawAmp ->
            val frac = idx.toFloat() / count.toFloat()
            val amp = rawAmp.coerceIn(0.06f, 1f)
            val barH = (size.height * 0.84f * amp).coerceAtLeast(4.dp.toPx())
            val color = if (frac <= clampedPlayhead) playedColor else activeColor
            drawRoundRect(
                color = color,
                topLeft = Offset(idx * slotW + (slotW - barW) / 2f, midY - barH / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }

        when (overlaySpec) {
            is WaveformOverlaySpec.None -> Unit
            is WaveformOverlaySpec.FadeEnvelope -> {
                val fadeInFrac = (overlaySpec.fadeInSec / 30f).coerceIn(0.04f, 0.42f)
                val fadeOutFrac = (overlaySpec.fadeOutSec / 30f).coerceIn(0.04f, 0.42f)
                val path = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width * fadeInFrac, size.height * 0.12f)
                    lineTo(size.width * (1f - fadeOutFrac), size.height * 0.12f)
                    lineTo(size.width, size.height)
                }
                drawPath(
                    path = path,
                    color = markerColor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            is WaveformOverlaySpec.SilenceThreshold -> {
                val linear = 10.0.pow((overlaySpec.thresholdDb.coerceIn(-70f, -5f) / 20.0)).toFloat()
                val offsetY = (size.height * 0.42f * linear).coerceIn(4.dp.toPx(), size.height * 0.4f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                drawLine(
                    color = thresholdColor,
                    start = Offset(0f, midY - offsetY),
                    end = Offset(size.width, midY - offsetY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dash
                )
                drawLine(
                    color = thresholdColor,
                    start = Offset(0f, midY + offsetY),
                    end = Offset(size.width, midY + offsetY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dash
                )
            }
            is WaveformOverlaySpec.SplitMarkers -> {
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                overlaySpec.splitFractions.forEach { frac ->
                    val x = size.width * frac.coerceIn(0.02f, 0.98f)
                    drawLine(
                        color = markerColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = dash
                    )
                }
            }
            is WaveformOverlaySpec.NoiseFloor -> {
                val band = (size.height * 0.18f * (overlaySpec.strengthPct / 100f)).coerceIn(4.dp.toPx(), size.height * 0.3f)
                drawRect(
                    color = thresholdColor.copy(alpha = 0.22f),
                    topLeft = Offset(0f, midY - band),
                    size = Size(size.width, band * 2f)
                )
            }
        }

        if (clampedPlayhead > 0f) {
            val px = size.width * clampedPlayhead
            drawLine(
                color = markerColor,
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
