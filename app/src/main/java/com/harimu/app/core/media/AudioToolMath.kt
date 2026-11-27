package com.harimu.app.core.media

import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import com.harimu.app.core.files.FileStorageManager
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class AudioFormatSpec(
    val id: String,
    val extension: String,
    val mimeType: String,
    val ffmpegCodec: String,
    val muxerFormat: String?,
    val maxSampleRateHz: Int = 192_000,
    val defaultBitrate: String? = null
)

data class AudioFormatValidation(
    val valid: Boolean,
    val spec: AudioFormatSpec? = null,
    val errorMessage: String? = null
)

data class AudioValidationResult(
    val valid: Boolean,
    val probes: List<MediaProbeResult> = emptyList(),
    val errorMessage: String? = null
)

data class LoudnormMeasuredStats(
    val inputI: Double,
    val inputTp: Double,
    val inputLra: Double,
    val inputThresh: Double,
    val targetOffset: Double
)

interface AudioEncoderCapabilities {
    fun isEncoderAvailable(codec: String, mimeType: String): Boolean
    fun availableFormatLabels(): List<String>
}

object DefaultAudioEncoderCapabilities : AudioEncoderCapabilities {
    // Verified compiled encoders in dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9 (LGPL)
    private val compiledFfmpegEncoders = setOf(
        "libmp3lame",
        "libvorbis",
        "libopus",
        "flac",
        "pcm_s16le",
        "aac",
        "alac"
    )

    override fun isEncoderAvailable(codec: String, mimeType: String): Boolean {
        if (codec in compiledFfmpegEncoders) return true
        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    override fun availableFormatLabels(): List<String> = listOf("MP3", "WAV", "OGG", "FLAC", "M4A (AAC)")
}

object AudioToolMath {

    private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "ogg", "oga", "opus", "flac", "m4a", "aac",
        "wma", "amr", "webm", "3gp", "mka", "aiff", "caf", "mp4"
    )

    val FORMAT_SPECS: Map<String, AudioFormatSpec> = mapOf(
        "MP3" to AudioFormatSpec(
            id = "MP3",
            extension = "mp3",
            mimeType = "audio/mpeg",
            ffmpegCodec = "libmp3lame",
            muxerFormat = "mp3",
            maxSampleRateHz = 48_000,
            defaultBitrate = "192k"
        ),
        "WAV" to AudioFormatSpec(
            id = "WAV",
            extension = "wav",
            mimeType = "audio/wav",
            ffmpegCodec = "pcm_s16le",
            muxerFormat = "wav",
            maxSampleRateHz = 192_000,
            defaultBitrate = null
        ),
        "WAV (PCM)" to AudioFormatSpec(
            id = "WAV (PCM)",
            extension = "wav",
            mimeType = "audio/wav",
            ffmpegCodec = "pcm_s16le",
            muxerFormat = "wav",
            maxSampleRateHz = 192_000,
            defaultBitrate = null
        ),
        "OGG" to AudioFormatSpec(
            id = "OGG",
            extension = "ogg",
            mimeType = "audio/ogg",
            ffmpegCodec = "libvorbis",
            muxerFormat = "ogg",
            maxSampleRateHz = 192_000,
            defaultBitrate = "192k"
        ),
        "FLAC" to AudioFormatSpec(
            id = "FLAC",
            extension = "flac",
            mimeType = "audio/flac",
            ffmpegCodec = "flac",
            muxerFormat = "flac",
            maxSampleRateHz = 192_000,
            defaultBitrate = null
        ),
        "M4A (AAC)" to AudioFormatSpec(
            id = "M4A (AAC)",
            extension = "m4a",
            mimeType = "audio/mp4",
            ffmpegCodec = "aac",
            muxerFormat = "ipod",
            maxSampleRateHz = 96_000,
            defaultBitrate = "192k"
        ),
        "OPUS" to AudioFormatSpec(
            id = "OPUS",
            extension = "opus",
            mimeType = "audio/ogg",
            ffmpegCodec = "libopus",
            muxerFormat = "opus",
            maxSampleRateHz = 48_000,
            defaultBitrate = "128k"
        ),
        "WMA" to AudioFormatSpec(
            id = "WMA",
            extension = "wma",
            mimeType = "audio/x-ms-wma",
            ffmpegCodec = "wmav2",
            muxerFormat = "asf",
            maxSampleRateHz = 48_000,
            defaultBitrate = "192k"
        )
    )

