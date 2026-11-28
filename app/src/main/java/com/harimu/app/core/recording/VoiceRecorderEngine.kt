package com.harimu.app.core.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin

/**
 * Perekam suara PCM + FFT 40 pita (CORE-06).
 *
 * Mendukung ekspor langsung WAV (streaming + header ukuran diperbarui saat selesai),
 * AAC (M4A via MediaCodec + MediaMuxer), serta Opus (WebM API 29+).
 */
class VoiceRecorderEngine(private val context: Context) {

    enum class Format(val extension: String, val mime: String, val label: String) {
        WAV("wav", "audio/wav", "WAV (Lossless)"),
        AAC("m4a", "audio/mp4", "AAC (M4A)"),
        OPUS("webm", "audio/webm", "Opus (WebM)")
    }

    data class Config(
        val sampleRate: Int = 44_100,
        val channels: Int = 1,
        val format: Format = Format.WAV,
        val bitrateBps: Int = 128_000
    )

    interface Listener {
        fun onSpectrum(bands: List<Float>, elapsedMs: Long)
        fun onFinished(file: File, durationMs: Long)
        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val paused = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)

    @Volatile
    private var running = false

    val isRunning: Boolean get() = running

    fun start(config: Config, listener: Listener): File {
        require(!running) { "Perekaman suara sudah berjalan" }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            error("Izin mikrofon diperlukan untuk merekam suara")
        }
        if (config.format == Format.OPUS && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("Format WebM Opus membutuhkan Android 10 (API 29)")
        }

        val file = File(
            context.filesDir,
            "recordings/voice_${System.currentTimeMillis()}.${config.format.extension}"
        ).apply { parentFile?.mkdirs() }

