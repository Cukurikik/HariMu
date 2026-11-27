package com.harimu.app.core.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import java.io.File
import java.util.Locale

data class MediaProbeResult(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val bitrateBps: Long = 0L,
    val audioChannels: Int = 0,
    val audioSampleRateHz: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val containerFormat: String = "",
    val rotationDegrees: Int = 0,
    val fileSizeBytes: Long = 0L
) {
    val hasVideo: Boolean get() = videoCodec.isNotBlank() || (width > 0 && height > 0)
    val hasAudio: Boolean get() = audioCodec.isNotBlank() || audioChannels > 0
    val isQuarterTurnRotated: Boolean get() = ((kotlin.math.abs(rotationDegrees) % 360) % 180) != 0
    val orientedWidth: Int get() = if (isQuarterTurnRotated && height > 0) height else width
    val orientedHeight: Int get() = if (isQuarterTurnRotated && width > 0) width else height

    fun formatSummary(isEnglish: Boolean): String = buildString {
        val dur = FfmpegProgressParser.formatDurationMs(durationMs)
        val res = if (width > 0 && height > 0) "${width}x${height}" else "-"
        val fpsText = if (fps > 0f) String.format(Locale.US, "%.2f fps", fps) else "-"
        val kbps = if (bitrateBps > 0L) "${bitrateBps / 1000L} kbps" else "-"
        if (isEnglish) {
            appendLine("Container: ${containerFormat.ifBlank { "-" }}")
            appendLine("Duration: $dur | Bitrate: $kbps")
            appendLine("Video: ${videoCodec.ifBlank { "none" }} ($res @ $fpsText)")
            append("Audio: ${audioCodec.ifBlank { "none" }} (${audioChannels}ch, ${audioSampleRateHz} Hz)")
        } else {
            appendLine("Kontainer: ${containerFormat.ifBlank { "-" }}")
            appendLine("Durasi: $dur | Bitrate: $kbps")
            appendLine("Video: ${videoCodec.ifBlank { "tidak ada" }} ($res @ $fpsText)")
            append("Audio: ${audioCodec.ifBlank { "tidak ada" }} (${audioChannels} kanal, ${audioSampleRateHz} Hz)")
        }
    }
}

object MediaProbe {

    fun probe(context: Context, uri: Uri): MediaProbeResult {
        val ffprobeResult = runCatching {
            val path = if (uri.scheme == "file") uri.path else FFmpegKitConfig.getSafParameterForRead(context, uri)
            if (!path.isNullOrBlank()) {
                val session = FFprobeKit.getMediaInformation(path)
                val info = session?.mediaInformation
                if (info != null) {
                    val fileSize = uri.path?.takeIf { uri.scheme == "file" }?.let { File(it).length() } ?: 0L
                    parseMediaInformation(info, fileSize)
                } else null
            } else null
        }.getOrNull()

        val platformResult = probeWithPlatform(context, uri)
        return mergeProbeResults(ffprobeResult, platformResult)
    }

    fun probeFile(file: File): MediaProbeResult {
        val ffprobeResult = runCatching {
            val session = FFprobeKit.getMediaInformation(file.absolutePath)
            session?.mediaInformation?.let { parseMediaInformation(it, file.length()) }
        }.getOrNull()

        val platformResult = probeFileWithPlatform(file)
        return mergeProbeResults(ffprobeResult, platformResult).let {
            if (it.fileSizeBytes <= 0L && file.exists()) it.copy(fileSizeBytes = file.length()) else it
        }
    }

