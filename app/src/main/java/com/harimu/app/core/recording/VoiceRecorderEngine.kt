package com.harimu.app.core.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Perekam suara (FASE 14, CORE-06).
 *
 * Fitur: AudioRecord 44,1/48 kHz, jeda/lanjut, spektrum FFT live, dan tiga format keluaran:
 * - **WAV** — PCM 16-bit ditulis streaming (header dipatch di akhir, memori konstan).
 * - **AAC** — MediaCodec `audio/mp4a-latm` + MediaMuxer MP4 (ekstensi `.m4a`).
 * - **Opus/WebM** — MediaCodec `audio/opus` + MediaMuxer WEBM; hanya Android 10+ (API 29)
 *   karena muxer WebM belum mendukung Opus sebelum itu.
 *
 * Penanganan fokus audio: meminta `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`; kehilangan fokus
 * (termasuk saat panggilan masuk) otomatis menjeda rekaman dan memberi tahu listener. Deteksi
 * panggilan **tidak** membaca status telepon (tidak perlu izin READ_PHONE_STATE) — audit Android
 * menyarankan pendekatan fokus audio, dan batas ini dicatat di DECISIONS.
 */
class VoiceRecorderEngine(private val context: Context) {

    enum class OutputFormat(val extension: String, val mime: String) {
        WAV("wav", "audio/wav"),
        AAC("m4a", "audio/mp4"),
        OPUS_WEBM("webm", "audio/webm")
    }

    data class Options(
        val sampleRate: Int = 44_100,
        val format: OutputFormat = OutputFormat.WAV
    )

    data class Session(
        val output: File,
        val format: OutputFormat,
        val elapsedMs: Long,
        val paused: Boolean
    )

    interface Listener {
        fun onSpectrum(bands: FloatArray, elapsedMs: Long)
        fun onPaused(reason: String)
        fun onFinished(session: Session)
        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var recorder: AudioRecord? = null
    private var focusRequest: AudioFocusRequest? = null
    @Volatile private var paused = false
    @Volatile private var running = false
    @Volatile private var stopRequested = false

    val isRunning: Boolean get() = running
    val isPaused: Boolean get() = paused

    @SuppressLint("MissingPermission")
    fun start(options: Options, listener: Listener): File {
        require(!running) { "Perekaman sudah berjalan" }
        require(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) { "Izin mikrofon belum diberikan" }
        if (options.format == OutputFormat.OPUS_WEBM && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("Opus/WebM membutuhkan Android 10 (API 29) atau lebih baru")
        }

        val output = File(
            context.filesDir,
            "recordings/rec_${System.currentTimeMillis()}.${options.format.extension}"
        ).apply { parentFile?.mkdirs() }

        val minBuffer = AudioRecord.getMinBufferSize(
            options.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            options.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        require(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord gagal diinisialisasi" }
        recorder = record
        running = true
        paused = false
        stopRequested = false
        requestFocus()

        job = scope.launch {
            val startedAt = System.currentTimeMillis()
            var pausedTotal = 0L
            var pauseStartedAt = 0L
            val encoder = when (options.format) {
                OutputFormat.WAV -> WavStreamWriter(output, options.sampleRate).let { Encoder.Wav(it) }
                OutputFormat.AAC -> Encoder.Muxed(
                    AudioMuxerEncoder(output, "audio/mp4a-latm", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, options.sampleRate)
                )
                OutputFormat.OPUS_WEBM -> Encoder.Muxed(
                    AudioMuxerEncoder(output, "audio/opus", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, options.sampleRate)
                )
            }

            val buffer = ShortArray(minBuffer / 2)
            val spectrumBuffer = ShortArray(SpectrumAnalyzer.FFT_SIZE)
            var spectrumFill = 0
            try {
                record.startRecording()
                while (isActive && !stopRequested) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    val now = System.currentTimeMillis()
                    if (paused) {
                        if (pauseStartedAt == 0L) pauseStartedAt = now
                        continue
                    }
                    if (pauseStartedAt != 0L) {
                        pausedTotal += now - pauseStartedAt
                        pauseStartedAt = 0L
                    }
                    encoder.write(buffer, read)

                    // Isi jendela FFT (bila sampel lebih banyak dari jendela, ambil ekor terbaru).
                    if (read >= spectrumBuffer.size) {
                        System.arraycopy(buffer, read - spectrumBuffer.size, spectrumBuffer, 0, spectrumBuffer.size)
                        spectrumFill = spectrumBuffer.size
                    } else {
                        val keep = minOf(spectrumBuffer.size - read, spectrumFill)
                        if (keep > 0) System.arraycopy(spectrumBuffer, spectrumFill - keep, spectrumBuffer, 0, keep)
                        System.arraycopy(buffer, 0, spectrumBuffer, keep, read)
                        spectrumFill = keep + read
                    }
                    if (spectrumFill >= SpectrumAnalyzer.FFT_SIZE) {
                        listener.onSpectrum(
                            SpectrumAnalyzer.analyze(spectrumBuffer, options.sampleRate),
                            now - startedAt - pausedTotal
                        )
                    }
                }
            } catch (error: Exception) {
                listener.onError(error.message ?: "Perekaman gagal")
            } finally {
                runCatching { record.stop() }
                runCatching { record.release() }
                recorder = null
                val finishError = runCatching { encoder.close() }.exceptionOrNull()
                abandonFocus()
                running = false
                if (finishError != null) {
                    listener.onError(finishError.message ?: "Gagal menulis berkas audio")
                } else {
                    listener.onFinished(
                        Session(
                            output = output,
                            format = options.format,
                            elapsedMs = System.currentTimeMillis() - startedAt - pausedTotal,
                            paused = false
                        )
                    )
                }
            }
        }
        return output
    }

    fun pause(reason: String = "manual") {
        if (!running || paused) return
        paused = true
        if (reason != "manual") listenerPaused?.invoke(reason)
    }

    fun resume() {
        if (!running) return
        paused = false
    }

    fun stop() {
        if (!running) return
        stopRequested = true
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private var listenerPaused: ((String) -> Unit)? = null

    fun setPauseListener(listener: (String) -> Unit) {
        listenerPaused = listener
    }

    // --------------------------------------------------------------- fokus audio

    private fun requestFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pause("focus")
                }
            }
            .build()
        focusRequest = request
        runCatching { audioManager.requestAudioFocus(request) }
    }

