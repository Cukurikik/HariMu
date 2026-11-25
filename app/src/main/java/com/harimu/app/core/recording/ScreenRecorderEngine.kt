package com.harimu.app.core.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Perekam layar (FASE 14, CORE-05).
 *
 * Video: MediaProjection → VirtualDisplay → permukaan MediaCodec (H.264 untuk MP4, VP8 untuk WebM)
 * → MediaMuxer. Audio: mikrofon, audio internal (AudioPlaybackCapture, Android 10+), keduanya
 * dicampur rata-rata, atau tanpa audio.
 *
 * Batas jujur:
 * - **MP4** memakai H.264 + AAC. **WebM** memakai VP8 + Opus dan hanya tersedia Android 10+ serta
 *   bergantung codec perangkat; bila tidak ada, start gagal dengan pesan jelas (tanpa fallback
 *   diam-diam ke MP4).
 * - Audio internal hanya menangkap aplikasi yang mengizinkan playback capture (banyak aplikasi
 *   streaming/DRM menolak); bila kosong hasilnya senyap dan itu dilaporkan.
 * - Pencampuran mic+internal memakai rata-rata sederhana tanpa resampling.
 * - Persetujuan MediaProjection diminta **per sesi** (kewajiban Android 14+), ditangani layar.
 * - Overlay tombol memakai izin "tampil di atas aplikasi lain"; bila tidak diizinkan, kontrol
 *   hanya lewat notifikasi.
 */
class ScreenRecorderEngine(private val context: Context) {

    enum class AudioMode { NONE, MIC, INTERNAL, MIC_AND_INTERNAL }
    enum class Container(val extension: String, val mime: String) {
        MP4("mp4", "video/mp4"),
        WEBM("webm", "video/webm")
    }

    data class Config(
        val width: Int = 1280,
        val height: Int = 720,
        val fps: Int = 30,
        val bitrateBps: Int = 6_000_000,
        val audioMode: AudioMode = AudioMode.MIC,
        val container: Container = Container.MP4
    )

    interface Listener {
        fun onFinished(file: File, durationMs: Long)
        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val paused = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var running = false
    @Volatile private var videoCodecRef: MediaCodec? = null

    val isRunning: Boolean get() = running

    fun start(projection: MediaProjection, config: Config, listener: Listener): File {
        require(!running) { "Perekaman layar sudah berjalan" }
        val file = File(
            context.filesDir,
            "recordings/screen_${System.currentTimeMillis()}.${config.container.extension}"
        ).apply { parentFile?.mkdirs() }

        val videoMime = if (config.container == Container.WEBM) "video/x-vnd.on2.vp8" else "video/avc"
        if (!isEncoderAvailable(videoMime)) {
            error(
                "Encoder $videoMime tidak tersedia di perangkat ini" +
                    if (config.container == Container.WEBM) "; gunakan MP4" else ""
            )
        }
        if (config.container == Container.WEBM && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("WebM membutuhkan Android 10 (API 29)")
        }
        if (config.audioMode != AudioMode.NONE && ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            error("Izin mikrofon diperlukan untuk merekam audio")
        }

        running = true
        stopRequested.set(false)
        paused.set(false)
        job = scope.launch { record(projection, config, file, listener) }
        return file
    }

