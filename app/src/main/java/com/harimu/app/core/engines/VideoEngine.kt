package com.harimu.app.core.engines

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.harimu.app.core.files.FileStorageManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object VideoEngine {

    fun getVideoDurationMs(context: Context, uri: Uri): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            mmr.release()
        }
    }

    fun trimOrCopyTracks(
        context: Context,
        uri: Uri,
        destMp4: File,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
        includeVideo: Boolean = true,
        includeAudio: Boolean = true,
        outputFormat: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    ): File {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val muxer = MediaMuxer(destMp4.absolutePath, outputFormat)
        try {
            val trackMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 512 * 1024
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                val keep = (includeVideo && mime.startsWith("video/")) ||
                    (includeAudio && mime.startsWith("audio/"))
                if (keep) {
                    val outIdx = muxer.addTrack(fmt)
                    trackMap[i] = outIdx
                    extractor.selectTrack(i)
                    if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBufferSize = maxOf(maxBufferSize, fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }
            require(trackMap.isNotEmpty()) { "Track video/audio yang sesuai tidak ditemukan" }

            val startUs = (startMs * 1000L).coerceAtLeast(0L)
            val endUs = if (endMs == Long.MAX_VALUE) Long.MAX_VALUE else endMs * 1000L
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            muxer.start()
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val info = MediaCodec.BufferInfo()
            var basePtsUs = -1L

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break
                val ptsUs = extractor.sampleTime
                if (ptsUs > endUs) break

                val outTrack = trackMap[trackIndex]
                if (outTrack == null) {
                    extractor.advance()
                    continue
                }
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                if (ptsUs >= startUs) {
                    if (basePtsUs < 0L) basePtsUs = ptsUs
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = (ptsUs - basePtsUs).coerceAtLeast(0L)
                    @Suppress("WrongConstant")
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(outTrack, buffer, info)
                }
                extractor.advance()
            }
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            extractor.release()
        }
        return destMp4
    }

    fun splitVideoToZip(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        destZip: File,
        segmentSec: Int
    ): Pair<File, Int> {
        val totalMs = getVideoDurationMs(context, uri).coerceAtLeast(1000L)
        val segMs = (segmentSec.coerceAtLeast(2) * 1000L)
        var start = 0L
        var idx = 0
        ZipOutputStream(FileOutputStream(destZip)).use { zos ->
            while (start < totalMs) {
                idx++
                val end = minOf(start + segMs, totalMs)
                val partFile = storage.createTempFile("vpart_$idx", "mp4")
                trimOrCopyTracks(context, uri, partFile, start, end, includeVideo = true, includeAudio = true)
                zos.putNextEntry(ZipEntry("part_${idx}.mp4"))
                partFile.inputStream().use { it.copyTo(zos, 16 * 1024) }
                zos.closeEntry()
                partFile.delete()
                start = end
            }
        }
        return destZip to idx
    }

    fun mergeVideos(
        context: Context,
        uris: List<Uri>,
        destMp4: File
    ): File {
        require(uris.size >= 2) { "Pilih minimal 2 video untuk digabung" }
        val firstExt = MediaExtractor().apply { setDataSource(context, uris.first(), null) }
        val muxer = MediaMuxer(destMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoOutTrack = -1
        var audioOutTrack = -1
        try {
            for (i in 0 until firstExt.trackCount) {
                val fmt = firstExt.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoOutTrack < 0) {
                    videoOutTrack = muxer.addTrack(fmt)
                } else if (mime.startsWith("audio/") && audioOutTrack < 0) {
                    audioOutTrack = muxer.addTrack(fmt)
                }
            }
            firstExt.release()
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var timeOffsetUs = 0L

            for (uri in uris) {
                val ext = MediaExtractor().apply { setDataSource(context, uri, null) }
                var vIn = -1
                var aIn = -1
                for (i in 0 until ext.trackCount) {
                    val mime = ext.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && vIn < 0) {
                        vIn = i; ext.selectTrack(i)
                    } else if (mime.startsWith("audio/") && aIn < 0 && audioOutTrack >= 0) {
                        aIn = i; ext.selectTrack(i)
                    }
                }
                var maxClipPts = 0L
                while (true) {
                    val idx = ext.sampleTrackIndex
                    if (idx < 0) break
                    val size = ext.readSampleData(buffer, 0)
                    if (size < 0) break
                    val pts = ext.sampleTime
                    if (pts > maxClipPts) maxClipPts = pts
                    val outTrack = when (idx) {
                        vIn -> videoOutTrack
                        aIn -> audioOutTrack
                        else -> -1
                    }
                    if (outTrack >= 0) {
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = timeOffsetUs + pts.coerceAtLeast(0L)
                        @Suppress("WrongConstant")
                        info.flags = ext.sampleFlags
                        muxer.writeSampleData(outTrack, buffer, info)
                    }
                    ext.advance()
                }
                timeOffsetUs += maxClipPts + 33_333L
                ext.release()
            }
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
        return destMp4
    }

    fun muxVideoWithAudio(
        context: Context,
        videoUri: Uri,
        audioUri: Uri,
        destMp4: File
    ): File {
        val vExt = MediaExtractor().apply { setDataSource(context, videoUri, null) }
        val aExt = MediaExtractor().apply { setDataSource(context, audioUri, null) }
        val muxer = MediaMuxer(destMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            var vIn = -1
            var vOut = -1
            var vDurationUs = Long.MAX_VALUE
            for (i in 0 until vExt.trackCount) {
                val fmt = vExt.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && vIn < 0) {
                    vIn = i
                    vOut = muxer.addTrack(fmt)
                    vExt.selectTrack(i)
                    if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        vDurationUs = fmt.getLong(MediaFormat.KEY_DURATION)
                    }
                }
            }
            var aIn = -1
            var aOut = -1
            for (i in 0 until aExt.trackCount) {
                val fmt = aExt.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/") && aIn < 0) {
                    aIn = i
                    aOut = muxer.addTrack(fmt)
                    aExt.selectTrack(i)
                }
            }
            require(vOut >= 0 && aOut >= 0) { "Track video atau audio tidak ditemukan" }
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = vExt.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = vExt.sampleTime.coerceAtLeast(0L)
                @Suppress("WrongConstant")
                info.flags = vExt.sampleFlags
                muxer.writeSampleData(vOut, buffer, info)
                vExt.advance()
            }
            while (true) {
                val pts = aExt.sampleTime
                if (pts > vDurationUs) break
                val size = aExt.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = pts.coerceAtLeast(0L)
                @Suppress("WrongConstant")
                info.flags = aExt.sampleFlags
                muxer.writeSampleData(aOut, buffer, info)
                aExt.advance()
            }
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            vExt.release()
            aExt.release()
        }
        return destMp4
    }

    fun extractFramesZip(
        context: Context,
        uri: Uri,
        destZip: File,
        intervalSec: Int,
        maxFrames: Int
    ): Pair<File, Int> {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
        val stepMs = (intervalSec.coerceAtLeast(1) * 1000L)
        var count = 0
        try {
            ZipOutputStream(FileOutputStream(destZip)).use { zos ->
                var tMs = 0L
                while (tMs <= durationMs && count < maxFrames) {
                    val frame = mmr.getFrameAtTime(tMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        count++
                        zos.putNextEntry(ZipEntry("frame_${String.format("%03d", count)}.png"))
                        frame.compress(Bitmap.CompressFormat.PNG, 95, zos)
                        zos.closeEntry()
                        frame.recycle()
                    }
                    tMs += stepMs
                }
            }
        } finally {
            mmr.release()
        }
        return destZip to count
    }

    // Pure calculation helpers (unit-testable)
    fun cropRatioValue(ratioLabel: String): Float = when (ratioLabel) {
        "1:1" -> 1f
        "16:9" -> 16f / 9f
        "9:16" -> 9f / 16f
        "4:3" -> 4f / 3f
        "3:4" -> 3f / 4f
        else -> 1f
    }

    fun cropDimensions(width: Int, height: Int, ratioLabel: String): Pair<Int, Int> {
        val targetRatio = cropRatioValue(ratioLabel)
        val srcRatio = width.toFloat() / height.coerceAtLeast(1).toFloat()
        val cropW: Int
        val cropH: Int
        if (srcRatio > targetRatio) {
            cropH = height
            cropW = (cropH * targetRatio).roundToInt().coerceIn(1, width)
        } else {
            cropW = width
            cropH = (cropW / targetRatio).roundToInt().coerceIn(1, height)
        }
        // ensure even dimensions for video encoders
        return cropW - (cropW % 2) to cropH - (cropH % 2)
    }

    fun calculateCropDimensions(width: Int, height: Int, ratio: String): Pair<Int, Int> =
        cropDimensions(width, height, ratio)

    fun cropSize(width: Int, height: Int, ratio: String): Pair<Int, Int> =
        cropDimensions(width, height, ratio)

    fun cropWidth(width: Int, height: Int, ratio: String): Int = cropDimensions(width, height, ratio).first
    fun cropHeight(width: Int, height: Int, ratio: String): Int = cropDimensions(width, height, ratio).second

    fun targetBitrateKbps(sourceBitrateBps: Long, crf: Int): Int {
        val baseKbps = (sourceBitrateBps / 1000L).toInt().coerceAtLeast(500)
        val factor = Math.pow(2.0, (24 - crf) / 6.0)
        return (baseKbps * factor).toInt().coerceIn(200, 20000)
    }

    fun calculateBitrate(sourceBitrateBps: Long, crf: Int): Int =
        targetBitrateKbps(sourceBitrateBps, crf)

    fun bitrateForResolution(height: Int, crf: Int = 24): Int {
        val base = when {
            height >= 1080 -> 8000
            height >= 720 -> 5000
            height >= 480 -> 2500
            else -> 1000
        }
        val factor = Math.pow(2.0, (24 - crf) / 6.0)
        return (base * factor).toInt().coerceIn(200, 20000)
    }

    fun setptsFilter(speed: Float): String = "setpts=PTS/${speed}"
    fun setpts(speed: Float): String = setptsFilter(speed)
    fun speedSetpts(speed: Float): String = setptsFilter(speed)
    fun videoSetpts(speed: Float): String = setptsFilter(speed)
    fun setptsFactor(speed: Float): Float = 1f / speed.coerceAtLeast(0.001f)

    fun atempoFilter(speed: Float): String {
        // atempo valid range 0.5..2.0; chain for extremes
        val factors = mutableListOf<Float>()
        var remaining = speed
        while (remaining > 2.0f) {
            factors.add(2.0f)
            remaining /= 2.0f
        }
        while (remaining < 0.5f) {
            factors.add(0.5f)
            remaining /= 0.5f
        }
        factors.add(remaining.coerceIn(0.5f, 2.0f))
        return "atempo=" + factors.joinToString(",")
    }
    fun audioAtempo(speed: Float): String = atempoFilter(speed)
}

