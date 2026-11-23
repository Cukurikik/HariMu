package com.harimu.app.core.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PcmAudio(
    val samples: ShortArray,
    val sampleRate: Int,
    val channels: Int
)

object AudioEngine {

    fun decodeToPcm(context: Context, uri: Uri): PcmAudio {
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
        require(audioTrack >= 0 && format != null) { "Track audio tidak ditemukan" }
        extractor.selectTrack(audioTrack)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmOut = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
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
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        if (info.size > 0) {
                            val chunk = ByteArray(info.size)
                            outBuf.position(info.offset)
                            outBuf.get(chunk)
                            pcmOut.write(chunk)
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

        val bytes = pcmOut.toByteArray()
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return PcmAudio(shorts, sampleRate, channels.coerceIn(1, 2))
    }

    fun writeWav(pcm: PcmAudio, dest: File): File {
        val dataBytes = pcm.samples.size * 2
        val byteRate = pcm.sampleRate * pcm.channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(pcm.channels.toShort())
            putInt(pcm.sampleRate)
            putInt(byteRate)
            putShort((pcm.channels * 2).toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()

        val pcmBytes = ByteArray(dataBytes)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm.samples)
        FileOutputStream(dest).use { fos ->
            fos.write(header)
            fos.write(pcmBytes)
        }
        return dest
    }

    fun trimPcm(pcm: PcmAudio, startSec: Float, endSec: Float): PcmAudio {
        val startIdx = (startSec * pcm.sampleRate * pcm.channels).toInt().coerceIn(0, pcm.samples.size)
        val endIdx = if (endSec <= 0f) pcm.samples.size else (endSec * pcm.sampleRate * pcm.channels).toInt().coerceIn(startIdx, pcm.samples.size)
        return pcm.copy(samples = pcm.samples.copyOfRange(startIdx, endIdx))
    }

    fun applyGain(pcm: PcmAudio, volumePercent: Float): PcmAudio {
        val gain = (volumePercent / 100f).coerceIn(0f, 4f)
        val out = ShortArray(pcm.samples.size)
        for (i in pcm.samples.indices) {
            out[i] = (pcm.samples[i] * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm.copy(samples = out)
    }

    fun reversePcm(pcm: PcmAudio): PcmAudio {
        val frames = pcm.samples.size / pcm.channels
        val out = ShortArray(pcm.samples.size)
        for (f in 0 until frames) {
            val srcF = frames - 1 - f
            for (c in 0 until pcm.channels) {
                out[f * pcm.channels + c] = pcm.samples[srcF * pcm.channels + c]
            }
        }
        return pcm.copy(samples = out)
    }

    fun applyFade(pcm: PcmAudio, fadeInSec: Float, fadeOutSec: Float): PcmAudio {
        val out = pcm.samples.copyOf()
        val frames = out.size / pcm.channels
        val inFrames = (fadeInSec * pcm.sampleRate).toInt().coerceIn(0, frames)
        val outFrames = (fadeOutSec * pcm.sampleRate).toInt().coerceIn(0, frames)
        for (f in 0 until inFrames) {
            val factor = f.toFloat() / max(1, inFrames)
            for (c in 0 until pcm.channels) {
                out[f * pcm.channels + c] = (out[f * pcm.channels + c] * factor).roundToInt().toShort()
            }
        }
        for (f in 0 until outFrames) {
            val frameIdx = frames - 1 - f
            val factor = f.toFloat() / max(1, outFrames)
            for (c in 0 until pcm.channels) {
                out[frameIdx * pcm.channels + c] = (out[frameIdx * pcm.channels + c] * factor).roundToInt().toShort()
            }
        }
        return pcm.copy(samples = out)
    }

    fun addEcho(pcm: PcmAudio, delayMs: Int, decay: Float): PcmAudio {
        val delaySamples = ((delayMs.coerceIn(50, 1200) / 1000f) * pcm.sampleRate * pcm.channels).toInt()
        val out = ShortArray(pcm.samples.size)
        for (i in pcm.samples.indices) {
            val dry = pcm.samples[i].toFloat()
            val wet = if (i >= delaySamples) out[i - delaySamples] * decay.coerceIn(0.1f, 0.75f) else 0f
            out[i] = (dry + wet).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm.copy(samples = out)
    }

    fun manipulateChannels(pcm: PcmAudio, mode: String): PcmAudio {
        if (pcm.channels < 2) return pcm
        val frames = pcm.samples.size / 2
        return when (mode) {
            "Mono Mix" -> {
                val mono = ShortArray(frames)
                for (f in 0 until frames) {
                    mono[f] = ((pcm.samples[f * 2].toInt() + pcm.samples[f * 2 + 1].toInt()) / 2).toShort()
                }
                PcmAudio(mono, pcm.sampleRate, 1)
            }
            "Tukar L/R" -> {
                val swapped = ShortArray(pcm.samples.size)
                for (f in 0 until frames) {
                    swapped[f * 2] = pcm.samples[f * 2 + 1]
                    swapped[f * 2 + 1] = pcm.samples[f * 2]
                }
                pcm.copy(samples = swapped)
            }
            "Hanya Kiri" -> {
                val out = ShortArray(pcm.samples.size)
                for (f in 0 until frames) {
                    out[f * 2] = pcm.samples[f * 2]
                    out[f * 2 + 1] = pcm.samples[f * 2]
                }
                pcm.copy(samples = out)
            }
            else -> {
                val out = ShortArray(pcm.samples.size)
                for (f in 0 until frames) {
                    out[f * 2] = pcm.samples[f * 2 + 1]
                    out[f * 2 + 1] = pcm.samples[f * 2 + 1]
                }
                pcm.copy(samples = out)
            }
        }
    }

    fun removeSilence(pcm: PcmAudio, thresholdPercent: Int): PcmAudio {
        val threshold = (thresholdPercent.coerceIn(1, 30) / 100f) * Short.MAX_VALUE
        val windowSamples = (pcm.sampleRate / 20) * pcm.channels
        val kept = ArrayList<Short>(pcm.samples.size)
        var i = 0
        while (i < pcm.samples.size) {
            val end = min(i + windowSamples, pcm.samples.size)
            var sumSq = 0.0
            for (j in i until end) {
                val v = pcm.samples[j].toDouble()
                sumSq += v * v
            }
            val rms = sqrt(sumSq / max(1, end - i))
            if (rms >= threshold) {
                for (j in i until end) kept.add(pcm.samples[j])
            }
            i = end
        }
        val finalSamples = if (kept.isEmpty()) pcm.samples else kept.toShortArray()
        return pcm.copy(samples = finalSamples)
    }

    fun mergeOrMix(clips: List<PcmAudio>, mixParallel: Boolean): PcmAudio {
        val base = clips.first()
        if (!mixParallel) {
            val totalSize = clips.sumOf { it.samples.size }
            val out = ShortArray(totalSize)
            var pos = 0
            for (c in clips) {
                System.arraycopy(c.samples, 0, out, pos, c.samples.size)
                pos += c.samples.size
            }
            return base.copy(samples = out)
        }
        val maxLen = clips.maxOf { it.samples.size }
        val out = ShortArray(maxLen)
        for (i in 0 until maxLen) {
            var acc = 0
            var count = 0
            for (c in clips) {
                if (i < c.samples.size) {
                    acc += c.samples[i]
                    count++
                }
            }
            out[i] = (acc / max(1, count)).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return base.copy(samples = out)
    }

    fun renderWaveformPoster(pcm: PcmAudio, destPng: File, title: String): File {
        val w = 1400
        val h = 700
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.parseColor("#11141C"), Color.parseColor("#1B2232"), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val bars = 120
        val step = max(1, pcm.samples.size / bars)
        val barWidth = (w - 160f) / bars
        val centerY = h / 2f
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 120f, 0f, h - 120f, Color.parseColor("#FF6B2C"), Color.parseColor("#00BFA5"), Shader.TileMode.CLAMP)
            strokeCap = Paint.Cap.ROUND
            strokeWidth = max(4f, barWidth * 0.58f)
        }
        for (b in 0 until bars) {
            val start = b * step
            val end = min(start + step, pcm.samples.size)
            var peak = 0
            for (s in start until end) {
                peak = max(peak, abs(pcm.samples[s].toInt()))
            }
            val norm = peak.toFloat() / Short.MAX_VALUE
            val halfBar = max(8f, norm * (h * 0.34f))
            val x = 80f + b * barWidth + barWidth / 2f
            canvas.drawLine(x, centerY - halfBar, x, centerY + halfBar, barPaint)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            isFakeBoldText = true
        }
        canvas.drawText(title.ifBlank { "HariMu Audio Waveform" }, 80f, 78f, textPaint)
        ImageEngine.saveBitmap(bmp, destPng, "PNG", 100)
        bmp.recycle()
        return destPng
    }

    suspend fun synthesizeTtsToWav(
        context: Context,
        text: String,
        destWav: File,
        speechRate: Float,
        pitch: Float,
        isIndonesian: Boolean
    ): File = suspendCancellableCoroutine { cont ->
        var tts: TextToSpeech? = null
        val utteranceId = UUID.randomUUID().toString()
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                runCatching { tts?.shutdown() }
                if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException("TTS engine tidak tersedia di perangkat ini")))
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            engine.language = if (isIndonesian) Locale("id", "ID") else Locale.US
            engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
            engine.setPitch(pitch.coerceIn(0.6f, 1.8f))
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    runCatching { engine.shutdown() }
                    if (cont.isActive) cont.resume(destWav)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    runCatching { engine.shutdown() }
                    if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException("Gagal mensintesis suara TTS")))
                }
            })
            val params = Bundle()
            engine.synthesizeToFile(text, params, destWav, utteranceId)
        }
        cont.invokeOnCancellation {
            runCatching { tts?.stop(); tts?.shutdown() }
        }
    }
}
