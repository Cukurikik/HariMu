package com.harimu.app.feature.studios

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.harimu.app.core.data.AudioClipSpec
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.design.audio.AudioTimelineMath
import kotlin.math.abs

/**
 * Multi-track live preview playback controller backed by Media3 [ExoPlayer] instances per clip.
 * Accurately honors clip source trim (`ClippingConfiguration`), timeline offset (`offsetMs`),
 * clip gain (dB), track volume, track Mute/Solo, and master volume during live timeline playback.
 * Note: Per-track L/R stereo balance (`pan`) is applied during FFmpeg mixdown export.
 */
class AudioStudioPlaybackController(private val context: Context) {

    private data class ClipPlayerEntry(
        val player: ExoPlayer,
        val uriString: String,
        val trimStartMs: Long,
        val trimEndMs: Long
    )

    private val players = mutableMapOf<String, ClipPlayerEntry>()

    fun syncState(
        tracks: List<AudioTrackSpec>,
        masterVolume: Float,
        playheadPositionMs: Long,
        isPlaying: Boolean
    ) {
        val hasAnySolo = tracks.any { it.solo && !it.mute }
        val activeIds = HashSet<String>()

        tracks.forEach { track ->
            track.clips.forEach { clip ->
                activeIds += clip.id
                val clipDur = AudioTimelineMath.effectiveClipDurationMs(clip)
                val endOnTimeline = clip.offsetMs + clipDur
                val inWindow = playheadPositionMs >= clip.offsetMs && playheadPositionMs < endOnTimeline

                if (!isPlaying || !inWindow) {
                    players[clip.id]?.let { entry ->
                        runCatching {
                            if (entry.player.isPlaying) entry.player.pause()
                        }
                    }
                } else {
                    val entry = ensurePlayer(clip) ?: return@forEach
                    val gain = AudioTimelineMath.computeClipEffectivePlaybackGain(
                        track = track,
                        clip = clip,
                        masterVolume = masterVolume,
                        hasAnySolo = hasAnySolo
                    )
                    val targetPosInClip = (playheadPositionMs - clip.offsetMs).coerceIn(0L, clipDur)
                    runCatching {
                        entry.player.volume = gain
                        val current = entry.player.currentPosition.coerceAtLeast(0L)
                        if (abs(current - targetPosInClip) > 220L) {
                            entry.player.seekTo(targetPosInClip)
                        }
                        if (!entry.player.isPlaying && entry.player.playbackState != Player.STATE_ENDED) {
                            entry.player.playWhenReady = true
                            entry.player.play()
                        }
                    }
                }
            }
        }

        val staleIds = players.keys.filter { it !in activeIds }
        staleIds.forEach { id ->
            players.remove(id)?.let { entry ->
                runCatching { entry.player.release() }
            }
        }
    }

    fun seekTo(tracks: List<AudioTrackSpec>, playheadPositionMs: Long) {
        tracks.forEach { track ->
            track.clips.forEach { clip ->
                val entry = players[clip.id] ?: return@forEach
                val clipDur = AudioTimelineMath.effectiveClipDurationMs(clip)
                if (playheadPositionMs >= clip.offsetMs && playheadPositionMs < clip.offsetMs + clipDur) {
                    val target = (playheadPositionMs - clip.offsetMs).coerceIn(0L, clipDur)
                    runCatching { entry.player.seekTo(target) }
                } else {
                    runCatching {
                        if (entry.player.isPlaying) entry.player.pause()
                        entry.player.seekTo(0L)
                    }
                }
            }
        }
    }

    fun pauseAll() {
        players.values.forEach { entry ->
            runCatching {
                if (entry.player.isPlaying) entry.player.pause()
            }
        }
    }

    fun release() {
        players.values.forEach { entry ->
            runCatching { entry.player.release() }
        }
        players.clear()
    }

    private fun ensurePlayer(clip: AudioClipSpec): ClipPlayerEntry? {
        val existing = players[clip.id]
        if (existing != null &&
            existing.uriString == clip.uriString &&
            existing.trimStartMs == clip.trimStartMs &&
            existing.trimEndMs == clip.trimEndMs
        ) {
            return existing
        }
        existing?.let { runCatching { it.player.release() } }

        val built = runCatching {
            val safeEnd = clip.trimEndMs.coerceAtLeast(clip.trimStartMs + AudioTimelineMath.MIN_CLIP_DURATION_MS)
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(clip.uriString))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(safeEnd)
                        .build()
                )
                .build()
            ExoPlayer.Builder(context.applicationContext).build().apply {
                setMediaItem(mediaItem)
                prepare()
            }
        }.getOrNull() ?: return null

        val entry = ClipPlayerEntry(
            player = built,
            uriString = clip.uriString,
            trimStartMs = clip.trimStartMs,
            trimEndMs = clip.trimEndMs
        )
        players[clip.id] = entry
        return entry
    }
}
