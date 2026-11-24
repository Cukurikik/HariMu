package com.harimu.app.core.engines

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 3D colour lookup table, red varying fastest (Adobe `.cube` order). */
class LutCube3D(
    val size: Int,
    /** size^3 * 3 floats, index = ((b * size + g) * size + r) * 3. */
    val data: FloatArray,
    val title: String = "LUT"
) {
    init {
        require(size in 2..64) { "Ukuran LUT harus 2-64 (ditemukan $size)" }
        require(data.size == size * size * size * 3) {
            "Data LUT tidak lengkap: butuh ${size * size * size * 3} nilai, ada ${data.size}"
        }
    }

    private fun clampAxis(value: Float): Float = value.coerceIn(0f, 1f)

    fun lookupNormalized(r: Float, g: Float, b: Float): FloatArray {
        val last = size - 1
        val rf = clampAxis(r) * last
        val gf = clampAxis(g) * last
        val bf = clampAxis(b) * last
        val r0 = floor(rf).toInt()
        val g0 = floor(gf).toInt()
        val b0 = floor(bf).toInt()
        val r1 = min(r0 + 1, last)
        val g1 = min(g0 + 1, last)
        val b1 = min(b0 + 1, last)
        val dr = rf - r0
        val dg = gf - g0
        val db = bf - b0
        val out = FloatArray(3)
        for (c in 0 until 3) {
            val c000 = at(r0, g0, b0, c)
            val c100 = at(r1, g0, b0, c)
            val c010 = at(r0, g1, b0, c)
            val c110 = at(r1, g1, b0, c)
            val c001 = at(r0, g0, b1, c)
            val c101 = at(r1, g0, b1, c)
            val c011 = at(r0, g1, b1, c)
            val c111 = at(r1, g1, b1, c)
            val c00 = c000 + (c100 - c000) * dr
            val c10 = c010 + (c110 - c010) * dr
            val c01 = c001 + (c101 - c001) * dr
            val c11 = c011 + (c111 - c011) * dr
            val c0 = c00 + (c10 - c00) * dg
            val c1 = c01 + (c11 - c01) * dg
            out[c] = c0 + (c1 - c0) * db
        }
        return out
    }

    private fun at(r: Int, g: Int, b: Int, channel: Int): Float =
        data[(((b * size) + g) * size + r) * 3 + channel]
}

object ImageLutEngine {