        running = true
        paused.set(false)
        stopRequested.set(false)
        job = scope.launch { recordLoop(config, file, listener) }
        return file
    }

    fun pause() {
        paused.set(true)
    }

    fun resume() {
        paused.set(false)
    }

    fun stop() {
        stopRequested.set(true)
    }

    fun release() {
        stop()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordLoop(config: Config, file: File, listener: Listener) {
        val channelConfig = if (config.channels >= 2) {
            AudioFormat.CHANNEL_IN_STEREO
        } else {
            AudioFormat.CHANNEL_IN_MONO
        }
        val minBuf = AudioRecord.getMinBufferSize(
            config.sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        var record: AudioRecord? = null
        var wavRaf: RandomAccessFile? = null
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false
        var activeMs = 0L
        var lastTick = System.currentTimeMillis()
        var totalPcmBytes = 0L
        var success = false

        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            require(record.state == AudioRecord.STATE_INITIALIZED) {
                "Gagal menginisialisasi mikrofon"
            }

            if (config.format == Format.WAV) {
                wavRaf = RandomAccessFile(file, "rw").apply {
                    write(ByteArray(44))
                }
            } else {
                val mime = if (config.format == Format.OPUS) "audio/opus" else "audio/mp4a-latm"
                val format = MediaFormat.createAudioFormat(mime, config.sampleRate, config.channels).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps)
                    if (mime == "audio/mp4a-latm") {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    }
                }
                codec = MediaCodec.createEncoderByType(mime).apply {
                    configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
                val muxerFormat = if (config.format == Format.OPUS) {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                } else {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }
                muxer = MediaMuxer(file.absolutePath, muxerFormat)
            }

            record.startRecording()
            val pcmShorts = ShortArray(2048)
            val byteBuf = ByteArray(pcmShorts.size * 2)
            var lastSpectrumAt = 0L
            lastTick = System.currentTimeMillis()

            while (currentCoroutineContext().isActive && !stopRequested.get()) {
                val now = System.currentTimeMillis()
                val delta = (now - lastTick).coerceAtLeast(0L)
                lastTick = now

                val read = record.read(pcmShorts, 0, pcmShorts.size)
                if (read <= 0) continue

                if (paused.get()) {
                    if (now - lastSpectrumAt >= 80L) {
                        lastSpectrumAt = now
                        listener.onSpectrum(List(40) { 0f }, activeMs)
                    }
                    continue
                }

                activeMs += delta
                var bi = 0
                for (i in 0 until read) {
                    val v = pcmShorts[i].toInt()
                    byteBuf[bi++] = (v and 0xFF).toByte()
                    byteBuf[bi++] = ((v shr 8) and 0xFF).toByte()
                }

                if (wavRaf != null) {
                    wavRaf.write(byteBuf, 0, bi)
                    totalPcmBytes += bi
                } else if (codec != null && muxer != null) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ib = codec.getInputBuffer(inIdx)
                        if (ib != null) {
                            ib.clear()
                            ib.put(byteBuf, 0, bi)
                            codec.queueInputBuffer(inIdx, 0, bi, activeMs * 1000L, 0)
                        }
                    }
                    val drained = drainEncoded(codec, muxer, trackIndex, muxerStarted)
                    trackIndex = drained.first
                    muxerStarted = drained.second
                }

                if (now - lastSpectrumAt >= 55L) {
                    lastSpectrumAt = now
                    listener.onSpectrum(compute40BandSpectrum(pcmShorts, read), activeMs)
                }
            }

            if (wavRaf != null) {
                writeWavHeader(wavRaf, totalPcmBytes, config.sampleRate, config.channels)
            } else if (codec != null && muxer != null) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    codec.queueInputBuffer(
                        inIdx,
                        0,
                        0,
                        activeMs * 1000L,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
                val drained = drainEncoded(codec, muxer, trackIndex, muxerStarted, waitEos = true)
                muxerStarted = drained.second
                if (muxerStarted) {
                    runCatching { muxer.stop() }
                }
            }
            success = true
        } catch (t: Throwable) {
            listener.onError(t.message ?: "Perekaman suara gagal")
        } finally {
            running = false
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { wavRaf?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.release() }
            if (success) {
                listener.onFinished(file, activeMs)
            } else {
                file.delete()
            }
        }
    }

    private fun drainEncoded(
        codec: MediaCodec,
        muxer: MediaMuxer,
        initialTrack: Int,
        initialStarted: Boolean,
        waitEos: Boolean = false
    ): Pair<Int, Boolean> {
        var track = initialTrack
        var started = initialStarted
        val info = MediaCodec.BufferInfo()
        val timeout = if (waitEos) 10_000L else 0L
        while (true) {
            val outIdx = codec.dequeueOutputBuffer(info, timeout)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> return track to started
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (track < 0) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }
                }
                outIdx >= 0 -> {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (started && track >= 0 && buf != null && info.size > 0 &&
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        runCatching { muxer.writeSampleData(track, buf, info) }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        return track to started
                    }
                }
            }
        }
    }

    private fun writeWavHeader(
        raf: RandomAccessFile,
        totalAudioBytes: Long,
        sampleRate: Int,
        channels: Int
    ) {
        val byteRate = sampleRate * channels * 2
        val totalDataLen = totalAudioBytes + 36
        raf.seek(0)
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalDataLen.toInt())
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        header[20] = 1; header[21] = 0 // PCM
        header[22] = channels.toByte(); header[23] = 0
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, byteRate)
        header[32] = (channels * 2).toByte(); header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, totalAudioBytes.toInt())
        raf.write(header, 0, 44)
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    companion object {
        fun compute40BandSpectrum(samples: ShortArray, count: Int): List<Float> {
            val n = 256
            val used = count.coerceAtMost(n)
            if (used <= 0) return List(40) { 0f }
            val re = DoubleArray(n)
            val im = DoubleArray(n)
            for (i in 0 until used) {
                val window = 0.5 * (1.0 - cos(2.0 * PI * i / (used - 1).coerceAtLeast(1)))
                re[i] = (samples[i] / 32768.0) * window
            }
            fftRadix2(re, im)
            val half = n / 2
            return List(40) { band ->
                val start = (band * half) / 40
                val end = (((band + 1) * half) / 40).coerceAtLeast(start + 1).coerceAtMost(half)
                var sum = 0.0
                for (k in start until end) {
                    sum += hypot(re[k], im[k])
                }
                val avg = sum / (end - start).coerceAtLeast(1)
                val db = 20.0 * log10(avg.coerceAtLeast(1e-5))
                ((db + 60.0) / 60.0).toFloat().coerceIn(0.03f, 1f)
            }
        }

        private fun fftRadix2(re: DoubleArray, im: DoubleArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while ((j and bit) != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var len = 2
            while (len <= n) {
                val ang = -2.0 * PI / len
                val wlenRe = cos(ang)
                val wlenIm = sin(ang)
                var i = 0
                while (i < n) {
                    var wRe = 1.0
                    var wIm = 0.0
                    for (k in 0 until len / 2) {
                        val uRe = re[i + k]
                        val uIm = im[i + k]
                        val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                        val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                        re[i + k] = uRe + vRe
                        im[i + k] = uIm + vIm
                        re[i + k + len / 2] = uRe - vRe
                        im[i + k + len / 2] = uIm - vIm
                        val nextWRe = wRe * wlenRe - wIm * wlenIm
                        wIm = wRe * wlenIm + wIm * wlenRe
                        wRe = nextWRe
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
