package com.harimu.app.core.media

import android.content.Context
import androidx.core.net.toUri
import com.harimu.app.core.data.EditorClipSpec
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

object VideoEditorMath {

    fun normalizeRotation(deg: Int): Int {
        val mod = ((deg % 360) + 360) % 360
        return when {
            mod in 45..134 -> 90
            mod in 135..224 -> 180
            mod in 225..314 -> 270
            else -> 0
        }
    }

    fun effectiveClipDurationMs(clip: EditorClipSpec): Long {
        val start = clip.trimStartMs.coerceAtLeast(0L)
        val end = clip.trimEndMs.coerceAtLeast(start + 250L)
        val rawSpan = (end - start).coerceAtLeast(250L)
        val spd = clip.speed.coerceIn(0.25f, 4.0f).toDouble()
        return (rawSpan.toDouble() / spd).roundToLong().coerceAtLeast(200L)
    }

    fun totalTimelineDurationMs(clips: List<EditorClipSpec>): Long =
        clips.sumOf { effectiveClipDurationMs(it) }

    fun targetCanvasDimensions(resolutionPreset: String, landscape: Boolean = true): TargetDimensions {
        val shortSide = VideoToolMath.parseResolutionShortSide(resolutionPreset)
        val longSide = VideoToolMath.makeEven((shortSide * 16f / 9f).toInt())
        return if (landscape) {
            TargetDimensions(width = longSide, height = VideoToolMath.makeEven(shortSide))
        } else {
            TargetDimensions(width = VideoToolMath.makeEven(shortSide), height = longSide)
        }
    }

    fun buildSegmentFfmpegArgs(
        inputPath: String,
        outputPath: String,
        clip: EditorClipSpec,
        targetWidth: Int,
        targetHeight: Int,
        sourceHasAudio: Boolean,
        includeAudioTrack: Boolean
    ): List<String> {
        val safeW = VideoToolMath.makeEven(targetWidth.coerceAtLeast(320))
        val safeH = VideoToolMath.makeEven(targetHeight.coerceAtLeast(240))
        val startMs = clip.trimStartMs.coerceAtLeast(0L)
        val rawSpanMs = (clip.trimEndMs - startMs).coerceAtLeast(250L)
        val speed = clip.speed.coerceIn(0.25f, 4.0f)
        val rot = normalizeRotation(clip.rotationDeg)

        val videoFilters = buildList {
            if (rot != 0) add(VideoToolMath.buildFfmpegRotateFilter(rot))
            if (abs(speed - 1.0f) > 0.01f) add(VideoToolMath.buildSetPtsFilter(speed))
            add("scale=$safeW:$safeH:force_original_aspect_ratio=decrease")
            add("pad=$safeW:$safeH:(ow-iw)/2:(oh-ih)/2")
            add("fps=30")
            add("format=yuv420p")
            add("setsar=1")
        }.joinToString(",")

        val hasUsableAudio = sourceHasAudio && !clip.muteAudio
        val outDurSec = String.format(Locale.US, "%.3f", effectiveClipDurationMs(clip) / 1000.0)
        val ssStr = String.format(Locale.US, "%.3f", startMs / 1000.0)
        val tStr = String.format(Locale.US, "%.3f", rawSpanMs / 1000.0)

        return buildList {
            add("-y")
            if (startMs > 0L) {
                add("-ss"); add(ssStr)
            }
            add("-t"); add(tStr)
            add("-i"); add(inputPath)

            if (includeAudioTrack && !hasUsableAudio) {
                add("-f"); add("lavfi")
                add("-t"); add(outDurSec)
                add("-i"); add("anullsrc=channel_layout=stereo:sample_rate=44100")
                add("-filter_complex"); add("[0:v]$videoFilters[vout];[1:a]aformat=sample_rates=44100:channel_layouts=stereo[aout]")
                add("-map"); add("[vout]")
                add("-map"); add("[aout]")
                add("-c:a"); add("aac"); add("-b:a"); add("160k")
            } else if (includeAudioTrack && hasUsableAudio) {
                val audioFilters = buildList {
                    if (abs(speed - 1.0f) > 0.01f) add(VideoToolMath.buildAtempoFilterChain(speed))
                    add("aformat=sample_rates=44100:channel_layouts=stereo")
                }.joinToString(",")
                add("-filter_complex"); add("[0:v]$videoFilters[vout];[0:a]$audioFilters[aout]")
                add("-map"); add("[vout]")
                add("-map"); add("[aout]")
                add("-c:a"); add("aac"); add("-b:a"); add("160k")
            } else {
                add("-vf"); add(videoFilters)
                add("-an")
            }

            add("-c:v"); add("mpeg4"); add("-q:v"); add("3")
            add("-movflags"); add("+faststart")
            add(outputPath)
        }
    }
}

