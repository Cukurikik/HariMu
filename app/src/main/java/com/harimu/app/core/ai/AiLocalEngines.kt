package com.harimu.app.core.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.harimu.app.core.engines.AudioEngine
import com.harimu.app.core.engines.PcmAudio
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pemrosesan "AI lokal" FASE 11 tanpa model neural (berkas model belum tersedia, lihat
 * `docs/DECISIONS.md` §14). Semua fungsi di sini **algoritma klasik yang benar-benar
 * menghitung**, bukan mock:
 *
 * - upscale: pembesaran + unsharp mask (bukan super-resolution ESRGAN)
 * - removeBackground: flood fill dari tepi dengan toleransi warna (heuristik, bukan U²-Net)
 * - portraitBokeh: blur radial dengan pusat wajah-subjek (bukan depth map)
 * - denoise: smart blur tepi-terjaga (bukan DnCNN)
 * - splitStems: mid/side + high-pass sederhana (bukan Demucs)
 *
 * Batas kualitas ini ditulis di katalog dan dirujuk di UI setiap tool.
 */
object AiLocalEngines {

    const val MAX_UPSCALE_PIXELS = 24_000_000L
    const val MAX_BLUR_PIXELS = 8_000_000L
    const val MAX_DENOISE_PIXELS = 8_000_000L

    // ------------------------------------------------------------- upscale

    fun upscale(source: Bitmap, factor: Int, sharpenPercent: Int): Bitmap {
        val safeFactor = factor.coerceIn(2, 4)
        val targetW = source.width * safeFactor
        val targetH = source.height * safeFactor
        require(targetW.toLong() * targetH <= MAX_UPSCALE_PIXELS) {
            "Hasil ${targetW}×${targetH} px melebihi batas ${MAX_UPSCALE_PIXELS / 1_000_000} MP. " +
                "Kecilkan dulu dengan IMG-02."
        }
        var result = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        if (sharpenPercent > 0) {
            result = unsharpMask(result, sharpenPercent.coerceIn(0, 100))
        }
        return result
    }

