package com.harimu.app.core.engines

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Single filter pipeline shared by the live GPU preview and the full-resolution export.
 *
 * - Colour work is described by [ImageFilterSpec] and compiled into an Android [ColorMatrix].
 *   The preview wraps the exact same 20 values in `ColorFilter.colorMatrix(...)` (GPU shader),
 *   the export wraps them in [ColorMatrixColorFilter] (Canvas). One source of truth, so the
 *   preview can never drift from the exported file.
 * - Blur and vignette are not expressible as a colour matrix; they are applied as an extra
 *   pass in [apply] and documented in `docs/DECISIONS.md` §13.
 */
data class ImageFilterSpec(
    val preset: String = "Normal",
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val blurRadius: Int = 0,
    val vignette: Int = 0,
    val tintHex: String = "",
    val tintOpacity: Int = 0
)

object ImageFilterPipeline {

    val presets: List<String> = listOf(
        "Normal", "Grayscale", "Sepia", "Invert", "Warm", "Cool", "Vintage", "Mono Kontras"
    )

    fun buildColorMatrix(spec: ImageFilterSpec): ColorMatrix {
        val matrix = ColorMatrix()
        when (spec.preset.lowercase()) {
            "grayscale" -> matrix.setSaturation(0f)
            "sepia" -> {
                matrix.setSaturation(0f)
                matrix.postConcat(ColorMatrix().apply { setScale(1.07f, 0.92f, 0.72f, 1f) })
            }
            "invert" -> matrix.set(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            "warm" -> matrix.postConcat(ColorMatrix().apply { setScale(1.08f, 1.02f, 0.90f, 1f) })
            "cool" -> matrix.postConcat(ColorMatrix().apply { setScale(0.92f, 1.00f, 1.12f, 1f) })
            "vintage" -> {
                matrix.setSaturation(0.75f)
                matrix.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.05f, 0f, 0f, 0f, 12f,
                            0f, 1.00f, 0f, 0f, 8f,
                            0f, 0f, 0.86f, 0f, 18f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            "mono kontras" -> {
                matrix.setSaturation(0f)
                matrix.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.25f, 0f, 0f, 0f, -32f,
                            0f, 1.25f, 0f, 0f, -32f,
                            0f, 0f, 1.25f, 0f, -32f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
        }
        if (spec.saturation != 1f) {
            matrix.postConcat(ColorMatrix().apply { setSaturation(spec.saturation.coerceIn(0f, 3f)) })
        }
        val contrast = spec.contrast.coerceIn(0.2f, 3f)
        if (contrast != 1f || spec.brightness != 0f) {
            val offset = (1f - contrast) * 127.5f + spec.brightness.coerceIn(-100f, 100f) * 2.55f
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, offset,
                        0f, contrast, 0f, 0f, offset,
                        0f, 0f, contrast, 0f, offset,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        if (spec.temperature != 0f) {
            val shift = spec.temperature.coerceIn(-100f, 100f) / 100f * 0.15f
            matrix.postConcat(ColorMatrix().apply { setScale(1f + shift, 1f, 1f - shift, 1f) })
        }
        return matrix
    }

    /** The 20 values consumed by both Compose `ColorFilter.colorMatrix` and the Canvas export. */
    fun matrixValues(spec: ImageFilterSpec): FloatArray = buildColorMatrix(spec).array

    fun apply(source: Bitmap, spec: ImageFilterSpec): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildColorMatrix(spec))
            isFilterBitmap = true
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        if (spec.tintOpacity > 0 && spec.tintHex.isNotBlank()) {
            val tint = ImageEngine.parseColorSafe(spec.tintHex, Color.TRANSPARENT)
            val tintPaint = Paint().apply {
                color = tint
                alpha = (spec.tintOpacity.coerceIn(0, 100) / 100f * 255).roundToInt()
            }
            canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), tintPaint)
        }
        if (spec.blurRadius > 0) {
            val radius = (spec.blurRadius.coerceIn(1, 40) * max(out.width, out.height) / 1200f)
                .roundToInt()
                .coerceIn(1, 60)
            blurInPlace(out, radius)
        }
        if (spec.vignette > 0) {
            drawVignette(canvas, out.width, out.height, spec.vignette)
        }
        return out
    }

    /** Preview render: identical spec, smaller pixels. Caller recycles the result. */
    fun applyPreview(source: Bitmap, spec: ImageFilterSpec, maxDim: Int): Bitmap {
        if (max(source.width, source.height) <= maxDim) return apply(source, spec)
        val scale = maxDim.toFloat() / max(source.width, source.height)
        val smallW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val smallH = (source.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        val filtered = apply(small, spec)
        if (small !== source) small.recycle()
        return filtered
    }

    // ------------------------------------------------------------------ blur

    fun blurInPlace(bitmap: Bitmap, radius: Int) {
        if (radius <= 0 || bitmap.width <= 1 || bitmap.height <= 1) return
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        boxBlurPass(pixels, width, height, radius)
        boxBlurPass(pixels, width, height, radius)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /** Returns a blurred copy; the source is left untouched. */
    fun blurredCopy(source: Bitmap, radius: Int): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, 0f, 0f, null)
        blurInPlace(out, radius)
        return out
    }