// Alias object for discoverability by tests
object VideoCalculations {
    fun cropDimensions(width: Int, height: Int, ratio: String) = VideoEngine.cropDimensions(width, height, ratio)
    fun calculateCropDimensions(width: Int, height: Int, ratio: String) = VideoEngine.calculateCropDimensions(width, height, ratio)
    fun cropSize(width: Int, height: Int, ratio: String) = VideoEngine.cropSize(width, height, ratio)
    fun cropWidth(width: Int, height: Int, ratio: String) = VideoEngine.cropWidth(width, height, ratio)
    fun cropHeight(width: Int, height: Int, ratio: String) = VideoEngine.cropHeight(width, height, ratio)
    fun targetBitrateKbps(sourceBitrateBps: Long, crf: Int) = VideoEngine.targetBitrateKbps(sourceBitrateBps, crf)
    fun calculateBitrate(sourceBitrateBps: Long, crf: Int) = VideoEngine.calculateBitrate(sourceBitrateBps, crf)
    fun bitrateForResolution(height: Int, crf: Int = 24) = VideoEngine.bitrateForResolution(height, crf)
    fun setptsFilter(speed: Float) = VideoEngine.setptsFilter(speed)
    fun setpts(speed: Float) = VideoEngine.setpts(speed)
    fun setptsFactor(speed: Float) = VideoEngine.setptsFactor(speed)
    fun atempoFilter(speed: Float) = VideoEngine.atempoFilter(speed)
}