    /** Unsharp mask 3×3: hasil = asli + amount × (asli − blur). */
    private fun unsharpMask(source: Bitmap, amountPercent: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val amount = amountPercent / 100f
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val center = pixels[index]
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        val p = pixels[yy * width + xx]
                        sumR += Color.red(p)
                        sumG += Color.green(p)
                        sumB += Color.blue(p)
                        count += 1
                    }
                }
                val r = Color.red(center) + amount * (Color.red(center) - sumR.toFloat() / count)
                val g = Color.green(center) + amount * (Color.green(center) - sumG.toFloat() / count)
                val b = Color.blue(center) + amount * (Color.blue(center) - sumB.toFloat() / count)
                out[index] = Color.argb(
                    Color.alpha(center),
                    r.roundToInt().coerceIn(0, 255),
                    g.roundToInt().coerceIn(0, 255),
                    b.roundToInt().coerceIn(0, 255)
                )
            }
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, width, 0, 0, width, height)
        if (result !== source) source.recycle()
        return result
    }

    // ------------------------------------------------------- background

    /**
     * Menghapus latar dengan flood fill dari keempat tepi: piksel yang warnanya dekat dengan
     * rata-rata warna tepi dan tersambung ke tepi dianggap latar. Objek yang menempel di tepi
     * bisa ikut terpotong — itu batas heuristik ini, bukan bug yang disembunyikan.
     */
    fun removeBackground(
        source: Bitmap,
        tolerancePercent: Int,
        featherPx: Int,
        backgroundColorHex: String
    ): Bitmap {
        val width = source.width
        val height = source.height
        require(width.toLong() * height <= MAX_BLUR_PIXELS) {
            "Gambar terlalu besar untuk pemrosesan lokal (maks ${MAX_BLUR_PIXELS / 1_000_000} MP)."
        }
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var borderCount = 0
        fun sample(x: Int, y: Int) {
            val p = pixels[y * width + x]
            sumR += Color.red(p)
            sumG += Color.green(p)
            sumB += Color.blue(p)
            borderCount += 1
        }
        for (x in 0 until width) {
            sample(x, 0)
            sample(x, height - 1)
        }
        for (y in 1 until height - 1) {
            sample(0, y)
            sample(width - 1, y)
        }
        val bgR = (sumR / borderCount).toInt()
        val bgG = (sumG / borderCount).toInt()
        val bgB = (sumB / borderCount).toInt()

        // Toleransi 2..80% dipetakan ke jarak warna 0..255.
        val threshold = 12.0 + tolerancePercent.coerceIn(0, 100) / 100.0 * 120.0
        val thresholdSq = threshold * threshold

        fun isBackground(index: Int): Boolean {
            val p = pixels[index]
            val dr = (Color.red(p) - bgR).toDouble()
            val dg = (Color.green(p) - bgG).toDouble()
            val db = (Color.blue(p) - bgB).toDouble()
            return dr * dr + dg * dg + db * db <= thresholdSq
        }

        val mask = BooleanArray(pixels.size)
        val stack = IntArray(pixels.size)
        var stackSize = 0
        fun push(index: Int) {
            if (!mask[index] && isBackground(index)) {
                mask[index] = true
                stack[stackSize++] = index
            }
        }
        for (x in 0 until width) {
            push(x)
            push((height - 1) * width + x)
        }
        for (y in 0 until height) {
            push(y * width)
            push(y * width + width - 1)
        }
        while (stackSize > 0) {
            val index = stack[--stackSize]
            val x = index % width
            val y = index / width
            if (x > 0) push(index - 1)
            if (x < width - 1) push(index + 1)
            if (y > 0) push(index - width)
            if (y < height - 1) push(index + width)
        }

        val alpha = IntArray(pixels.size) { index -> if (mask[index]) 0 else 255 }
        if (featherPx > 0) boxBlurAlpha(alpha, width, height, featherPx.coerceIn(1, 8))

        val fill = if (backgroundColorHex.isBlank() || backgroundColorHex.equals("transparan", true)) {
            null
        } else {
            runCatching {
                Color.parseColor(
                    if (backgroundColorHex.startsWith("#")) backgroundColorHex else "#$backgroundColorHex"
                )
            }.getOrNull()
        }
        if (fill != null) {
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val outPixels = IntArray(pixels.size) { index ->
                val a = alpha[index]
                val p = pixels[index]
                val r = (Color.red(p) * a + Color.red(fill) * (255 - a)) / 255
                val g = (Color.green(p) * a + Color.green(fill) * (255 - a)) / 255
                val b = (Color.blue(p) * a + Color.blue(fill) * (255 - a)) / 255
                Color.argb(255, r, g, b)
            }
            out.setPixels(outPixels, 0, width, 0, 0, width, height)
            return out
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(pixels.size) { index ->
            val p = pixels[index]
            Color.argb(alpha[index], Color.red(p), Color.green(p), Color.blue(p))
        }
        out.setPixels(outPixels, 0, width, 0, 0, width, height)
        return out
    }

    private fun boxBlurAlpha(alpha: IntArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) return
        val temp = IntArray(alpha.size)
        for (y in 0 until height) {
            var sum = 0
            for (x in -radius..radius) sum += alpha[y * width + x.coerceIn(0, width - 1)]
            for (x in 0 until width) {
                temp[y * width + x] = sum / (radius * 2 + 1)
                sum -= alpha[y * width + (x - radius).coerceIn(0, width - 1)]
                sum += alpha[y * width + (x + radius + 1).coerceIn(0, width - 1)]
            }
        }
        for (x in 0 until width) {
            var sum = 0
            for (y in -radius..radius) sum += temp[y.coerceIn(0, height - 1) * width + x]
            for (y in 0 until height) {
                alpha[y * width + x] = sum / (radius * 2 + 1)
                sum -= temp[(y - radius).coerceIn(0, height - 1) * width + x]
                sum += temp[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            }
        }
    }

    // ----------------------------------------------------------- bokeh

    fun portraitBokeh(
        source: Bitmap,
        focusXPct: Float,
        focusYPct: Float,
        radiusPct: Int,
        strengthPct: Int
    ): Bitmap {
        val width = source.width
        val height = source.height
        require(width.toLong() * height <= MAX_BLUR_PIXELS) {
            "Gambar terlalu besar untuk bokeh lokal (maks ${MAX_BLUR_PIXELS / 1_000_000} MP)."
        }
        val small = Bitmap.createScaledBitmap(
            source,
            max(1, width / 8),
            max(1, height / 8),
            true
        )
        val blurredSmall = stackBlur(small, 6)
        val blurred = Bitmap.createScaledBitmap(blurredSmall, width, height, true)
        if (blurredSmall !== small) blurredSmall.recycle()
        if (small !== source) small.recycle()

        val original = IntArray(width * height)
        val blur = IntArray(width * height)
        source.getPixels(original, 0, width, 0, 0, width, height)
        blurred.getPixels(blur, 0, width, 0, 0, width, height)
        blurred.recycle()

        val cx = width * focusXPct.coerceIn(0f, 100f) / 100f
        val cy = height * focusYPct.coerceIn(0f, 100f) / 100f
        val radius = max(width, height) * radiusPct.coerceIn(5, 90) / 100f
        val strength = strengthPct.coerceIn(0, 100) / 100f
        val out = IntArray(original.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val dx = x - cx
                val dy = y - cy
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val t = ((distance - radius) / (radius * 0.6f)).coerceIn(0f, 1f)
                val blend = t * t * (3 - 2 * t) * strength
                val a = original[index]
                val b = blur[index]
                out[index] = Color.argb(
                    Color.alpha(a),
                    (Color.red(a) * (1 - blend) + Color.red(b) * blend).roundToInt(),
                    (Color.green(a) * (1 - blend) + Color.green(b) * blend).roundToInt(),
                    (Color.blue(a) * (1 - blend) + Color.blue(b) * blend).roundToInt()
                )
            }
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, width, 0, 0, width, height)
        return result
    }

    // ---------------------------------------------------------- denoise

    fun denoise(source: Bitmap, strengthPercent: Int, detailPercent: Int): Bitmap {
        val width = source.width
        val height = source.height
        require(width.toLong() * height <= MAX_DENOISE_PIXELS) {
            "Gambar terlalu besar untuk denoise lokal (maks ${MAX_DENOISE_PIXELS / 1_000_000} MP)."
        }
        val radius = (strengthPercent.coerceIn(0, 100) / 20) + 1
        val blurred = stackBlur(source, radius)
        val original = IntArray(width * height)
        val blur = IntArray(width * height)
        source.getPixels(original, 0, width, 0, 0, width, height)
        blurred.getPixels(blur, 0, width, 0, 0, width, height)
        if (blurred !== source) blurred.recycle()

        // Detail menentukan ambang tepi: makin tinggi, makin sedikit piksel yang ikut dihaluskan.
        val edgeThreshold = 6.0 + (100 - detailPercent.coerceIn(0, 100)) * 0.6
        val out = IntArray(original.size)
        for (index in original.indices) {
            val a = original[index]
            val b = blur[index]
            val diff = (abs(Color.red(a) - Color.red(b)) +
                abs(Color.green(a) - Color.green(b)) +
                abs(Color.blue(a) - Color.blue(b))) / 3.0
            out[index] = if (diff <= edgeThreshold) b else a
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, width, 0, 0, width, height)
        return result
    }

    /** Box blur berulang (3 pass) — pendekatan gaussian yang cepat tanpa library. */
    private fun stackBlur(source: Bitmap, radius: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val safeRadius = radius.coerceIn(0, 40)
        if (safeRadius == 0) return source.copy(Bitmap.Config.ARGB_8888, false)

        var current = pixels
        repeat(3) {
            current = boxBlurRgb(current, width, height, safeRadius)
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(current, 0, width, 0, 0, width, height)
        return out
    }

    private fun boxBlurRgb(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val horizontal = IntArray(pixels.size)
        val window = radius * 2 + 1
        for (y in 0 until height) {
            var sumR = 0
            var sumG = 0
            var sumB = 0
            var sumA = 0
            for (x in -radius..radius) {
                val p = pixels[y * width + x.coerceIn(0, width - 1)]
                sumR += Color.red(p)
                sumG += Color.green(p)
                sumB += Color.blue(p)
                sumA += Color.alpha(p)
            }
            for (x in 0 until width) {
                horizontal[y * width + x] = Color.argb(
                    sumA / window,
                    sumR / window,
                    sumG / window,
                    sumB / window
                )
                val remove = pixels[y * width + (x - radius).coerceIn(0, width - 1)]
                val add = pixels[y * width + (x + radius + 1).coerceIn(0, width - 1)]
                sumR += Color.red(add) - Color.red(remove)
                sumG += Color.green(add) - Color.green(remove)
                sumB += Color.blue(add) - Color.blue(remove)
                sumA += Color.alpha(add) - Color.alpha(remove)
            }
        }
        val vertical = IntArray(pixels.size)
        for (x in 0 until width) {
            var sumR = 0
            var sumG = 0
            var sumB = 0
            var sumA = 0
            for (y in -radius..radius) {
                val p = horizontal[y.coerceIn(0, height - 1) * width + x]
                sumR += Color.red(p)
                sumG += Color.green(p)
                sumB += Color.blue(p)
                sumA += Color.alpha(p)
            }
            for (y in 0 until height) {
                vertical[y * width + x] = Color.argb(
                    sumA / window,
                    sumR / window,
                    sumG / window,
                    sumB / window
                )
                val remove = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                val add = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                sumR += Color.red(add) - Color.red(remove)
                sumG += Color.green(add) - Color.green(remove)
                sumB += Color.blue(add) - Color.blue(remove)
                sumA += Color.alpha(add) - Color.alpha(remove)
            }
        }
        return vertical
    }

    // ----------------------------------------------------------- stems

    data class StemResult(val vocals: File, val instrumental: File, val channels: Int)

    /**
     * Pemisahan 2 jalur: vokal ≈ kanal tengah (L+R)/2, instrumen ≈ sisi (L−R)/2.
     * Ini trik mid/side klasik, bukan Demucs — hasil terbaik pada rekaman studio dengan vokal
     * center-panned. Batas ini ditulis di UI dan katalog.
     */
    fun splitStems(pcm: PcmAudio, destDir: File, instrumentalOnly: Boolean = false): StemResult {
        require(pcm.channels >= 1) { "Audio tanpa kanal" }
        destDir.mkdirs()
        val frames = pcm.samples.size / pcm.channels
        val vocals = ShortArray(frames * 2) { 0 }
        val instrumental = ShortArray(frames * 2) { 0 }
        for (frame in 0 until frames) {
            val left = pcm.samples[frame * pcm.channels].toInt()
            val right = if (pcm.channels > 1) pcm.samples[frame * pcm.channels + 1].toInt() else left
            val mid = ((left + right) / 2).coerceIn(-32768, 32767)
            val side = ((left - right) / 2).coerceIn(-32768, 32767)
            vocals[frame * 2] = mid.toShort()
            vocals[frame * 2 + 1] = mid.toShort()
            instrumental[frame * 2] = side.toShort()
            instrumental[frame * 2 + 1] = side.toShort()
        }
        val vocalsFile = File(destDir, if (instrumentalOnly) "instrumental.wav" else "vokal.wav")
        val instrumentalFile = File(destDir, "instrumental.wav")
        if (!instrumentalOnly) {
            AudioEngine.writeWav(PcmAudio(vocals, pcm.sampleRate, 2), vocalsFile)
        }
        AudioEngine.writeWav(PcmAudio(instrumental, pcm.sampleRate, 2), instrumentalFile)
        return StemResult(vocalsFile, instrumentalFile, 2)
    }

    /** Skala 0..255 → alpha untuk compositing cepat. */
    fun clampColor(value: Int): Int = value.coerceIn(0, 255)

    fun noteForUpscale(factor: Int): String =
        "Pembesaran ${factor}× + unsharp mask. Bukan model super-resolution."
}
