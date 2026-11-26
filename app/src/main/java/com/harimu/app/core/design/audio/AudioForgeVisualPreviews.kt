package com.harimu.app.core.design.audio

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harimu.app.R
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.media.AudioBatch2Math
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Resolves a live [WaveformOverlaySpec] for Audio Forge tools that transform time/amplitude
 * envelopes directly over the waveform preview (Fade, Silence Removal, Split, Noise Floor).
 */
fun resolveWaveformOverlaySpec(
    toolId: String,
    params: Map<String, Any>,
    totalDurationSec: Float = 30f
): WaveformOverlaySpec {
    val safeDur = totalDurationSec.coerceAtLeast(1f)
    return when (toolId) {
        "AUD-26" -> WaveformOverlaySpec.FadeEnvelope(
            fadeInSec = (params["fadeIn"] as? Number)?.toFloat() ?: 2f,
            fadeOutSec = (params["fadeOut"] as? Number)?.toFloat() ?: 3f
        )

        "AUD-16" -> WaveformOverlaySpec.SilenceThreshold(
            thresholdDb = (params["thresholdDb"] as? Number)?.toFloat() ?: -40f,
            minDurationSec = (params["minDurationSec"] as? Number)?.toFloat() ?: 0.5f
        )

        "AUD-23" -> {
            val mode = params["mode"]?.toString().orEmpty()
            val parts = if (mode.contains("Jumlah") || mode.contains("Number")) {
                ((params["partsCount"] as? Number)?.toInt() ?: 4).coerceIn(2, 12)
            } else {
                val segSec = ((params["segmentSec"] as? Number)?.toFloat() ?: 30f).coerceAtLeast(1f)
                (safeDur / segSec).toInt().coerceIn(2, 12)
            }
            val fracs = (1 until parts).map { it.toFloat() / parts.toFloat() }
            WaveformOverlaySpec.SplitMarkers(splitFractions = fracs, partsCount = parts)
        }

        "AUD-03" -> WaveformOverlaySpec.NoiseFloor(
            strengthPct = (params["strength"] as? Number)?.toFloat() ?: 55f,
            noiseFloorDb = (params["noiseFloorDb"] as? Number)?.toFloat() ?: -45f
        )

        else -> WaveformOverlaySpec.None
    }
}

/**
 * Renders tool-specific visual workspace cards in ToolScreen for Audio Forge tools (`AUD-01..AUD-28`)
 * strictly derived from real tool parameters and [AudioBatch2Math] formulas.
 */
@Composable
fun AudioForgeToolPreviewSection(
    toolId: String,
    params: Map<String, Any>,
    secondaryUri: Uri?,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    when (toolId) {
        "AUD-01" -> AudioMasteringCurveCard(params = params, modifier = modifier)
        "AUD-11" -> AudioCompressionPreviewCard(params = params, isEnglish = isEnglish, modifier = modifier)
        "AUD-05" -> AudioSpatial8DCard(params = params, isEnglish = isEnglish, modifier = modifier)
        "AUD-24" -> AudioEchoDiagramCard(params = params, isEnglish = isEnglish, modifier = modifier)
        "AUD-25" -> AudioReverbDiagramCard(params = params, isEnglish = isEnglish, modifier = modifier)
        "AUD-18" -> AudioId3PreviewCard(params = params, coverUri = secondaryUri, isEnglish = isEnglish, modifier = modifier)
        "AUD-28" -> AudioVisualizerPreviewCard(params = params, isEnglish = isEnglish, modifier = modifier)
        "AUD-03" -> AudioOverlaySummaryBanner(
            text = stringResource(
                R.string.audio_visual_noise_overlay,
                (params["strength"] as? Number)?.toFloat() ?: 55f,
                (params["noiseFloorDb"] as? Number)?.toFloat() ?: -45f
            ),
            modifier = modifier
        )
        "AUD-16" -> AudioOverlaySummaryBanner(
            text = stringResource(
                R.string.audio_visual_silence_overlay,
                (params["thresholdDb"] as? Number)?.toFloat() ?: -40f,
                (params["minDurationSec"] as? Number)?.toFloat() ?: 0.5f
            ),
            modifier = modifier
        )
        "AUD-23" -> {
            val spec = resolveWaveformOverlaySpec("AUD-23", params) as WaveformOverlaySpec.SplitMarkers
            AudioOverlaySummaryBanner(
                text = stringResource(R.string.audio_visual_split_overlay, spec.partsCount),
                modifier = modifier
            )
        }
        "AUD-26" -> AudioOverlaySummaryBanner(
            text = stringResource(
                R.string.audio_visual_fade_overlay,
                (params["fadeIn"] as? Number)?.toFloat() ?: 2f,
                (params["fadeOut"] as? Number)?.toFloat() ?: 3f
            ),
            modifier = modifier
        )
    }
}