object VideoMath {
    fun cropDimensions(width: Int, height: Int, ratio: String) = VideoEngine.cropDimensions(width, height, ratio)
    fun calculateCropDimensions(width: Int, height: Int, ratio: String) = VideoEngine.calculateCropDimensions(width, height, ratio)
    fun cropSize(width: Int, height: Int, ratio: String) = VideoEngine.cropSize(width, height, ratio)
    fun cropWidth(width: Int, height: Int, ratio: String) = VideoEngine.cropWidth(width, height, ratio)
    fun cropHeight(width: Int, height: Int, ratio: String) = VideoEngine.cropHeight(width, height, ratio)
    fun targetBitrateKbps(sourceBitrateBps: Long, crf: Int) = VideoEngine.targetBitrateKbps(sourceBitrateBps, crf)
    fun calculateBitrate(sourceBitrateBps: Long, crf: Int) = VideoEngine.calculateBitrate(sourceBitrateBps, crf)
    fun bitrateForResolution(height: Int, crf: Int = 24) = VideoEngine.bitrateForResolution(height, crf)
    fun setptsFilter(speed: Float) = VideoEngine.setptsFilter(speed)
    fun setpts(speed: Float) = VideoEngine.setpts(speed)
    fun setptsFactor(speed: Float) = VideoEngine.setptsFactor(speed)
    fun atempoFilter(speed: Float) = VideoEngine.atempoFilter(speed)
}

object CropCalculator {
    fun cropDimensions(width: Int, height: Int, ratio: String) = VideoEngine.cropDimensions(width, height, ratio)
    fun calculateCropDimensions(width: Int, height: Int, ratio: String) = VideoEngine.calculateCropDimensions(width, height, ratio)
    fun cropSize(width: Int, height: Int, ratio: String) = VideoEngine.cropSize(width, height, ratio)
    fun cropWidth(width: Int, height: Int, ratio: String) = VideoEngine.cropWidth(width, height, ratio)
    fun cropHeight(width: Int, height: Int, ratio: String) = VideoEngine.cropHeight(width, height, ratio)
}

object BitrateCalculator {
    fun targetBitrateKbps(sourceBitrateBps: Long, crf: Int) = VideoEngine.targetBitrateKbps(sourceBitrateBps, crf)
    fun calculateBitrate(sourceBitrateBps: Long, crf: Int) = VideoEngine.calculateBitrate(sourceBitrateBps, crf)
    fun bitrateForResolution(height: Int, crf: Int = 24) = VideoEngine.bitrateForResolution(height, crf)
}

object SetptsCalculator {
    fun setptsFilter(speed: Float) = VideoEngine.setptsFilter(speed)
    fun setpts(speed: Float) = VideoEngine.setpts(speed)
    fun setptsFactor(speed: Float) = VideoEngine.setptsFactor(speed)
    fun atempoFilter(speed: Float) = VideoEngine.atempoFilter(speed)
}
