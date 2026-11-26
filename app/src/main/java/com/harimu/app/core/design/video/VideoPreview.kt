package com.harimu.app.core.design.video

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.getCustomActions
import androidx.compose.ui.semantics.onClickLabel
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toDp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
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
    val clipUri = remember(clipUriString) { clipUriString.toUri() }

    val exoPlayer = remember(clipUriString, clipUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(clipUri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    var isPlaying by remember(exoPlayer) { mutableStateOf(false) }
    var currentPosMs by remember(exoPlayer) { mutableLongStateOf(trimStartMs) }
    var bufferedPercentage by remember { mutableStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var controlsHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val effectiveStartMs = trimStartMs.coerceAtLeast(0L)
    val effectiveEndMs = trimEndMs.coerceAtLeast(effectiveStartMs + 250L).coerceAtMost(durationMs)
    val effectiveDurationMs = (effectiveEndMs - effectiveStartMs).coerceAtLeast(250L)

    androidx.compose.runtime.DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlaybackStateChanged?.invoke(playing)
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                // Speed changed
            }

            override fun onBufferedPositionChanged(bufferedPosition: Long) {
                if (durationMs > 0) {
                    bufferedPercentage = (bufferedPosition.toFloat() / durationMs).coerceIn(0f, 1f)
                }
            }

            override fun onPlayerError(error: com.harimu.app.core.media.MediaError) {
                // Error handling via UI state
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    androidx.compose.runtime.LaunchedEffect(exoPlayer, speed) {
        exoPlayer.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4.0f))
    }

    androidx.compose.runtime.LaunchedEffect(exoPlayer, isPlaying, effectiveStartMs, effectiveEndMs) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val clampedPos = currentPosMs.coerceIn(effectiveStartMs, effectiveEndMs)
            if (clampedPos != currentPosMs) {
                currentPosMs = clampedPos
            }
            onPositionChanged?.invoke(currentPosMs)

            if (isPlaying && currentPosMs >= effectiveEndMs) {
                exoPlayer.seekTo(effectiveStartMs)
            }
            delay(100L)
        }
    }

    // Auto-hide controls
    androidx.compose.runtime.LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            controlsHideJob?.cancel()
            controlsHideJob = kotlinx.coroutines.launch(lifecycleScope) {
                delay(3000L)
                showControls = false
            }
        } else {
            controlsHideJob?.cancel()
        }
    }

    val maxSec = (durationMs / 1000f).coerceAtLeast(1f)
    val startSec = (effectiveStartMs / 1000f).coerceIn(0f, (maxSec - 0.3f).coerceAtLeast(0f))
    val endSec = (effectiveEndMs / 1000f).coerceIn(startSec + 0.3f, maxSec)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        ContentFrame(
            player = exoPlayer,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )

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
                        .width(bufferedPercentage)
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }
        }

        // Controls overlay
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.01f))
                    .clip(RoundedCornerShape(12.dp))
                    .onClick { showControls = !showControls }
            ) {
                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    // Seek bar with trim handles
                    Box(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.Slider(
                            value = (currentPosMs / 1000f).coerceIn(startSec, endSec),
                            onValueChange = { newSec ->
                                val newMs = (newSec * 1000f).roundToLong()
                                exoPlayer.seekTo(newMs.coerceIn(effectiveStartMs, effectiveEndMs))
                            },
                            onValueChangeFinished = { newSec ->
                                val newMs = (newSec * 1000f).roundToLong()
                                exoPlayer.seekTo(newMs.coerceIn(effectiveStartMs, effectiveEndMs))
                            },
                            valueRange = startSec..endSec,
                            colors = SliderDefaults.colors(
                                thumbColor = HariMuColors.ElectricOrange,
                                activeTrackColor = HariMuColors.ElectricOrange,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = effectiveDurationMs > 0
                        )

                        // Trim handle markers
                        if (showTrimHandles && effectiveDurationMs > 0) {
                            val startX = (startSec / maxSec)
                            val endX = (endSec / maxSec)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .align(Alignment.TopCenter)
                            ) {
                                // Start trim handle
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .graphicsLayer { translationX = (startX * this.width).coerceIn(0f, this.width - 2f) }
                                        .background(HariMuColors.ElectricOrange)
                                )
                                // End trim handle
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .graphicsLayer { translationX = (endX * this.width).coerceIn(0f, this.width - 2f) }
                                        .background(HariMuColors.ElectricOrange)
                                )
                            }
                        }
                    }

                    // Time display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
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
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(effectiveStartMs)) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            ),
                            contentDescription = stringResource(R.string.video_cd_rewind_10)
                        ) {
                            androidx.compose.material.Icon(Icons.Filled.Replay10, contentDescription = null, tint = Color.White)
                        }
                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 5_000).coerceAtLeast(effectiveStartMs)) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            ),
                            contentDescription = stringResource(R.string.video_cd_rewind_5)
                        ) {
                            androidx.compose.material.Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.currentPosition < effectiveStartMs || exoPlayer.currentPosition >= effectiveEndMs) {
                                        exoPlayer.seekTo(effectiveStartMs)
                                    }
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = HariMuColors.ElectricOrange.copy(alpha = 0.9f),
                                contentColor = Color.White
                            ),
                            contentDescription = if (isPlaying) stringResource(R.string.video_cd_pause) else stringResource(R.string.video_cd_play)
                        ) {
                            androidx.compose.material.Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 5_000).coerceAtMost(effectiveEndMs)) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            ),
                            contentDescription = stringResource(R.string.video_cd_forward_5)
                        ) {
                            androidx.compose.material.Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.White)
                        }
                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(effectiveEndMs)) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            ),
                            contentDescription = stringResource(R.string.video_cd_forward_10)
                        ) {
                            androidx.compose.material.Icon(Icons.Filled.Replay10, contentDescription = null, tint = Color.White)
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

                // Top-right rotation indicator
                if (rotationDeg != 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                text = "${rotationDeg}°",
                                style = MonoTextStyle.copy(fontSize = 11.sp, color = Color.White),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
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
                androidx.compose.material.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.MovieCreation,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = title,
                    style = androidx.compose.material3.Typography().titleMedium.copy(color = Color.White.copy(alpha = 0.7f))
                )
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.Typography().bodySmall.copy(color = Color.White.copy(alpha = 0.5f)),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}