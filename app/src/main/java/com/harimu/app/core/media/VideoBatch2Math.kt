package com.harimu.app.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ColorGradeConfig(
    val brightness: Float = 0f,     // -0.5 .. 0.5
    val contrast: Float = 1f,       // 0.5 .. 2.0
    val saturation: Float = 1f,     // 0.0 .. 2.0
    val temperature: Float = 0f,    // -1.0 (cool) .. 1.0 (warm)
    val grayscale: Boolean = false,
    val lutName: String = "None"
)

data class FaceBox(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val trackingId: Int? = null
)

data class TimedFaces(
    val timeMs: Long,
    val faces: List<FaceBox>
)

data class BlurredTrackWindow(
    val startMs: Long,
    val endMs: Long,
    val box: FaceBox
)

data class TransitionClipInfo(
    val path: String,
    val durationMs: Long,
    val hasAudio: Boolean
)

object VideoBatch2Math {

    // VID-11: Deshake filter (LGPL alternative to GPL vidstab)
    fun buildStabilizeFilter(strength: String, cropBorder: Boolean): String {
        val radius = when {
            strength.contains("Kuat", ignoreCase = true) || strength.contains("Strong", ignoreCase = true) -> 48
            strength.contains("Ringan", ignoreCase = true) || strength.contains("Light", ignoreCase = true) -> 16
            else -> 32
        }
        val deshake = "deshake=x=-1:y=-1:w=-1:h=-1:rx=$radius:ry=$radius:edge=mirror:blocksize=8:contrast=125"
        return if (cropBorder) {
            "$deshake,crop=trunc(iw*0.92/2)*2:trunc(ih*0.92/2)*2"
        } else {
            deshake
        }
    }

    // VID-14: ROI clamp & blur/pixelate filter (LGPL avgblur/gblur/scale+overlay, since delogo/boxblur are GPL)
    fun clampRoiRect(
        srcWidth: Int,
        srcHeight: Int,
        xPct: Float,
        yPct: Float,
        wPct: Float,
        hPct: Float
    ): CropGeometry {
        val safeSrcW = VideoToolMath.makeEven(srcWidth.coerceAtLeast(16))
        val safeSrcH = VideoToolMath.makeEven(srcHeight.coerceAtLeast(16))

        val x = VideoToolMath.makeEven(((xPct.coerceIn(0f, 95f) / 100f) * safeSrcW).roundToInt(), minEven = 0)
            .coerceIn(0, safeSrcW - 8)
        val y = VideoToolMath.makeEven(((yPct.coerceIn(0f, 95f) / 100f) * safeSrcH).roundToInt(), minEven = 0)
            .coerceIn(0, safeSrcH - 8)

        val maxW = safeSrcW - x
        val maxH = safeSrcH - y
        val w = VideoToolMath.makeEven(((wPct.coerceIn(3f, 100f) / 100f) * safeSrcW).roundToInt(), minEven = 8)
            .coerceIn(8, maxW)
        val h = VideoToolMath.makeEven(((hPct.coerceIn(3f, 100f) / 100f) * safeSrcH).roundToInt(), minEven = 8)
            .coerceIn(8, maxH)
        return CropGeometry(width = w, height = h, x = x, y = y)
    }

    fun buildRemoveLogoFilter(roi: CropGeometry, mode: String, strength: Int): String {
        val safeStrength = strength.coerceIn(4, 40)
        val effect = when {
            mode.contains("Piksel", ignoreCase = true) || mode.contains("Pixel", ignoreCase = true) -> {
                val block = (safeStrength / 2).coerceIn(4, 24)
                val downW = max(4, roi.width / block)
                val downH = max(4, roi.height / block)
                "scale=$downW:$downH:flags=neighbor,scale=${roi.width}:${roi.height}:flags=neighbor"
            }
            mode.contains("Rata", ignoreCase = true) || mode.contains("Avg", ignoreCase = true) -> {
                val rad = (safeStrength / 2).coerceIn(2, 20)
                "avgblur=sizeX=$rad:sizeY=$rad"
            }
            else -> {
                val sigma = (safeStrength * 0.8f).coerceIn(3f, 30f)
                String.format(Locale.US, "gblur=sigma=%.1f", sigma)
            }
        }
        return "[0:v]crop=${roi.width}:${roi.height}:${roi.x}:${roi.y},$effect[roi];[0:v][roi]overlay=${roi.x}:${roi.y}"
    }

