package com.harimu.app.core.engines

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Blend modes shared by IMG-32, IMG-47 layers and any future compositing tool. */
object ImageBlendModes {

    val labels: List<String> = listOf(
        "Normal", "Multiply", "Screen", "Overlay", "Darken", "Lighten", "Add", "Difference", "Exclusion"
    )

    fun porterDuff(label: String): PorterDuff.Mode = when (label.trim().lowercase()) {
        "multiply" -> PorterDuff.Mode.MULTIPLY
        "screen" -> PorterDuff.Mode.SCREEN
        "overlay" -> PorterDuff.Mode.OVERLAY
        "darken" -> PorterDuff.Mode.DARKEN
        "lighten" -> PorterDuff.Mode.LIGHTEN
        "add", "linear dodge" -> PorterDuff.Mode.ADD
        "difference", "exclusion" -> PorterDuff.Mode.XOR
        else -> PorterDuff.Mode.SRC_OVER
    }

    fun applyTo(paint: Paint, label: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode = when (label.trim().lowercase()) {
                "multiply" -> BlendMode.MULTIPLY
                "screen" -> BlendMode.SCREEN
                "overlay" -> BlendMode.OVERLAY
                "darken" -> BlendMode.DARKEN
                "lighten" -> BlendMode.LIGHTEN
                "add", "linear dodge" -> BlendMode.PLUS
                "difference" -> BlendMode.DIFFERENCE
                "exclusion" -> BlendMode.EXCLUSION
                else -> BlendMode.SRC_OVER
            }
        } else {
            paint.xfermode = PorterDuffXfermode(porterDuff(label))
        }
    }
}

object ImageOverlayEngine {

    // ------------------------------------------------------------- IMG-32