    fun parseMediaInformation(info: MediaInformation, fallbackSizeBytes: Long = 0L): MediaProbeResult {
        val durationMs = ((info.duration?.toDoubleOrNull() ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
        val bitrateBps = info.bitrate?.toLongOrNull() ?: 0L
        val fileSize = info.size?.toLongOrNull() ?: fallbackSizeBytes
        val format = info.format ?: ""

        var width = 0
        var height = 0
        var fps = 0f
        var videoCodec = ""
        var audioCodec = ""
        var audioChannels = 0
        var audioSampleRateHz = 0
        var rotation = 0

        info.streams?.forEach { stream ->
            when (stream.type?.lowercase()) {
                "video" -> if (videoCodec.isBlank()) {
                    videoCodec = stream.codec ?: ""
                    width = (stream.width ?: 0L).toInt()
                    height = (stream.height ?: 0L).toInt()
                    fps = parseFpsFraction(stream.realFrameRate).takeIf { it > 0f }
                        ?: parseFpsFraction(stream.averageFrameRate)
                    rotation = stream.tags?.optString("rotate")?.toIntOrNull() ?: 0
                }
                "audio" -> if (audioCodec.isBlank()) {
                    audioCodec = stream.codec ?: ""
                    audioSampleRateHz = stream.sampleRate?.toIntOrNull() ?: 0
                    audioChannels = parseChannelCount(stream.channelLayout, stream.getNumberProperty("channels"))
                }
            }
        }

        return MediaProbeResult(
            durationMs = durationMs,
            width = width,
            height = height,
            fps = fps,
            bitrateBps = bitrateBps,
            audioChannels = audioChannels,
            audioSampleRateHz = audioSampleRateHz,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            containerFormat = format,
            rotationDegrees = rotation,
            fileSizeBytes = fileSize
        )
    }

    fun parseFpsFraction(raw: String?): Float {
        val clean = raw?.trim() ?: return 0f
        if (clean.isEmpty() || clean == "0/0") return 0f
        if ('/' in clean) {
            val parts = clean.split('/')
            if (parts.size == 2) {
                val num = parts[0].toFloatOrNull() ?: return 0f
                val den = parts[1].toFloatOrNull() ?: return 0f
                if (den > 0f) return num / den
            }
            return 0f
        }
        return clean.toFloatOrNull()?.takeIf { it > 0f } ?: 0f
    }

    fun parseChannelCount(layout: String?, numericChannels: Long?): Int {
        if (numericChannels != null && numericChannels > 0L) return numericChannels.toInt()
        return when (layout?.lowercase()) {
            "mono" -> 1
            "stereo" -> 2
            "2.1" -> 3
            "5.1" -> 6
            "7.1" -> 8
            else -> 0
        }
    }

    private fun mergeProbeResults(primary: MediaProbeResult?, fallback: MediaProbeResult): MediaProbeResult {
        if (primary == null) return fallback
        return MediaProbeResult(
            durationMs = if (primary.durationMs > 0L) primary.durationMs else fallback.durationMs,
            width = if (primary.width > 0) primary.width else fallback.width,
            height = if (primary.height > 0) primary.height else fallback.height,
            fps = if (primary.fps > 0f) primary.fps else fallback.fps,
            bitrateBps = if (primary.bitrateBps > 0L) primary.bitrateBps else fallback.bitrateBps,
            audioChannels = if (primary.audioChannels > 0) primary.audioChannels else fallback.audioChannels,
            audioSampleRateHz = if (primary.audioSampleRateHz > 0) primary.audioSampleRateHz else fallback.audioSampleRateHz,
            videoCodec = primary.videoCodec.ifBlank { fallback.videoCodec },
            audioCodec = primary.audioCodec.ifBlank { fallback.audioCodec },
            containerFormat = primary.containerFormat.ifBlank { fallback.containerFormat },
            rotationDegrees = if (primary.rotationDegrees != 0) primary.rotationDegrees else fallback.rotationDegrees,
            fileSizeBytes = if (primary.fileSizeBytes > 0L) primary.fileSizeBytes else fallback.fileSizeBytes
        )
    }

    private fun probeWithPlatform(context: Context, uri: Uri): MediaProbeResult {
        var durationMs = 0L
        var width = 0
        var height = 0
        var fps = 0f
        var bitrate = 0L
        var rotation = 0
        var audioChannels = 0
        var sampleRate = 0
        var vCodec = ""
        var aCodec = ""
        val container = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: ""
        val sizeBytes = uri.path?.takeIf { uri.scheme == "file" }?.let { File(it).length() } ?: 0L

        val mmr = MediaMetadataRetriever()
        runCatching {
            mmr.setDataSource(context, uri)
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val frameCount = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
                if (frameCount != null && durationMs > 0L) {
                    fps = frameCount / (durationMs / 1000f)
                }
            }
        }
        runCatching { mmr.release() }

        val extractor = MediaExtractor()
        runCatching {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && vCodec.isBlank()) {
                    vCodec = mime
                    if (width == 0 && fmt.containsKey(MediaFormat.KEY_WIDTH)) width = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    if (height == 0 && fmt.containsKey(MediaFormat.KEY_HEIGHT)) height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    if (fps <= 0f && fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = runCatching { fmt.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                            .getOrElse { runCatching { fmt.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(0f) }
                    }
                    if (durationMs == 0L && fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = fmt.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }
                } else if (mime.startsWith("audio/") && aCodec.isBlank()) {
                    aCodec = mime
                    if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        audioChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (durationMs == 0L && fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = fmt.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }
                }
            }
        }
        runCatching { extractor.release() }

        return MediaProbeResult(
            durationMs = durationMs,
            width = width,
            height = height,
            fps = fps,
            bitrateBps = bitrate,
            audioChannels = audioChannels,
            audioSampleRateHz = sampleRate,
            videoCodec = vCodec,
            audioCodec = aCodec,
            containerFormat = container,
            rotationDegrees = rotation,
            fileSizeBytes = sizeBytes
        )
    }

    private fun probeFileWithPlatform(file: File): MediaProbeResult {
        var durationMs = 0L
        var width = 0
        var height = 0
        var fps = 0f
        var bitrate = 0L
        var rotation = 0
        var audioChannels = 0
        var sampleRate = 0
        var vCodec = ""
        var aCodec = ""

        val mmr = MediaMetadataRetriever()
        runCatching {
            mmr.setDataSource(file.absolutePath)
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        }
        runCatching { mmr.release() }

        val extractor = MediaExtractor()
        runCatching {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && vCodec.isBlank()) {
                    vCodec = mime
                    if (width == 0 && fmt.containsKey(MediaFormat.KEY_WIDTH)) width = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    if (height == 0 && fmt.containsKey(MediaFormat.KEY_HEIGHT)) height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    if (fps <= 0f && fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = runCatching { fmt.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                            .getOrElse { runCatching { fmt.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(0f) }
                    }
                    if (durationMs == 0L && fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = fmt.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }
                } else if (mime.startsWith("audio/") && aCodec.isBlank()) {
                    aCodec = mime
                    if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        audioChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (durationMs == 0L && fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = fmt.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }
                }
            }
        }
        runCatching { extractor.release() }

        return MediaProbeResult(
            durationMs = durationMs,
            width = width,
            height = height,
            fps = fps,
            bitrateBps = bitrate,
            audioChannels = audioChannels,
            audioSampleRateHz = sampleRate,
            videoCodec = vCodec,
            audioCodec = aCodec,
            containerFormat = file.extension.lowercase(),
            rotationDegrees = rotation,
            fileSizeBytes = if (file.exists()) file.length() else 0L
        )
    }
}