    private fun boxBlurPass(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val r = radius.coerceAtLeast(1)
        val temp = IntArray(pixels.size)
        blurRows(pixels, temp, width, height, r)
        blurColumns(temp, pixels, width, height, r)
    }

    private fun blurRows(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (i in -radius..radius) {
                val p = src[row + i.coerceIn(0, width - 1)]
                val a = (p ushr 24) and 0xFF
                sumA += a
                sumR += (((p shr 16) and 0xFF) * a + 127) / 255
                sumG += (((p shr 8) and 0xFF) * a + 127) / 255
                sumB += ((p and 0xFF) * a + 127) / 255
            }
            for (x in 0 until width) {
                val a = sumA / window
                val r = (if (sumA == 0) 0 else (sumR * 255 + sumA / 2) / sumA).coerceIn(0, 255)
                val g = (if (sumA == 0) 0 else (sumG * 255 + sumA / 2) / sumA).coerceIn(0, 255)
                val b = (if (sumA == 0) 0 else (sumB * 255 + sumA / 2) / sumA).coerceIn(0, 255)
                dst[row + x] = (a shl 24) or (r shl 16) or (g shl 8) or b

                val outIdx = (x - radius).coerceIn(0, width - 1)
                val inIdx = (x + radius + 1).coerceIn(0, width - 1)
                val outP = src[row + outIdx]
                val inP = src[row + inIdx]
                val outA = (outP ushr 24) and 0xFF
                val inA = (inP ushr 24) and 0xFF
                sumA += inA - outA
                sumR += (((inP shr 16) and 0xFF) * inA + 127) / 255 - (((outP shr 16) and 0xFF) * outA + 127) / 255
                sumG += (((inP shr 8) and 0xFF) * inA + 127) / 255 - (((outP shr 8) and 0xFF) * outA + 127) / 255
                sumB += ((inP and 0xFF) * inA + 127) / 255 - ((outP and 0xFF) * outA + 127) / 255
            }
        }
    }

    private fun blurColumns(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until width) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (i in -radius..radius) {
                val p = src[i.coerceIn(0, height - 1) * width + x]
                val a = (p ushr 24) and 0xFF
                sumA += a
                sumR += (((p shr 16) and 0xFF) * a + 127) / 255
                sumG += (((p shr 8) and 0xFF) * a + 127) / 255
                sumB += ((p and 0xFF) * a + 127) / 255
            }
            for (y in 0 until height) {
                val a = sumA / window
                val r = (if (sumA == 0) 0 else (sumR * 255 + sumA / 2) / sumA).coerceIn(0, 255)
                val g = (if (sumA == 0) 0 else (sumG * 255 + sumA / 2) / sumA).coerceIn(0, 255)
                val b = (if (sumA == 0) 0 else (sumB * 255 + sumA / 2) / sumA).coerceIn(0, 255)
                dst[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b

                val outIdx = (y - radius).coerceIn(0, height - 1)
                val inIdx = (y + radius + 1).coerceIn(0, height - 1)
                val outP = src[outIdx * width + x]
                val inP = src[inIdx * width + x]
                val outA = (outP ushr 24) and 0xFF
                val inA = (inP ushr 24) and 0xFF
                sumA += inA - outA
                sumR += (((inP shr 16) and 0xFF) * inA + 127) / 255 - (((outP shr 16) and 0xFF) * outA + 127) / 255
                sumG += (((inP shr 8) and 0xFF) * inA + 127) / 255 - (((outP shr 8) and 0xFF) * outA + 127) / 255
                sumB += ((inP and 0xFF) * inA + 127) / 255 - ((outP and 0xFF) * outA + 127) / 255
            }
        }
    }

    fun drawVignette(canvas: Canvas, width: Int, height: Int, strengthPct: Int) {
        val radius = hypot(width / 2.0, height / 2.0).toFloat().coerceAtLeast(1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width / 2f,
                height / 2f,
                radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            alpha = (strengthPct.coerceIn(0, 100) * 2.2f).roundToInt().coerceIn(0, 235)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /** Gaussian-ish grain: deterministic per size so preview and export match. */
    fun addGrain(bitmap: Bitmap, amountPercent: Int) {
        if (amountPercent <= 0) return
        val amplitude = (amountPercent.coerceIn(0, 100) / 100f * 46f).roundToInt()
        if (amplitude <= 0) return
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var seed = (width.toLong() * 73856093L) xor (height.toLong() * 19349663L) xor 0x9E3779B97F4A7C15uL.toLong()
        for (i in pixels.indices) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val noise = (((seed ushr 33) % (amplitude * 2 + 1)) - amplitude).toInt()
            val p = pixels[i]
            val r = (((p shr 16) and 0xFF) + noise).coerceIn(0, 255)
            val g = (((p shr 8) and 0xFF) + noise).coerceIn(0, 255)
            val b = ((p and 0xFF) + noise).coerceIn(0, 255)
            pixels[i] = (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /** Scales a blur radius (px) from a preview size to the export size. */
    fun scaledRadius(radiusPct: Float, longEdge: Int, minPx: Int = 1): Int {
        val lower = minPx.coerceAtLeast(1)
        val upper = min(60, longEdge / 4).coerceAtLeast(lower)
        return ((radiusPct.coerceIn(0f, 20f) / 100f) * longEdge)
            .roundToInt()
            .coerceIn(lower, upper)
    }
}
