package com.harimu.app.core.design.audio

import androidx.compose.ui.graphics.Color
import com.harimu.app.core.data.AudioClipSpec
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.design.HariMuColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

data class AudioExportSummary(
    val activeTrackCount: Int,
    val activeClipCount: Int,
    val totalDurationMs: Long,
    val soloActive: Boolean,
    val hasPanApplied: Boolean
)

/**
 * Optional visual overlays rendered onto [com.harimu.app.feature.toolscreen.WaveformView]
 * so Audio Forge tools show real parameter feedback directly over the waveform.
 */
sealed interface WaveformOverlaySpec {
    data object None : WaveformOverlaySpec

    data class FadeEnvelope(
        val fadeInSec: Float,
        val fadeOutSec: Float
    ) : WaveformOverlaySpec

    data class SilenceThreshold(
        val thresholdDb: Float,
        val minDurationSec: Float
    ) : WaveformOverlaySpec

    data class SplitMarkers(
        val splitFractions: List<Float>,
        val partsCount: Int
    ) : WaveformOverlaySpec

    data class NoiseFloor(
        val strengthPct: Float,
        val noiseFloorDb: Float
    ) : WaveformOverlaySpec
}

object AudioTimelineMath {

    private const val BASE_DP_PER_SECOND = 48f
    const val MIN_TIMELINE_DP = 640f
    const val TIMELINE_TAIL_PADDING_DP = 48f
    const val MIN_CLIP_DURATION_MS = 250L

    val TRACK_ACCENTS: List<Color> = listOf(
        HariMuColors.GroupAudio,
        HariMuColors.TealEmerald,
        HariMuColors.ElectricOrange,
        HariMuColors.GroupImage,
        HariMuColors.GroupConverter,
        HariMuColors.GroupVideo,
        HariMuColors.GroupNusantara,
        HariMuColors.GroupPdf
    )

    fun accentForTrack(index: Int): Color =
        TRACK_ACCENTS[index.coerceAtLeast(0) % TRACK_ACCENTS.size]

    fun dpPerSecond(zoomLevel: Float): Float =
        BASE_DP_PER_SECOND * zoomLevel.coerceIn(0.5f, 5.0f)

    fun msToOffsetDp(ms: Long, zoomLevel: Float): Float =
        (ms.coerceAtLeast(0L) / 1000f) * dpPerSecond(zoomLevel)

    fun offsetDpToMs(dp: Float, zoomLevel: Float, maxDurationMs: Long = Long.MAX_VALUE): Long {
        val rawMs = ((dp.coerceAtLeast(0f) / dpPerSecond(zoomLevel)) * 1000f).roundToLong()
        return rawMs.coerceIn(0L, maxDurationMs.coerceAtLeast(0L))
    }

    fun timelineWidthDp(totalDurationMs: Long, zoomLevel: Float): Float =
        max(MIN_TIMELINE_DP, msToOffsetDp(totalDurationMs, zoomLevel) + TIMELINE_TAIL_PADDING_DP)