class VideoEditorEngine(
    private val media3: Media3TransformerEngine = Media3TransformerEngine(),
    private val mediaJob: MediaJob = MediaJob()
) {

    fun exportTimeline(
        context: Context,
        storage: FileStorageManager,
        clips: List<EditorClipSpec>,
        resolutionPreset: String,
        isEnglish: Boolean = false
    ): Flow<ToolProgress> = flow {
        if (clips.isEmpty()) {
            emit(ToolProgress.Failed(if (isEnglish) "Add at least 1 video clip to the timeline." else "Tambahkan minimal 1 klip video ke timeline."))
            return@flow
        }
        if (!storage.hasEnoughDiskSpace()) {
            emit(ToolProgress.Failed(if (isEnglish) "Not enough free disk space." else "Ruang disk tidak cukup untuk mengekspor timeline."))
            return@flow
        }

        emit(ToolProgress.Queued)
        val jobDir = storage.createJobTempDir("editor_${UUID.randomUUID().toString().take(8)}")
        val renderedFiles = mutableListOf<File>()
        val clipUris = clips.map { it.uriString.toUri() }

        try {
            val validation = VideoToolMath.validateVideoInputs(context, storage, clipUris, minCount = 1, isEnglish = isEnglish)
            if (!validation.valid) {
                emit(ToolProgress.Failed(validation.errorMessage ?: "Invalid timeline clip"))
                return@flow
            }
            val probes = validation.probes
            val firstProbe = probes.first()
            val landscape = firstProbe.width >= firstProbe.height || firstProbe.width == 0
            val dims = VideoEditorMath.targetCanvasDimensions(resolutionPreset, landscape)
            val includeAudio = clips.indices.any { idx -> probes[idx].hasAudio && !clips[idx].muteAudio }

            val segmentFiles = mutableListOf<File>()
            for (idx in clips.indices) {
                currentCoroutineContext().ensureActive()
                val clip = clips[idx]
                val probe = probes[idx]
                val clipUri = clipUris[idx]
                val pctStart = 5 + ((idx * 75) / clips.size)
                emit(
                    ToolProgress.Running(
                        percent = pctStart,
                        message = if (isEnglish) {
                            "Processing clip ${idx + 1}/${clips.size}: ${clip.displayName}…"
                        } else {
                            "Memproses klip ${idx + 1}/${clips.size}: ${clip.displayName}…"
                        }
                    )
                )

                val segmentOut = File(jobDir, String.format(Locale.US, "seg_%03d.mp4", idx))
                val canUseMedia3 = includeAudio == (probe.hasAudio && !clip.muteAudio)

                var segmentSuccess = false
                if (canUseMedia3) {
                    val req = Media3Request(
                        inputUri = clipUri,
                        toolTag = "ED_SEG_${idx}",
                        removeAudio = clip.muteAudio || !probe.hasAudio,
                        clipStartMs = clip.trimStartMs.coerceAtLeast(0L),
                        clipEndMs = clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 250L),
                        targetWidth = dims.width,
                        targetHeight = dims.height,
                        rotationDegrees = VideoToolMath.mapRotationToMedia3Degrees(clip.rotationDeg),
                        speed = clip.speed.coerceIn(0.25f, 4.0f)
                    )
                    var producedFile: File? = null
                    media3.runTransformerOnly(context, storage, req, isEnglish)
                        .catch { e -> if (e is CancellationException) throw e }
                        .collect { prog ->
                            if (prog is ToolProgress.Done) {
                                producedFile = prog.result.outputs.firstOrNull()?.file
                            } else if (prog is ToolProgress.Running) {
                                val subPct = pctStart + ((prog.percent * 75) / (clips.size * 100))
                                emit(ToolProgress.Running(subPct.coerceIn(pctStart, 82), prog.message))
                            }
                        }
                    val produced = producedFile
                    if (produced != null && produced.exists() && produced.length() > 0L) {
                        if (!produced.renameTo(segmentOut)) {
                            produced.copyTo(segmentOut, overwrite = true)
                            MediaUriUtils.cleanupQuietly(produced)
                        }
                        segmentSuccess = true
                    }
                }

                if (!segmentSuccess) {
                    val localIn = MediaUriUtils.copyUriToSafeTemp(
                        context = context,
                        storage = storage,
                        uri = clipUri,
                        jobDir = jobDir,
                        index = idx,
                        fallbackExt = "mp4"
                    )
                    val args = VideoEditorMath.buildSegmentFfmpegArgs(
                        inputPath = localIn.absolutePath,
                        outputPath = segmentOut.absolutePath,
                        clip = clip,
                        targetWidth = dims.width,
                        targetHeight = dims.height,
                        sourceHasAudio = probe.hasAudio,
                        includeAudioTrack = includeAudio
                    )
                    val stepResult = mediaJob.runStep(
                        arguments = args,
                        expectedDurationMs = VideoEditorMath.effectiveClipDurationMs(clip),
                        isEnglish = isEnglish
                    )
                    if (stepResult.isFailure || !segmentOut.exists() || segmentOut.length() == 0L) {
                        val reason = stepResult.exceptionOrNull()?.localizedMessage
                            ?: if (isEnglish) "Failed rendering clip #${idx + 1}" else "Gagal merender klip ke-${idx + 1}"
                        emit(ToolProgress.Failed(reason))
                        return@flow
                    }
                }

                segmentFiles.add(segmentOut)
                renderedFiles.add(segmentOut)
            }

            currentCoroutineContext().ensureActive()
            val finalOutput = MediaUriUtils.createOutputFile(storage, "EDITOR", "mp4")
            if (segmentFiles.size == 1) {
                emit(ToolProgress.Running(90, if (isEnglish) "Finalizing export…" else "Menyelesaikan ekspor…"))
                segmentFiles.first().copyTo(finalOutput, overwrite = true)
            } else {
                emit(ToolProgress.Running(85, if (isEnglish) "Merging ${segmentFiles.size} timeline clips…" else "Menggabungkan ${segmentFiles.size} klip timeline…"))
                var mergedViaMedia3 = false
                val mergeReq = Media3Request(
                    inputUri = segmentFiles.first().toUri(),
                    additionalInputUris = segmentFiles.drop(1).map { it.toUri() },
                    toolTag = "ED_MERGE"
                )
                var mergedOutFile: File? = null
                media3.runTransformerOnly(context, storage, mergeReq, isEnglish)
                    .catch { e -> if (e is CancellationException) throw e }
                    .collect { prog ->
                        if (prog is ToolProgress.Done) {
                            mergedOutFile = prog.result.outputs.firstOrNull()?.file
                        } else if (prog is ToolProgress.Running) {
                            val p = 85 + (prog.percent / 8)
                            emit(ToolProgress.Running(p.coerceIn(85, 98), prog.message))
                        }
                    }
                val mFile = mergedOutFile
                if (mFile != null && mFile.exists() && mFile.length() > 0L) {
                    if (!mFile.renameTo(finalOutput)) {
                        mFile.copyTo(finalOutput, overwrite = true)
                        MediaUriUtils.cleanupQuietly(mFile)
                    }
                    mergedViaMedia3 = true
                }

                if (!mergedViaMedia3) {
                    val concatList = File(jobDir, "concat.txt")
                    concatList.writeText(
                        segmentFiles.joinToString("\n") { "file '${it.absolutePath.replace("'", "'\\''")}'" },
                        Charsets.UTF_8
                    )
                    val totalDurMs = VideoEditorMath.totalTimelineDurationMs(clips)
                    val concatResult = mediaJob.runStep(
                        arguments = listOf(
                            "-y",
                            "-f", "concat",
                            "-safe", "0",
                            "-i", concatList.absolutePath,
                            "-c", "copy",
                            "-movflags", "+faststart",
                            finalOutput.absolutePath
                        ),
                        expectedDurationMs = totalDurMs,
                        isEnglish = isEnglish
                    )
                    if (concatResult.isFailure || !finalOutput.exists() || finalOutput.length() == 0L) {
                        val reason = concatResult.exceptionOrNull()?.localizedMessage
                            ?: if (isEnglish) "Failed merging timeline clips" else "Gagal menggabungkan klip timeline"
                        MediaUriUtils.cleanupQuietly(finalOutput)
                        emit(ToolProgress.Failed(reason))
                        return@flow
                    }
                }
            }

            val finalProbe = MediaProbe.probeFile(finalOutput)
            val sizeText = storage.formatBytes(finalOutput.length())
            emit(
                ToolProgress.Done(
                    ToolResult(
                        outputs = listOf(ToolOutputFile(finalOutput, "video/mp4", finalOutput.name)),
                        textOutput = finalProbe.formatSummary(isEnglish),
                        summary = if (isEnglish) {
                            "Exported ${clips.size} clips: ${finalOutput.name} ($sizeText)"
                        } else {
                            "Ekspor ${clips.size} klip selesai: ${finalOutput.name} ($sizeText)"
                        }
                    )
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            emit(ToolProgress.Failed(t.localizedMessage ?: "Timeline export failed"))
        } finally {
            MediaUriUtils.cleanupQuietly(jobDir)
        }
    }.flowOn(Dispatchers.IO)
}