    private suspend fun record(
        projection: MediaProjection,
        config: Config,
        file: File,
        listener: Listener
    ) {
        val startedAt = System.currentTimeMillis()
        var videoCodec: MediaCodec? = null
        var audioCodec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var virtualDisplay: VirtualDisplay? = null
        var micRecord: AudioRecord? = null
        var internalRecord: AudioRecord? = null
        var audioThread: Thread? = null
        val videoInfo = MediaCodec.BufferInfo()
        val audioInfo = MediaCodec.BufferInfo()
        var videoTrack = -1
        var audioTrack = -1
        var muxerStarted = false
        var success = false

        try {
            val videoMime = if (config.container == Container.WEBM) "video/x-vnd.on2.vp8" else "video/avc"
            val format = MediaFormat.createVideoFormat(videoMime, config.width, config.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            val codec = MediaCodec.createEncoderByType(videoMime)
            videoCodec = codec
            videoCodecRef = codec
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface: Surface = codec.createInputSurface()
            codec.start()

            val outputMuxer = MediaMuxer(
                file.absolutePath,
                if (config.container == Container.WEBM) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = outputMuxer
            virtualDisplay = projection.createVirtualDisplay(
                "HariMuScreen",
                config.width,
                config.height,
                context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
            )

            if (config.audioMode != AudioMode.NONE) {
                val audioMime = if (config.container == Container.WEBM) "audio/opus" else "audio/mp4a-latm"
                val audioFormat = MediaFormat.createAudioFormat(audioMime, SAMPLE_RATE, 2).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                    if (audioMime == "audio/mp4a-latm") {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    }
                }
                val encoder = MediaCodec.createEncoderByType(audioMime)
                audioCodec = encoder
                encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()

                val records = buildAudioRecords(projection, config.audioMode)
                micRecord = records.first
                internalRecord = records.second
                micRecord.startRecording()
                internalRecord?.startRecording()

                audioThread = Thread({
                    val primary = ShortArray(2048)
                    val secondary = ShortArray(2048)
                    while (!stopRequested.get()) {
                        val readA = runCatching { micRecord?.read(primary, 0, primary.size) ?: 0 }.getOrDefault(0)
                        val readB = internalRecord?.let {
                            runCatching { it.read(secondary, 0, secondary.size) }.getOrDefault(0)
                        } ?: 0
                        val count = maxOf(readA, readB)
                        if (count <= 0) {
                            Thread.sleep(5)
                            continue
                        }
                        if (paused.get()) {
                            Thread.sleep(5)
                            continue
                        }
                        if (readA > 0 && readB > 0) {
                            for (i in 0 until minOf(readA, readB)) {
                                primary[i] = ((primary[i].toInt() + secondary[i].toInt()) / 2).toShort()
                            }
                        }
                        feedAudio(encoder, primary, count)
                    }
                    signalAudioEos(encoder)
                }, "harimu-audio").apply { start() }
            }

            var eosSent = false
            var videoEos = false
            while (currentCoroutineContext().isActive && !videoEos) {
                if (stopRequested.get() && !eosSent) {
                    codec.signalEndOfInputStream()
                    eosSent = true
                }
                val drain = drain(codec, 10_000) { buffer, info ->
                    if (videoTrack >= 0 && muxerStarted) {
                        runCatching { outputMuxer.writeSampleData(videoTrack, buffer, info) }
                    }
                }
                for (result in drain) {
                    when (result) {
                        is DrainResult.Format -> if (videoTrack < 0) {
                            videoTrack = outputMuxer.addTrack(result.format)
                        }
                        DrainResult.Eos -> videoEos = true
                        DrainResult.TryAgain -> Unit
                    }
                }
                // Deteksi track audio (format berubah) supaya muxer bisa dimulai.
                val currentAudio = audioCodec
                if (currentAudio != null && audioTrack < 0) {
                    val index = currentAudio.dequeueOutputBuffer(audioInfo, 0)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        audioTrack = outputMuxer.addTrack(currentAudio.outputFormat)
                    } else if (index >= 0) {
                        currentAudio.releaseOutputBuffer(index, false)
                    }
                }
                if (muxerStarted && currentAudio != null && audioTrack >= 0) {
                    drainAudio(currentAudio, outputMuxer, audioTrack, 0)
                }
                if (!muxerStarted && videoTrack >= 0 &&
                    (config.audioMode == AudioMode.NONE || audioTrack >= 0)
                ) {
                    outputMuxer.start()
                    muxerStarted = true
                }
                delay(5)
            }

            audioThread?.join(3000)
            audioCodec?.let { encoder -> if (audioTrack >= 0) drainAudio(encoder, outputMuxer, audioTrack) }
            // Buang sisa frame video agar muxer menerima semua sampel sebelum stop.
            drain(codec, 0) { _, _ -> }
            if (muxerStarted) runCatching { outputMuxer.stop() }
            success = true
        } catch (error: Exception) {
            listener.onError(error.message ?: "Perekaman layar gagal")
        } finally {
            running = false
            videoCodecRef = null
            runCatching { virtualDisplay?.release() }
            runCatching { micRecord?.stop() }
            runCatching { micRecord?.release() }
            runCatching { internalRecord?.stop() }
            runCatching { internalRecord?.release() }
            runCatching { videoCodec?.stop() }
            runCatching { videoCodec?.release() }
            runCatching { audioCodec?.stop() }
            runCatching { audioCodec?.release() }
            runCatching { muxer?.release() }
            if (success) {
                listener.onFinished(file, System.currentTimeMillis() - startedAt)
            } else {
                file.delete()
            }
        }
    }