    private fun abandonFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    // --------------------------------------------------------------- encoders

    private sealed interface Encoder {
        fun write(samples: ShortArray, count: Int)
        fun close()

        class Wav(private val writer: WavStreamWriter) : Encoder {
            override fun write(samples: ShortArray, count: Int) = writer.write(samples, count)
            override fun close() = writer.close()
        }

        class Muxed(private val encoder: AudioMuxerEncoder) : Encoder {
            override fun write(samples: ShortArray, count: Int) = encoder.encode(samples, count)
            override fun close() = encoder.close()
        }
    }
}

/** WAV 16-bit mono writer dengan patch header di akhir (memori konstan). */
class WavStreamWriter(file: File, private val sampleRate: Int, private val channels: Int = 1) {
    private val handle = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        handle.setLength(0)
        handle.write(ByteArray(44))
    }

    fun write(samples: ShortArray, count: Int) {
        val bytes = ByteArray(count * 2)
        var index = 0
        for (i in 0 until count) {
            val value = samples[i].toInt()
            bytes[index++] = (value and 0xFF).toByte()
            bytes[index++] = ((value shr 8) and 0xFF).toByte()
        }
        handle.write(bytes)
        dataBytes += bytes.size
    }

    fun close() {
        val byteRate = sampleRate * channels * 2
        handle.seek(0)
        handle.write("RIFF".toByteArray(Charsets.US_ASCII))
        handle.write(intLe((36 + dataBytes).toInt()))
        handle.write("WAVE".toByteArray(Charsets.US_ASCII))
        handle.write("fmt ".toByteArray(Charsets.US_ASCII))
        handle.write(intLe(16))
        handle.write(shortLe(1))
        handle.write(shortLe(channels))
        handle.write(intLe(sampleRate))
        handle.write(intLe(byteRate))
        handle.write(shortLe(channels * 2))
        handle.write(shortLe(16))
        handle.write("data".toByteArray(Charsets.US_ASCII))
        handle.write(intLe(dataBytes.toInt()))
        handle.close()
    }

    private fun intLe(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun shortLe(value: Int) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
}

/**
 * Encoder audio berbasis MediaCodec + MediaMuxer untuk AAC (MP4/M4A) dan Opus (WebM).
 * Input selalu PCM 16-bit mono pada [sampleRate].
 */
class AudioMuxerEncoder(
    private val output: File,
    private val mime: String,
    private val muxerFormat: Int,
    private val sampleRate: Int
) {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(mime)
    private val muxer: MediaMuxer = MediaMuxer(output.absolutePath, muxerFormat)
    private var trackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()

    init {
        val format = MediaFormat.createAudioFormat(mime, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, if (mime == "audio/opus") 96_000 else 128_000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun encode(samples: ShortArray, count: Int) {
        val bytes = ByteArray(count * 2)
        var index = 0
        for (i in 0 until count) {
            val value = samples[i].toInt()
            bytes[index++] = (value and 0xFF).toByte()
            bytes[index++] = ((value shr 8) and 0xFF).toByte()
        }
        var offset = 0
        while (offset < bytes.size) {
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                inputBuffer.clear()
                val chunk = minOf(inputBuffer.remaining(), bytes.size - offset)
                inputBuffer.put(bytes, offset, chunk)
                val pts = totalSamples * 1_000_000L / sampleRate
                totalSamples += chunk / 2
                codec.queueInputBuffer(inputIndex, 0, chunk, pts, 0)
                offset += chunk
            }
            drain(false)
        }
    }

    private var totalSamples = 0L

    private fun presentationTimeUs(): Long = totalSamples * 1_000_000L / sampleRate

    private fun drain(endOfStream: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && bufferInfo.size > 0 && muxerStarted && trackIndex >= 0) {
                        buffer.position(bufferInfo.offset)
                        buffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (endOfStream && bufferInfo.size > 0) return
                }
            }
        }
    }

    fun close() {
        runCatching {
            codec.queueInputBuffer(codec.dequeueInputBuffer(10_000), 0, 0, presentationTimeUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        runCatching { drain(true) }
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { if (muxerStarted) muxer.stop() }
        runCatching { muxer.release() }
    }
}