    fun effectiveClipDurationMs(clip: AudioClipSpec): Long =
        (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(MIN_CLIP_DURATION_MS)

    fun rulerTickIntervalSec(zoomLevel: Float): Int = when {
        zoomLevel >= 2.5f -> 1
        zoomLevel >= 1.25f -> 2
        else -> 5
    }

    /**
     * Computes normalized ExoPlayer live-preview volume `[0f..1f]` for a clip inside a track,
     * mirroring [com.harimu.app.core.media.AudioStudioEngine] mute/solo/gain/track/master rules.
     */
    fun computeClipEffectivePlaybackGain(
        track: AudioTrackSpec,
        clip: AudioClipSpec,
        masterVolume: Float,
        hasAnySolo: Boolean
    ): Float {
        if (track.mute) return 0f
        if (hasAnySolo && !track.solo) return 0f
        val clipLinear = 10.0.pow((clip.gainDb.coerceIn(-24f, 24f) / 20.0)).toFloat()
        val combined = track.volume.coerceIn(0f, 2f) * masterVolume.coerceIn(0f, 2f) * clipLinear
        return combined.coerceIn(0f, 1f)
    }

    /**
     * Slices full-source waveform peaks `[0f..1f]` to match the clip's `[trimStartMs..trimEndMs]`
     * window and scales visually by the clip gain without overflowing `1f`.
     */
    fun slicePeaksForClip(
        fullPeaks: List<Float>,
        clip: AudioClipSpec,
        targetBars: Int = 48
    ): List<Float> {
        if (fullPeaks.isEmpty() || targetBars <= 0) return emptyList()
        val dur = clip.durationMs.coerceAtLeast(1L).toFloat()
        val startFrac = (clip.trimStartMs.toFloat() / dur).coerceIn(0f, 0.98f)
        val endFrac = (clip.trimEndMs.toFloat() / dur).coerceIn(startFrac + 0.01f, 1f)
        val startIdx = (startFrac * (fullPeaks.size - 1)).toInt().coerceIn(0, fullPeaks.lastIndex)
        val endIdx = (endFrac * (fullPeaks.size - 1)).toInt().coerceIn(startIdx, fullPeaks.lastIndex)
        val sub = fullPeaks.subList(startIdx, endIdx + 1)
        val gainMul = 10.0.pow((clip.gainDb.coerceIn(-24f, 24f) / 20.0)).toFloat().coerceIn(0.25f, 1.8f)

        return List(targetBars) { idx ->
            val mapped = if (sub.size == 1) {
                sub.first()
            } else {
                val pos = (idx.toFloat() / (targetBars - 1).coerceAtLeast(1)) * (sub.size - 1)
                val lo = pos.toInt().coerceIn(0, sub.lastIndex)
                val hi = (lo + 1).coerceAtMost(sub.lastIndex)
                val frac = pos - lo
                sub[lo] + (sub[hi] - sub[lo]) * frac
            }
            (mapped * gainMul).coerceIn(0.08f, 1f)
        }
    }

    fun activeClipsSummary(tracks: List<AudioTrackSpec>): AudioExportSummary {
        val hasSolo = tracks.any { it.solo && !it.mute }
        val activeTracks = tracks.filter { track ->
            !track.mute && (!hasSolo || track.solo)
        }
        val tracksWithClips = activeTracks.filter { it.clips.isNotEmpty() }
        val activeClips = tracksWithClips.flatMap { it.clips }
        val totalMs = tracksWithClips.maxOfOrNull { tr ->
            tr.clips.maxOfOrNull { clip -> clip.offsetMs + effectiveClipDurationMs(clip) } ?: 0L
        } ?: 0L
        val hasPan = tracksWithClips.any { abs(it.pan) > 0.05f }

        return AudioExportSummary(
            activeTrackCount = tracksWithClips.size,
            activeClipCount = activeClips.size,
            totalDurationMs = totalMs,
            soloActive = hasSolo,
            hasPanApplied = hasPan
        )
    }

    fun estimateAudioExportSizeBytes(durationMs: Long, format: String): Long {
        val sec = (durationMs.coerceAtLeast(1_000L) / 1000.0)
        val bytesPerSec = when (format.lowercase(Locale.US)) {
            "mp3" -> 24_000.0     // 192 kbps
            "flac" -> 96_000.0    // ~768 kbps lossless average
            else -> 176_400.0     // 44.1 kHz * 2 ch * 16-bit PCM WAV
        }
        return (sec * bytesPerSec).roundToLong().coerceAtLeast(8_192L)
    }

    fun formatDurationMs(ms: Long, includeTenths: Boolean = true): String {
        val safeMs = ms.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (includeTenths) {
            val tenths = (safeMs % 1000L) / 100L
            String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatPan(pan: Float, centerLabel: String): String {
        val pct = (abs(pan) * 100f).toInt()
        return when {
            abs(pan) <= 0.05f -> centerLabel
            pan < 0f -> "L $pct%"
            else -> "R $pct%"
        }
    }
}