@Composable
private fun AudioOverlaySummaryBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = HariMuColors.GroupAudio.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, HariMuColors.GroupAudio.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = HariMuColors.GroupAudio, modifier = Modifier.size(16.dp))
            Text(text = text, style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioMasteringCurveCard(
    params: Map<String, Any>,
    modifier: Modifier = Modifier
) {
    val presetName = params["preset"]?.toString() ?: "Studio Warm"
    val thresholdDb = (params["thresholdDb"] as? Number)?.toFloat() ?: -18f
    val ratio = (params["ratio"] as? Number)?.toFloat() ?: 3.0f
    val ceilingDb = (params["ceilingDb"] as? Number)?.toFloat() ?: -1.0f
    val makeupDb = (params["makeupDb"] as? Number)?.toFloat() ?: 3.0f
    val warmth = params["warmthEnhance"] as? Boolean ?: true
    val spec = AudioBatch2Math.resolveMasteringPreset(presetName)

    val curveColor = HariMuColors.GroupAudio
    val ceilColor = HariMuColors.ElectricOrange
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_visual_mastering_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.audio_visual_mastering_title), style = MaterialTheme.typography.titleSmall)
                EngineBadge(engine = presetName)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(10.dp)
            ) {
                val minDb = -48f
                val maxDb = 0f
                fun dbToX(db: Float): Float = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f) * size.width
                fun dbToY(db: Float): Float = size.height - ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f) * size.height

                // Grid diagonal (1:1 unity)
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, 0f), 1.5f)
                // Limiter ceiling line
                val ceilY = dbToY(ceilingDb)
                drawLine(ceilColor, Offset(0f, ceilY), Offset(size.width, ceilY), 2f)

                val path = Path()
                val steps = 48
                for (i in 0..steps) {
                    val inDb = minDb + (i.toFloat() / steps) * (maxDb - minDb)
                    val compressed = if (inDb <= thresholdDb) {
                        inDb
                    } else {
                        thresholdDb + (inDb - thresholdDb) / ratio.coerceAtLeast(1f)
                    }
                    val withMakeup = (compressed + makeupDb).coerceAtMost(ceilingDb)
                    val pt = Offset(dbToX(inDb), dbToY(withMakeup))
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(path, curveColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EngineBadge(engine = String.format(Locale.US, "Thr %.0f dB", thresholdDb))
                EngineBadge(engine = String.format(Locale.US, "%.1f:1", ratio))
                EngineBadge(engine = String.format(Locale.US, "Ceil %.1f dB", ceilingDb))
                EngineBadge(engine = String.format(Locale.US, "+%.1f dB", makeupDb))
                if (warmth) {
                    EngineBadge(engine = String.format(Locale.US, "Width %.2fx", spec.stereoWidth))
                }
            }
        }
    }
}