    // VID-15 & VID-17: Overlay anchors and text PNG generator
    fun computeFfmpegOverlayXY(position: String, marginPx: Int = 24): Pair<String, String> = when (position) {
        "Atas Tengah", "Top Center" -> "(W-w)/2" to "$marginPx"
        "Kiri Atas", "Top Left" -> "$marginPx" to "$marginPx"
        "Kanan Atas", "Top Right" -> "W-w-$marginPx" to "$marginPx"
        "Tengah", "Center" -> "(W-w)/2" to "(H-h)/2"
        "Kiri Bawah", "Bottom Left" -> "$marginPx" to "H-h-$marginPx"
        "Kanan Bawah", "Bottom Right" -> "W-w-$marginPx" to "H-h-$marginPx"
        else -> "(W-w)/2" to "H-h-$marginPx"
    }

    fun computeMedia3Anchor(position: String): Pair<Float, Float> = when (position) {
        "Atas Tengah", "Top Center" -> 0f to 0.82f
        "Kiri Atas", "Top Left" -> -0.82f to 0.82f
        "Kanan Atas", "Top Right" -> 0.82f to 0.82f
        "Tengah", "Center" -> 0f to 0f
        "Kiri Bawah", "Bottom Left" -> -0.82f to -0.82f
        "Kanan Bawah", "Bottom Right" -> 0.82f to -0.82f
        else -> 0f to -0.82f
    }

    fun parseOverlayColor(name: String): Int = when (name) {
        "Kuning", "Yellow" -> Color.YELLOW
        "Oranye", "Orange" -> Color.rgb(255, 107, 44)
        "Merah", "Red" -> Color.rgb(239, 68, 68)
        "Cyan" -> Color.CYAN
        "Hitam", "Black" -> Color.BLACK
        else -> Color.WHITE
    }

