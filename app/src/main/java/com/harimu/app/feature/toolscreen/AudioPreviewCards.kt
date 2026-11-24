package com.harimu.app.feature.toolscreen

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.harimu.app.R
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.media.AudioWaveformExtractor
import com.harimu.app.core.media.FfmpegProgressParser
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.media.MediaProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun WaveformView(
    peaks: List<Float>,
    playheadFraction: Float,
    modifier: Modifier = Modifier,
    showTrimWindow: Boolean = false,
    trimStartFraction: Float = 0f,
    trimEndFraction: Float = 1f,
    activeColor: Color = HariMuColors.ElectricOrange,
    playedColor: Color = HariMuColors.TealEmerald,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
    onSeekFraction: ((Float) -> Unit)? = null
) {
    val safePeaks = if (peaks.isEmpty()) List(64) { 0.2f } else peaks
    val clampedPlayhead = playheadFraction.coerceIn(0f, 1f)
    val startF = trimStartFraction.coerceIn(0f, 1f)
    val endF = trimEndFraction.coerceIn(startF, 1f)
    val playheadLineColor = MaterialTheme.colorScheme.onSurface
    val trimBorderColor = activeColor.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .let { base ->
                if (onSeekFraction != null) {
                    base
                        .pointerInput(onSeekFraction) {
                            detectTapGestures { offset ->
                                val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                onSeekFraction(frac)
                            }
                        }
                        .pointerInput(onSeekFraction) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val frac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                onSeekFraction(frac)
                            }
                        }
                } else {
                    base
                }
            }
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val count = safePeaks.size
            val slotW = size.width / count.toFloat()
            val barW = (slotW * 0.62f).coerceAtLeast(2.5f)
            val centerY = size.height / 2f
            val maxHalfH = (size.height * 0.44f).coerceAtLeast(6f)

            safePeaks.forEachIndexed { idx, amp ->
                val frac = (idx + 0.5f) / count.toFloat()
                val inTrim = !showTrimWindow || (frac >= startF && frac <= endF)
                val barColor = when {
                    !inTrim -> inactiveColor.copy(alpha = 0.45f)
                    frac <= clampedPlayhead -> playedColor
                    showTrimWindow -> activeColor
                    else -> activeColor.copy(alpha = 0.72f)
                }
                val halfH = (amp.coerceIn(0.06f, 1f) * maxHalfH).coerceAtLeast(3f)
                val x = idx * slotW + (slotW - barW) / 2f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, centerY - halfH),
                    size = Size(barW, halfH * 2f),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
            }

            if (showTrimWindow) {
                val xStart = size.width * startF
                val xEnd = size.width * endF
                drawLine(
                    color = trimBorderColor,
                    start = Offset(xStart, 0f),
                    end = Offset(xStart, size.height),
                    strokeWidth = 3f
                )
                drawLine(
                    color = trimBorderColor,
                    start = Offset(xEnd, 0f),
                    end = Offset(xEnd, size.height),
                    strokeWidth = 3f
                )
            }

            val headX = (size.width * clampedPlayhead).coerceIn(1.5f, (size.width - 1.5f).coerceAtLeast(1.5f))
            drawLine(
                color = playheadLineColor,
                start = Offset(headX, 0f),
                end = Offset(headX, size.height),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
fun AudioBeforeAfterPreviewCard(
    inputUri: Uri?,
    outputFile: File?,
    showTrimControls: Boolean,
    startSec: Float,
    endSec: Float,
    onRangeChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val outputUri = remember(outputFile) { outputFile?.toUri() }
    var selectedAfter by remember(inputUri, outputUri) { mutableStateOf(outputUri != null && inputUri == null) }
    LaunchedEffect(outputUri) {
        if (outputUri != null) {
            selectedAfter = true
        } else if (inputUri != null) {
            selectedAfter = false
        }
    }

    val activeUri = if (selectedAfter && outputUri != null) outputUri else inputUri
    if (activeUri == null) return

    var probe by remember(activeUri) { mutableStateOf<MediaProbeResult?>(null) }
    var peaks by remember(activeUri) { mutableStateOf<List<Float>>(emptyList()) }
    var isExtracting by remember(activeUri) { mutableStateOf(true) }

    val exoPlayer = remember(activeUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(activeUri))
            repeatMode = if (showTrimControls && !selectedAfter) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }
    var currentPosMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var playerDurationMs by remember(exoPlayer) { mutableLongStateOf(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exoPlayer.duration
                if (dur > 0L) playerDurationMs = dur
                if (playbackState == Player.STATE_ENDED && exoPlayer.repeatMode == Player.REPEAT_MODE_OFF) {
                    exoPlayer.seekTo(0L)
                    exoPlayer.pause()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(activeUri) {
        isExtracting = true
        val probed = withContext(Dispatchers.IO) { MediaProbe.probe(context, activeUri) }
        probe = probed
        val wave = AudioWaveformExtractor.extractPeaks(context, activeUri, barCount = 96)
        peaks = wave.peaks
        if (wave.durationMs > 0L && (probe?.durationMs ?: 0L) <= 0L) {
            probe = probed.copy(durationMs = wave.durationMs)
        }
        isExtracting = false
    }

    val totalDurationMs = (probe?.durationMs?.takeIf { it > 0L } ?: playerDurationMs.takeIf { it > 0L } ?: 10_000L)
    val totalSec = (totalDurationMs / 1000f).coerceAtLeast(1f)
    val isTrimActiveOnCurrent = showTrimControls && !selectedAfter
    val clampedStart = startSec.coerceIn(0f, (totalSec - 0.5f).coerceAtLeast(0f))
    val effectiveEnd = if (endSec <= 0f || endSec > totalSec) totalSec else endSec.coerceIn(clampedStart + 0.5f, totalSec)

    LaunchedEffect(exoPlayer, isPlaying, isTrimActiveOnCurrent, clampedStart, effectiveEnd) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (isTrimActiveOnCurrent && isPlaying) {
                val startMs = (clampedStart * 1000f).toLong()
                val endMs = (effectiveEnd * 1000f).toLong()
                if (currentPosMs >= endMs && endMs > startMs) {
                    exoPlayer.seekTo(startMs)
                }
            }
            delay(120L)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_before_after_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = HariMuColors.GroupAudio,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(stringResource(R.string.audio_preview_title), style = MaterialTheme.typography.titleSmall)
                }
                probe?.let { p ->
                    val chLabel = if (p.audioChannels <= 1) {
                        stringResource(R.string.audio_channels_mono)
                    } else {
                        stringResource(R.string.audio_channels_stereo, p.audioChannels)
                    }
                    EngineBadge(engine = chLabel)
                }
            }

            if (inputUri != null && outputUri != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !selectedAfter,
                        onClick = { selectedAfter = false },
                        label = { Text(stringResource(R.string.audio_tab_before)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("audio_tab_before")
                    )
                    FilterChip(
                        selected = selectedAfter,
                        onClick = { selectedAfter = true },
                        label = { Text(stringResource(R.string.audio_tab_after)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("audio_tab_after")
                    )
                }
            }

            probe?.let { p ->
                val durTxt = FfmpegProgressParser.formatDurationMs(p.durationMs)
                val srTxt = if (p.audioSampleRateHz > 0) "${p.audioSampleRateHz} Hz" else ""
                val kbpsTxt = if (p.bitrateBps > 0L) "${p.bitrateBps / 1000L} kbps" else ""
                val codecTxt = p.audioCodec.uppercase()
                Text(
                    text = listOf(durTxt, srTxt, kbpsTxt, codecTxt).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExtracting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.audio_loading_waveform),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val playheadFraction = (currentPosMs.toFloat() / totalDurationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
            WaveformView(
                peaks = peaks,
                playheadFraction = playheadFraction,
                showTrimWindow = isTrimActiveOnCurrent,
                trimStartFraction = (clampedStart / totalSec).coerceIn(0f, 1f),
                trimEndFraction = (effectiveEnd / totalSec).coerceIn(0f, 1f),
                onSeekFraction = { frac ->
                    val targetMs = (frac * totalDurationMs).toLong()
                    exoPlayer.seekTo(targetMs)
                    currentPosMs = targetMs
                },
                modifier = Modifier.testTag("audio_waveform_view")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (isTrimActiveOnCurrent) {
                                val sMs = (clampedStart * 1000f).toLong()
                                val eMs = (effectiveEnd * 1000f).toLong()
                                if (exoPlayer.currentPosition < sMs || exoPlayer.currentPosition >= eMs) {
                                    exoPlayer.seekTo(sMs)
                                }
                            }
                            exoPlayer.play()
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = HariMuColors.GroupAudio,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("audio_preview_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.audio_cd_play_pause)
                    )
                }

                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${FfmpegProgressParser.formatDurationMs(currentPosMs)} / ${FfmpegProgressParser.formatDurationMs(totalDurationMs)}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isTrimActiveOnCurrent) {
                Text(
                    text = stringResource(R.string.audio_trim_range_title),
                    style = MaterialTheme.typography.labelLarge
                )
                var sliderStart by remember(activeUri, startSec) { mutableFloatStateOf(clampedStart) }
                var sliderEnd by remember(activeUri, endSec, totalSec) { mutableFloatStateOf(effectiveEnd) }

                RangeSlider(
                    value = sliderStart..sliderEnd,
                    onValueChange = { range ->
                        val minGap = minOf(0.5f, totalSec * 0.1f)
                        val s = range.start.coerceIn(0f, (totalSec - minGap).coerceAtLeast(0f))
                        val e = range.endInclusive.coerceIn(s + minGap, totalSec)
                        sliderStart = s
                        sliderEnd = e
                        onRangeChange(s, e)
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo((sliderStart * 1000f).toLong())
                    },
                    valueRange = 0f..totalSec,
                    colors = SliderDefaults.colors(
                        thumbColor = HariMuColors.ElectricOrange,
                        activeTrackColor = HariMuColors.ElectricOrange
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("audio_trim_range_slider")
                )

                Text(
                    text = stringResource(
                        R.string.video_trim_range_labels,
                        FfmpegProgressParser.formatDurationMs((sliderStart * 1000f).toLong()),
                        FfmpegProgressParser.formatDurationMs((sliderEnd * 1000f).toLong())
                    ),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val headSec = (exoPlayer.currentPosition / 1000f).coerceIn(0f, (sliderEnd - 0.5f).coerceAtLeast(0f))
                            sliderStart = headSec
                            onRangeChange(headSec, sliderEnd)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("audio_set_start_btn")
                    ) {
                        Text(stringResource(R.string.video_set_start_playhead))
                    }
                    OutlinedButton(
                        onClick = {
                            val headSec = (exoPlayer.currentPosition / 1000f).coerceIn(sliderStart + 0.5f, totalSec)
                            sliderEnd = headSec
                            onRangeChange(sliderStart, headSec)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("audio_set_end_btn")
                    ) {
                        Text(stringResource(R.string.video_set_end_playhead))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "WaveformView Light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "WaveformView Dark")
@Composable
private fun PreviewWaveformView() {
    val samplePeaks = remember {
        List(72) { idx ->
            (0.22f + 0.72f * abs(sin(idx * 0.27f))).coerceIn(0.1f, 1f)
        }
    }
    HariMuTheme(themeMode = ThemeMode.SYSTEM, isSystemDark = false) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WaveformView(
                peaks = samplePeaks,
                playheadFraction = 0.42f,
                showTrimWindow = true,
                trimStartFraction = 0.18f,
                trimEndFraction = 0.78f
            )
        }
    }
}
