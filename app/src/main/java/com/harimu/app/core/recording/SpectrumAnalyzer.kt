package com.harimu.app.core.recording

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Analisis spektrum untuk visualisasi Voice Recorder (CORE-06).
 *
 * FFT radix-2 iteratif (Cooley–Tukey) pada jendela Hann, lalu 40 pita logaritmik 40 Hz–16 kHz
 * dipetakan ke 0..1. Murni Kotlin sehingga bisa diuji unit. Tidak memakai library FFT eksternal.
 *
 * Batas: resolusi terbatas pada [FFT_SIZE] (1024) sampel; nilai adalah magnitudo relatif, bukan
 * dBFS terkalibrasi.
 */
object SpectrumAnalyzer {

    const val FFT_SIZE = 1024
    const val BANDS = 40
    private const val MIN_HZ = 40.0
    private const val MAX_HZ = 16_000.0

    private val window = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1)) }
    private val bitReverse = IntArray(FFT_SIZE).also { table ->
        val bits = Integer.numberOfTrailingZeros(FFT_SIZE)
        for (i in 0 until FFT_SIZE) {
            var value = 0
            for (b in 0 until bits) {
                if (i and (1 shl b) != 0) value = value or (1 shl (bits - 1 - b))
            }
            table[i] = value
        }
    }

    /** Hitung 40 pita spektrum dari sampel PCM 16-bit. */
    fun analyze(samples: ShortArray, sampleRate: Int, bandCount: Int = BANDS): FloatArray {
        val size = min(samples.size, FFT_SIZE)
        require(size > 1) { "Sampel terlalu sedikit" }
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        for (i in 0 until size) {
            real[i] = (samples[i] / 32768.0) * window[i]
        }
        fft(real, imag)
        val bins = FFT_SIZE / 2
        val magnitudes = DoubleArray(bins) { i -> hypot(real[i], imag[i]) }
        val result = FloatArray(bandCount)
        val nyquist = sampleRate / 2.0
        for (band in 0 until bandCount) {
            val lowHz = MIN_HZ * (MAX_HZ / MIN_HZ).pow(band.toDouble() / bandCount)
            val highHz = MIN_HZ * (MAX_HZ / MIN_HZ).pow((band + 1).toDouble() / bandCount)
            val lowBin = ((lowHz / nyquist) * bins).toInt().coerceIn(0, bins - 1)
            val highBin = ((highHz / nyquist) * bins).toInt().coerceIn(lowBin + 1, bins)
            var peak = 0.0
            for (bin in lowBin until highBin) peak = max(peak, magnitudes[bin])
            // Skala log: -60 dB..0 dB → 0..1
            val db = 20.0 * log10(peak.coerceAtLeast(1e-6))
            result[band] = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
        }
        return result
    }

    /** RMS sederhana untuk indikator level. */
    fun rms(samples: ShortArray, count: Int = samples.size): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val v = samples[i] / 32768.0
            sum += v * v
        }
        return kotlin.math.sqrt(sum / count).toFloat().coerceIn(0f, 1f)
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = FFT_SIZE
        for (i in 0 until n) {
            val j = bitReverse[i]
            if (j > i) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wReal = cos(angle)
            val wImag = kotlin.math.sin(angle)
            var start = 0
            while (start < n) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until length / 2) {
                    val i = start + k
                    val j = i + length / 2
                    val ur = real[i]
                    val ui = imag[i]
                    val vr = real[j] * wr - imag[j] * wi
                    val vi = real[j] * wi + imag[j] * wr
                    real[i] = ur + vr
                    imag[i] = ui + vi
                    real[j] = ur - vr
                    imag[j] = ui - vi
                    val nextWr = wr * wReal - wi * wImag
                    wi = wr * wImag + wi * wReal
                    wr = nextWr
                }
                start += length
            }
            length *= 2
        }
    }
}