    fun renderStyledTextBitmap(
        text: String,
        fontStyle: String,
        textColorName: String,
        fontSizePx: Float,
        withBgBox: Boolean
    ): Bitmap {
        val cleanText = text.ifBlank { "HariMu" }
        val typeface = when {
            fontStyle.contains("Mono", ignoreCase = true) -> Typeface.MONOSPACE
            fontStyle.contains("Serif", ignoreCase = true) -> Typeface.SERIF
            fontStyle.contains("Regular", ignoreCase = true) -> Typeface.DEFAULT
            else -> Typeface.DEFAULT_BOLD
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseOverlayColor(textColorName)
            textSize = fontSizePx.coerceIn(22f, 120f)
            this.typeface = typeface
        }
        val lines = cleanText.lines().take(5).ifEmpty { listOf("HariMu") }
        val padX = (fontSizePx * 0.55f).roundToInt().coerceAtLeast(16)
        val padY = (fontSizePx * 0.40f).roundToInt().coerceAtLeast(12)
        val lineHeight = paint.fontMetrics.let { it.descent - it.ascent + 6f }
        val maxLineW = lines.maxOf { paint.measureText(it) }.roundToInt().coerceAtLeast(40)
        val w = (maxLineW + padX * 2).coerceAtLeast(64)
        val h = ((lineHeight * lines.size) + padY * 2).roundToInt().coerceAtLeast(48)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (withBgBox) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(175, 14, 17, 23)
            }
            val radius = (fontSizePx * 0.35f).coerceIn(8f, 24f)
            canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, bgPaint)
        } else {
            paint.setShadowLayer(4f, 2f, 2f, Color.argb(200, 0, 0, 0))
        }
        var y = padY - paint.fontMetrics.ascent
        lines.forEach { line ->
            canvas.drawText(line, padX.toFloat(), y, paint)
            y += lineHeight
        }
        return bmp
    }

    // VID-16: Add Audio filter (mix vs replace, offset ms)
    fun buildAddAudioFilter(
        videoHasAudio: Boolean,
        mixWithOriginal: Boolean,
        origVolumePct: Float,
        addedVolumePct: Float,
        offsetSec: Float
    ): Pair<String, String> {
        val addedVol = (addedVolumePct.coerceIn(0f, 200f) / 100f)
        val origVol = (origVolumePct.coerceIn(0f, 150f) / 100f)
        val delayMs = (offsetSec.coerceAtLeast(0f) * 1000f).roundToInt()
        val delayPart = if (delayMs > 0) "adelay=$delayMs|$delayMs," else ""
        val addedChain = "[1:a]${delayPart}volume=${String.format(Locale.US, "%.2f", addedVol)}[a_new]"

        return if (videoHasAudio && mixWithOriginal) {
            val origChain = "[0:a]volume=${String.format(Locale.US, "%.2f", origVol)}[a_orig]"
            val filter = "$origChain;$addedChain;[a_orig][a_new]amix=inputs=2:duration=first:dropout_transition=2:normalize=0[aout]"
            filter to "[aout]"
        } else {
            "$addedChain;[a_new]anull[aout]" to "[aout]"
        }
    }

    // VID-26: Color grading presets, FFmpeg filter, LUT, and Media3 effects
    fun colorPresetConfig(
        preset: String,
        manualBrightness: Float,
        manualContrast: Float,
        manualSaturation: Float,
        manualTemp: Float
    ): ColorGradeConfig = when (preset) {
        "Sinematik Hangat", "Warm Cinema" -> ColorGradeConfig(
            brightness = 0.02f,
            contrast = 1.18f,
            saturation = 1.15f,
            temperature = 0.45f,
            lutName = "WarmCinema"
        )
        "Dingin Nordik", "Nordic Cool" -> ColorGradeConfig(
            brightness = 0.0f,
            contrast = 1.12f,
            saturation = 0.90f,
            temperature = -0.50f,
            lutName = "NordicCool"
        )
        "Vivid Pop" -> ColorGradeConfig(
            brightness = 0.04f,
            contrast = 1.22f,
            saturation = 1.45f,
            temperature = 0.12f,
            lutName = "VividPop"
        )
        "Film Pudar", "Faded Film" -> ColorGradeConfig(
            brightness = 0.06f,
            contrast = 0.88f,
            saturation = 0.78f,
            temperature = 0.20f,
            lutName = "FadedFilm"
        )
        "Monokrom", "Monochrome" -> ColorGradeConfig(
            brightness = 0.02f,
            contrast = 1.20f,
            saturation = 0.0f,
            temperature = 0.0f,
            grayscale = true,
            lutName = "None"
        )
        else -> ColorGradeConfig(
            brightness = manualBrightness.coerceIn(-0.5f, 0.5f),
            contrast = manualContrast.coerceIn(0.5f, 2.0f),
            saturation = manualSaturation.coerceIn(0f, 2.0f),
            temperature = manualTemp.coerceIn(-1f, 1f),
            grayscale = manualSaturation <= 0.01f
        )
    }

    fun buildColorGradingFfmpegFilter(config: ColorGradeConfig, cubeLutPath: String? = null): String {
        val parts = mutableListOf<String>()
        if (cubeLutPath != null) {
            val escaped = cubeLutPath.replace("'", "\\'")
            parts.add("lut3d=file='$escaped'")
        }
        val eqStr = String.format(
            Locale.US,
            "eq=brightness=%.3f:contrast=%.3f:saturation=%.3f",
            config.brightness.coerceIn(-0.5f, 0.5f),
            config.contrast.coerceIn(0.5f, 2.0f),
            if (config.grayscale) 0f else config.saturation.coerceIn(0f, 2.0f)
        )
        parts.add(eqStr)
        if (abs(config.temperature) > 0.04f) {
            val t = config.temperature.coerceIn(-1f, 1f)
            val rs = t * 0.22f
            val bs = -t * 0.22f
            parts.add(String.format(Locale.US, "colorbalance=rs=%.3f:bs=%.3f:rm=%.3f:bm=%.3f", rs, bs, rs * 0.7f, bs * 0.7f))
        }
        return parts.joinToString(",")
    }

    fun generatePresetCubeLut(config: ColorGradeConfig, size: Int = 9): String {
        val safeSize = size.coerceIn(5, 17)
        val sb = StringBuilder()
        sb.appendLine("TITLE \"HariMu_${config.lutName}\"")
        sb.appendLine("LUT_3D_SIZE $safeSize")
        sb.appendLine("DOMAIN_MIN 0.0 0.0 0.0")
        sb.appendLine("DOMAIN_MAX 1.0 1.0 1.0")
        for (b in 0 until safeSize) {
            val bf = b.toFloat() / (safeSize - 1)
            for (g in 0 until safeSize) {
                val gf = g.toFloat() / (safeSize - 1)
                for (r in 0 until safeSize) {
                    val rf = r.toFloat() / (safeSize - 1)
                    val (ro, go, bo) = when (config.lutName) {
                        "WarmCinema" -> Triple(
                            (rf * 1.06f + 0.02f).coerceIn(0f, 1f),
                            (gf * 1.01f).coerceIn(0f, 1f),
                            (bf * 0.91f).coerceIn(0f, 1f)
                        )
                        "NordicCool" -> Triple(
                            (rf * 0.91f).coerceIn(0f, 1f),
                            (gf * 1.00f).coerceIn(0f, 1f),
                            (bf * 1.09f + 0.02f).coerceIn(0f, 1f)
                        )
                        "VividPop" -> Triple(
                            ((rf - 0.5f) * 1.12f + 0.5f).coerceIn(0f, 1f),
                            ((gf - 0.5f) * 1.12f + 0.5f).coerceIn(0f, 1f),
                            ((bf - 0.5f) * 1.12f + 0.5f).coerceIn(0f, 1f)
                        )
                        "FadedFilm" -> Triple(
                            (rf * 0.88f + 0.06f).coerceIn(0f, 1f),
                            (gf * 0.87f + 0.05f).coerceIn(0f, 1f),
                            (bf * 0.84f + 0.06f).coerceIn(0f, 1f)
                        )
                        else -> Triple(rf, gf, bf)
                    }
                    sb.appendLine(String.format(Locale.US, "%.4f %.4f %.4f", ro, go, bo))
                }
            }
        }
        return sb.toString()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun buildMedia3ColorEffects(config: ColorGradeConfig): List<Effect> = buildList {
        if (config.grayscale) {
            add(RgbFilter.createGrayscaleFilter())
        }
        if (abs(config.contrast - 1f) > 0.02f) {
            // Media3 Contrast expects -1f..1f where 0f is neutral
            add(Contrast((config.contrast - 1f).coerceIn(-0.8f, 1f)))
        }
        if (!config.grayscale && (abs(config.saturation - 1f) > 0.02f || abs(config.brightness) > 0.02f)) {
            val satDelta = ((config.saturation - 1f) * 100f).coerceIn(-100f, 100f)
            val lightDelta = (config.brightness * 100f).coerceIn(-100f, 100f)
            add(
                HslAdjustment.Builder()
                    .adjustSaturation(satDelta)
                    .adjustLightness(lightDelta)
                    .build()
            )
        }
        if (abs(config.temperature) > 0.04f) {
            val t = config.temperature.coerceIn(-1f, 1f)
            add(
                RgbAdjustment.Builder()
                    .setRedScale((1f + t * 0.18f).coerceIn(0.6f, 1.4f))
                    .setBlueScale((1f - t * 0.18f).coerceIn(0.6f, 1.4f))
                    .build()
            )
        }
    }

    // VID-27: Transitions filter graph builder
    fun buildTransitionFilterGraph(
        clips: List<TransitionClipInfo>,
        transitionName: String,
        transitionDurationSec: Float,
        targetWidth: Int,
        targetHeight: Int
    ): Pair<String, Boolean> {
        require(clips.size >= 2) { "Need at least 2 clips for transitions" }
        val minClipSec = clips.minOf { (it.durationMs / 1000.0).coerceAtLeast(0.5) }
        val dSec = transitionDurationSec.toDouble().coerceIn(0.3, minOf(3.0, minClipSec * 0.45))
        val safeW = VideoToolMath.makeEven(targetWidth)
        val safeH = VideoToolMath.makeEven(targetHeight)
        val allHaveAudio = clips.all { it.hasAudio }

        val sb = StringBuilder()
        clips.forEachIndexed { idx, _ ->
            sb.append("[$idx:v]scale=$safeW:$safeH:force_original_aspect_ratio=decrease,pad=$safeW:$safeH:(ow-iw)/2:(oh-ih)/2,fps=30,format=yuv420p,setsar=1[v$idx];")
            if (allHaveAudio) {
                sb.append("[$idx:a]aformat=sample_rates=44100:channel_layouts=stereo[a$idx];")
            }
        }

        var cumulativeDurSec = (clips[0].durationMs / 1000.0).coerceAtLeast(0.5)
        var prevV = "[v0]"
        var prevA = "[a0]"
        for (i in 1 until clips.size) {
            val offset = (cumulativeDurSec - dSec).coerceAtLeast(0.1)
            val outV = if (i == clips.lastIndex) "[vout]" else "[vx$i]"
            sb.append(
                String.format(
                    Locale.US,
                    "%s[v%d]xfade=transition=%s:duration=%.3f:offset=%.3f%s;",
                    prevV, i, transitionName, dSec, offset, outV
                )
            )
            prevV = outV
            if (allHaveAudio) {
                val outA = if (i == clips.lastIndex) "[aout]" else "[ax$i]"
                sb.append(
                    String.format(
                        Locale.US,
                        "%s[a%d]acrossfade=d=%.3f:c1=tri:c2=tri%s;",
                        prevA, i, dSec, outA
                    )
                )
                prevA = outA
            }
            cumulativeDurSec = offset + (clips[i].durationMs / 1000.0).coerceAtLeast(0.5)
        }
        return sb.toString().trimEnd(';') to allHaveAudio
    }

    // VID-28: Green Screen (Chroma Key)
    fun mapChromaKeyHex(colorOption: String): String = when {
        colorOption.contains("Biru", ignoreCase = true) || colorOption.contains("Blue", ignoreCase = true) -> "0x0000FF"
        colorOption.contains("Putih", ignoreCase = true) || colorOption.contains("White", ignoreCase = true) -> "0xFFFFFF"
        colorOption.startsWith("#") && colorOption.length == 7 -> "0x${colorOption.substring(1)}"
        else -> "0x00FF00"
    }

    fun mapFallbackBgColorHex(bgOption: String): String = when {
        bgOption.contains("Putih", ignoreCase = true) || bgOption.contains("White", ignoreCase = true) -> "0xF8FAFC"
        bgOption.contains("Biru", ignoreCase = true) || bgOption.contains("Blue", ignoreCase = true) -> "0x0F172A"
        else -> "0x111827"
    }

    fun buildChromaKeyFilter(
        hasBackgroundMedia: Boolean,
        backgroundIsVideo: Boolean,
        keyHex: String,
        similarity: Float,
        blend: Float,
        targetW: Int,
        targetH: Int,
        fallbackBgHex: String
    ): String {
        val safeW = VideoToolMath.makeEven(targetW.coerceAtLeast(320))
        val safeH = VideoToolMath.makeEven(targetH.coerceAtLeast(240))
        val sim = similarity.coerceIn(0.02f, 0.70f)
        val bl = blend.coerceIn(0.0f, 0.40f)
        val fgPart = String.format(Locale.US, "[0:v]chromakey=%s:%.2f:%.2f[fg]", keyHex, sim, bl)
        val bgPart = if (hasBackgroundMedia) {
            val scalePad = "scale=$safeW:$safeH:force_original_aspect_ratio=increase,crop=$safeW:$safeH"
            if (backgroundIsVideo) "[1:v]$scalePad[bg]" else "[1:v]$scalePad[bg]"
        } else {
            "color=c=$fallbackBgHex:s=${safeW}x${safeH}[bg]"
        }
        return "$fgPart;$bgPart;[bg][fg]overlay=shortest=1:format=auto[vout]"
    }

    // VID-30: Time Lapse
    fun buildTimeLapseVideoFilter(speedFactor: Int, targetFps: Int): String {
        val factor = speedFactor.coerceIn(2, 64)
        val fps = targetFps.coerceIn(15, 60)
        return String.format(Locale.US, "setpts=%.5f*PTS,fps=%d", 1.0 / factor.toDouble(), fps)
    }

    // VID-25: Simple IoU + trackingId temporal tracker & interval merger for Face Blur
    fun smoothAndGroupFaceTracks(
        samples: List<TimedFaces>,
        srcWidth: Int,
        srcHeight: Int,
        detectWidth: Int,
        detectHeight: Int,
        totalDurationMs: Long,
        paddingRatio: Float = 0.18f,
        maxFilterWindows: Int = 24
    ): List<BlurredTrackWindow> {
        if (samples.isEmpty()) return emptyList()
        val scaleX = srcWidth.toFloat() / detectWidth.coerceAtLeast(1).toFloat()
        val scaleY = srcHeight.toFloat() / detectHeight.coerceAtLeast(1).toFloat()
        val safeSrcW = VideoToolMath.makeEven(srcWidth.coerceAtLeast(16))
        val safeSrcH = VideoToolMath.makeEven(srcHeight.coerceAtLeast(16))

        val sorted = samples.sortedBy { it.timeMs }
        val rawWindows = mutableListOf<BlurredTrackWindow>()

        for (i in sorted.indices) {
            val sample = sorted[i]
            val startMs = if (i == 0) 0L else sample.timeMs
            val nextStart = sorted.getOrNull(i + 1)?.timeMs ?: totalDurationMs.coerceAtLeast(sample.timeMs + 500L)
            val endMs = max(startMs + 250L, nextStart)

            sample.faces.forEach { box ->
                val rawW = (box.width * scaleX).roundToInt()
                val rawH = (box.height * scaleY).roundToInt()
                val padX = (rawW * paddingRatio).roundToInt()
                val padY = (rawH * paddingRatio).roundToInt()

                val left = VideoToolMath.makeEven(((box.left * scaleX).roundToInt() - padX).coerceIn(0, safeSrcW - 16), 0)
                val top = VideoToolMath.makeEven(((box.top * scaleY).roundToInt() - padY).coerceIn(0, safeSrcH - 16), 0)
                val right = ((box.left * scaleX).roundToInt() + rawW + padX).coerceIn(left + 16, safeSrcW)
                val bottom = ((box.top * scaleY).roundToInt() + rawH + padY).coerceIn(top + 16, safeSrcH)
                val w = VideoToolMath.makeEven(right - left, 16).coerceAtMost(safeSrcW - left)
                val h = VideoToolMath.makeEven(bottom - top, 16).coerceAtMost(safeSrcH - top)

                val mapped = FaceBox(left = left, top = top, width = w, height = h, trackingId = box.trackingId)

                // Merge with last window if consecutive in time and high overlap / same trackingId
                val matchIdx = rawWindows.indexOfLast { prev ->
                    abs(prev.endMs - startMs) <= 200L &&
                        ((mapped.trackingId != null && prev.box.trackingId == mapped.trackingId) ||
                            computeIoU(prev.box, mapped) >= 0.45f)
                }
                if (matchIdx >= 0) {
                    val prev = rawWindows[matchIdx]
                    val mergedBox = FaceBox(
                        left = VideoToolMath.makeEven(min(prev.box.left, mapped.left), 0),
                        top = VideoToolMath.makeEven(min(prev.box.top, mapped.top), 0),
                        width = VideoToolMath.makeEven(max(prev.box.left + prev.box.width, mapped.left + mapped.width) - min(prev.box.left, mapped.left), 16)
                            .coerceAtMost(safeSrcW - min(prev.box.left, mapped.left)),
                        height = VideoToolMath.makeEven(max(prev.box.top + prev.box.height, mapped.top + mapped.height) - min(prev.box.top, mapped.top), 16)
                            .coerceAtMost(safeSrcH - min(prev.box.top, mapped.top)),
                        trackingId = mapped.trackingId ?: prev.box.trackingId
                    )
                    rawWindows[matchIdx] = prev.copy(endMs = endMs, box = mergedBox)
                } else {
                    rawWindows.add(BlurredTrackWindow(startMs = startMs, endMs = endMs, box = mapped))
                }
            }
        }
        return rawWindows.take(maxFilterWindows)
    }

    fun computeIoU(a: FaceBox, b: FaceBox): Float {
        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.left + a.width, b.left + b.width)
        val y2 = min(a.top + a.height, b.top + b.height)
        val inter = max(0, x2 - x1) * max(0, y2 - y1)
        if (inter <= 0) return 0f
        val union = (a.width * a.height) + (b.width * b.height) - inter
        return if (union <= 0) 0f else inter.toFloat() / union.toFloat()
    }

    fun buildFaceBlurFilterGraph(
        windows: List<BlurredTrackWindow>,
        pixelate: Boolean,
        blurStrength: Int
    ): String {
        if (windows.isEmpty()) return "null"
        val sb = StringBuilder()
        var currentLabel = "[0:v]"
        windows.forEachIndexed { idx, w ->
            val box = w.box
            val startSec = String.format(Locale.US, "%.2f", w.startMs / 1000.0)
            val endSec = String.format(Locale.US, "%.2f", w.endMs / 1000.0)
            val roiFilter = if (pixelate) {
                val block = (blurStrength / 2).coerceIn(5, 20)
                val dw = max(4, box.width / block)
                val dh = max(4, box.height / block)
                "scale=$dw:$dh:flags=neighbor,scale=${box.width}:${box.height}:flags=neighbor"
            } else {
                val sigma = blurStrength.coerceIn(6, 30)
                "gblur=sigma=$sigma"
            }
            val nextLabel = if (idx == windows.lastIndex) "[vout]" else "[vf$idx]"
            sb.append("${currentLabel}split[base$idx][src$idx];")
            sb.append("[src$idx]crop=${box.width}:${box.height}:${box.left}:${box.top},$roiFilter[blur$idx];")
            sb.append("[base$idx][blur$idx]overlay=${box.left}:${box.top}:enable='between(t,$startSec,$endSec)'$nextLabel;")
            currentLabel = nextLabel
        }
        return sb.toString().trimEnd(';')
    }
}
