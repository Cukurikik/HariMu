package com.harimu.app.core.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class WaveformData(
    val peaks: List<Float>,
    val durationMs: Long,
    val sampleRateHz: Int,
    val channels: Int
)

object AudioWaveformExtractor {

    private const val DEFAULT_BAR_COUNT = 96
    private val memoryCache = LruCache<String, WaveformData>(16)

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    fun buildCacheKey(uri: Uri, sizeBytes: Long, barCount: Int): String {
        val raw = "${uri}|${sizeBytes}|$barCount"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    suspend fun extractPeaks(
        context: Context,
        uri: Uri,
        barCount: Int = DEFAULT_BAR_COUNT
    ): WaveformData = withContext(Dispatchers.IO) {
        val safeBars = barCount.coerceIn(24, 180)
        val sizeBytes = VideoToolMath.resolveInputSizeBytes(context, uri)
        val cacheKey = buildCacheKey(uri, sizeBytes, safeBars)

        memoryCache.get(cacheKey)?.let { return@withContext it }
        readDiskCache(context, cacheKey)?.let {
            memoryCache.put(cacheKey, it)
            return@withContext it
        }

        val probe = MediaProbe.probe(context, uri)
        currentCoroutineContext().ensureActive()

        val extracted = runCatching {
            decodeStreamingPeaks(context, uri, probe, safeBars)
        }.getOrElse {
            // Fallback for plain PCM WAV or unreadable codec formats
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    extractPeaksFromWavStream(stream, safeBars)
                }
            }.getOrNull() ?: WaveformData(
                peaks = List(safeBars) { 0.15f },
                durationMs = probe.durationMs,
                sampleRateHz = probe.audioSampleRateHz.takeIf { it > 0 } ?: 44100,
                channels = probe.audioChannels.takeIf { it > 0 } ?: 2
            )
        }

