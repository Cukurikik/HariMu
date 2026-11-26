package com.harimu.app.core.design.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.data.AudioClipSpec
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Inline Audio Clip Inspector exposing every operation actually supported by
 * [com.harimu.app.core.media.AudioStudioEngine] (source trim, timeline offset, clip gain dB, delete)
 * plus categorized "Open in Tool" handoffs to Audio Forge via [com.harimu.app.feature.toolscreen.ToolPrefillData].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioClipInspector(
    track: AudioTrackSpec?,
    clip: AudioClipSpec?,
    isEnglish: Boolean,
    onUpdateTrim: (trackId: String, clipId: String, startMs: Long, endMs: Long) -> Unit,
    onUpdateOffset: (trackId: String, clipId: String, offsetMs: Long) -> Unit,
    onUpdateGain: (trackId: String, clipId: String, gainDb: Float) -> Unit,
    onDeleteClip: (trackId: String, clipId: String) -> Unit,
    onOpenInTool: ((toolId: String, clip: AudioClipSpec, params: Map<String, Any>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (track == null || clip == null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("audio_clip_inspector_empty"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = HariMuColors.GroupAudio
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.audio_inspector_empty_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.audio_inspector_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val totalDurSec = (clip.durationMs.coerceAtLeast(1_000L) / 1000f)
    val startSec = (clip.trimStartMs / 1000f).coerceIn(0f, (totalDurSec - 0.25f).coerceAtLeast(0f))
    val endSec = (clip.trimEndMs / 1000f).coerceIn(startSec + 0.25f, totalDurSec)
    val effectiveDurMs = AudioTimelineMath.effectiveClipDurationMs(clip)
    val maxOffsetSec = ((clip.offsetMs / 1000f) + 60f).coerceAtLeast(60f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_clip_inspector"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, HariMuColors.GroupAudio.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audio_inspector_title, clip.displayName),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EngineBadge(engine = track.name)
                        Text(
                            text = AudioTimelineMath.formatDurationMs(effectiveDurMs),
                            style = MonoTextStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = { onDeleteClip(track.trackId, clip.id) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audio_inspector_delete_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.audio_inspector_delete_clip),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Trim Source Range
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.audio_inspector_trim_label,
                            AudioTimelineMath.formatDurationMs(clip.trimStartMs),
                            AudioTimelineMath.formatDurationMs(clip.trimEndMs),
                            AudioTimelineMath.formatDurationMs(effectiveDurMs)
                        ),
                        style = MonoTextStyle,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onUpdateTrim(track.trackId, clip.id, 0L, clip.durationMs) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.audio_inspector_reset_trim))
                    }
                }
                RangeSlider(
                    value = startSec..endSec,
                    onValueChange = { range ->
                        val minGapMs = 250L
                        val sMs = (range.start * 1000f).roundToLong()
                            .coerceIn(0L, (clip.durationMs - minGapMs).coerceAtLeast(0L))
                        val eMs = (range.endInclusive * 1000f).roundToLong()
                            .coerceIn(sMs + minGapMs, clip.durationMs)
                        onUpdateTrim(track.trackId, clip.id, sMs, eMs)
                    },
                    valueRange = 0f..totalDurSec,
                    colors = SliderDefaults.colors(
                        thumbColor = HariMuColors.GroupAudio,
                        activeTrackColor = HariMuColors.GroupAudio
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("audio_inspector_trim_slider")
                )
            }

            // Timeline Position (Offset)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        R.string.audio_inspector_offset_label,
                        AudioTimelineMath.formatDurationMs(clip.offsetMs)
                    ),
                    style = MonoTextStyle
                )
                Slider(
                    value = (clip.offsetMs / 1000f).coerceIn(0f, maxOffsetSec),
                    onValueChange = { sec ->
                        onUpdateOffset(track.trackId, clip.id, (sec * 1000f).roundToLong().coerceAtLeast(0L))
                    },
                    valueRange = 0f..maxOffsetSec,
                    colors = SliderDefaults.colors(
                        thumbColor = HariMuColors.TealEmerald,
                        activeTrackColor = HariMuColors.TealEmerald
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("audio_inspector_offset_slider")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { onUpdateOffset(track.trackId, clip.id, 0L) },
                        label = { Text(stringResource(R.string.audio_inspector_snap_start)) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                    AssistChip(
                        onClick = { onUpdateOffset(track.trackId, clip.id, (clip.offsetMs - 500L).coerceAtLeast(0L)) },
                        label = { Text(stringResource(R.string.audio_inspector_nudge_back)) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                    AssistChip(
                        onClick = { onUpdateOffset(track.trackId, clip.id, clip.offsetMs + 500L) },
                        label = { Text(stringResource(R.string.audio_inspector_nudge_fwd)) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }

            // Clip Gain (-24 to +24 dB)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.audio_inspector_gain_label, clip.gainDb),
                        style = MonoTextStyle
                    )
                    TextButton(
                        onClick = { onUpdateGain(track.trackId, clip.id, 0f) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.audio_inspector_reset_gain))
                    }
                }
                Slider(
                    value = clip.gainDb.coerceIn(-24f, 24f),
                    onValueChange = { db ->
                        val snapped = (db * 2f).roundToLong() / 2f
                        onUpdateGain(track.trackId, clip.id, snapped.coerceIn(-24f, 24f))
                    },
                    valueRange = -24f..24f,
                    colors = SliderDefaults.colors(
                        thumbColor = HariMuColors.ElectricOrange,
                        activeTrackColor = HariMuColors.ElectricOrange
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("audio_inspector_gain_slider")
                )
            }

            // Open in Tool section
            if (onOpenInTool != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.audio_inspector_open_in_tool),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.audio_inspector_open_in_tool_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val trimEndParam = if (clip.trimEndMs >= clip.durationMs) 0f else (clip.trimEndMs / 1000f)
                        AudioToolShortcutChip(
                            label = if (isEnglish) "Trim (AUD-09)" else "Potong (AUD-09)",
                            icon = Icons.Filled.ContentCut,
                            onClick = {
                                onOpenInTool(
                                    "AUD-09",
                                    clip,
                                    mapOf(
                                        "startSec" to (clip.trimStartMs / 1000f),
                                        "endSec" to trimEndParam
                                    )
                                )
                            }
                        )
                        AudioToolShortcutChip(
                            label = "Mastering (AUD-01)",
                            icon = Icons.Filled.GraphicEq,
                            onClick = { onOpenInTool("AUD-01", clip, emptyMap()) }
                        )
                        AudioToolShortcutChip(
                            label = if (isEnglish) "Noise Reduce (AUD-03)" else "Hapus Derau (AUD-03)",
                            icon = Icons.Filled.CleaningServices,
                            onClick = { onOpenInTool("AUD-03", clip, emptyMap()) }
                        )
                        AudioToolShortcutChip(
                            label = if (isEnglish) "Enhancer (AUD-22)" else "Peningkat (AUD-22)",
                            icon = Icons.Filled.AutoFixHigh,
                            onClick = { onOpenInTool("AUD-22", clip, emptyMap()) }
                        )
                        AudioToolShortcutChip(
                            label = "Loudness (AUD-12)",
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            onClick = { onOpenInTool("AUD-12", clip, emptyMap()) }
                        )
                        AudioToolShortcutChip(
                            label = "Pitch (AUD-06)",
                            icon = Icons.Filled.MusicNote,
                            onClick = { onOpenInTool("AUD-06", clip, emptyMap()) }
                        )
                        AudioToolShortcutChip(
                            label = if (isEnglish) "Speed (AUD-07)" else "Kecepatan (AUD-07)",
                            icon = Icons.Filled.Speed,
                            onClick = { onOpenInTool("AUD-07", clip, emptyMap()) }
                        )
                        AudioToolShortcutChip(
                            label = "Reverb (AUD-25)",
                            icon = Icons.Filled.SurroundSound,
                            onClick = { onOpenInTool("AUD-25", clip, emptyMap()) }
                        )
                        AudioToolShortcutChip(
                            label = "Echo (AUD-24)",
                            icon = Icons.Filled.Tune,
                            onClick = { onOpenInTool("AUD-24", clip, emptyMap()) }
                        )
                        AudioToolShortcutChip(
                            label = "Fade (AUD-26)",
                            icon = Icons.Filled.GraphicEq,
                            onClick = { onOpenInTool("AUD-26", clip, emptyMap()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioToolShortcutChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, HariMuColors.GroupAudio.copy(alpha = 0.25f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HariMuColors.GroupAudio,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