    /** Parses an Adobe/Resolve `.cube` 3D LUT. Throws with a readable reason on bad input. */
    fun parseCube(text: String, fallbackTitle: String = "LUT"): LutCube3D {
        var size = 0
        var title = fallbackTitle
        val values = ArrayList<Float>(1024)
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val upper = line.uppercase()
            when {
                upper.startsWith("TITLE") -> {
                    title = line.substringAfter("TITLE", "").trim().trim('"').ifBlank { fallbackTitle }
                }
                upper.startsWith("LUT_3D_SIZE") -> {
                    size = line.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: 0
                }
                upper.startsWith("LUT_1D_SIZE") -> throw IllegalArgumentException(
                    "File ini LUT 1D, bukan 3D. Fitur ini hanya mendukung LUT_3D_SIZE."
                )
                upper.startsWith("DOMAIN_MIN") || upper.startsWith("DOMAIN_MAX") -> Unit
                else -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size != 3) {
                        throw IllegalArgumentException("Baris LUT tidak valid: '$line'")
                    }
                    parts.forEach { part ->
                        values += part.toFloatOrNull()
                            ?: throw IllegalArgumentException("Angka LUT tidak valid: '$part'")
                    }
                }
            }
        }
        require(size >= 2) { "Header LUT_3D_SIZE tidak ditemukan di file .cube" }
        val expected = size * size * size * 3
        require(values.size == expected) {
            "Jumlah nilai LUT tidak cocok: butuh $expected (size $size), ada ${values.size}"
        }
        return LutCube3D(size = size, data = values.toFloatArray(), title = title)
    }

    /** Applies the LUT with a strength blend (0 = original, 100 = full LUT). */
    fun apply(source: Bitmap, lut: LutCube3D, intensityPercent: Int = 100): Bitmap {
        val intensity = intensityPercent.coerceIn(0, 100) / 100f
        if (intensity == 0f) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) continue
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val mapped = lut.lookupNormalized(r, g, b)
            val nr = ((r + (mapped[0].coerceIn(0f, 1f) - r) * intensity) * 255f).roundToInt().coerceIn(0, 255)
            val ng = ((g + (mapped[1].coerceIn(0f, 1f) - g) * intensity) * 255f).roundToInt().coerceIn(0, 255)
            val nb = ((b + (mapped[2].coerceIn(0f, 1f) - b) * intensity) * 255f).roundToInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Builds a LUT from the classic horizontal "Hald/lookup strip" PNG: width = size*size,
     * height = size, x = r + g*size, y = b.
     */
    fun fromPngStrip(strip: Bitmap, size: Int): LutCube3D {
        require(size in 2..64) { "Ukuran strip LUT harus 2-64 (ditemukan $size)" }
        val expectedW = size * size
        require(strip.width >= expectedW && strip.height >= size) {
            "Strip LUT harus minimal ${expectedW}×$size, gambar ${strip.width}×${strip.height}"
        }
        val data = FloatArray(size * size * size * 3)
        val row = IntArray(strip.width)
        for (b in 0 until size) {
            strip.getPixels(row, 0, strip.width, 0, b, strip.width, 1)
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val pixel = row[g * size + r]
                    val index = (((b * size) + g) * size + r) * 3
                    data[index] = ((pixel shr 16) and 0xFF) / 255f
                    data[index + 1] = ((pixel shr 8) and 0xFF) / 255f
                    data[index + 2] = (pixel and 0xFF) / 255f
                }
            }
        }
        return LutCube3D(size = size, data = data, title = "PNG strip")
    }

    private val presetCache = HashMap<String, LutCube3D>()

    val presetNames: List<String> = listOf("Teal & Orange", "Fade Film", "Mono Kontras", "Warm Kodak")

    fun preset(name: String): LutCube3D {
        presetCache[name]?.let { return it }
        val size = 17
        val data = FloatArray(size * size * size * 3)
        var index = 0
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val rf = r / (size - 1f)
                    val gf = g / (size - 1f)
                    val bf = b / (size - 1f)
                    val mapped = when (name) {
                        "Teal & Orange" -> floatArrayOf(
                            (rf * 1.12f + 0.02f).coerceIn(0f, 1f),
                            (gf * 1.02f + (rf - bf) * 0.04f).coerceIn(0f, 1f),
                            (bf * 0.88f + (bf - rf) * 0.10f + 0.04f).coerceIn(0f, 1f)
                        )
                        "Fade Film" -> {
                            val lift = 0.06f
                            floatArrayOf(
                                (lift + rf * (1f - lift)).coerceIn(0f, 1f),
                                (lift + gf * (1f - lift) * 0.98f).coerceIn(0f, 1f),
                                (lift + bf * (1f - lift) * 0.94f).coerceIn(0f, 1f)
                            )
                        }
                        "Mono Kontras" -> {
                            val luma = (rf * 0.299f + gf * 0.587f + bf * 0.114f)
                            val eased = ((luma - 0.5f) * 1.25f + 0.5f).coerceIn(0f, 1f)
                            floatArrayOf(eased, eased, eased)
                        }
                        else -> floatArrayOf(
                            (rf * 1.06f + 0.02f).coerceIn(0f, 1f),
                            (gf * 1.01f).coerceIn(0f, 1f),
                            (bf * 0.94f).coerceIn(0f, 1f)
                        )
                    }
                    data[index++] = mapped[0]
                    data[index++] = mapped[1]
                    data[index++] = mapped[2]
                }
            }
        }
        val lut = LutCube3D(size, data, name)
        presetCache[name] = lut
        return lut
    }

    fun maxIntensityLabel(intensityPercent: Int): String = "$intensityPercent%"
}