        memoryCache.put(cacheKey, extracted)
        writeDiskCache(context, cacheKey, extracted)
        extracted
    }

    private fun decodeStreamingPeaks(
        context: Context,
        uri: Uri,
        probe: MediaProbeResult,
        barCount: Int
    ): WaveformData {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var audioTrack = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrack = i
                format = fmt
                break
            }
        }
        require(audioTrack >= 0 && format != null) { "Audio track missing" }
        extractor.selectTrack(audioTrack)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        val formatDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val totalDurationUs = max(formatDurationUs, probe.durationMs * 1000L)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        val windowPeaks = ArrayList<Float>(512)
        val bucketPeaks = FloatArray(barCount)
        val samplesPerWindow = max(512, (sampleRate * channels) / 20)
        var sampleCounter = 0
        var currentWindowPeak = 0
        var inputDone = false
        var outputDone = false
        var maxPtsUs = 0L

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(8_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 8_000)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            maxPtsUs = max(maxPtsUs, info.presentationTimeUs)
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val shortBuf = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val count = shortBuf.remaining()
                            var bufferPeak = 0
                            // Stride by 2 or 4 when chunks are dense to stay fast even for long tracks
                            val stride = max(1, channels)
                            var idx = 0
                            while (idx < count) {
                                val amp = abs(shortBuf.get(idx).toInt())
                                if (amp > bufferPeak) bufferPeak = amp
                                if (amp > currentWindowPeak) currentWindowPeak = amp
                                sampleCounter += stride
                                if (sampleCounter >= samplesPerWindow) {
                                    windowPeaks.add(currentWindowPeak / 32768f)
                                    currentWindowPeak = 0
                                    sampleCounter = 0
                                }
                                idx += stride
                            }
                            if (totalDurationUs > 0L) {
                                val barIdx = ((info.presentationTimeUs.toDouble() / totalDurationUs.toDouble()) * barCount)
                                    .toInt()
                                    .coerceIn(0, barCount - 1)
                                val norm = bufferPeak / 32768f
                                if (norm > bucketPeaks[barIdx]) bucketPeaks[barIdx] = norm
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            extractor.release()
        }

        if (currentWindowPeak > 0) {
            windowPeaks.add(currentWindowPeak / 32768f)
        }

        val rawBars = if (windowPeaks.size >= barCount / 2) {
            normalizeAndResamplePeaks(windowPeaks, barCount)
        } else {
            normalizeAndResamplePeaks(bucketPeaks.toList(), barCount)
        }

        val durationMs = max(probe.durationMs, max(totalDurationUs / 1000L, maxPtsUs / 1000L))
        return WaveformData(
            peaks = rawBars,
            durationMs = durationMs,
            sampleRateHz = sampleRate,
            channels = channels.coerceIn(1, 2)
        )
    }

    fun extractPeaksFromWavStream(input: InputStream, barCount: Int = DEFAULT_BAR_COUNT): WaveformData? {
        val header = ByteArray(44)
        var readTotal = 0
        while (readTotal < 44) {
            val n = input.read(header, readTotal, 44 - readTotal)
            if (n <= 0) break
            readTotal += n
        }
        if (readTotal < 44) return null
        if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(header, 8, 4, Charsets.US_ASCII) != "WAVE"
        ) {
            return null
        }
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val channels = bb.getShort(22).toInt().coerceIn(1, 2)
        val sampleRate = bb.getInt(24).coerceAtLeast(8000)
        val bitsPerSample = bb.getShort(34).toInt().coerceAtLeast(8)
        val dataBytes = bb.getInt(40).coerceAtLeast(0)

        val safeBars = barCount.coerceIn(16, 180)
        val bytesPerFrame = max(1, channels * (bitsPerSample / 8))
        val totalFrames = max(1, dataBytes / bytesPerFrame)
        val durationMs = ((totalFrames.toDouble() / sampleRate.toDouble()) * 1000.0).toLong()
        val framesPerBar = max(1, totalFrames / safeBars)

        val peaks = FloatArray(safeBars)
        val buf = ByteArray(8192)
        var frameIndex = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            val aligned = n - (n % bytesPerFrame)
            var i = 0
            while (i + 1 < aligned) {
                val sample = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
                val amp = abs(sample) / 32768f
                val barIdx = (frameIndex / framesPerBar).coerceIn(0, safeBars - 1)
                if (amp > peaks[barIdx]) peaks[barIdx] = amp
                frameIndex += 1
                i += bytesPerFrame
            }
        }
        return WaveformData(
            peaks = normalizeAndResamplePeaks(peaks.toList(), safeBars),
            durationMs = durationMs,
            sampleRateHz = sampleRate,
            channels = channels
        )
    }

    fun normalizeAndResamplePeaks(raw: List<Float>, targetCount: Int): List<Float> {
        val count = targetCount.coerceAtLeast(8)
        if (raw.isEmpty()) return List(count) { 0.15f }
        val resampled = FloatArray(count)
        if (raw.size == count) {
            for (i in 0 until count) resampled[i] = raw[i]
        } else {
            val step = raw.size.toFloat() / count.toFloat()
            for (i in 0 until count) {
                val start = (i * step).toInt().coerceIn(0, raw.size - 1)
                val end = min(raw.size, max(start + 1, ((i + 1) * step).toInt()))
                var maxVal = 0f
                for (j in start until end) {
                    if (raw[j] > maxVal) maxVal = raw[j]
                }
                resampled[i] = maxVal
            }
        }
        val peakMax = resampled.maxOrNull()?.takeIf { it > 0.001f } ?: 1f
        return resampled.map { value ->
            ((value / peakMax) * 0.92f + 0.08f).coerceIn(0.08f, 1.0f)
        }
    }

    private fun cacheDir(context: Context): File = File(context.cacheDir, "waveforms").apply {
        if (!exists()) mkdirs()
    }

    private fun readDiskCache(context: Context, key: String): WaveformData? {
        val file = File(cacheDir(context), "$key.peaks")
        if (!file.exists()) return null
        return runCatching {
            val lines = file.readLines()
            if (lines.size < 2) return null
            val meta = lines[0].split(',')
            val dur = meta[0].toLong()
            val sr = meta[1].toInt()
            val ch = meta[2].toInt()
            val peaks = lines[1].split(',').mapNotNull { it.toFloatOrNull() }
            if (peaks.isEmpty()) null else WaveformData(peaks, dur, sr, ch)
        }.getOrNull()
    }

    private fun writeDiskCache(context: Context, key: String, data: WaveformData) {
        runCatching {
            val file = File(cacheDir(context), "$key.peaks")
            val header = "${data.durationMs},${data.sampleRateHz},${data.channels}"
            val body = data.peaks.joinToString(",") { "%.4f".format(java.util.Locale.US, it) }
            file.writeText("$header\n$body")
        }
    }
}
