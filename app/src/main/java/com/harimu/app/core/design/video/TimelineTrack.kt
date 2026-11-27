package com.harimu.app.core.design.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.harimu.app.R
import com.harimu.app.core.data.EditorClipSpec
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.media.VideoEditorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineTrack(
    modifier: Modifier = Modifier,
    clips: List<EditorClipSpec>,
    selectedClipId: String?,
    onClipSelected: (String) -> Unit,
    onClipMoved: ((Int, Int) -> Unit)? = null,
    onClipRemoved: ((String) -> Unit)? = null,
    onClipDuplicated: ((String) -> Unit)? = null,
    zoomLevel: Float = 1.0f,
    onZoomChanged: ((Float) -> Unit)? = null,
    showThumbnails: Boolean = true,
    minClipWidth: Float = 80f,
    maxClipWidth: Float = 300f
) {
    val totalDurationMs = VideoEditorMath.totalTimelineDurationMs(clips)

    val clipWidths = remember(clips, zoomLevel, minClipWidth, maxClipWidth) {
        clips.map { clip ->
            val effectiveMs = VideoEditorMath.effectiveClipDurationMs(clip)
            val proportion = if (totalDurationMs > 0 && clips.isNotEmpty()) {
                effectiveMs.toFloat() / totalDurationMs
            } else {
                1f / clips.size.coerceAtLeast(1)
            }
            val baseWidth = proportion * 1000f * zoomLevel
            baseWidth.coerceIn(minClipWidth, maxClipWidth)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Zoom control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.editor_timeline_zoom),
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = zoomLevel.coerceIn(0.25f, 4.0f),
                onValueChange = { onZoomChanged?.invoke(it) },
                valueRange = 0.25f..4.0f,
                steps = 15,
                colors = SliderDefaults.colors(
                    thumbColor = HariMuColors.ElectricOrange,
                    activeTrackColor = HariMuColors.ElectricOrange
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format(Locale.US, "%.1fx", zoomLevel),
                style = MonoTextStyle.copy(fontSize = 12.sp),
                modifier = Modifier.width(40.dp)
            )
        }

        // Timeline header with total duration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.editor_timeline_header, clips.size, formatDurationMs(totalDurationMs)),
                style = MaterialTheme.typography.titleSmall
            )
            if (clips.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.editor_clips_count, clips.size),
                    style = MonoTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        if (clips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MovieCreation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.editor_timeline_empty_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = stringResource(R.string.editor_timeline_empty_desc),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    userScrollEnabled = true
                ) {
                    itemsIndexed(clips, key = { _, item -> item.id }) { idx, clip ->
                        val isSelected = selectedClipId == clip.id
                        val effectiveMs = VideoEditorMath.effectiveClipDurationMs(clip)
                        val clipWidth = clipWidths[idx]

                        TimelineClipItem(
                            index = idx,
                            clip = clip,
                            clipWidth = clipWidth,
                            effectiveDurationMs = effectiveMs,
                            isSelected = isSelected,
                            onSelect = { onClipSelected(clip.id) },
                            onMoveLeft = { onClipMoved?.invoke(idx, idx - 1) },
                            onMoveRight = { onClipMoved?.invoke(idx, idx + 1) },
                            onDuplicate = { onClipDuplicated?.invoke(clip.id) },
                            onRemove = { onClipRemoved?.invoke(clip.id) },
                            showThumbnail = showThumbnails,
                            canMoveLeft = idx > 0,
                            canMoveRight = idx < clips.size - 1
                        )
                    }
                }

                TimeRuler(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    totalDurationMs = totalDurationMs
                )
            }
        }
    }
}