    fun resolveFormatSpec(formatLabel: String): AudioFormatSpec? {
        val clean = formatLabel.trim().uppercase(Locale.US)
        return FORMAT_SPECS.entries.firstOrNull { (k, _) ->
            k.uppercase(Locale.US) == clean
        }?.value ?: when {
            clean.startsWith("WAV") -> FORMAT_SPECS["WAV"]
            clean.startsWith("MP3") -> FORMAT_SPECS["MP3"]
            clean.startsWith("OGG") -> FORMAT_SPECS["OGG"]
            clean.startsWith("FLAC") -> FORMAT_SPECS["FLAC"]
            clean.startsWith("M4A") || clean == "AAC" -> FORMAT_SPECS["M4A (AAC)"]
            clean.startsWith("OPUS") -> FORMAT_SPECS["OPUS"]
            else -> null
        }
    }

    fun validateOutputFormatAndRate(
        formatLabel: String,
        sampleRateHz: Int? = null,
        capabilities: AudioEncoderCapabilities = DefaultAudioEncoderCapabilities,
        isEnglish: Boolean = false
    ): AudioFormatValidation {
        val spec = resolveFormatSpec(formatLabel)
        val suggestions = capabilities.availableFormatLabels().joinToString(", ")
        if (spec == null) {
            return AudioFormatValidation(
                valid = false,
                errorMessage = if (isEnglish) {
                    "Format '$formatLabel' does not have a supported encoder. Try one of: $suggestions."
                } else {
                    "Format '$formatLabel' tidak memiliki encoder yang didukung. Gunakan salah satu format lain: $suggestions."
                }
            )
        }
        if (!capabilities.isEncoderAvailable(spec.ffmpegCodec, spec.mimeType)) {
            return AudioFormatValidation(
                valid = false,
                errorMessage = if (isEnglish) {
                    "Encoder '${spec.ffmpegCodec}' for $formatLabel is unavailable in this build/device. Recommended alternatives: $suggestions."
                } else {
                    "Encoder '${spec.ffmpegCodec}' untuk format $formatLabel tidak tersedia pada build/perangkat ini. Saran format lain: $suggestions."
                }
            )
        }
        if (sampleRateHz != null && sampleRateHz > spec.maxSampleRateHz) {
            return AudioFormatValidation(
                valid = false,
                errorMessage = if (isEnglish) {
                    "${spec.id} (${spec.ffmpegCodec}) supports up to ${spec.maxSampleRateHz} Hz only. Choose WAV or FLAC for ${sampleRateHz} Hz."
                } else {
                    "Format ${spec.id} (${spec.ffmpegCodec}) hanya mendukung hingga ${spec.maxSampleRateHz} Hz. Pilih WAV atau FLAC untuk ${sampleRateHz} Hz."
                }
            )
        }
        return AudioFormatValidation(valid = true, spec = spec)
    }

    fun isSupportedAudioExtension(ext: String): Boolean {
        val clean = ext.trim().lowercase(Locale.US).trimStart('.')
        return clean.isBlank() || clean in SUPPORTED_AUDIO_EXTENSIONS
    }

