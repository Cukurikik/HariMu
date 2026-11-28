package com.harimu.app.core.design.video

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreview(
    modifier: Modifier = Modifier,
    clipUriString: String,
    trimStartMs: Long = 0L,
    trimEndMs: Long = Long.MAX_VALUE,
    speed: Float = 1.0f,
    rotationDeg: Int = 0,
    durationMs: Long,
    onPositionChanged: ((Long) -> Unit)? = null,
    onPlaybackStateChanged: ((Boolean) -> Unit)? = null,
    showTrimHandles: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipUri = remember(clipUriString) { clipUriString.toUri() }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }
    var isBuffering by remember(exoPlayer) { mutableStateOf(true) }
    var playerError by remember(exoPlayer) { mutableStateOf<String?>(null) }
    var currentPosMs by remember(exoPlayer) { mutableLongStateOf(trimStartMs.coerceAtLeast(0L)) }
    var bufferedPercentage by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }

    val effectiveStartMs = trimStartMs.coerceAtLeast(0L)
    val effectiveEndMs = trimEndMs.coerceAtLeast(effectiveStartMs + 200L).coerceAtMost(durationMs.coerceAtLeast(200L))
    val effectiveDurationMs = (effectiveEndMs - effectiveStartMs).coerceAtLeast(200L)

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlaybackStateChanged?.invoke(playing)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
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

    LaunchedEffect(exoPlayer, clipUriString) {
        playerError = null
        isBuffering = true
        exoPlayer.pause()
        exoPlayer.setMediaItem(MediaItem.fromUri(clipUri))
        exoPlayer.prepare()
        exoPlayer.seekTo(effectiveStartMs)
        currentPosMs = effectiveStartMs
        onPositionChanged?.invoke(effectiveStartMs)
    }

    LaunchedEffect(exoPlayer, speed) {
        exoPlayer.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4.0f))
    }

    LaunchedEffect(exoPlayer, effectiveStartMs, effectiveEndMs) {
        val clamped = exoPlayer.currentPosition.coerceIn(effectiveStartMs, effectiveEndMs)
        currentPosMs = clamped
        onPositionChanged?.invoke(clamped)
    }

    LaunchedEffect(exoPlayer, isPlaying, effectiveStartMs, effectiveEndMs) {
        while (isPlaying) {
            val rawPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (durationMs > 0) {
                bufferedPercentage = (exoPlayer.bufferedPosition.toFloat() / durationMs).coerceIn(0f, 1f)
            }
            if (rawPos >= effectiveEndMs) {
                exoPlayer.seekTo(effectiveStartMs)
                currentPosMs = effectiveStartMs
            } else {
                currentPosMs = rawPos.coerceIn(effectiveStartMs, effectiveEndMs)
            }
            onPositionChanged?.invoke(currentPosMs)
            delay(100L)
        }
    }

    // Auto-hide controls while playing
    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            delay(3000L)
            showControls = false
        }
    }

    val maxSec = (durationMs / 1000f).coerceAtLeast(1f)
    val startSec = (effectiveStartMs / 1000f).coerceIn(0f, (maxSec - 0.2f).coerceAtLeast(0f))
    val endSec = (effectiveEndMs / 1000f).coerceIn(startSec + 0.2f, maxSec)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        val presentationState = rememberPresentationState(exoPlayer)
        PlayerSurface(
            player = exoPlayer,
            modifier = Modifier
                .fillMaxSize()
                .resizeWithContentScale(contentScale, presentationState.videoSizeDp)
        )

        if (playerError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = stringResource(R.string.video_preview_error),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        } else if (isBuffering) {
            CircularProgressIndicator(
                color = HariMuColors.ElectricOrange,
                modifier = Modifier.size(36.dp)
            )
        }

        // Buffered progress indicator
        if (bufferedPercentage > 0f && bufferedPercentage < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bufferedPercentage)
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }
        }

        // Top-right rotation indicator (always visible when clip is rotated)
        if (rotationDeg != 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.72f)
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.editor_export_badge, rotationDeg),
                    style = MonoTextStyle.copy(fontSize = 11.sp, color = Color.White),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Controls overlay
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Seek bar aligned with 0..maxSec trim handles
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = (currentPosMs / 1000f).coerceIn(0f, maxSec),
                            onValueChange = { newSec ->
                                val newMs = (newSec * 1000f).roundToLong().coerceIn(effectiveStartMs, effectiveEndMs)
                                exoPlayer.seekTo(newMs)
                                currentPosMs = newMs
                                onPositionChanged?.invoke(newMs)
                            },
                            valueRange = 0f..maxSec,
                            colors = SliderDefaults.colors(
                                thumbColor = HariMuColors.ElectricOrange,
                                activeTrackColor = HariMuColors.ElectricOrange,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = effectiveDurationMs > 0
                        )

                        // Trim handle markers aligned with full 0..maxSec slider track
                        if (showTrimHandles && effectiveDurationMs > 0) {
                            val startX = (startSec / maxSec).coerceIn(0f, 1f)
                            val endX = (endSec / maxSec).coerceIn(0f, 1f)
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .align(Alignment.TopCenter)
                            ) {
                                val trackWidth = (maxWidth - 2.dp).coerceAtLeast(0.dp)
                                Box(
                                    modifier = Modifier
                                        .offset(x = trackWidth * startX)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(HariMuColors.ElectricOrange)
                                )
                                Box(
                                    modifier = Modifier
                                        .offset(x = trackWidth * endX)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(HariMuColors.ElectricOrange)
                                )
                            }
                        }
                    }

                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTimeMs(currentPosMs),
                            style = MonoTextStyle.copy(fontSize = 12.sp, color = Color.White)
                        )
                        Text(
                            text = formatTimeMs(durationMs),
                            style = MonoTextStyle.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        )
                    }

                    // Playback controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10_000).coerceAtLeast(effectiveStartMs)
                                exoPlayer.seekTo(target)
                                currentPosMs = target
                                onPositionChanged?.invoke(target)
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.Replay10, contentDescription = stringResource(R.string.video_cd_rewind_10), tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 5_000).coerceAtLeast(effectiveStartMs)
                                exoPlayer.seekTo(target)
                                currentPosMs = target
                                onPositionChanged?.invoke(target)
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.video_cd_rewind_5), tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.currentPosition < effectiveStartMs || exoPlayer.currentPosition >= effectiveEndMs) {
                                        exoPlayer.seekTo(effectiveStartMs)
                                        currentPosMs = effectiveStartMs
                                    }
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = HariMuColors.ElectricOrange.copy(alpha = 0.9f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_resume),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition + 5_000).coerceAtMost(effectiveEndMs)
                                exoPlayer.seekTo(target)
                                currentPosMs = target
                                onPositionChanged?.invoke(target)
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.video_cd_forward_5), tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition + 10_000).coerceAtMost(effectiveEndMs)
                                exoPlayer.seekTo(target)
                                currentPosMs = target
                                onPositionChanged?.invoke(target)
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.Forward10, contentDescription = stringResource(R.string.video_cd_forward_10), tint = Color.White)
                        }
                    }

                    // Speed indicator
                    if (abs(speed - 1.0f) > 0.01f) {
                        Text(
                            text = stringResource(R.string.editor_clip_speed_label, String.format(Locale.US, "%.2f", speed)),
                            style = MonoTextStyle.copy(fontSize = 11.sp, color = HariMuColors.ElectricOrange)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreviewPlaceholder(
    modifier: Modifier = Modifier,
    title: String = "No clip selected",
    subtitle: String = "Select a clip from the timeline to preview"
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Black),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MovieCreation,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White.copy(alpha = 0.7f))
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}