@Composable
private fun TimelineClipItem(
    index: Int,
    clip: EditorClipSpec,
    clipWidth: Float,
    effectiveDurationMs: Long,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    showThumbnail: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean
) {
    val context = LocalContext.current
    val clipUri = remember(clip.uriString) { clip.uriString.toUri() }
    val thumbBitmap by produceState<Bitmap?>(initialValue = null, clip.uriString, showThumbnail) {
        value = if (showThumbnail) {
            withContext(Dispatchers.IO) {
                extractClipThumbnail(context, clipUri, clip.trimStartMs)
            }
        } else {
            null
        }
    }

    DisposableEffect(thumbBitmap) {
        val current = thumbBitmap
        onDispose {
            if (current != null && !current.isRecycled) current.recycle()
        }
    }

    val resolvedWidth = if (isSelected) {
        clipWidth.coerceAtLeast(212f).dp
    } else {
        clipWidth.dp.coerceIn(80.dp, 300.dp)
    }

    Card(
        modifier = Modifier
            .width(resolvedWidth)
            .heightIn(min = 100.dp)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) HariMuColors.ElectricOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Top row: index & effective duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = HariMuColors.ElectricOrange
                    )
                )
                Text(
                    text = formatDurationMs(effectiveDurationMs),
                    style = MonoTextStyle.copy(fontSize = 10.sp)
                )
            }

            // Thumbnail or color bar
            if (showThumbnail) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = thumbBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MovieCreation,
                            contentDescription = null,
                            tint = getClipColor(index),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(getClipColor(index))
                )
            }

            // Clip name and badges
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = clip.displayName,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2gx", clip.speed),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    if (clip.rotationDeg != 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${clip.rotationDeg}°",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (clip.muteAudio) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = stringResource(R.string.editor_clip_muted),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Reorder, duplicate, remove controls
            if (isSelected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onMoveLeft,
                        enabled = canMoveLeft,
                        modifier = Modifier.minimumInteractiveComponentSize().size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.editor_cd_move_left),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveRight,
                        enabled = canMoveRight,
                        modifier = Modifier.minimumInteractiveComponentSize().size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.editor_cd_move_right),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.minimumInteractiveComponentSize().size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.editor_cd_duplicate),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.minimumInteractiveComponentSize().size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.editor_cd_remove),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRuler(
    modifier: Modifier = Modifier,
    totalDurationMs: Long
) {
    val totalSeconds = (totalDurationMs / 1000L).coerceAtLeast(1L)
    val majorInterval = when {
        totalSeconds <= 20 -> 5L
        totalSeconds <= 60 -> 15L
        totalSeconds <= 180 -> 30L
        totalSeconds <= 600 -> 60L
        totalSeconds <= 1800 -> 300L
        else -> 600L
    }

    val tickSeconds = remember(totalSeconds, majorInterval) {
        buildList {
            var currentSec = 0L
            while (currentSec < totalSeconds) {
                add(currentSec)
                currentSec += majorInterval
            }
            add(totalSeconds)
        }.distinct()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val usableWidth = (maxWidth - 40.dp).coerceAtLeast(1.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopStart)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )
        tickSeconds.forEach { sec ->
            val fraction = (sec.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
            val xOffset = usableWidth * fraction
            Column(
                modifier = Modifier.offset(x = xOffset),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                )
                Text(
                    text = formatTimeSec(sec),
                    style = MonoTextStyle.copy(fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}

private fun extractClipThumbnail(
    context: android.content.Context,
    uri: android.net.Uri,
    trimStartMs: Long
): Bitmap? {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(context, uri)
        val timeUs = (trimStartMs.coerceAtLeast(0L) + 250L) * 1000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            mmr.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 90)
        } else {
            mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { raw ->
                val scaled = Bitmap.createScaledBitmap(raw, 160, 90, true)
                if (scaled != raw) raw.recycle()
                scaled
            }
        }
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { mmr.release() }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    } else {
        String.format(Locale.US, "%ds", seconds)
    }
}

private fun formatTimeSec(sec: Long): String {
    val minutes = sec / 60
    val seconds = sec % 60
    return if (minutes > 0) {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    } else {
        String.format(Locale.US, "%ds", seconds)
    }
}

private fun getClipColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFFEC4899), // Pink
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFFEF4444), // Red
        Color(0xFF8B5CF6), // Violet
        Color(0xFF06B6D4), // Cyan
        Color(0xFFF97316), // Orange
    )
    return colors[index % colors.size]
}