    fun validateAudioInputs(
        context: Context,
        storage: FileStorageManager,
        uris: List<Uri>,
        minCount: Int = 1,
        isEnglish: Boolean = false
    ): AudioValidationResult {
        if (uris.size < minCount) {
            return AudioValidationResult(
                valid = false,
                errorMessage = if (isEnglish) {
                    if (minCount > 1) "Select at least $minCount audio files." else "Please pick an audio file first."
                } else {
                    if (minCount > 1) "Pilih minimal $minCount file audio." else "Pilih file audio terlebih dahulu."
                }
            )
        }
        if (!storage.hasEnoughDiskSpace()) {
            return AudioValidationResult(
                valid = false,
                errorMessage = if (isEnglish) "Not enough free disk space." else "Ruang disk tidak cukup untuk memproses audio."
            )
        }

        val probes = mutableListOf<MediaProbeResult>()
        uris.forEachIndexed { idx, uri ->
            val size = VideoToolMath.resolveInputSizeBytes(context, uri)
            if (size <= 0L) {
                return AudioValidationResult(
                    valid = false,
                    errorMessage = if (isEnglish) {
                        "Audio file #${idx + 1} is empty (0 bytes) or unreadable."
                    } else {
                        "File audio ke-${idx + 1} kosong (0 byte) atau tidak dapat dibaca."
                    }
                )
            }

            val ext = MediaUriUtils.resolveExtension(context, uri, fallbackExt = "")
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            val validMime = mime.isBlank() || mime.startsWith("audio/") || mime == "application/ogg" ||
                mime == "application/octet-stream" || mime == "video/mp4"
            if (!validMime) {
                return AudioValidationResult(
                    valid = false,
                    errorMessage = if (isEnglish) {
                        "Unsupported file format ($mime). Choose a valid audio file."
                    } else {
                        "Format file tidak didukung ($mime). Pilih file audio yang valid."
                    }
                )
            }
            if (ext.isNotBlank() && !isSupportedAudioExtension(ext)) {
                return AudioValidationResult(
                    valid = false,
                    errorMessage = if (isEnglish) {
                        "Unsupported audio extension: .$ext."
                    } else {
                        "Ekstensi file audio tidak didukung: .$ext."
                    }
                )
            }

            val probe = MediaProbe.probe(context, uri)
            if (!probe.hasAudio && probe.durationMs <= 0L) {
                return AudioValidationResult(
                    valid = false,
                    errorMessage = if (isEnglish) {
                        "No valid audio stream found in file #${idx + 1}."
                    } else {
                        "Trek audio tidak ditemukan atau file rusak pada input ke-${idx + 1}."
                    }
                )
            }
            probes.add(probe)
        }
        return AudioValidationResult(valid = true, probes = probes)
    }

    fun semitonesToPitchRatio(semitones: Float): Double {
        val clamped = semitones.coerceIn(-12f, 12f).toDouble()
        return 2.0.pow(clamped / 12.0)
    }

