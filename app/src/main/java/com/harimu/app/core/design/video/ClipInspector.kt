package com.harimu.app.core.design.video
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harimu.app.R
import com.harimu.app.core.data.EditorClipSpec
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ParamToggle
import com.harimu.app.core.media.VideoEditorMath
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipInspector(
    modifier: Modifier = Modifier,
    clipIndex: Int,
    clip: EditorClipSpec,
    onUpdate: (EditorClipSpec) -> Unit,
    onOpenInTool: ((String, Map<String, Any>) -> Unit)? = null,
    currentPlayheadMs: () -> Long = { 0L },
    showAdvancedActions: Boolean = true
) {
    val maxSec = (clip.durationMs / 1000f).coerceAtLeast(1f)
    val startSec = (clip.trimStartMs / 1000f).coerceIn(0f, (maxSec - 0.2f).coerceAtLeast(0f))
    val endSec = (clip.trimEndMs / 1000f).coerceIn(startSec + 0.2f, maxSec)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_inspector_title, clipIndex + 1, clip.displayName),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDurationMs(VideoEditorMath.effectiveClipDurationMs(clip)),
                    style = MonoTextStyle.copy(color = MaterialTheme.colorScheme.primary)
                )
            }

            // Trim section
            InspectorSection(
                title = stringResource(
                    R.string.editor_clip_trim_label,
                    formatDurationMs(clip.trimStartMs),
                    formatDurationMs(clip.trimEndMs),
                    formatDurationMs(VideoEditorMath.effectiveClipDurationMs(clip))
                )
            ) {
                RangeSlider(
                    value = startSec..endSec,
                    onValueChange = { range ->
                        val sMs = (range.start * 1000f).roundToLong().coerceIn(0L, (clip.durationMs - 200L).coerceAtLeast(0L))
                        val eMs = (range.endInclusive * 1000f).roundToLong().coerceIn(sMs + 200L, clip.durationMs)
                        onUpdate(clip.copy(trimStartMs = sMs, trimEndMs = eMs))
                    },
                    valueRange = 0f..maxSec,
                    colors = SliderDefaults.colors(
                        thumbColor = HariMuColors.ElectricOrange,
                        activeTrackColor = HariMuColors.ElectricOrange,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionChip(
                        label = stringResource(R.string.editor_set_start),
                        onClick = {
                            val maxStart = (clip.trimEndMs - 200L).coerceAtLeast(0L)
                            val newStart = currentPlayheadMs().coerceIn(0L, maxStart)
                            onUpdate(clip.copy(trimStartMs = newStart))
                        },
                        icon = Icons.Filled.FirstPage
                    )
                    QuickActionChip(
                        label = stringResource(R.string.editor_set_end),
                        onClick = {
                            val minEnd = (clip.trimStartMs + 200L).coerceAtMost(clip.durationMs)
                            val newEnd = currentPlayheadMs().coerceIn(minEnd, clip.durationMs)
                            onUpdate(clip.copy(trimEndMs = newEnd))
                        },
                        icon = Icons.AutoMirrored.Filled.LastPage
                    )
                    QuickActionChip(
                        label = stringResource(R.string.editor_reset_trim),
                        onClick = { onUpdate(clip.copy(trimStartMs = 0L, trimEndMs = clip.durationMs)) },
                        icon = Icons.Filled.RestartAlt
                    )
                }
            }

            // Speed section
            InspectorSection(title = stringResource(R.string.editor_clip_speed_label, String.format(Locale.US, "%.2f", clip.speed))) {
                Slider(
                    value = clip.speed.coerceIn(0.25f, 4.0f),
                    onValueChange = { spd ->
                        val snapped = ((spd * 4f).roundToLong() / 4f).coerceIn(0.25f, 4.0f)
                        onUpdate(clip.copy(speed = snapped))
                    },
                    valueRange = 0.25f..4.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = HariMuColors.ElectricOrange,
                        activeTrackColor = HariMuColors.ElectricOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f).forEach { spd ->
                        FilterChip(
                            selected = abs(clip.speed - spd) < 0.05f,
                            onClick = { onUpdate(clip.copy(speed = spd)) },
                            label = { Text(String.format(Locale.US, "%.2gx", spd)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                selectedContainerColor = HariMuColors.ElectricOrange.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }

            // Rotation section
            InspectorSection(title = stringResource(R.string.editor_clip_rotation_label)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 90, 180, 270).forEach { deg ->
                        val normalized = VideoEditorMath.normalizeRotation(clip.rotationDeg)
                        FilterChip(
                            selected = normalized == deg,
                            onClick = { onUpdate(clip.copy(rotationDeg = deg)) },
                            label = { Text("${deg}°") },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                selectedContainerColor = HariMuColors.ElectricOrange.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }

            // Mute toggle
            InspectorSection(title = stringResource(R.string.editor_clip_mute_label)) {
                ParamToggle(
                    label = stringResource(R.string.editor_clip_mute_label),
                    checked = clip.muteAudio,
                    onCheckedChange = { muted -> onUpdate(clip.copy(muteAudio = muted)) }
                )
            }

            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EngineBadge(engine = String.format(Locale.US, "%.2gx", clip.speed))
                if (clip.rotationDeg != 0) {
                    EngineBadge(engine = "${clip.rotationDeg}°")
                }
                if (clip.muteAudio) {
                    EngineBadge(engine = stringResource(R.string.editor_clip_muted))
                }
            }

            // Advanced actions - "Open in Tool" buttons
            if (showAdvancedActions && onOpenInTool != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.editor_advanced_tools_label),
                    style = MaterialTheme.typography.labelLarge
                )

                // Row 1: Transform & Audio
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_trim_precise),
                            icon = Icons.Filled.ContentCut,
                            onClick = { onOpenInTool("VID-01", mapOf(
                                "startSec" to (clip.trimStartMs / 1000f),
                                "endSec" to if (clip.trimEndMs >= clip.durationMs) 0f else (clip.trimEndMs / 1000f)
                            )) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_speed),
                            icon = Icons.Filled.Speed,
                            onClick = { onOpenInTool("VID-05", mapOf(
                                "speed" to clip.speed,
                                "includeAudio" to !clip.muteAudio
                            )) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_rotate_flip),
                            icon = Icons.AutoMirrored.Filled.RotateRight,
                            onClick = {
                                val normalized = VideoEditorMath.normalizeRotation(clip.rotationDeg)
                                val degParams = if (normalized in setOf(90, 180, 270)) {
                                    mapOf("deg" to normalized.toString())
                                } else {
                                    emptyMap()
                                }
                                onOpenInTool("VID-09", degParams)
                            }
                        )
                    }

                    // Row 2: Color & Overlays
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_color_grade),
                            icon = Icons.Filled.Palette,
                            onClick = { onOpenInTool("VID-26", mapOf(
                                "preset" to "Manual"
                            )) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_text_overlay),
                            icon = Icons.Filled.TextFields,
                            onClick = { onOpenInTool("VID-15", mapOf(
                                "startSec" to (clip.trimStartMs / 1000f),
                                "endSec" to if (clip.trimEndMs >= clip.durationMs) 0f else (clip.trimEndMs / 1000f)
                            )) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_image_overlay),
                            icon = Icons.Filled.Image,
                            onClick = { onOpenInTool("VID-17", mapOf(
                                "startSec" to (clip.trimStartMs / 1000f),
                                "endSec" to if (clip.trimEndMs >= clip.durationMs) 0f else (clip.trimEndMs / 1000f)
                            )) }
                        )
                    }

                    // Row 3: Effects & Corrections
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_stabilize),
                            icon = Icons.Filled.CenterFocusStrong,
                            onClick = { onOpenInTool("VID-11", mapOf()) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_face_blur),
                            icon = Icons.Filled.FaceRetouchingOff,
                            onClick = { onOpenInTool("VID-25", mapOf()) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_logo_blur),
                            icon = Icons.Filled.BlurOn,
                            onClick = { onOpenInTool("VID-14", mapOf()) }
                        )
                    }

                    // Row 4: Audio & Subtitles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_subtitles),
                            icon = Icons.Filled.Subtitles,
                            onClick = { onOpenInTool("VID-23", mapOf()) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_volume),
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            onClick = { onOpenInTool("VID-13", mapOf()) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_add_audio),
                            icon = Icons.Filled.MusicNote,
                            onClick = { onOpenInTool("VID-16", mapOf()) }
                        )
                    }

                    // Row 5: Creative & Transform
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_reverse),
                            icon = Icons.Filled.FastRewind,
                            onClick = { onOpenInTool("VID-19", mapOf()) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_loop),
                            icon = Icons.Filled.Loop,
                            onClick = { onOpenInTool("VID-12", mapOf()) }
                        )
                        ToolActionButton(
                            label = stringResource(R.string.editor_tool_chroma_key),
                            icon = Icons.Filled.FilterBAndW,
                            onClick = { onOpenInTool("VID-28", mapOf()) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectorSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionChip(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    AssistChip(
        onClick = onClick,
        leadingIcon = icon?.let {
            { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(16.dp)) }
        },
        label = {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        },
        modifier = Modifier.heightIn(min = 48.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.ToolActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .weight(1f)
            .heightIn(min = 80.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HariMuColors.ElectricOrange,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatDurationMs(ms: Long): String {
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