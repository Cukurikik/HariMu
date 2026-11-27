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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.harimu.app.R
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.design.audio.WaveformOverlaySpec
import com.harimu.app.core.media.AudioWaveformExtractor
import com.harimu.app.core.media.FfmpegProgressParser
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.media.MediaProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.pow
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
    overlaySpec: WaveformOverlaySpec = WaveformOverlaySpec.None,
    overlayDurationSec: Float = 30f,
    onSeekFraction: ((Float) -> Unit)? = null
) {
    val safePeaks = if (peaks.isEmpty()) List(64) { 0.2f } else peaks
    val clampedPlayhead = playheadFraction.coerceIn(0f, 1f)
    val startF = trimStartFraction.coerceIn(0f, 1f)
    val endF = trimEndFraction.coerceIn(startF, 1f)
    val playheadLineColor = MaterialTheme.colorScheme.onSurface
    val trimBorderColor = activeColor.copy(alpha = 0.85f)
    val scrubCd = stringResource(R.string.cd_audio_waveform_scrub)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .let { base ->
                if (onSeekFraction != null) {
                    base
                        .semantics { contentDescription = scrubCd }
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

            when (overlaySpec) {
                is WaveformOverlaySpec.FadeEnvelope -> {
                    val safeDur = overlayDurationSec.coerceAtLeast(1f)
                    val inFrac = (overlaySpec.fadeInSec / safeDur).coerceIn(0f, 0.45f)
                    val outFrac = (overlaySpec.fadeOutSec / safeDur).coerceIn(0f, 0.45f)
                    val inX = size.width * inFrac
                    val outX = size.width * (1f - outFrac)
                    val envPath = Path().apply {
                        moveTo(0f, centerY)
                        lineTo(inX, 4f)
                        lineTo(outX, 4f)
                        lineTo(size.width, centerY)
                    }
                    drawPath(
                        path = envPath,
                        color = HariMuColors.TealEmerald,
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }

                is WaveformOverlaySpec.SilenceThreshold -> {
                    val norm = 10.0.pow((overlaySpec.thresholdDb.coerceIn(-65f, -5f) / 20.0)).toFloat()
                    val threshHalfH = (norm * maxHalfH * 2.2f).coerceIn(4f, maxHalfH)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    drawLine(
                        color = HariMuColors.ElectricOrange,
                        start = Offset(0f, centerY - threshHalfH),
                        end = Offset(size.width, centerY - threshHalfH),
                        strokeWidth = 2f,
                        pathEffect = dash
                    )
                    drawLine(
                        color = HariMuColors.ElectricOrange,
                        start = Offset(0f, centerY + threshHalfH),
                        end = Offset(size.width, centerY + threshHalfH),
                        strokeWidth = 2f,
                        pathEffect = dash
                    )
                }

                is WaveformOverlaySpec.SplitMarkers -> {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    overlaySpec.splitFractions.forEach { frac ->
                        val mx = (size.width * frac.coerceIn(0.02f, 0.98f))
                        drawLine(
                            color = HariMuColors.ElectricOrange,
                            start = Offset(mx, 0f),
                            end = Offset(mx, size.height),
                            strokeWidth = 2.5f,
                            pathEffect = dash
                        )
                    }
                }

                is WaveformOverlaySpec.NoiseFloor -> {
                    val floorNorm = 10.0.pow((overlaySpec.noiseFloorDb.coerceIn(-75f, -15f) / 20.0)).toFloat()
                    val floorHalfH = (floorNorm * maxHalfH * 2.0f).coerceIn(4f, maxHalfH * 0.7f)
                    val alpha = (0.14f + (overlaySpec.strengthPct.coerceIn(0f, 100f) / 100f) * 0.18f)
                    drawRect(
                        color = HariMuColors.TealEmerald.copy(alpha = alpha),
                        topLeft = Offset(0f, centerY - floorHalfH),
                        size = Size(size.width, floorHalfH * 2f)
                    )
                }

                WaveformOverlaySpec.None -> Unit
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
    modifier: Modifier = Modifier,
    overlaySpec: WaveformOverlaySpec = WaveformOverlaySpec.None
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
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
                    currentPosMs = 0L
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                overlaySpec = if (selectedAfter) WaveformOverlaySpec.None else overlaySpec,
                overlayDurationSec = totalSec,
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
                MediaTrimRangeControls(
                    mediaKey = activeUri,
                    title = stringResource(R.string.audio_trim_range_title),
                    totalSec = totalSec,
                    startSec = startSec,
                    endSec = endSec,
                    currentPlayheadMs = { exoPlayer.currentPosition },
                    onSeekToMs = { exoPlayer.seekTo(it) },
                    onRangeChange = onRangeChange,
                    sliderTestTag = "audio_trim_range_slider",
                    setStartTestTag = "audio_set_start_btn",
                    setEndTestTag = "audio_set_end_btn"
                )
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
