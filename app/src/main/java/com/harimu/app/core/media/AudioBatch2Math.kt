package com.harimu.app.core.media

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class EqPresetSpec(
    val id: String,
    val labelId: String,
    val labelEn: String,
    val gainsDb: List<Float>
)

data class EqParityReport(
    val using10BandDynamicsProcessing: Boolean,
    val liveBandCount: Int,
    val maxDeltaDb: Float,
    val meanDeltaDb: Float,
    val summaryId: String,
    val summaryEn: String
)

data class MasteringPresetSpec(
    val id: String,
    val thresholdDb: Float,
    val ratio: Float,
    val ceilingDb: Float,
    val makeupDb: Float,
    val warmthDb: Float,
    val presenceDb: Float,
    val airDb: Float,
    val stereoWidth: Float
)

data class VisualizerConfigSpec(
    val styleId: String,
    val primaryHex: String,
    val secondaryHex: String,
    val bgHex: String,
    val spectrumColorScheme: String,
    val width: Int,
    val height: Int,
    val fps: Int
)

object AudioBatch2Math {

    // Standard 10-band ISO octave center frequencies (Hz)
    val EQ_10_BAND_FREQS_HZ: List<Int> = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val EQ_10_BAND_LABELS: List<String> = listOf(
        "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz",
        "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
    )

    // Geometric midpoint upper cutoff boundaries (fc * sqrt(2)) for Android DynamicsProcessing.EqBand
    val EQ_10_BAND_CUTOFFS_HZ: List<Float> = listOf(
        44f, 88f, 177f, 355f, 707f, 1414f, 2828f, 5657f, 11314f, 20000f
    )

