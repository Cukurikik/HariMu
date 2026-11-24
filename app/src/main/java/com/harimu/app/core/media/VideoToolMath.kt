package com.harimu.app.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.harimu.app.core.files.FileStorageManager
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class CropGeometry(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int
) {
    fun toFfmpegFilter(): String = "crop=$width:$height:$x:$y"
}

data class TargetDimensions(
    val width: Int,
    val height: Int
)

data class SafetyAssessment(
    val allowed: Boolean,
    val useSegmentedMode: Boolean = false,
    val warningMessage: String? = null,
    val blockingError: String? = null
)

data class VideoValidationResult(
    val valid: Boolean,
    val probes: List<MediaProbeResult> = emptyList(),
    val errorMessage: String? = null
)

object VideoToolMath {

    private val SUPPORTED_VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "ts", "mts", "flv"
    )

    fun parseAspectRatio(label: String): Float = when (label.trim()) {
        "1:1" -> 1f
        "16:9" -> 16f / 9f
        "9:16" -> 9f / 16f
        "4:3" -> 4f / 3f
        "3:4" -> 3f / 4f
        else -> 1f
    }

    fun makeEven(value: Int, minEven: Int = 2): Int {
        val clamped = value.coerceAtLeast(minEven)
        return if (clamped % 2 == 0) clamped else clamped - 1
    }

    fun calculateCropRect(srcWidth: Int, srcHeight: Int, ratioLabel: String): CropGeometry {
        val safeW = makeEven(srcWidth.coerceAtLeast(2))
        val safeH = makeEven(srcHeight.coerceAtLeast(2))
        val targetRatio = parseAspectRatio(ratioLabel)
        val srcRatio = safeW.toFloat() / safeH.toFloat()

        val (rawW, rawH) = if (abs(srcRatio - targetRatio) < 0.005f) {
            safeW to safeH
        } else if (srcRatio > targetRatio) {
            val w = (safeH * targetRatio).roundToInt()
            w to safeH
        } else {
            val h = (safeW / targetRatio).roundToInt()
            safeW to h
        }

        val outW = makeEven(rawW.coerceAtMost(safeW))
        val outH = makeEven(rawH.coerceAtMost(safeH))
        val offsetX = makeEven(((safeW - outW) / 2).coerceAtLeast(0), 0)
        val offsetY = makeEven(((safeH - outH) / 2).coerceAtLeast(0), 0)
        return CropGeometry(width = outW, height = outH, x = offsetX, y = offsetY)
    }

    fun parseResolutionShortSide(preset: String): Int = when (preset.trim().lowercase(Locale.US)) {
        "1080p", "1080" -> 1080
        "720p", "720" -> 720
        "480p", "480" -> 480
        "360p", "360" -> 360
        else -> 720
    }

    fun calculateTargetDimensions(srcWidth: Int, srcHeight: Int, preset: String): TargetDimensions {
        val targetShort = parseResolutionShortSide(preset)
        val w = srcWidth.coerceAtLeast(2)
        val h = srcHeight.coerceAtLeast(2)
        return if (w >= h) {
            val outH = makeEven(targetShort)
            val outW = makeEven(((w.toDouble() / h.toDouble()) * outH).roundToInt())
            TargetDimensions(width = outW, height = outH)
        } else {
            val outW = makeEven(targetShort)
            val outH = makeEven(((h.toDouble() / w.toDouble()) * outW).roundToInt())
            TargetDimensions(width = outW, height = outH)
        }
    }

    fun buildSetPtsFilter(speed: Float): String {
        val safeSpeed = speed.coerceIn(0.25f, 4.0f)
        val ptsMultiplier = 1.0 / safeSpeed.toDouble()
        return String.format(Locale.US, "setpts=%.4f*PTS", ptsMultiplier)
    }

    fun buildAtempoFilterChain(speed: Float): String {
        var remaining = speed.coerceIn(0.25f, 4.0f).toDouble()
        val stages = mutableListOf<String>()
        while (remaining > 2.0001) {
            stages.add("atempo=2.0000")
            remaining /= 2.0
        }
        while (remaining < 0.4999) {
            stages.add("atempo=0.5000")
            remaining /= 0.5
        }
        stages.add(String.format(Locale.US, "atempo=%.4f", remaining))
        return stages.joinToString(",")
    }

    fun calculateTargetVideoBitrateBps(
        sourceBitrateBps: Long,
        crf: Int,
        width: Int,
        height: Int
    ): Long {
        val safeCrf = crf.coerceIn(18, 35)
        val pixels = (width.coerceAtLeast(320).toLong() * height.coerceAtLeast(240).toLong()).toDouble()
        val ref1080pPixels = 1920.0 * 1080.0
        val pixelScale = (pixels / ref1080pPixels).coerceIn(0.15, 2.5)
        val baseAtCrf20 = 5_200_000.0 * pixelScale
        val computed = (baseAtCrf20 * 0.85.pow((safeCrf - 20).toDouble())).toLong()
        val bounded = computed.coerceIn(250_000L, 18_000_000L)
        return if (sourceBitrateBps > 0L) {
            minOf(bounded, (sourceBitrateBps * 0.90).toLong().coerceAtLeast(250_000L))
        } else {
            bounded
        }
    }

    fun buildVolumeFilter(volumePercent: Float): String {
        val factor = (volumePercent.coerceIn(0f, 200f) / 100f).toDouble()
        return String.format(Locale.US, "volume=%.2f", factor)
    }

    fun mapRotationToMedia3Degrees(clockwiseDeg: Int): Float = when (((clockwiseDeg % 360) + 360) % 360) {
        90 -> 270f
        180 -> 180f
        270 -> 90f
        else -> 0f
    }

    fun buildFfmpegRotateFilter(clockwiseDeg: Int): String = when (((clockwiseDeg % 360) + 360) % 360) {
        90 -> "transpose=1"
        180 -> "hflip,vflip"
        270 -> "transpose=2"
        else -> "null"
    }

    fun computeSplitterRanges(
        totalDurationMs: Long,
        segmentDurationMs: Long,
        customStartMs: Long = 0L,
        customEndMs: Long = Long.MAX_VALUE
    ): List<Pair<Long, Long>> {
        val safeTotal = totalDurationMs.coerceAtLeast(500L)
        val start = customStartMs.coerceIn(0L, safeTotal - 200L)
        val end = if (customEndMs == Long.MAX_VALUE || customEndMs <= 0L) {
            safeTotal
        } else {
            customEndMs.coerceIn(start + 200L, safeTotal)
        }
        val step = segmentDurationMs.coerceAtLeast(1_000L)
        val ranges = mutableListOf<Pair<Long, Long>>()
        var cursor = start
        while (cursor < end) {
            val next = minOf(cursor + step, end)
            if (next > cursor) {
                ranges.add(cursor to next)
            }
            cursor = next
        }
        return ranges
    }

    fun computeReverseChunkRanges(totalDurationMs: Long, chunkDurationMs: Long = 12_000L): List<Pair<Long, Long>> {
        if (totalDurationMs <= 0L) return emptyList()
        val safeChunk = chunkDurationMs.coerceAtLeast(2_000L)
        val chunks = mutableListOf<Pair<Long, Long>>()
        var cursor = 0L
        while (cursor < totalDurationMs) {
            val next = minOf(cursor + safeChunk, totalDurationMs)
            chunks.add(cursor to next)
            cursor = next
        }
        return chunks
    }

    fun assessReverseSafety(durationMs: Long, isEnglish: Boolean): SafetyAssessment {
        return when {
            durationMs > 180_000L -> SafetyAssessment(
                allowed = false,
                blockingError = if (isEnglish) {
                    "Video is longer than 3 minutes (${durationMs / 1000}s). Reverse buffers uncompressed frames in memory; please trim to <= 3 minutes first with VID-01."
                } else {
                    "Durasi video melebihi 3 menit (${durationMs / 1000} dtk). Reverse memuat frame tak terkompresi di memori; potong dulu ke <= 3 menit dengan VID-01."
                }
            )
            durationMs > 20_000L -> SafetyAssessment(
                allowed = true,
                useSegmentedMode = true,
                warningMessage = if (isEnglish) {
                    "Video > 20s: using 12s segmented reverse mode to keep memory safe."
                } else {
                    "Video > 20 dtk: memakai mode reverse tersegmentasi per 12 detik agar RAM aman."
                }
            )
            else -> SafetyAssessment(allowed = true, useSegmentedMode = false)
        }
    }

    fun assessLoopSafety(durationMs: Long, loops: Int, isEnglish: Boolean): SafetyAssessment {
        val safeLoops = loops.coerceAtLeast(2)
        val totalOutMs = durationMs.coerceAtLeast(0L) * safeLoops.toLong()
        return when {
            totalOutMs > 600_000L -> SafetyAssessment(
                allowed = false,
                blockingError = if (isEnglish) {
                    "Total looped duration (${totalOutMs / 1000}s) exceeds the 10-minute safety limit. Reduce loop count or trim input first."
                } else {
                    "Total durasi loop (${totalOutMs / 1000} dtk) melebihi batas aman 10 menit. Kurangi jumlah loop atau potong video terlebih dahulu."
                }
            )
            totalOutMs > 180_000L -> SafetyAssessment(
                allowed = true,
                warningMessage = if (isEnglish) {
                    "Output duration will reach ${totalOutMs / 1000}s; ensure adequate storage space."
                } else {
                    "Durasi output akan mencapai ${totalOutMs / 1000} dtk; pastikan ruang penyimpanan cukup."
                }
            )
            else -> SafetyAssessment(allowed = true)
        }
    }

    fun isSupportedVideoExtension(ext: String): Boolean {
        val clean = ext.trim().lowercase(Locale.US).trimStart('.')
        return clean.isBlank() || clean in SUPPORTED_VIDEO_EXTENSIONS
    }

    fun resolveInputSizeBytes(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            val path = uri.path ?: return 0L
            val f = File(path)
            return if (f.exists()) f.length() else 0L
        }
        val fromAfd = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()
        if (fromAfd != null && fromAfd >= 0L) return fromAfd
        val fromColumns = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()
        if (fromColumns != null && fromColumns > 0L) return fromColumns
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(4096)
                val read = stream.read(buf)
                if (read > 0) read.toLong() else 0L
            }
        }.getOrNull() ?: 0L
    }

    fun validateVideoInputs(
        context: Context,
        storage: FileStorageManager,
        uris: List<Uri>,
        minCount: Int = 1,
        requireAudio: Boolean = false,
        requireVideo: Boolean = true,
        isEnglish: Boolean = false
    ): VideoValidationResult {
        if (uris.size < minCount) {
            return VideoValidationResult(
                valid = false,
                errorMessage = if (isEnglish) {
                    if (minCount > 1) "Select at least $minCount video files." else "Please pick a video file first."
                } else {
                    if (minCount > 1) "Pilih minimal $minCount file video." else "Pilih file video terlebih dahulu."
                }
            )
        }
        if (!storage.hasEnoughDiskSpace()) {
            return VideoValidationResult(
                valid = false,
                errorMessage = if (isEnglish) "Not enough free disk space." else "Ruang disk tidak cukup untuk memproses video."
            )
        }

        val probes = mutableListOf<MediaProbeResult>()
        uris.forEachIndexed { idx, uri ->
            val size = resolveInputSizeBytes(context, uri)
            if (size <= 0L) {
                return VideoValidationResult(
                    valid = false,
                    errorMessage = if (isEnglish) {
                        "Input video #${idx + 1} is empty (0 bytes) or unreadable."
                    } else {
                        "File video ke-${idx + 1} kosong (0 byte) atau tidak dapat dibaca."
                    }
                )
            }

            val ext = MediaUriUtils.resolveExtension(context, uri, fallbackExt = "")
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            if (mime.isNotBlank() && !mime.startsWith("video/") && mime != "application/octet-stream") {
                return VideoValidationResult(
                    valid = false,
                    errorMessage = if (isEnglish) {
                        "Unsupported file format ($mime). Please choose a valid video file."
                    } else {
                        "Format file tidak didukung ($mime). Pilih file video yang valid."
                    }
                )
            }
            if (ext.isNotBlank() && !isSupportedVideoExtension(ext)) {
                return VideoValidationResult(
                    valid = false,
                    errorMessage = if (isEnglish) {
                        "Unsupported video extension: .$ext."
                    } else {
                        "Format video tidak didukung: .$ext."
                    }
                )
            }

            val probe = MediaProbe.probe(context, uri)
            if (requireVideo && !probe.hasVideo && probe.durationMs <= 0L) {
                return VideoValidationResult(
                    valid = false,
                    errorMessage = if (isEnglish) {
                        "Unsupported or corrupted video stream in file #${idx + 1}."
                    } else {
                        "Format video tidak didukung atau file rusak pada input ke-${idx + 1}."
                    }
                )
            }
            if (requireAudio && !probe.hasAudio) {
                return VideoValidationResult(
                    valid = false,
                    errorMessage = if (isEnglish) {
                        "Video has no audio track. Choose a video that contains audio."
                    } else {
                        "Video tidak memiliki trek audio. Pilih video yang memiliki suara."
                    }
                )
            }
            probes.add(probe)
        }

        return VideoValidationResult(valid = true, probes = probes)
    }
}