@Composable
fun AudioCompressionPreviewCard(
    params: Map<String, Any>,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val bitrateLabel = params["bitrate"]?.toString() ?: "128k (Seimbang)"
    val formatLabel = params["format"]?.toString() ?: "MP3"
    val kbps = Regex("(\\d+)").find(bitrateLabel)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 128

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_visual_compress_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Compress, contentDescription = null, tint = HariMuColors.GroupAudio)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isEnglish) "Target Bitrate & Codec" else "Target Bitrate & Codec",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = String.format(Locale.US, "%s • %d kbps (~%.2f MB / min)", formatLabel, kbps, (kbps * 60.0) / 8192.0),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AudioSpatial8DCard(
    params: Map<String, Any>,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val rotationHz = (params["rotationHz"] as? Number)?.toFloat() ?: 0.15f
    val depthPct = (params["depthPct"] as? Number)?.toFloat() ?: 85f
    val roomReverb = params["roomReverb"] as? Boolean ?: true
    val cycleSec = (1f / rotationHz.coerceAtLeast(0.05f))

    val accent = HariMuColors.GroupAudio
    val ringColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_visual_spatial_8d_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Canvas(modifier = Modifier.size(84.dp)) {
                val r = (min(size.width, size.height) * 0.42f) * (depthPct.coerceIn(20f, 100f) / 100f)
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(color = ringColor, radius = min(size.width, size.height) * 0.44f, center = center, style = Stroke(2f))
                drawCircle(color = accent.copy(alpha = 0.7f), radius = r, center = center, style = Stroke(3.5f))
                drawCircle(color = HariMuColors.TealEmerald, radius = 6.dp.toPx(), center = center)
                val angle = -0.8f
                val orb = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
                drawCircle(color = HariMuColors.ElectricOrange, radius = 5.dp.toPx(), center = orb)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.SurroundSound, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.audio_visual_spatial_title), style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    text = if (isEnglish) {
                        String.format(Locale.US, "360° orbit %.1fs/rev • Depth %.0f%%", cycleSec, depthPct)
                    } else {
                        String.format(Locale.US, "Putaran 360° %.1f dtk/siklus • Kedalaman %.0f%%", cycleSec, depthPct)
                    },
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.primary
                )
                EngineBadge(engine = if (roomReverb) "3D Ambience ON" else "Dry Orbit")
            }
        }
    }
}