    val EQ_10_PRESETS: List<EqPresetSpec> = listOf(
        EqPresetSpec("flat", "Flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPresetSpec("bass", "Bass Boost", "Bass Boost", listOf(6.0f, 5.5f, 4.0f, 2.0f, 0.5f, 0f, 0f, 0.5f, 1.0f, 1.0f)),
        EqPresetSpec("vocal", "Kehadiran Vokal", "Vocal Presence", listOf(-2.0f, -1.5f, -0.5f, 0.5f, 1.5f, 3.0f, 4.5f, 4.0f, 2.0f, 0.5f)),
        EqPresetSpec("treble", "Treble Cerah", "Treble Sparkle", listOf(0f, 0f, 0f, 0f, 0.5f, 1.0f, 2.5f, 4.5f, 5.5f, 6.0f)),
        EqPresetSpec("acoustic", "Akustik", "Acoustic", listOf(2.5f, 2.0f, 1.5f, 0.5f, 1.0f, 1.5f, 2.5f, 3.0f, 2.5f, 1.5f)),
        EqPresetSpec("electronic", "Elektronik", "Electronic", listOf(5.0f, 4.0f, 2.0f, 0f, -1.5f, 0.5f, 1.5f, 3.0f, 4.5f, 5.0f)),
        EqPresetSpec("podcast", "Podcast Jernih", "Podcast Clear", listOf(-4.0f, -2.5f, -1.0f, 0.5f, 1.0f, 2.5f, 4.0f, 3.5f, 1.5f, -0.5f)),
        EqPresetSpec("rock", "Rock", "Rock", listOf(4.5f, 3.5f, 2.0f, 0.5f, -1.0f, -0.5f, 1.5f, 3.0f, 4.0f, 4.5f))
    )

    fun dbToLinearAmplitude(db: Float): Double = 10.0.pow(db / 20.0)

    fun buildEqualizer10BandFfmpegFilter(
        gainsDb: List<Float>,
        preampDb: Float = 0f,
        enableLimiter: Boolean = true
    ): String {
        val safeGains = normalizeBandGains(gainsDb)
        val filters = mutableListOf<String>()
        EQ_10_BAND_FREQS_HZ.forEachIndexed { idx, freqHz ->
            val g = safeGains[idx].coerceIn(-12f, 12f)
            if (abs(g) >= 0.05f) {
                filters.add(String.format(Locale.US, "equalizer=f=%d:t=o:w=1.0:g=%.2f", freqHz, g))
            }
        }
        val clampedPreamp = preampDb.coerceIn(-6f, 6f)
        if (abs(clampedPreamp) >= 0.05f) {
            val factor = dbToLinearAmplitude(clampedPreamp).coerceIn(0.25, 4.0)
            filters.add(String.format(Locale.US, "volume=%.4f", factor))
        }
        val peakPositiveBoost = safeGains.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        if (enableLimiter && (peakPositiveBoost + clampedPreamp > 0.5f || filters.isNotEmpty())) {
            // Matches DynamicsProcessing output stage -0.5 dBTP soft limiter
            filters.add("alimiter=limit=0.9441:attack=5:release=50:level=disabled")
        }
        return if (filters.isEmpty()) "anull" else filters.joinToString(",")
    }

    fun normalizeBandGains(gainsDb: List<Float>): List<Float> =
        List(EQ_10_BAND_FREQS_HZ.size) { idx ->
            gainsDb.getOrElse(idx) { 0f }.coerceIn(-12f, 12f)
        }

    fun interpolateGainAtFreqDb(gainsDb: List<Float>, targetFreqHz: Int): Float {
        val safeGains = normalizeBandGains(gainsDb)
        val target = targetFreqHz.coerceAtLeast(20).toDouble()
        if (target <= EQ_10_BAND_FREQS_HZ.first()) return safeGains.first()
        if (target >= EQ_10_BAND_FREQS_HZ.last()) return safeGains.last()

        val logTarget = ln(target)
        for (i in 0 until EQ_10_BAND_FREQS_HZ.size - 1) {
            val f0 = EQ_10_BAND_FREQS_HZ[i].toDouble()
            val f1 = EQ_10_BAND_FREQS_HZ[i + 1].toDouble()
            if (target in f0..f1) {
                val t = ((logTarget - ln(f0)) / (ln(f1) - ln(f0))).toFloat().coerceIn(0f, 1f)
                return safeGains[i] + (safeGains[i + 1] - safeGains[i]) * t
            }
        }
        return 0f
    }

    fun map10BandsToDeviceEqLevels(
        gainsDb: List<Float>,
        deviceCenterFreqsHz: List<Int>,
        minMillibel: Int,
        maxMillibel: Int
    ): List<Int> {
        if (deviceCenterFreqsHz.isEmpty()) return emptyList()
        val lowMb = minOf(minMillibel, maxMillibel)
        val highMb = maxOf(minMillibel, maxMillibel)
        return deviceCenterFreqsHz.map { centerHz ->
            val gainDb = interpolateGainAtFreqDb(gainsDb, centerHz)
            (gainDb * 100f).roundToInt().coerceIn(lowMb, highMb)
        }
    }

    fun computeEqParityReport(
        gainsDb: List<Float>,
        using10BandDynamicsProcessing: Boolean,
        deviceCenterFreqsHz: List<Int> = emptyList(),
        minMillibel: Int = -1500,
        maxMillibel: Int = 1500,
        isEnglish: Boolean = false
    ): EqParityReport {
        val safeGains = normalizeBandGains(gainsDb)
        if (using10BandDynamicsProcessing) {
            return EqParityReport(
                using10BandDynamicsProcessing = true,
                liveBandCount = 10,
                maxDeltaDb = 0f,
                meanDeltaDb = 0f,
                summaryId = "Paritas 1:1 (DynamicsProcessing 10-band ↔ FFmpeg equalizer 1-oktaf + limiter -0.5 dB).",
                summaryEn = "1:1 parity (DynamicsProcessing 10-band ↔ FFmpeg 1-octave equalizer + -0.5 dB limiter)."
            )
        }

        val fallbackBands = deviceCenterFreqsHz.ifEmpty { listOf(60, 230, 910, 3600, 14000) }
        val mappedDb = map10BandsToDeviceEqLevels(safeGains, fallbackBands, minMillibel, maxMillibel)
            .map { it / 100f }

        // Evaluate interpolated curve error across all 10 ISO center frequencies
        val deltas = EQ_10_BAND_FREQS_HZ.mapIndexed { idx, isoFreq ->
            val targetDb = safeGains[idx]
            val approxDb = interpolateDeviceCurveAtFreq(fallbackBands, mappedDb, isoFreq)
            abs(targetDb - approxDb)
        }
        val maxDelta = deltas.maxOrNull() ?: 0f
        val meanDelta = if (deltas.isNotEmpty()) deltas.sum() / deltas.size else 0f
        return EqParityReport(
            using10BandDynamicsProcessing = false,
            liveBandCount = fallbackBands.size,
            maxDeltaDb = maxDelta,
            meanDeltaDb = meanDelta,
            summaryId = String.format(
                Locale.US,
                "Mode fallback %d-band (API < 28/perangkat): deviasi rata-rata %.1f dB, maks %.1f dB vs ekspor penuh 10-band.",
                fallbackBands.size,
                meanDelta,
                maxDelta
            ),
            summaryEn = String.format(
                Locale.US,
                "%d-band fallback mode: mean delta %.1f dB, max %.1f dB vs full 10-band FFmpeg export.",
                fallbackBands.size,
                meanDelta,
                maxDelta
            )
        )
    }

    private fun interpolateDeviceCurveAtFreq(
        deviceFreqsHz: List<Int>,
        deviceGainsDb: List<Float>,
        targetFreqHz: Int
    ): Float {
        if (deviceFreqsHz.isEmpty()) return 0f
        if (deviceFreqsHz.size == 1) return deviceGainsDb.first()
        val sorted = deviceFreqsHz.zip(deviceGainsDb).sortedBy { it.first }
        val target = targetFreqHz.toDouble()
        if (target <= sorted.first().first) return sorted.first().second
        if (target >= sorted.last().first) return sorted.last().second
        val logT = ln(target)
        for (i in 0 until sorted.size - 1) {
            val (f0, g0) = sorted[i]
            val (f1, g1) = sorted[i + 1]
            if (target in f0.toDouble()..f1.toDouble() && f1 > f0) {
                val t = ((logT - ln(f0.toDouble())) / (ln(f1.toDouble()) - ln(f0.toDouble()))).toFloat()
                return g0 + (g1 - g0) * t
            }
        }
        return sorted.last().second
    }

    fun resolveMasteringPreset(preset: String): MasteringPresetSpec {
        val clean = preset.lowercase(Locale.US)
        return when {
            clean.contains("broadcast") || clean.contains("vocal") -> MasteringPresetSpec(
                id = "Vocal Broadcast",
                thresholdDb = -20f,
                ratio = 4.0f,
                ceilingDb = -1.0f,
                makeupDb = 4.0f,
                warmthDb = 1.5f,
                presenceDb = 3.0f,
                airDb = 2.0f,
                stereoWidth = 1.05f
            )
            clean.contains("club") || clean.contains("punch") -> MasteringPresetSpec(
                id = "Club Punch",
                thresholdDb = -16f,
                ratio = 4.5f,
                ceilingDb = -0.3f,
                makeupDb = 4.5f,
                warmthDb = 3.5f,
                presenceDb = 1.5f,
                airDb = 3.0f,
                stereoWidth = 1.22f
            )
            clean.contains("acoustic") || clean.contains("clear") -> MasteringPresetSpec(
                id = "Acoustic Clear",
                thresholdDb = -22f,
                ratio = 2.5f,
                ceilingDb = -1.2f,
                makeupDb = 2.0f,
                warmthDb = 1.0f,
                presenceDb = 2.0f,
                airDb = 2.5f,
                stereoWidth = 1.12f
            )
            else -> MasteringPresetSpec(
                id = "Studio Warm",
                thresholdDb = -18f,
                ratio = 3.0f,
                ceilingDb = -1.0f,
                makeupDb = 3.0f,
                warmthDb = 2.5f,
                presenceDb = 1.5f,
                airDb = 2.0f,
                stereoWidth = 1.14f
            )
        }
    }

    fun buildMasteringFilter(
        preset: String,
        ratio: Float,
        thresholdDb: Float,
        ceilingDb: Float,
        makeupDb: Float,
        warmthEnhance: Boolean
    ): String {
        val spec = resolveMasteringPreset(preset)
        val safeRatio = ratio.coerceIn(1.2f, 12.0f)
        val threshLin = dbToLinearAmplitude(thresholdDb.coerceIn(-40f, -3f)).coerceIn(0.001, 0.99)
        val ceilLin = dbToLinearAmplitude(ceilingDb.coerceIn(-6f, -0.1f)).coerceIn(0.0625, 0.995)
        val makeupLin = dbToLinearAmplitude(makeupDb.coerceIn(0f, 12f)).coerceIn(1.0, 4.0)

        return buildList {
            add("aformat=channel_layouts=stereo")
            add("highpass=f=30")
            if (warmthEnhance) {
                add(String.format(Locale.US, "bass=g=%.1f:f=95:w=0.6", spec.warmthDb))
                add(String.format(Locale.US, "equalizer=f=2800:t=q:w=1.2:g=%.1f", spec.presenceDb))
                add(String.format(Locale.US, "treble=g=%.1f:f=10500", spec.airDb))
                add(String.format(Locale.US, "stereotools=mlev=1.0:slev=%.2f", spec.stereoWidth))
            }
            add(
                String.format(
                    Locale.US,
                    "acompressor=threshold=%.4f:ratio=%.2f:attack=12:release=140:makeup=1",
                    threshLin,
                    safeRatio
                )
            )
            if (makeupDb >= 0.05f) {
                add(String.format(Locale.US, "volume=%.4f", makeupLin))
            }
            add(String.format(Locale.US, "alimiter=limit=%.4f:attack=5:release=50:level=disabled", ceilLin))
        }.joinToString(",")
    }

    fun buildNoiseReductionFilter(
        strengthPct: Float,
        noiseFloorDb: Float,
        mode: String
    ): String {
        val s = strengthPct.coerceIn(10f, 100f)
        val nrDb = 6f + ((s - 10f) / 90f) * 39f // 6..45 dB reduction
        val nf = noiseFloorDb.coerceIn(-80f, -20f)
        val cleanMode = mode.lowercase(Locale.US)
        return buildList {
            if (cleanMode.contains("jernih") || cleanMode.contains("speech") || cleanMode.contains("agres")) {
                add("highpass=f=85")
            }
            add(String.format(Locale.US, "afftdn=nr=%.1f:nf=%.1f", nrDb, nf))
            if (cleanMode.contains("agres")) {
                add(String.format(Locale.US, "afftdn=nr=%.1f:nf=%.1f", (nrDb * 0.55f).coerceIn(4f, 25f), nf))
            }
        }.joinToString(",")
    }

    fun validateVocalRemoverInput(
        probe: MediaProbeResult,
        mode: String,
        isEnglish: Boolean
    ): String? {
        val clean = mode.lowercase(Locale.US)
        if (clean.contains("ai") || clean.contains("demucs")) {
            return if (isEnglish) {
                "AI Vocal Separation (Demucs) is scheduled for Phase 11 after ONNX model integration. Switch to Fast Mode (Center Cancellation) for instant local processing."
            } else {
                "Pemisahan Vokal AI (Demucs) dijadwalkan pada Fase 11 bersama integrasi model ONNX. Pilih Mode Cepat (Kanselasi Tengah) untuk pemrosesan lokal langsung."
            }
        }
        if (probe.audioChannels in 1..1) {
            return if (isEnglish) {
                "Fast center cancellation requires stereo audio (2 channels). Selected file is mono (1 channel)."
            } else {
                "Mode cepat kanselasi tengah membutuhkan audio stereo (2 kanal). File masukan terdeteksi mono (1 kanal)."
            }
        }
        return null
    }

    fun buildVocalRemoverFilterComplex(
        mode: String,
        preserveBass: Boolean,
        strengthPct: Float
    ): String {
        val clean = mode.lowercase(Locale.US)
        val alpha = (strengthPct.coerceIn(50f, 100f) / 100f)
        if (clean.contains("vokal") || clean.contains("acapella")) {
            val slev = (1f - alpha * 0.92f).coerceIn(0.05f, 0.5f)
            return String.format(
                Locale.US,
                "[0:a]aformat=channel_layouts=stereo,stereotools=mlev=1.0:slev=%.2f,highpass=f=150,lowpass=f=7000,alimiter=limit=0.95:level=disabled[aout]",
                slev
            )
        }
        val cancelExpr = String.format(Locale.US, "pan=stereo|c0=c0-%.2f*c1|c1=c1-%.2f*c0", alpha, alpha)
        return if (preserveBass) {
            "[0:a]aformat=channel_layouts=stereo,asplit=2[lowin][highin];" +
                "[lowin]lowpass=f=120,volume=0.85[bass];" +
                "[highin]highpass=f=120,$cancelExpr[inst];" +
                "[bass][inst]amix=inputs=2:normalize=0,alimiter=limit=0.95:level=disabled[aout]"
        } else {
            "[0:a]aformat=channel_layouts=stereo,$cancelExpr,alimiter=limit=0.95:level=disabled[aout]"
        }
    }

    fun build8dAudioFilter(
        rotationHz: Float,
        depthPct: Float,
        roomReverb: Boolean
    ): String {
        val hz = rotationHz.coerceIn(0.05f, 1.5f)
        val amount = (depthPct.coerceIn(20f, 100f) / 100f)
        return buildList {
            add("aformat=channel_layouts=stereo")
            add(
                String.format(
                    Locale.US,
                    "apulsator=mode=sine:hz=%.2f:amount=%.2f:width=1:offset_l=0:offset_r=0.5",
                    hz,
                    amount
                )
            )
            add("stereotools=mlev=1.0:slev=1.22")
            if (roomReverb) {
                add("aecho=0.85:0.88:32|64:0.24|0.14")
            }
            add("alimiter=limit=0.95:level=disabled")
        }.joinToString(",")
    }

    fun parseToneFrequencyHz(label: String): Int = when {
        label.contains("1000") -> 1000
        label.contains("523") -> 523
        else -> 880
    }

    fun buildBuiltInWatermarkFilterComplex(
        intervalSec: Float,
        volumePct: Float,
        toneHz: Int,
        totalDurationMs: Long
    ): String {
        val safeInterval = intervalSec.coerceIn(2f, 120f)
        val vol = (volumePct.coerceIn(5f, 100f) / 100f) * 0.45f
        val durSec = (totalDurationMs.coerceAtLeast(1_000L) / 1000.0)
        return String.format(
            Locale.US,
            "sine=f=%d:r=44100:d=%.2f,aformat=channel_layouts=stereo," +
                "volume='if(lt(mod(t,%.2f),0.28),%.4f*exp(-mod(t,%.2f)*7),0)':eval=frame[wm];" +
                "[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo[main];" +
                "[main][wm]amix=inputs=2:duration=first:normalize=0,alimiter=limit=0.95:level=disabled[aout]",
            toneHz.coerceIn(220, 4000),
            durSec,
            safeInterval,
            vol,
            safeInterval
        )
    }

    fun buildCustomWatermarkFilterComplex(
        intervalSec: Float,
        volumePct: Float,
        totalDurationMs: Long
    ): String {
        val safeInterval = intervalSec.coerceIn(2f, 120f)
        val vol = (volumePct.coerceIn(5f, 100f) / 100f)
        val durSec = (totalDurationMs.coerceAtLeast(1_000L) / 1000.0)
        return String.format(
            Locale.US,
            "[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo," +
                "apad=whole_dur=%.2f,aloop=loop=-1:size=2000000000,atrim=0:%.2f,volume=%.3f[wm];" +
                "[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo[main];" +
                "[main][wm]amix=inputs=2:duration=first:normalize=0,alimiter=limit=0.95:level=disabled[aout]",
            safeInterval,
            durSec,
            vol
        )
    }

    fun buildEnhancerFilter(
        profile: String,
        clarityPct: Float,
        warmthPct: Float,
        autoLevel: Boolean
    ): String {
        val clarityNorm = clarityPct.coerceIn(0f, 100f) / 100f
        val warmthNorm = warmthPct.coerceIn(0f, 100f) / 100f
        val clean = profile.lowercase(Locale.US)
        val presenceGain = (1.0f + clarityNorm * 4.5f)
        val airGain = (0.5f + clarityNorm * 4.0f)
        val bassGain = (warmthNorm * 4.5f)
        return buildList {
            add("aformat=channel_layouts=stereo")
            add(if (clean.contains("vokal") || clean.contains("jauh")) "highpass=f=75" else "highpass=f=38")
            if (bassGain >= 0.2f) {
                add(String.format(Locale.US, "bass=g=%.1f:f=105:w=0.6", bassGain))
            }
            add(String.format(Locale.US, "equalizer=f=3200:t=q:w=1.2:g=%.1f", presenceGain))
            add(String.format(Locale.US, "treble=g=%.1f:f=9500", airGain))
            if (clarityNorm >= 0.30f) {
                val cryst = (0.6f + clarityNorm * 1.4f).coerceIn(0.5f, 2.5f)
                add(String.format(Locale.US, "crystalizer=i=%.2f", cryst))
            }
            if (autoLevel) {
                add("dynaudnorm=f=150:g=15:p=0.9")
            }
            add("alimiter=limit=0.95:attack=5:release=50:level=disabled")
        }.joinToString(",")
    }

    fun buildEchoFilter(
        delayMs: Int,
        decay: Float,
        tapsMode: String,
        wetMix: Float
    ): String {
        val d1 = delayMs.coerceIn(50, 1200)
        val dec1 = decay.coerceIn(0.1f, 0.8f)
        val outGain = wetMix.coerceIn(0.25f, 1.0f)
        val cleanTaps = tapsMode.lowercase(Locale.US)
        val (delaysStr, decaysStr) = when {
            cleanTaps.contains("3") || cleanTaps.contains("stadion") || cleanTaps.contains("stadium") -> {
                val d2 = (d1 * 1.85f).roundToInt().coerceAtMost(2400)
                val d3 = (d1 * 2.75f).roundToInt().coerceAtMost(3500)
                val dec2 = (dec1 * 0.65f).coerceAtLeast(0.08f)
                val dec3 = (dec1 * 0.40f).coerceAtLeast(0.05f)
                "$d1|$d2|$d3" to String.format(Locale.US, "%.2f|%.2f|%.2f", dec1, dec2, dec3)
            }
            cleanTaps.contains("2") || cleanTaps.contains("ganda") || cleanTaps.contains("dual") -> {
                val d2 = (d1 * 1.8f).roundToInt().coerceAtMost(2200)
                val dec2 = (dec1 * 0.6f).coerceAtLeast(0.08f)
                "$d1|$d2" to String.format(Locale.US, "%.2f|%.2f", dec1, dec2)
            }
            else -> "$d1" to String.format(Locale.US, "%.2f", dec1)
        }
        return String.format(
            Locale.US,
            "aecho=0.80:%.2f:%s:%s,alimiter=limit=0.95:level=disabled",
            outGain,
            delaysStr,
            decaysStr
        )
    }

    fun buildReverbFilter(
        roomPreset: String,
        roomSizePct: Float,
        dampingPct: Float,
        wetPct: Float
    ): String {
        val clean = roomPreset.lowercase(Locale.US)
        val sizeScale = (roomSizePct.coerceIn(20f, 100f) / 60f)
        val baseScale = when {
            clean.contains("katedral") || clean.contains("cathedral") -> 1.45f * sizeScale
            clean.contains("aula") || clean.contains("hall") -> 1.15f * sizeScale
            clean.contains("plate") -> 0.85f * sizeScale
            else -> 0.68f * sizeScale
        }
        val wet = (wetPct.coerceIn(10f, 90f) / 100f)
        val dry = (1.0f - wet * 0.35f).coerceIn(0.45f, 1.0f)
        // High-frequency damping: 10% damping -> 14 kHz cutoff; 90% damping -> 3.2 kHz cutoff
        val dampHz = (14_000f - (dampingPct.coerceIn(10f, 90f) - 10f) * 135f).roundToInt().coerceIn(2800, 16000)

        fun scaledMs(base: Int): Int = (base * baseScale).roundToInt().coerceIn(12, 650)
        val earlyDelays = "${scaledMs(19)}|${scaledMs(31)}|${scaledMs(47)}|${scaledMs(67)}"
        val earlyDecays = String.format(Locale.US, "%.2f|%.2f|%.2f|%.2f", 0.55f * wet, 0.45f * wet, 0.38f * wet, 0.30f * wet)
        val lateDelays = "${scaledMs(97)}|${scaledMs(139)}|${scaledMs(193)}|${scaledMs(257)}"
        val lateDecays = String.format(Locale.US, "%.2f|%.2f|%.2f|%.2f", 0.42f * wet, 0.34f * wet, 0.26f * wet, 0.18f * wet)

        return String.format(
            Locale.US,
            "aformat=channel_layouts=stereo," +
                "asplit=2[dry][wet];" +
                "[wet]aecho=0.8:0.9:%s:%s,aecho=0.75:0.88:%s:%s,lowpass=f=%d,stereotools=slev=1.18[wout];" +
                "[dry]volume=%.2f[dout];" +
                "[dout][wout]amix=inputs=2:normalize=0,alimiter=limit=0.95:level=disabled",
            earlyDelays,
            earlyDecays,
            lateDelays,
            lateDecays,
            dampHz,
            dry
        )
    }

    fun resolveVisualizerConfig(
        style: String,
        colorTheme: String,
        resolution: String,
        fpsLabel: String
    ): VisualizerConfigSpec {
        val cleanStyle = style.lowercase(Locale.US)
        val styleId = when {
            cleanStyle.contains("batang") || cleanStyle.contains("bar") -> "bar_wave"
            cleanStyle.contains("spektrum") || cleanStyle.contains("spectrum") -> "spectrum"
            cleanStyle.contains("cqt") -> "cqt"
            cleanStyle.contains("vektor") || cleanStyle.contains("vector") -> "vectorscope"
            else -> "line_wave"
        }
        val cleanColor = colorTheme.lowercase(Locale.US)
        val (c1, c2, bg, specScheme) = when {
            cleanColor.contains("emerald") || cleanColor.contains("hijau") ->
                listOf("0x00BFA5", "0x69F0AE", "0x0B1518", "green")
            cleanColor.contains("violet") || cleanColor.contains("ungu") ->
                listOf("0x9D6CFF", "0xFF4081", "0x120E1D", "magma")
            cleanColor.contains("mono") || cleanColor.contains("putih") ->
                listOf("0xFFFFFF", "0x94A3B8", "0x0E1117", "channel")
            else ->
                listOf("0xFF6B2C", "0x00BFA5", "0x11141C", "fire")
        }
        val (w, h) = when {
            resolution.contains("1080x1080") -> 1080 to 1080
            resolution.contains("720x1280") -> 720 to 1280
            else -> 1280 to 720
        }
        val fps = if (fpsLabel.contains("24")) 24 else 30
        return VisualizerConfigSpec(
            styleId = styleId,
            primaryHex = c1,
            secondaryHex = c2,
            bgHex = bg,
            spectrumColorScheme = specScheme,
            width = w,
            height = h,
            fps = fps
        )
    }

    fun buildVisualizerFilterComplex(spec: VisualizerConfigSpec): String {
        val w = spec.width
        val h = spec.height
        val fps = spec.fps
        return when (spec.styleId) {
            "spectrum" -> String.format(
                Locale.US,
                "[0:a]aformat=channel_layouts=stereo,showspectrum=s=%dx%d:mode=combined:color=%s:scale=log:slide=scroll,fps=%d,format=yuv420p[vout]",
                w, h, spec.spectrumColorScheme, fps
            )
            "cqt" -> String.format(
                Locale.US,
                "[0:a]aformat=channel_layouts=stereo,showcqt=s=%dx%d:r=%d:axis_h=0:bar_g=2:sono_g=4,format=yuv420p[vout]",
                w, h, fps
            )
            "vectorscope" -> String.format(
                Locale.US,
                "color=c=%s:s=%dx%d:r=%d[bg];" +
                    "[0:a]aformat=channel_layouts=stereo,avectorscope=s=%dx%d:r=%d:mode=lissajous:draw=line:scale=lin:zoom=1.2[vis];" +
                    "[bg][vis]overlay=shortest=1:format=auto,format=yuv420p[vout]",
                spec.bgHex, w, h, fps, w, h, fps
            )
            "bar_wave" -> String.format(
                Locale.US,
                "color=c=%s:s=%dx%d:r=%d[bg];" +
                    "[0:a]aformat=channel_layouts=stereo,showwaves=s=%dx%d:mode=cline:rate=%d:colors=%s|%s[vis];" +
                    "[bg][vis]overlay=shortest=1:format=auto,format=yuv420p[vout]",
                spec.bgHex, w, h, fps, w, h, fps, spec.primaryHex, spec.secondaryHex
            )
            else -> String.format(
                Locale.US,
                "color=c=%s:s=%dx%d:r=%d[bg];" +
                    "[0:a]aformat=channel_layouts=stereo,showwaves=s=%dx%d:mode=line:rate=%d:colors=%s|%s[vis];" +
                    "[bg][vis]overlay=shortest=1:format=auto,format=yuv420p[vout]",
                spec.bgHex, w, h, fps, w, h, fps, spec.primaryHex, spec.secondaryHex
            )
        }
    }
}
