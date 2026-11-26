package com.harimu.app.core.design.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.data.AudioClipSpec
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import java.util.Locale
import kotlin.math.abs

val AUDIO_TRACK_HEADER_WIDTH: Dp = 108.dp

/**
 * Interactive timeline ruler showing seconds aligned to [AudioTimelineMath.dpPerSecond].
 * Tapping or dragging anywhere on the ruler scrubs [onSeekMs].
 */
@Composable
fun AudioTimeRuler(
    totalDurationMs: Long,
    zoomLevel: Float,
    playheadPositionMs: Long,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val timelineWidthDp = AudioTimelineMath.timelineWidthDp(totalDurationMs, zoomLevel)
    val tickStepSec = AudioTimelineMath.rulerTickIntervalSec(zoomLevel)
    val totalSeconds = ((totalDurationMs / 1000L) + 2L).coerceAtLeast(5L)
    val density = LocalDensity.current
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    val majorTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val playheadColor = HariMuColors.ElectricOrange
    val rulerCd = stringResource(R.string.cd_audio_timeline_ruler)

    Row(
        modifier = modifier
            .height(30.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics { contentDescription = rulerCd }
            .testTag("audio_studio_time_ruler"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(AUDIO_TRACK_HEADER_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = AudioTimelineMath.formatDurationMs(playheadPositionMs),
                style = MonoTextStyle,
                color = HariMuColors.ElectricOrange,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .width(timelineWidthDp.dp)
                .fillMaxHeight()
                .pointerInput(zoomLevel, totalDurationMs) {
                    detectTapGestures { offset ->
                        val dpX = with(density) { offset.x.toDp().value }
                        onSeekMs(AudioTimelineMath.offsetDpToMs(dpX, zoomLevel, totalDurationMs))
                    }
                }
                .pointerInput(zoomLevel, totalDurationMs) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val dpX = with(density) { change.position.x.toDp().value }
                        onSeekMs(AudioTimelineMath.offsetDpToMs(dpX, zoomLevel, totalDurationMs))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pxPerSec = with(density) { AudioTimelineMath.dpPerSecond(zoomLevel).dp.toPx() }
                var sec = 0L
                while (sec <= totalSeconds) {
                    val x = sec * pxPerSec
                    if (x <= size.width) {
                        val isMajor = sec % tickStepSec == 0L
                        drawLine(
                            color = if (isMajor) majorTickColor else tickColor,
                            start = Offset(x, if (isMajor) size.height * 0.45f else size.height * 0.7f),
                            end = Offset(x, size.height),
                            strokeWidth = if (isMajor) 2f else 1f
                        )
                    }
                    sec++
                }
                val headX = with(density) { AudioTimelineMath.msToOffsetDp(playheadPositionMs, zoomLevel).dp.toPx() }
                drawCircle(
                    color = playheadColor,
                    radius = 5.dp.toPx(),
                    center = Offset(headX.coerceIn(0f, size.width), size.height * 0.3f)
                )
            }

            var labelSec = 0L
            while (labelSec <= totalSeconds) {
                val xDp = AudioTimelineMath.msToOffsetDp(labelSec * 1000L, zoomLevel)
                Text(
                    text = AudioTimelineMath.formatDurationMs(labelSec * 1000L, includeTenths = false),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .offset(x = (xDp + 3f).dp, y = 2.dp)
                )
                labelSec += tickStepSec
            }
        }
    }
}

/**
 * Multi-track timeline lane displaying track header (color strip, name, M/S status)
 * and horizontally positioned audio clips with real extracted waveform peaks.
 */
@Composable
fun AudioTimelineTrackRow(
    track: AudioTrackSpec,
    trackColor: Color,
    isSelected: Boolean,
    hasAnySolo: Boolean,
    selectedClipId: String?,
    zoomLevel: Float,
    totalDurationMs: Long,
    clipWaveforms: Map<String, List<Float>>,
    onSelectTrack: () -> Unit,
    onSelectClip: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val laneWidthDp = AudioTimelineMath.timelineWidthDp(totalDurationMs, zoomLevel)
    val isMutedOut = track.mute || (hasAnySolo && !track.solo)

    Row(
        modifier = modifier
            .height(78.dp)
            .clickable(onClick = onSelectTrack)
            .background(
                if (isSelected) trackColor.copy(alpha = 0.09f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected) 1.dp else 0.5.dp,
                color = if (isSelected) trackColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
            )
            .testTag("audio_track_row_${track.trackId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Header
        Row(
            modifier = Modifier
                .width(AUDIO_TRACK_HEADER_WIDTH)
                .fillMaxHeight()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(trackColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isMutedOut) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.audio_studio_clip_count, track.clips.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (track.mute) {
                        TrackStatusPill("M", MaterialTheme.colorScheme.error)
                    }
                    if (track.solo) {
                        TrackStatusPill("S", HariMuColors.TealEmerald)
                    }
                }
            }
        }

        // Clip Lane
        Box(
            modifier = Modifier
                .width(laneWidthDp.dp)
                .fillMaxHeight()
                .alpha(if (isMutedOut) 0.45f else 1f)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (track.clips.isEmpty()) {
                Text(
                    text = stringResource(R.string.audio_studio_track_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            } else {
                track.clips.forEach { clip ->
                    val offsetDp = AudioTimelineMath.msToOffsetDp(clip.offsetMs, zoomLevel)
                    val durMs = AudioTimelineMath.effectiveClipDurationMs(clip)
                    val clipWidthDp = AudioTimelineMath.msToOffsetDp(durMs, zoomLevel).coerceAtLeast(64f)
                    val clipSelected = clip.id == selectedClipId
                    val rawPeaks = clipWaveforms[clip.id].orEmpty()
                    val slicedPeaks = remember(rawPeaks, clip.trimStartMs, clip.trimEndMs, clip.gainDb, clipWidthDp) {
                        val barTarget = (clipWidthDp / 4f).toInt().coerceIn(18, 96)
                        if (rawPeaks.isEmpty()) {
                            List(barTarget) { 0.28f }
                        } else {
                            AudioTimelineMath.slicePeaksForClip(rawPeaks, clip, barTarget)
                        }
                    }

                    AudioTimelineClipCard(
                        clip = clip,
                        peaks = slicedPeaks,
                        trackColor = trackColor,
                        isSelected = clipSelected,
                        modifier = Modifier
                            .offset(x = offsetDp.dp)
                            .width(clipWidthDp.dp)
                            .fillMaxHeight(),
                        onClick = {
                            onSelectTrack()
                            onSelectClip(clip.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackStatusPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.6f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun AudioTimelineClipCard(
    clip: AudioClipSpec,
    peaks: List<Float>,
    trackColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        trackColor.copy(alpha = 0.24f)
    } else {
        trackColor.copy(alpha = 0.13f)
    }
    val borderColor = if (isSelected) trackColor else trackColor.copy(alpha = 0.45f)
    val waveColor = if (isSelected) trackColor else trackColor.copy(alpha = 0.72f)
    val cdClip = stringResource(R.string.cd_audio_clip_card, clip.displayName)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = cdClip }
            .testTag("audio_clip_card_${clip.id}"),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Canvas(modifier = Modifier.fillMaxSize().padding(top = 14.dp, bottom = 2.dp)) {
                if (peaks.isNotEmpty()) {
                    val slotW = size.width / peaks.size.toFloat()
                    val barW = (slotW * 0.58f).coerceAtLeast(1.8f)
                    val centerY = size.height / 2f
                    val maxHalfH = (size.height * 0.46f).coerceAtLeast(3f)
                    peaks.forEachIndexed { idx, amp ->
                        val h = (amp.coerceIn(0.08f, 1f) * maxHalfH).coerceAtLeast(2f)
                        val x = idx * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            color = waveColor,
                            topLeft = Offset(x, centerY - h),
                            size = Size(barW, h * 2f),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clip.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                val durText = AudioTimelineMath.formatDurationMs(AudioTimelineMath.effectiveClipDurationMs(clip))
                val gainText = if (abs(clip.gainDb) >= 0.1f) {
                    String.format(Locale.US, " %+.0fdB", clip.gainDb)
                } else ""
                Text(
                    text = "$durText$gainText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Density-accurate vertical playhead drawn over the scrollable timeline lane.
 */
@Composable
fun AudioPlayheadOverlay(
    playheadPositionMs: Long,
    zoomLevel: Float,
    horizontalScrollPx: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scrollDp = with(density) { horizontalScrollPx.toDp() }
    val playheadOffsetDp = AudioTimelineMath.msToOffsetDp(playheadPositionMs, zoomLevel).dp
    val finalOffsetDp = AUDIO_TRACK_HEADER_WIDTH + playheadOffsetDp - scrollDp

    if (finalOffsetDp >= AUDIO_TRACK_HEADER_WIDTH) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .offset(x = finalOffsetDp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(HariMuColors.ElectricOrange)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(HariMuColors.ElectricOrange)
            )
        }
    }
}