@Composable
fun AudioEchoDiagramCard(
    params: Map<String, Any>,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val delayMs = (params["delayMs"] as? Number)?.toInt() ?: 260
    val decay = (params["decay"] as? Number)?.toFloat() ?: 0.45f
    val taps = params["taps"]?.toString().orEmpty()
    val tapCount = when {
        taps.contains("3") || taps.contains("Stadion") || taps.contains("Stadium") -> 3
        taps.contains("2") || taps.contains("Ganda") || taps.contains("Dual") -> 2
        else -> 1
    }
    val barColor = HariMuColors.GroupAudio
    val dryColor = HariMuColors.TealEmerald

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_visual_echo_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.audio_visual_spatial_title), style = MaterialTheme.typography.titleSmall)
                EngineBadge(engine = "$tapCount ${if (isEnglish) "Taps" else "Pantulan"} • ${delayMs}ms")
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val barW = 10.dp.toPx()
                // Dry impulse
                drawRoundRect(dryColor, Offset(0f, 0f), Size(barW, size.height), CornerRadius(4f, 4f))
                for (i in 1..tapCount) {
                    val xFrac = (i.toFloat() / (tapCount + 0.6f)).coerceIn(0.15f, 0.92f)
                    val amp = decay.pow(i).coerceIn(0.12f, 0.95f)
                    val h = size.height * amp
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(size.width * xFrac, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioReverbDiagramCard(
    params: Map<String, Any>,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val roomPreset = params["roomPreset"]?.toString() ?: "Aula Konser (Hall)"
    val roomSize = (params["roomSizePct"] as? Number)?.toFloat() ?: 60f
    val damping = (params["dampingPct"] as? Number)?.toFloat() ?: 40f
    val wet = (params["wetPct"] as? Number)?.toFloat() ?: 45f
    val dampHz = (14_000f - (damping.coerceIn(10f, 90f) - 10f) * 135f).roundToInt().coerceIn(2800, 16000)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_visual_reverb_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.audio_visual_spatial_title), style = MaterialTheme.typography.titleSmall)
                EngineBadge(engine = roomPreset.substringBefore(" ("))
            }
            Text(
                text = if (isEnglish) {
                    "Room ${roomSize.toInt()}% • Wet ${wet.toInt()}% • HF LPF ${dampHz} Hz"
                } else {
                    "Ruang ${roomSize.toInt()}% • Gaung ${wet.toInt()}% • LPF ${dampHz} Hz"
                },
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AudioId3PreviewCard(
    params: Map<String, Any>,
    coverUri: Uri?,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val title = params["title"]?.toString().orEmpty().ifBlank {
        if (isEnglish) "Untitled Track" else "Judul Lagu"
    }
    val artist = params["artist"]?.toString().orEmpty().ifBlank {
        if (isEnglish) "Unknown Artist" else "Artis"
    }
    val album = params["album"]?.toString().orEmpty()
    val year = params["year"]?.toString().orEmpty()
    val genre = params["genre"]?.toString().orEmpty()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_visual_id3_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, HariMuColors.GroupAudio.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.audio_visual_id3_title), style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HariMuColors.GroupAudio.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUri != null) {
                        AsyncImage(
                            model = coverUri,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Album,
                            contentDescription = null,
                            tint = HariMuColors.GroupAudio,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    val meta = listOf(album, year, genre).filter { it.isNotBlank() }.joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MonoTextStyle, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun AudioVisualizerPreviewCard(
    params: Map<String, Any>,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val style = params["style"]?.toString() ?: "Gelombang Garis (Line Wave)"
    val colorTheme = params["colorTheme"]?.toString() ?: "Neon Sunset (Oranye & Cyan)"
    val resolution = params["resolution"]?.toString() ?: "1280x720 (HD 16:9)"
    val fps = params["fps"]?.toString() ?: "30 fps"
    val spec = AudioBatch2Math.resolveVisualizerConfig(style, colorTheme, resolution, fps)

    val bgColor = parseOxHexColor(spec.bgHex, HariMuColors.DeepObsidian)
    val c1 = parseOxHexColor(spec.primaryHex, HariMuColors.ElectricOrange)
    val c2 = parseOxHexColor(spec.secondaryHex, HariMuColors.TealEmerald)
    val ratio = spec.width.toFloat() / spec.height.toFloat().coerceAtLeast(1f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_visual_visualizer_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Movie, contentDescription = null, tint = HariMuColors.GroupAudio, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.audio_visual_visualizer_title), style = MaterialTheme.typography.titleSmall)
                }
                EngineBadge(engine = "${spec.width}×${spec.height} • ${spec.fps}fps")
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .widthIn(max = if (ratio < 1f) 140.dp else 280.dp)
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(0.56f, 1.8f))
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .padding(12.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bars = 24
                        val step = size.width / bars
                        val midY = size.height / 2f
                        when (spec.styleId) {
                            "bar_wave", "spectrum", "cqt" -> {
                                for (i in 0 until bars) {
                                    val amp = 0.25f + 0.65f * kotlin.math.abs(sin(i * 0.45f))
                                    val h = size.height * amp
                                    val col = if (i % 2 == 0) c1 else c2
                                    drawRoundRect(
                                        color = col,
                                        topLeft = Offset(i * step + 2f, midY - h / 2f),
                                        size = Size((step * 0.6f).coerceAtLeast(2f), h),
                                        cornerRadius = CornerRadius(3f, 3f)
                                    )
                                }
                            }
                            "vectorscope" -> {
                                val r = min(size.width, size.height) * 0.35f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                drawCircle(color = c2.copy(alpha = 0.4f), radius = r, center = center, style = Stroke(2f))
                                drawOval(
                                    color = c1,
                                    topLeft = Offset(center.x - r * 0.9f, center.y - r * 0.45f),
                                    size = Size(r * 1.8f, r * 0.9f),
                                    style = Stroke(3f)
                                )
                            }
                            else -> {
                                val path = Path()
                                for (i in 0..48) {
                                    val x = (i / 48f) * size.width
                                    val y = midY + sin(i * 0.5f) * (size.height * 0.32f) * sin(i / 48f * Math.PI).toFloat()
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, c1, style = Stroke(width = 4f, cap = StrokeCap.Round))
                            }
                        }
                    }
                }
            }
            Text(
                text = if (isEnglish) "Style: ${spec.styleId} • Scheme: ${spec.spectrumColorScheme}"
                else "Gaya: ${spec.styleId} • Skema: ${spec.spectrumColorScheme}",
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun parseOxHexColor(oxHex: String, fallback: Color): Color =
    runCatching {
        val hex = oxHex.removePrefix("0x").removePrefix("#")
        val value = hex.toLong(16)
        Color(value or 0xFF000000L)
    }.getOrDefault(fallback)
