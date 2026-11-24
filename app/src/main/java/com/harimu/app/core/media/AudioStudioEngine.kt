package com.harimu.app.core.media

import android.content.Context
import android.net.Uri
import com.harimu.app.core.data.AudioTrackSpec
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object AudioStudioEngine {

    fun exportProject(
        context: Context,
        storage: FileStorageManager,
        tracks: List<AudioTrackSpec>,
        masterVolume: Float,
        outputFormat: String = "wav", // wav, mp3, flac
        isEnglish: Boolean = false
    ): Flow<ToolProgress> {
        val mediaJob = MediaJob()
        val ext = outputFormat.lowercase(Locale.US).takeIf { it in listOf("wav", "mp3", "flac") } ?: "wav"
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            else -> "audio/wav"
        }

        val hasSolo = tracks.any { it.solo && !it.mute }
        val activeTracks = tracks.filter { track ->
            if (track.mute) false
            else if (hasSolo) track.solo
            else true
        }

        val allClipsWithTrack = activeTracks.flatMap { track ->
            val trackVol = track.volume.coerceIn(0f, 2f) * masterVolume.coerceIn(0f, 2f)
            track.clips.map { clip ->
                Triple(track, clip, trackVol)
            }
        }

        if (allClipsWithTrack.isEmpty()) {
            return kotlinx.coroutines.flow.flow {
                emit(ToolProgress.Failed(if (isEnglish) "No active audio clips to export." else "Tidak ada klip audio aktif untuk diekspor."))
            }
        }

        val inputUris = allClipsWithTrack.map { (_, clip, _) -> Uri.parse(clip.uriString) }
        val maxDurationMs = allClipsWithTrack.maxOfOrNull { (_, clip, _) -> clip.offsetMs + (clip.trimEndMs - clip.trimStartMs) } ?: 1000L

        return mediaJob.runCommand(
            context = context,
            storage = storage,
            inputUris = inputUris,
            toolTag = "DAW_${ext.uppercase(Locale.US)}",
            outputExt = ext,
            outputMime = mime,
            expectedDurationMs = maxDurationMs,
            isEnglish = isEnglish
        ) { localFiles, outputFile ->
            val args = mutableListOf<String>()
            args.add("-y")

            localFiles.forEach { file ->
                args.add("-i")
                args.add(file.absolutePath)
            }

            val filterParts = mutableListOf<String>()
            val mixInputs = StringBuilder()

            allClipsWithTrack.forEachIndexed { index, (track, clip, trackVol) ->
                val startSec = String.format(Locale.US, "%.3f", clip.trimStartMs.coerceAtLeast(0L) / 1000.0)
                val durSec = String.format(Locale.US, "%.3f", (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(250L) / 1000.0)
                val delayMs = clip.offsetMs.coerceAtLeast(0L)

                val gainLinear = 10.0.pow((clip.gainDb.coerceIn(-24f, 24f) / 20.0)).toFloat()
                val finalVol = (trackVol * gainLinear).coerceIn(0f, 4f)
                val panVal = track.pan.coerceIn(-1f, 1f)

                val label = "a$index"
                val filterChain = buildList {
                    add("atrim=start=$startSec:duration=$durSec")
                    add("asetpts=PTS-STARTPTS")
                    if (abs(finalVol - 1.0f) > 0.01f) {
                        add("volume=$finalVol")
                    }
                    if (abs(panVal) > 0.05f) {
                        val leftGain = String.format(Locale.US, "%.2f", 1.0f - max(0f, panVal))
                        val rightGain = String.format(Locale.US, "%.2f", 1.0f + min(0f, panVal))
                        add("pan=stereo|c0=$leftGain*c0|c1=$rightGain*c1")
                    }
                    if (delayMs > 0L) {
                        add("adelay=$delayMs|$delayMs")
                    }
                    add("aformat=sample_rates=44100:channel_layouts=stereo")
                }.joinToString(",")

                filterParts.add("[$index:a]$filterChain[$label]")
                mixInputs.append("[$label]")
            }

            val mixCount = allClipsWithTrack.size
            val filterComplex = if (mixCount == 1) {
                "${filterParts.first()};[a0]acopy[outa]"
            } else {
                "${filterParts.joinToString(";")};${mixInputs}amix=inputs=$mixCount:duration=longest:dropout_transition=2[outa]"
            }

            args.add("-filter_complex")
            args.add(filterComplex)
            args.add("-map")
            args.add("[outa]")

            if (ext == "mp3") {
                args.add("-b:a")
                args.add("192k")
            } else if (ext == "flac") {
                args.add("-compression_level")
                args.add("5")
            }

            args.add(outputFile.absolutePath)
            args
        }
    }
}
