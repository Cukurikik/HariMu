package com.harimu.app.feature.toolscreen

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import com.harimu.app.R
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.media.FfmpegProgressParser
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.media.MediaProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoTrimPreviewCard(
    videoUri: Uri,
    showTrimStrip: Boolean,
    startSec: Float,
    endSec: Float,
    onRangeChange: (newStartSec: Float, newEndSec: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var probe by remember(videoUri) { mutableStateOf<MediaProbeResult?>(null) }
    var thumbnails by remember(videoUri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loadingThumbs by remember(videoUri) { mutableStateOf(showTrimStrip) }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }
    var playerError by remember(exoPlayer) { mutableStateOf<String?>(null) }
    var currentPosMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var playerDurationMs by remember(exoPlayer) { mutableLongStateOf(0L) }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exoPlayer.duration
                if (dur > 0L) playerDurationMs = dur
                if (playbackState == Player.STATE_READY) {
                    playerError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isPlaying = false
                playerError = error.localizedMessage ?: "Playback error"
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(videoUri, showTrimStrip) {
        val probed = withContext(Dispatchers.IO) { MediaProbe.probe(context, videoUri) }
        probe = probed
        if (showTrimStrip) {
            loadingThumbs = true
            val thumbs = withContext(Dispatchers.IO) {
                extractFilmstripThumbnails(context, videoUri, probed.durationMs, count = 8)
            }
            thumbnails = thumbs
            loadingThumbs = false
        }
    }

    DisposableEffect(thumbnails) {
        val currentList = thumbnails
        onDispose {
            currentList.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
    }

    val totalDurationMs = (probe?.durationMs?.takeIf { it > 0L } ?: playerDurationMs.takeIf { it > 0L } ?: 10_000L)
    val totalSec = (totalDurationMs / 1000f).coerceAtLeast(1f)
    val clampedStart = startSec.coerceIn(0f, (totalSec - 0.5f).coerceAtLeast(0f))
    val effectiveEnd = if (endSec <= 0f || endSec > totalSec) totalSec else endSec.coerceIn(clampedStart + 0.5f, totalSec)

    LaunchedEffect(exoPlayer, isPlaying, showTrimStrip, clampedStart, effectiveEnd) {
        while (isPlaying) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (showTrimStrip) {
                val endMs = (effectiveEnd * 1000f).toLong()
                val startMs = (clampedStart * 1000f).toLong()
                if (currentPosMs >= endMs && endMs > startMs) {
                    exoPlayer.seekTo(startMs)
                    currentPosMs = startMs
                }
            }
            delay(150L)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.video_preview_title), style = MaterialTheme.typography.titleSmall)
                probe?.let { p ->
                    EngineBadge(
                        engine = if (p.hasAudio) {
                            stringResource(R.string.video_audio_present)
                        } else {
                            stringResource(R.string.video_audio_missing)
                        }
                    )
                }
            }

            probe?.let { p ->
                val res = if (p.orientedWidth > 0 && p.orientedHeight > 0) "${p.orientedWidth}×${p.orientedHeight}" else "-"
                val fpsTxt = if (p.fps > 0f) String.format(Locale.US, "%.1f fps", p.fps) else ""
                val durTxt = FfmpegProgressParser.formatDurationMs(p.durationMs)
                Text(
                    text = listOf(durTxt, res, fpsTxt).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                ContentFrame(
                    player = exoPlayer,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (playerError != null) {
                    Text(
                        text = stringResource(R.string.video_preview_error),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                FilledIconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (showTrimStrip) {
                                val pos = exoPlayer.currentPosition
                                val sMs = (clampedStart * 1000f).toLong()
                                val eMs = (effectiveEnd * 1000f).toLong()
                                if (pos < sMs || pos >= eMs) {
                                    exoPlayer.seekTo(sMs)
                                }
                            }
                            exoPlayer.play()
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .size(48.dp)
                        .testTag("video_preview_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.video_cd_play_pause)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${FfmpegProgressParser.formatDurationMs(currentPosMs)} / ${FfmpegProgressParser.formatDurationMs(totalDurationMs)}",
                        style = MonoTextStyle,
                        color = Color.White
                    )
                }
            }

            if (showTrimStrip) {
                val startFraction = (clampedStart / totalSec).coerceIn(0f, 1f)
                val endFraction = (effectiveEnd / totalSec).coerceIn(startFraction, 1f)
                val playheadFraction = ((currentPosMs / 1000f) / totalSec).coerceIn(0f, 1f)

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val stripWidth = maxWidth
                    if (thumbnails.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            thumbnails.forEach { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.cd_video_trim_thumbnail),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    } else if (loadingThumbs) {
                        Text(
                            text = stringResource(R.string.video_loading_thumbnails),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Dim excluded left/right regions
                    if (startFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(stripWidth * startFraction)
                                .background(Color.Black.copy(alpha = 0.55f))
                        )
                    }
                    if (endFraction < 1f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(stripWidth * (1f - endFraction))
                                .background(Color.Black.copy(alpha = 0.55f))
                        )
                    }

                    // Selected range border highlight
                    Box(
                        modifier = Modifier
                            .offset(x = stripWidth * startFraction)
                            .width((stripWidth * (endFraction - startFraction)).coerceAtLeast(4.dp))
                            .fillMaxHeight()
                            .border(2.dp, HariMuColors.ElectricOrange, RoundedCornerShape(6.dp))
                    )

                    // Playhead line
                    Box(
                        modifier = Modifier
                            .offset(x = (stripWidth * playheadFraction).coerceAtMost(stripWidth - 2.dp))
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }

                MediaTrimRangeControls(
                    mediaKey = videoUri,
                    title = stringResource(R.string.video_trim_range_title),
                    totalSec = totalSec,
                    startSec = startSec,
                    endSec = endSec,
                    currentPlayheadMs = { exoPlayer.currentPosition },
                    onSeekToMs = { exoPlayer.seekTo(it) },
                    onRangeChange = onRangeChange,
                    sliderTestTag = "video_trim_range_slider",
                    setStartTestTag = "video_set_start_btn",
                    setEndTestTag = "video_set_end_btn"
                )
            }
        }
    }
}

@Composable
fun VideoSafetyWarningCard(
    isReverse: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_safety_warning_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        if (isReverse) R.string.video_safety_reverse_title
                        else R.string.video_safety_loop_title
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(
                        if (isReverse) R.string.video_safety_reverse_body
                        else R.string.video_safety_loop_body
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

private fun extractFilmstripThumbnails(
    context: android.content.Context,
    uri: Uri,
    knownDurationMs: Long,
    count: Int
): List<Bitmap> {
    val mmr = MediaMetadataRetriever()
    val results = mutableListOf<Bitmap>()
    try {
        mmr.setDataSource(context, uri)
        val dur = knownDurationMs.takeIf { it > 0L }
            ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?: 5000L
        val safeCount = count.coerceAtLeast(2)
        for (i in 0 until safeCount) {
            val timeUs = ((dur * i) / safeCount.toLong()) * 1000L
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                mmr.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 144, 96)
            } else {
                mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                    val scaled = Bitmap.createScaledBitmap(raw, 128, 72, true)
                    if (scaled != raw) raw.recycle()
                    scaled
                }
            }
            if (frame != null) {
                results.add(frame)
            }
        }
    } catch (_: Throwable) {
        // Ignore preview thumbnail failure; playback & sliders remain functional.
    } finally {
        runCatching { mmr.release() }
    }
    return results
}
