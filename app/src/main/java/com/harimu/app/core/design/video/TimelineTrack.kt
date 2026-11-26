package com.harimu.app.core.design.video

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.harimu.app.R
import com.harimu.app.core.data.EditorClipSpec
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.media.VideoEditorMath
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val clipWidths = remember(clips, zoomLevel, minClipWidth, maxClipWidth) {
        clips.map { clip ->
            val effectiveMs = VideoEditorMath.effectiveClipDurationMs(clip)
            val proportion = if (totalDurationMs > 0) effectiveMs.toFloat() / totalDurationMs else 1f / clips.size
            val baseWidth = proportion * 1000f * zoomLevel
            baseWidth.coerceIn(minClipWidth, maxClipWidth)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth(),
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
                value = zoomLevel,
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.editor_timeline_header, clips.size, formatDurationMs(totalDurationMs)),
                style = androidx.compose.material3.Typography().titleSmall
            )
            if (clips.isNotEmpty()) {
                Text(
                    text = "${clips.size} ${if (clips.size == 1) "clip" else "clips"}",
                    style = MonoTextStyle.copy(color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }

        if (clips.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MovieCreation,
                        contentDescription = null,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.editor_timeline_empty_title),
                        style = androidx.compose.material3.Typography().titleMedium.copy(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = stringResource(R.string.editor_timeline_empty_desc),
                        style = androidx.compose.material3.Typography().bodySmall.copy(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Timeline track with clips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
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
                            totalDurationMs = totalDurationMs,
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

                // Time ruler (optional, shown at bottom)
                TimeRuler(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    totalDurationMs = totalDurationMs,
                    zoomLevel = zoomLevel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineClipItem(
    index: Int,
    clip: EditorClipSpec,
    clipWidth: Float,
    effectiveDurationMs: Long,
    totalDurationMs: Long,
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

    Card(
        modifier = Modifier
            .width(clipWidth.dp.coerceAtLeast(80.dp).coerceAtMost(300.dp))
            .heightIn(min = 100.dp)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                androidx.compose.material3.MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) HariMuColors.ElectricOrange else androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top row: index, duration, drag handle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = androidx.compose.material3.Typography().labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = HariMuColors.ElectricOrange
                        )
                    )
                    Text(
                        text = formatDurationMs(effectiveDurationMs),
                        style = MonoTextStyle.copy(fontSize = 10.sp)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.DragIndicator,
                            contentDescription = stringResource(R.string.editor_cd_drag),
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 4.dp)
                        )
                    }
                }

                // Thumbnail or color bar
                if (showThumbnail) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(clipUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                        )
                    }
                } else {
                    // Color bar representing the clip
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

                // Action buttons (visible on selected or always for accessibility)
                if (isSelected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onMoveLeft,
                            enabled = canMoveLeft,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.editor_cd_move_left),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onMoveRight,
                            enabled = canMoveRight,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.editor_cd_move_right),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDuplicate,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
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
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = stringResource(R.string.editor_cd_remove),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRuler(
    modifier: Modifier = Modifier,
    totalDurationMs: Long,
    zoomLevel: Float
) {
    val context = LocalContext.current
    val totalSeconds = (totalDurationMs / 1000L).coerceAtLeast(1L)
    val majorInterval = when {
        totalSeconds <= 30 -> 5L
        totalSeconds <= 120 -> 10L
        totalSeconds <= 600 -> 30L
        totalSeconds <= 1800 -> 60L
        else -> 300L
    }

    val rulerWidth = (totalDurationMs / 1000f * 20f * zoomLevel).coerceAtLeast(300f).coerceAtMost(2000f)

    Box(
        modifier = modifier
            .width(rulerWidth.dp)
            .height(24.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        // Major ticks
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            var currentSec = 0L
            while (currentSec <= totalSeconds) {
                val xPos = (currentSec.toFloat() / totalSeconds * rulerWidth).coerceIn(0f, rulerWidth)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .graphicsLayer { translationX = xPos }
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                )
                currentSec += majorInterval
            }
        }
        // Labels
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            var currentSec = 0L
            while (currentSec <= totalSeconds) {
                val xPos = (currentSec.toFloat() / totalSeconds * rulerWidth).coerceIn(0f, rulerWidth - 40f)
                Text(
                    text = formatTimeSec(currentSec),
                    style = MonoTextStyle.copy(fontSize = 8.sp),
                    modifier = Modifier
                        .width(40.dp)
                        .graphicsLayer { translationX = xPos }
                )
                currentSec += majorInterval
            }
        }
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