    fun pause() {
        if (!running) return
        paused.set(true)
        runCatching {
            videoCodecRef?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 1) })
        }
        listenerPaused?.invoke()
    }

    fun resume() {
        if (!running) return
        paused.set(false)
        runCatching {
            videoCodecRef?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0) })
        }
        listenerResumed?.invoke()
    }

    fun stop() {
        if (!running) return
        stopRequested.set(true)
    }

    fun release() {
        stop()
        scope.cancel()
    }

    var listenerPaused: (() -> Unit)? = null
    var listenerResumed: (() -> Unit)? = null

    // ------------------------------------------------------------------ audio

    private fun buildAudioRecords(projection: MediaProjection, mode: AudioMode): Pair<AudioRecord, AudioRecord?> {
        val mic = if (mode == AudioMode.MIC || mode == AudioMode.MIC_AND_INTERNAL) buildMicRecord() else null
        val internal = if (mode == AudioMode.INTERNAL || mode == AudioMode.MIC_AND_INTERNAL) {
            buildInternalRecord(projection)
        } else null
        val first = mic ?: internal ?: error("Tidak ada sumber audio yang bisa dipakai")
        return first to if (first === mic) internal else null
    }

    @SuppressLint("MissingPermission")
    private fun buildMicRecord(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        require(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord mikrofon gagal" }
        return record
    }

    private fun buildInternalRecord(projection: MediaProjection): AudioRecord {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            error("Audio internal (AudioPlaybackCapture) membutuhkan Android 10 (API 29)")
        }
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)
        @SuppressLint("MissingPermission")
        val record = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setAudioPlaybackCaptureConfig(config)
            .build()
        require(record.state == AudioRecord.STATE_INITIALIZED) {
            "Audio internal tidak bisa dibuka (aplikasi sumber mungkin menolak playback capture)"
        }
        return record
    }

    private fun feedAudio(codec: MediaCodec, samples: ShortArray, count: Int) {
        val bytes = ByteArray(count * 2)
        var index = 0
        for (i in 0 until count) {
            val value = samples[i].toInt()
            bytes[index++] = (value and 0xFF).toByte()
            bytes[index++] = ((value shr 8) and 0xFF).toByte()
        }
        val inputIndex = codec.dequeueInputBuffer(10_000)
        if (inputIndex >= 0) {
            val buffer = codec.getInputBuffer(inputIndex) ?: return
            buffer.clear()
            buffer.put(bytes)
            codec.queueInputBuffer(inputIndex, 0, bytes.size, System.nanoTime() / 1000, 0)
        }
    }

    private fun signalAudioEos(codec: MediaCodec) {
        runCatching {
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) {
                codec.queueInputBuffer(index, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        }
    }

    private fun drainAudio(codec: MediaCodec, muxer: MediaMuxer, track: Int, timeoutUs: Long = 10_000) {
        while (true) {
            val info = MediaCodec.BufferInfo()
            val index = codec.dequeueOutputBuffer(info, timeoutUs)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue
            if (index >= 0) {
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    runCatching { muxer.writeSampleData(track, buffer, info) }
                }
                codec.releaseOutputBuffer(index, false)
            }
        }
    }

    // ------------------------------------------------------------------ video

    private sealed interface DrainResult {
        data class Format(val format: MediaFormat) : DrainResult
        data object Eos : DrainResult
        data object TryAgain : DrainResult
    }

    /**
     * Salurkan output encoder ke [onSample] **sebelum** buffer dilepas (menghindari pemakaian
     * buffer yang sudah di-release), lalu kembalikan kejadian format/EOS.
     */
    private fun drain(
        codec: MediaCodec,
        timeoutUs: Long,
        onSample: (java.nio.ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ): List<DrainResult> {
        val results = mutableListOf<DrainResult>()
        while (true) {
            val info = MediaCodec.BufferInfo()
            val index = codec.dequeueOutputBuffer(info, timeoutUs)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    results.add(DrainResult.TryAgain)
                    return results
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> results.add(DrainResult.Format(codec.outputFormat))
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        onSample(buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        results.add(DrainResult.Eos)
                        return results
                    }
                }
            }
        }
    }

    private fun isEncoderAvailable(mime: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && runCatching { info.getCapabilitiesForType(mime) }.isSuccess
        }

    companion object {
        private const val SAMPLE_RATE = 48_000
    }
}