    fun buildAudioAtempoChain(speed: Float): String {
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

    fun buildPitchShiftFilter(semitones: Float, sampleRateHz: Int): String {
        val clamped = semitones.coerceIn(-12f, 12f)
        if (abs(clamped) < 0.01f) return "anull"
        val baseRate = sampleRateHz.takeIf { it in 8000..192000 } ?: 44_100
        val pitchRatio = semitonesToPitchRatio(clamped)
        val shiftedRate = (baseRate * pitchRatio).roundToInt().coerceIn(4000, 384_000)
        val tempoCompensation = (1.0 / pitchRatio).toFloat()
        val atempoChain = buildAudioAtempoChain(tempoCompensation)
        return "asetrate=$shiftedRate,aresample=$baseRate,$atempoChain"
    }

    fun computeAdjustedDurationMs(durationMs: Long, speed: Float): Long {
        if (durationMs <= 0L) return 0L
        val safeSpeed = speed.coerceIn(0.25f, 4.0f).toDouble()
        return (durationMs.toDouble() / safeSpeed).roundToInt().toLong().coerceAtLeast(1L)
    }

    fun buildVolumeFilter(volumePercent: Float): String {
        val factor = (volumePercent.coerceIn(0f, 300f) / 100f).toDouble()
        return String.format(Locale.US, "volume=%.2f", factor)
    }

    fun buildMergeFilterComplex(inputCount: Int, mixMode: Boolean): String {
        require(inputCount >= 2) { "At least 2 inputs required" }
        val normalizedPads = (0 until inputCount).joinToString(";") { idx ->
            "[$idx:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo[a$idx]"
        }
        val labels = (0 until inputCount).joinToString("") { "[a$it]" }
        val tail = if (mixMode) {
            "${labels}amix=inputs=$inputCount:duration=longest:normalize=1[aout]"
        } else {
            "${labels}concat=n=$inputCount:v=0:a=1[aout]"
        }
        return "$normalizedPads;$tail"
    }

    fun buildLoudnormPass1Filter(
        targetLufs: Double = -16.0,
        truePeakDb: Double = -1.5,
        lra: Double = 11.0
    ): String = String.format(
        Locale.US,
        "loudnorm=I=%.1f:TP=%.1f:LRA=%.1f:print_format=json",
        targetLufs.coerceIn(-30.0, -5.0),
        truePeakDb.coerceIn(-9.0, 0.0),
        lra.coerceIn(1.0, 20.0)
    )

    fun parseLoudnormStats(logs: String): LoudnormMeasuredStats? {
        val start = logs.lastIndexOf('{')
        val end = logs.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val jsonSlice = logs.substring(start, end + 1)
        return runCatching {
            val obj = JSONObject(jsonSlice)
            val i = obj.optString("input_i").toDoubleOrNull() ?: return null
            val tp = obj.optString("input_tp").toDoubleOrNull() ?: return null
            val lra = obj.optString("input_lra").toDoubleOrNull() ?: return null
            val thresh = obj.optString("input_thresh").toDoubleOrNull() ?: return null
            val offset = obj.optString("target_offset").toDoubleOrNull() ?: return null
            if (!i.isFinite() || !tp.isFinite() || !lra.isFinite() || !thresh.isFinite() || !offset.isFinite()) {
                return null
            }
            LoudnormMeasuredStats(
                inputI = i,
                inputTp = tp,
                inputLra = lra,
                inputThresh = thresh,
                targetOffset = offset
            )
        }.getOrNull()
    }

    fun buildLoudnormPass2Filter(
        stats: LoudnormMeasuredStats?,
        targetLufs: Double = -16.0,
        truePeakDb: Double = -1.5,
        lra: Double = 11.0
    ): String {
        val safeI = targetLufs.coerceIn(-30.0, -5.0)
        val safeTp = truePeakDb.coerceIn(-9.0, 0.0)
        val safeLra = lra.coerceIn(1.0, 20.0)
        if (stats == null) {
            return String.format(Locale.US, "loudnorm=I=%.1f:TP=%.1f:LRA=%.1f", safeI, safeTp, safeLra)
        }
        return String.format(
            Locale.US,
            "loudnorm=I=%.1f:TP=%.1f:LRA=%.1f:measured_I=%.2f:measured_TP=%.2f:measured_LRA=%.2f:measured_thresh=%.2f:offset=%.2f:linear=true",
            safeI,
            safeTp,
            safeLra,
            stats.inputI,
            stats.inputTp,
            stats.inputLra,
            stats.inputThresh,
            stats.targetOffset
        )
    }

    fun buildChannelArgs(mode: String, inputChannels: Int): List<String> {
        return when (mode) {
            "Mono Mix" -> listOf("-ac", "1")
            "Stereo" -> listOf("-ac", "2")
            "Tukar L/R" -> if (inputChannels >= 2) {
                listOf("-af", "pan=stereo|c0=c1|c1=c0", "-ac", "2")
            } else {
                listOf("-ac", "2")
            }
            "Hanya Kiri" -> listOf("-af", "pan=stereo|c0=c0|c1=c0", "-ac", "2")
            "Hanya Kanan" -> if (inputChannels >= 2) {
                listOf("-af", "pan=stereo|c0=c1|c1=c1", "-ac", "2")
            } else {
                listOf("-af", "pan=stereo|c0=c0|c1=c0", "-ac", "2")
            }
            else -> listOf("-ac", "2")
        }
    }

    fun buildSilenceRemoveFilter(thresholdDb: Float, minDurationSec: Float): String {
        val db = thresholdDb.coerceIn(-70f, -10f)
        val dur = minDurationSec.coerceIn(0.1f, 5.0f)
        return String.format(
            Locale.US,
            "silenceremove=start_periods=1:start_duration=0.05:start_threshold=%.1fdB:stop_periods=-1:stop_duration=%.2f:stop_threshold=%.1fdB",
            db,
            dur,
            db
        )
    }

    fun buildFadeFilter(totalDurationMs: Long, fadeInSec: Float, fadeOutSec: Float): String {
        val parts = mutableListOf<String>()
        val safeIn = fadeInSec.coerceIn(0f, 30f)
        val safeOut = fadeOutSec.coerceIn(0f, 30f)
        if (safeIn > 0.05f) {
            parts.add(String.format(Locale.US, "afade=t=in:st=0:d=%.2f", safeIn))
        }
        if (safeOut > 0.05f && totalDurationMs > 0L) {
            val totalSec = totalDurationMs / 1000.0
            val actualOutDur = minOf(safeOut.toDouble(), (totalSec * 0.85).coerceAtLeast(0.1))
            val outStart = (totalSec - actualOutDur).coerceAtLeast(0.0)
            parts.add(String.format(Locale.US, "afade=t=out:st=%.2f:d=%.2f", outStart, actualOutDur))
        }
        return if (parts.isEmpty()) "anull" else parts.joinToString(",")
    }

    fun computeSplitRanges(
        totalDurationMs: Long,
        byPartCount: Boolean,
        segmentSec: Int,
        partsCount: Int
    ): List<Pair<Long, Long>> {
        val safeTotal = totalDurationMs.coerceAtLeast(1_000L)
        if (!byPartCount) {
            val stepMs = (segmentSec.coerceIn(5, 3600) * 1000L)
            return VideoToolMath.computeSplitterRanges(safeTotal, stepMs)
        }
        val count = partsCount.coerceIn(2, 24)
        val chunkMs = (safeTotal / count.toLong()).coerceAtLeast(500L)
        val ranges = mutableListOf<Pair<Long, Long>>()
        var start = 0L
        for (idx in 0 until count) {
            val end = if (idx == count - 1) safeTotal else minOf(safeTotal, start + chunkMs)
            if (end > start) {
                ranges.add(start to end)
            }
            start = end
            if (start >= safeTotal) break
        }
        return ranges
    }

    fun resolveDefaultOutputSpec(sourceExt: String): AudioFormatSpec {
        return when (sourceExt.trim().lowercase(Locale.US)) {
            "wav" -> FORMAT_SPECS.getValue("WAV")
            "flac" -> FORMAT_SPECS.getValue("FLAC")
            "ogg", "oga" -> FORMAT_SPECS.getValue("OGG")
            "m4a", "aac" -> FORMAT_SPECS.getValue("M4A (AAC)")
            else -> FORMAT_SPECS.getValue("MP3")
        }
    }

    fun buildCodecOutputArgs(spec: AudioFormatSpec, bitrateOverride: String? = null, sampleRateHz: Int? = null): List<String> {
        return buildList {
            if (sampleRateHz != null) {
                add("-ar")
                add(sampleRateHz.toString())
            }
            add("-c:a")
            add(spec.ffmpegCodec)
            val br = bitrateOverride ?: spec.defaultBitrate
            if (br != null && spec.ffmpegCodec != "pcm_s16le" && spec.ffmpegCodec != "flac") {
                add("-b:a")
                add(br)
            }
            if (spec.muxerFormat == "ipod") {
                add("-movflags")
                add("+faststart")
            }
        }
    }

    fun assessAudioReverseSafety(durationMs: Long, isEnglish: Boolean): SafetyAssessment {
        return when {
            durationMs > 600_000L -> SafetyAssessment(
                allowed = false,
                blockingError = if (isEnglish) {
                    "Audio is longer than 10 minutes. Reverse buffers decoded samples in memory; please trim first with AUD-09."
                } else {
                    "Durasi audio melebihi 10 menit. Reverse memuat sampel audio di RAM; potong dulu dengan AUD-09."
                }
            )
            durationMs > 45_000L -> SafetyAssessment(
                allowed = true,
                useSegmentedMode = true,
                warningMessage = if (isEnglish) {
                    "Audio > 45s: using segmented 20s reverse mode to keep RAM safe."
                } else {
                    "Audio > 45 dtk: memakai mode reverse tersegmentasi per 20 detik agar RAM aman."
                }
            )
            else -> SafetyAssessment(allowed = true, useSegmentedMode = false)
        }
    }

    fun assessInMemoryDspSafety(durationMs: Long, isEnglish: Boolean): SafetyAssessment {
        return if (durationMs > 300_000L) {
            SafetyAssessment(
                allowed = false,
                blockingError = if (isEnglish) {
                    "Audio exceeds 5 minutes for in-memory PCM DSP. Trim it first with AUD-09 or use FFmpeg fast mode."
                } else {
                    "Durasi audio melebihi 5 menit untuk pemrosesan PCM di RAM. Potong dulu dengan AUD-09 atau gunakan mode cepat FFmpeg."
                }
            )
        } else {
            SafetyAssessment(allowed = true, useSegmentedMode = false)
        }
    }
}