    fun blend(
        base: Bitmap,
        top: Bitmap,
        blendMode: String,
        opacityPct: Int,
        scalePct: Int,
        offsetXPct: Float,
        offsetYPct: Float
    ): Bitmap {
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val targetWidth = (out.width * scalePct.coerceIn(5, 400) / 100f).roundToInt().coerceAtLeast(1)
        val targetHeight = (top.height.toFloat() * targetWidth / top.width).roundToInt().coerceAtLeast(1)
        val scaled = if (targetWidth == top.width && targetHeight == top.height) {
            top
        } else {
            Bitmap.createScaledBitmap(top, targetWidth, targetHeight, true)
        }
        val left = (out.width - targetWidth) / 2f + offsetXPct.coerceIn(-200f, 200f) / 100f * out.width / 2f
        val topY = (out.height - targetHeight) / 2f + offsetYPct.coerceIn(-200f, 200f) / 100f * out.height / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (opacityPct.coerceIn(0, 100) / 100f * 255).roundToInt()
            ImageBlendModes.applyTo(this, blendMode)
        }
        canvas.drawBitmap(scaled, left, topY, paint)
        if (scaled !== top) scaled.recycle()
        return out
    }

    // ------------------------------------------------------------- IMG-37

    fun shadowGlow(
        source: Bitmap,
        shape: String,
        shadowHex: String,
        shadowBlurPct: Float,
        shadowOffsetXPct: Float,
        shadowOffsetYPct: Float,
        glowHex: String,
        glowStrengthPct: Int,
        paddingPct: Int
    ): Bitmap {
        val padding = (max(source.width, source.height) * paddingPct.coerceIn(0, 40) / 100f)
            .roundToInt()
            .coerceAtLeast(0)
        val outW = source.width + padding * 2
        val outH = source.height + padding * 2
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val silhouette = if (shape == "Kotak") {
            roundedRectSilhouette(source.width, source.height, Color.WHITE)
        } else {
            tintedSilhouette(source, Color.WHITE)
        }
        val shadowColor = ImageEngine.parseColorSafe(shadowHex, Color.BLACK)
        val glowColor = ImageEngine.parseColorSafe(glowHex, Color.parseColor("#FFE082"))

        val shadowBlur = ((shadowBlurPct.coerceIn(0f, 30f) / 100f) * max(source.width, source.height))
            .roundToInt()
            .coerceAtLeast(1)
        val glowBlur = (shadowBlur * 2).coerceAtLeast(2)

        // Glow first (widest), then shadow, then the artwork itself.
        if (glowStrengthPct > 0) {
            val glow = ImageFilterPipeline.blurredCopy(tintedSilhouette(silhouette, glowColor), glowBlur)
            val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (glowStrengthPct.coerceIn(0, 100) / 100f * 255).roundToInt()
            }
            canvas.drawBitmap(glow, padding.toFloat(), padding.toFloat(), glowPaint)
            glow.recycle()
        }
        if (shadowBlurPct > 0f) {
            val shadow = ImageFilterPipeline.blurredCopy(tintedSilhouette(silhouette, shadowColor), shadowBlur)
            val dx = shadowOffsetXPct.coerceIn(-40f, 40f) / 100f * source.width
            val dy = shadowOffsetYPct.coerceIn(-40f, 40f) / 100f * source.height
            canvas.drawBitmap(shadow, padding + dx, padding + dy, null)
            shadow.recycle()
        }
        canvas.drawBitmap(source, padding.toFloat(), padding.toFloat(), null)
        silhouette.recycle()
        return out
    }

    private fun tintedSilhouette(source: Bitmap, color: Int): Bitmap {
        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(color)
        val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun roundedRectSilhouette(width: Int, height: Int, color: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = minOf(width, height) * 0.06f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        return out
    }

    // ------------------------------------------------------------- IMG-38

    fun addSticker(
        source: Bitmap,
        emoji: String,
        xPct: Float,
        yPct: Float,
        sizePct: Float,
        rotationDeg: Float,
        opacityPct: Int
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val textSize = (source.width * sizePct.coerceIn(2f, 80f) / 100f).coerceAtLeast(12f)
        val x = source.width * xPct.coerceIn(0f, 100f) / 100f
        val y = source.height * yPct.coerceIn(0f, 100f) / 100f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            alpha = (opacityPct.coerceIn(0, 100) / 100f * 255).roundToInt()
            setShadowLayer(textSize * 0.06f, 0f, textSize * 0.02f, Color.argb(140, 0, 0, 0))
        }
        canvas.save()
        canvas.rotate(rotationDeg.coerceIn(-180f, 180f), x, y)
        canvas.drawText(emoji.ifBlank { "⭐" }, x, y + textSize * 0.35f, paint)
        canvas.restore()
        return out
    }

    // ------------------------------------------------------------- IMG-40

    fun vintageFilm(
        source: Bitmap,
        preset: String,
        warmth: Float,
        fadePct: Int,
        grainPct: Int,
        vignettePct: Int
    ): Bitmap {
        val basePreset = when (preset) {
            "Mono Grain" -> "Mono Kontras"
            "Cinema Fade" -> "Vintage"
            "Fuji Classic" -> "Cool"
            else -> "Vintage"
        }
        val spec = ImageFilterSpec(
            preset = basePreset,
            brightness = if (preset == "Fuji Classic") 4f else 0f,
            contrast = if (preset == "Mono Grain") 1.18f else 1.04f,
            saturation = if (preset == "Mono Grain") 0f else 0.9f,
            temperature = warmth.coerceIn(-50f, 50f),
            vignette = 0
        )
        val graded = ImageFilterPipeline.apply(source, spec)
        val canvas = Canvas(graded)
        if (fadePct > 0) {
            val fadePaint = Paint().apply {
                color = Color.WHITE
                alpha = (fadePct.coerceIn(0, 100) / 100f * 120f).roundToInt()
            }
            canvas.drawRect(0f, 0f, graded.width.toFloat(), graded.height.toFloat(), fadePaint)
        }
        if (vignettePct > 0) {
            ImageFilterPipeline.drawVignette(canvas, graded.width, graded.height, vignettePct)
        }
        ImageFilterPipeline.addGrain(graded, grainPct)
        return graded
    }

    // ------------------------------------------------------------- IMG-41

    fun lensFlare(
        source: Bitmap,
        xPct: Float,
        yPct: Float,
        intensityPct: Int,
        streakPct: Int,
        ghostCount: Int,
        warmHex: String
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val intensity = intensityPct.coerceIn(0, 100) / 100f
        val longEdge = max(out.width, out.height).toFloat()
        val lightX = out.width * xPct.coerceIn(0f, 100f) / 100f
        val lightY = out.height * yPct.coerceIn(0f, 100f) / 100f
        val warm = ImageEngine.parseColorSafe(warmHex, Color.parseColor("#FFD9A0"))
        val screen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }

        if (streakPct > 0) {
            val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                shader = LinearGradient(
                    lightX - longEdge * streakPct / 200f,
                    lightY,
                    lightX + longEdge * streakPct / 200f,
                    lightY,
                    intArrayOf(Color.TRANSPARENT, warm, Color.WHITE, warm, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            val thickness = longEdge * 0.012f * intensity.coerceAtLeast(0.05f)
            canvas.drawRect(
                lightX - longEdge * streakPct / 200f,
                lightY - thickness / 2f,
                lightX + longEdge * streakPct / 200f,
                lightY + thickness / 2f,
                streakPaint
            )
        }

        val haloRadius = longEdge * (0.10f + 0.22f * intensity)
        drawRadial(canvas, lightX, lightY, haloRadius, warm, intensity * 0.55f, screen)
        drawRadial(canvas, lightX, lightY, haloRadius * 0.35f, Color.WHITE, intensity * 0.85f, screen)

        val centerX = out.width / 2f
        val centerY = out.height / 2f
        val ghosts = ghostCount.coerceIn(0, 5)
        for (i in 1..ghosts) {
            val t = i / (ghosts + 1f)
            val gx = lightX + (centerX - lightX) * 2f * t
            val gy = lightY + (centerY - lightY) * 2f * t
            val radius = longEdge * (0.05f - 0.006f * i) * (1f + intensity)
            val tint = if (i % 2 == 0) warm else Color.argb(255, 150, 210, 255)
            drawRadial(canvas, gx, gy, radius.coerceAtLeast(2f), tint, intensity * (0.30f - 0.04f * i), screen)
        }
        return out
    }

    private fun drawRadial(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int,
        strength: Float,
        paint: Paint
    ) {
        if (radius <= 0f || strength <= 0f) return
        val safeRadius = radius.coerceAtLeast(1f)
        val local = Paint(paint).apply {
            shader = RadialGradient(
                x,
                y,
                safeRadius,
                intArrayOf(color, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            alpha = (strength.coerceIn(0f, 1f) * 255).roundToInt()
        }
        canvas.drawCircle(x, y, safeRadius, local)
    }

    // --------------------------------------------------- shared utilities

    /** Cover-crops [source] into a 1:1-ish canvas without distortion (used by icons/thumbs). */
    fun centerCrop(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = max(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
        val scaledW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val scaledH = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val left = ((scaledW - targetW) / 2).coerceAtLeast(0)
        val top = ((scaledH - targetH) / 2).coerceAtLeast(0)
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(scaled, -left.toFloat(), -top.toFloat(), null)
        if (scaled !== source) scaled.recycle()
        return out
    }

    fun fitCenter(source: Bitmap, targetW: Int, targetH: Int, background: Int = Color.TRANSPARENT): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(background)
        val scale = minOf(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
        val drawW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val drawH = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, drawW, drawH, true)
        canvas.drawBitmap(scaled, (targetW - drawW) / 2f, (targetH - drawH) / 2f, null)
        if (scaled !== source) scaled.recycle()
        return out
    }

    fun glowRadiusFor(longEdge: Int, percent: Float): Int =
        (longEdge * percent.coerceIn(0f, 20f) / 100f).roundToInt().coerceAtLeast(1)

    fun distanceToCenter(width: Int, height: Int): Float =
        hypot(width / 2.0, height / 2.0).toFloat()
}
