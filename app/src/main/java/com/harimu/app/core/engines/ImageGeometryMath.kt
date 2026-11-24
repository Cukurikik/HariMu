package com.harimu.app.core.engines

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Rectangle in pixel coordinates. */
data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
    val area: Long get() = width.toLong() * height.toLong()

    fun clampedTo(boundW: Int, boundH: Int): PixelRect {
        val l = left.coerceIn(0, max(0, boundW - 1))
        val t = top.coerceIn(0, max(0, boundH - 1))
        val w = width.coerceIn(1, max(1, boundW - l))
        val h = height.coerceIn(1, max(1, boundH - t))
        return PixelRect(l, t, w, h)
    }

    companion object {
        val EMPTY = PixelRect(0, 0, 0, 0)
    }
}

data class GeoPoint(val x: Float, val y: Float)

/** Four corner points, ordered top-left, top-right, bottom-right, bottom-left. */
data class Quad(
    val tl: GeoPoint,
    val tr: GeoPoint,
    val br: GeoPoint,
    val bl: GeoPoint
) {
    fun points(): List<GeoPoint> = listOf(tl, tr, br, bl)

    companion object {
        fun fromPercents(
            viewW: Int,
            viewH: Int,
            tlX: Float,
            tlY: Float,
            trX: Float,
            trY: Float,
            brX: Float,
            brY: Float,
            blX: Float,
            blY: Float
        ): Quad = Quad(
            GeoPoint(tlX / 100f * viewW, tlY / 100f * viewH),
            GeoPoint(trX / 100f * viewW, trY / 100f * viewH),
            GeoPoint(brX / 100f * viewW, brY / 100f * viewH),
            GeoPoint(blX / 100f * viewW, blY / 100f * viewH)
        )

        fun fullFrame(viewW: Int, viewH: Int): Quad = Quad(
            GeoPoint(0f, 0f),
            GeoPoint(viewW.toFloat(), 0f),
            GeoPoint(viewW.toFloat(), viewH.toFloat()),
            GeoPoint(0f, viewH.toFloat())
        )
    }
}

data class NamedRegion(
    val name: String,
    val leftPct: Float,
    val topPct: Float,
    val widthPct: Float,
    val heightPct: Float
)

data class IconSpec(val path: String, val sizePx: Int, val label: String)

/**
 * Pure geometry/clustering helpers for Image Forge. No Android imports on purpose so
 * [com.harimu.app.ImageGeometryGoldenTest] can pin the numbers with plain JUnit.
 */
object ImageGeometryMath {

    const val MIN_REGION_PX = 8
    private const val MIN_ANGLE_DEG = 0.01f

    // ---------------------------------------------------------------- crop

    /** Crop rectangle from percentages of the (orientation-applied) image. */
    fun cropRect(
        viewW: Int,
        viewH: Int,
        leftPct: Float,
        topPct: Float,
        widthPct: Float,
        heightPct: Float
    ): PixelRect {
        require(viewW > 0 && viewH > 0) { "Ukuran gambar tidak valid" }
        val w = (viewW * widthPct / 100f).roundToInt().coerceIn(1, viewW)
        val h = (viewH * heightPct / 100f).roundToInt().coerceIn(1, viewH)
        val left = (viewW * leftPct / 100f).roundToInt().coerceIn(0, viewW - w)
        val top = (viewH * topPct / 100f).roundToInt().coerceIn(0, viewH - h)
        return PixelRect(left, top, w, h)
    }

    /**
     * Crop rectangle for IMG-01: largest rect with [ratioW]:[ratioH] inside the frame,
     * shrunk by [zoomPercent] (100 = no zoom) and shifted by the offsets (-100..100).
     */
    fun cropRectForRatio(
        viewW: Int,
        viewH: Int,
        ratioW: Float,
        ratioH: Float,
        zoomPercent: Float,
        offsetXPct: Float,
        offsetYPct: Float
    ): PixelRect {
        require(viewW > 0 && viewH > 0) { "Ukuran gambar tidak valid" }
        require(ratioW > 0f && ratioH > 0f) { "Rasio tidak valid" }
        val fitByWidth = (viewW * ratioH / ratioW).roundToInt()
        val baseW: Int
        val baseH: Int
        if (fitByWidth <= viewH) {
            baseW = viewW
            baseH = fitByWidth.coerceAtLeast(1)
        } else {
            baseW = (viewH * ratioW / ratioH).roundToInt().coerceAtLeast(1)
            baseH = viewH
        }
        val zoom = (zoomPercent / 100f).coerceAtLeast(1f)
        val w = (baseW / zoom).roundToInt().coerceIn(1, viewW)
        val h = (baseH / zoom).roundToInt().coerceIn(1, viewH)
        val cx = viewW / 2f + (offsetXPct / 100f) * ((viewW - w) / 2f)
        val cy = viewH / 2f + (offsetYPct / 100f) * ((viewH - h) / 2f)
        val left = (cx - w / 2f).roundToInt().coerceIn(0, viewW - w)
        val top = (cy - h / 2f).roundToInt().coerceIn(0, viewH - h)
        return PixelRect(left, top, w, h)
    }

    /**
     * Maps a rect expressed in orientation-applied ("display") coordinates back to raw
     * source coordinates so the region decoder can read it without decoding everything.
     */
    fun cropSourceRegion(
        srcW: Int,
        srcH: Int,
        rect: PixelRect,
        exifOrientation: Int
    ): PixelRect {
        val mapped = when (exifOrientation) {
            2 -> PixelRect(srcW - rect.left - rect.width, rect.top, rect.width, rect.height)
            3 -> PixelRect(srcW - rect.left - rect.width, srcH - rect.top - rect.height, rect.width, rect.height)
            4 -> PixelRect(rect.left, srcH - rect.top - rect.height, rect.width, rect.height)
            5 -> PixelRect(rect.top, rect.left, rect.height, rect.width)
            6 -> PixelRect(rect.top, srcH - rect.left - rect.width, rect.height, rect.width)
            7 -> PixelRect(srcW - rect.top - rect.height, srcH - rect.left - rect.width, rect.height, rect.width)
            8 -> PixelRect(srcW - rect.top - rect.height, rect.left, rect.height, rect.width)
            else -> rect
        }
        return mapped.clampedTo(srcW, srcH)
    }

    // ------------------------------------------------------------ rotation

    fun normalizedDegrees(degrees: Float): Int {
        val mod = ((degrees % 360f) + 360f) % 360f
        return mod.roundToInt() % 360
    }

    fun rotateSize(width: Int, height: Int, degrees: Float): Pair<Int, Int> {
        val deg = normalizedDegrees(degrees)
        return if (deg == 90 || deg == 270) Pair(height, width) else Pair(width, height)
    }

    fun expandCanvasSize(width: Int, height: Int, ratio: Float): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Ukuran gambar tidak valid" }
        require(ratio > 0f) { "Rasio tidak valid" }
        return if (width.toFloat() / height > ratio) {
            Pair(width, (width / ratio).roundToInt().coerceAtLeast(height))
        } else {
            Pair((height * ratio).roundToInt().coerceAtLeast(width), height)
        }
    }

    /** Bounding box of the rotated bitmap (the canvas a rotation needs). */
    fun rotatedBounds(width: Int, height: Int, angleDeg: Float): Pair<Int, Int> {
        if (abs(angleDeg) < MIN_ANGLE_DEG) return Pair(width, height)
        val rad = angleDeg * PI / 180.0
        val c = abs(cos(rad))
        val s = abs(sin(rad))
        return Pair(
            (width * c + height * s).roundToInt().coerceAtLeast(1),
            (width * s + height * c).roundToInt().coerceAtLeast(1)
        )
    }

    /**
     * Largest same-aspect rectangle without empty corners after a rotation, expressed in
     * the rotated canvas coordinates.
     */
    fun straightenSafeRect(width: Int, height: Int, angleDeg: Float): PixelRect {
        if (abs(angleDeg) < MIN_ANGLE_DEG) return PixelRect(0, 0, width, height)
        val rad = abs(angleDeg) * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        val safeW = (width * height / (height * c + width * s)).roundToInt().coerceIn(1, width)
        val safeH = (safeW.toFloat() * height / width).roundToInt().coerceIn(1, height)
        val (canvasW, canvasH) = rotatedBounds(width, height, angleDeg)
        return PixelRect(
            ((canvasW - safeW) / 2).coerceAtLeast(0),
            ((canvasH - safeH) / 2).coerceAtLeast(0),
            safeW,
            safeH
        )
    }

    // -------------------------------------------------------- perspective

    fun distance(a: GeoPoint, b: GeoPoint): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    fun perspectiveOutputSize(quad: Quad): Pair<Int, Int> {
        val width = max(distance(quad.tl, quad.tr), distance(quad.bl, quad.br))
        val height = max(distance(quad.tl, quad.bl), distance(quad.tr, quad.br))
        return Pair(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1))
    }

    fun polygonArea(quad: Quad): Float {
        val pts = quad.points()
        var sum = 0f
        for (i in 0 until pts.size) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    /** Returns null when the quad can be warped, otherwise a user-facing reason. */
    fun quadValidationError(quad: Quad, viewW: Int, viewH: Int): String? {
        val pts = quad.points()
        if (pts.any { it.x < 0f || it.y < 0f || it.x > viewW || it.y > viewH }) {
            return "Titik harus berada di dalam gambar"
        }
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            if (distance(a, b) < MIN_REGION_PX) return "Titik terlalu berdekatan"
        }
        var positive = 0
        var negative = 0
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val c = pts[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (cross > 0f) positive++ else if (cross < 0f) negative++
        }
        if (positive != 4 && negative != 4) return "Urutan titik tidak membentuk segi empat cembung"
        if (polygonArea(quad) < viewW.toFloat() * viewH * 0.01f) {
            return "Area koreksi terlalu kecil"
        }
        return null
    }

    /**
     * Solves the 8x8 system for the projective transform src -> dst.
     * Returns the 9 row-major values directly usable by [android.graphics.Matrix.setValues].
     */
    fun homography(src: List<GeoPoint>, dst: List<GeoPoint>): FloatArray? {
        if (src.size != 4 || dst.size != 4) return null
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val sx = src[i].x.toDouble()
            val sy = src[i].y.toDouble()
            val dx = dst[i].x.toDouble()
            val dy = dst[i].y.toDouble()
            a[2 * i] = doubleArrayOf(sx, sy, 1.0, 0.0, 0.0, 0.0, -dx * sx, -dx * sy, dx)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, sx, sy, 1.0, -dy * sx, -dy * sy, dy)
        }
        for (col in 0 until 8) {
            var pivot = col
            for (row in col + 1 until 8) {
                if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
            }
            if (abs(a[pivot][col]) < 1e-10) return null
            val swap = a[col]
            a[col] = a[pivot]
            a[pivot] = swap
            val pivotValue = a[col][col]
            for (c in col until 9) a[col][c] /= pivotValue
            for (row in 0 until 8) {
                if (row == col) continue
                val factor = a[row][col]
                if (factor == 0.0) continue
                for (c in col until 9) a[row][c] -= factor * a[col][c]
            }
        }
        val result = FloatArray(9)
        for (i in 0 until 8) result[i] = a[i][8].toFloat()
        result[8] = 1f
        return result
    }

    fun perspectiveMatrixValues(quad: Quad, outW: Int, outH: Int): FloatArray? = homography(
        quad.points(),
        Quad.fullFrame(outW, outH).points()
    )

    // ------------------------------------------------------- multi crop

    /** Parses `nama:x,y,w,h;nama:x,y,w,h` (all values in percent). */
    fun parseCropRegions(raw: String): List<NamedRegion> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        return text.split(';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, entry ->
                val name = entry.substringBefore(':', "").trim().ifBlank { "crop_${index + 1}" }
                val body = entry.substringAfter(':', entry)
                val parts = body.split(',').map { it.trim() }
                require(parts.size == 4) {
                    "Region '$entry' tidak valid. Format: nama:x,y,w,h (persen), dipisah ';'."
                }
                val values = parts.map { part ->
                    part.toFloatOrNull() ?: throw IllegalArgumentException(
                        "Angka pada region '$entry' tidak valid: '$part'."
                    )
                }
                require(values[2] > 0f && values[3] > 0f) {
                    "Lebar/tinggi region '${name}' harus lebih dari 0."
                }
                require(values[0] >= 0f && values[1] >= 0f && values[0] < 100f && values[1] < 100f) {
                    "Posisi region '${name}' harus 0-100 persen."
                }
                NamedRegion(name, values[0], values[1], values[2], values[3])
            }
    }

    // ---------------------------------------------------- auto crop (saliency)

    /**
     * Local saliency heuristic: everything far enough from the average image colour counts
     * as subject. Returns the bounding box in the supplied pixel grid, or null.
     */
    fun saliencyBoundingBox(pixels: IntArray, width: Int, height: Int, thresholdPct: Int): PixelRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var counted = 0L
        for (i in 0 until width * height) {
            val p = pixels[i]
            if ((p ushr 24) and 0xFF < 32) continue
            sumR += (p shr 16) and 0xFF
            sumG += (p shr 8) and 0xFF
            sumB += p and 0xFF
            counted++
        }
        if (counted == 0L) return null
        val meanR = sumR.toDouble() / counted
        val meanG = sumG.toDouble() / counted
        val meanB = sumB.toDouble() / counted
        val maxDistance = 441.673
        val threshold = (thresholdPct.coerceIn(1, 100) / 100.0) * maxDistance
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                if ((p ushr 24) and 0xFF < 32) continue
                val dr = ((p shr 16) and 0xFF) - meanR
                val dg = ((p shr 8) and 0xFF) - meanG
                val db = (p and 0xFF) - meanB
                val dist = kotlin.math.sqrt(dr * dr + dg * dg + db * db)
                if (dist >= threshold) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0 || maxY < 0) return null
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        if (w < 2 || h < 2) return null
        return PixelRect(minX, minY, w, h)
    }

    /** Pads a box by [paddingPct] of the box size and clamps it to the frame. */
    fun padBoundingBox(rect: PixelRect, frameW: Int, frameH: Int, paddingPct: Float): PixelRect {
        val padX = (rect.width * paddingPct / 100f).roundToInt()
        val padY = (rect.height * paddingPct / 100f).roundToInt()
        return PixelRect(rect.left - padX, rect.top - padY, rect.width + padX * 2, rect.height + padY * 2)
            .clampedTo(frameW, frameH)
    }

    // -------------------------------------------------------- alignment

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /**
     * Sum of absolute differences search. Returns (dx, dy) such that
     * `target[x + dx, y + dy]` best matches `ref[x, y]`.
     */
    fun alignmentOffset(
        ref: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        maxShift: Int
    ): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Ukuran gambar tidak valid" }
        require(ref.size >= width * height && target.size >= width * height) { "Buffer piksel tidak cocok" }
        val refLum = IntArray(width * height) { luminance(ref[it]) }
        val targetLum = IntArray(width * height) { luminance(target[it]) }
        val step = max(1, (width * height) / 20000)
        var bestDx = 0
        var bestDy = 0
        var bestScore = Long.MAX_VALUE
        val shift = maxShift.coerceIn(0, min(width, height) / 2)
        for (dy in -shift..shift) {
            for (dx in -shift..shift) {
                var score = 0L
                var count = 0
                var y = max(0, -dy)
                val yEnd = min(height, height - dy)
                while (y < yEnd) {
                    var x = max(0, -dx)
                    val xEnd = min(width, width - dx)
                    while (x < xEnd) {
                        val diff = refLum[y * width + x] - targetLum[(y + dy) * width + (x + dx)]
                        score += abs(diff)
                        count++
                        x += step
                    }
                    y += step
                }
                if (count * 4 < width * height) continue
                if (score < bestScore) {
                    bestScore = score
                    bestDx = dx
                    bestDy = dy
                }
            }
        }
        return Pair(bestDx, bestDy)
    }

    // ---------------------------------------------------------- icon pack

    /** Full icon pack: legacy Android, round, adaptive foreground, Play, iOS and favicons. */
    fun iconPackEntries(): List<IconSpec> {
        val densities = listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
        val entries = mutableListOf<IconSpec>()
        densities.forEach { (density, size) ->
            entries += IconSpec("mipmap-$density/ic_launcher.png", size, "Android legacy $density")
        }
        densities.forEach { (density, size) ->
            entries += IconSpec("mipmap-$density/ic_launcher_round.png", size, "Android round $density")
        }
        densities.forEach { (density, size) ->
            entries += IconSpec("mipmap-$density/ic_launcher_foreground.png", size * 108 / 48, "Adaptive foreground $density")
        }
        entries += IconSpec("playstore-icon-512.png", 512, "Google Play 512px")
        entries += IconSpec("favicon-16.png", 16, "Favicon 16px")
        entries += IconSpec("favicon-32.png", 32, "Favicon 32px")
        entries += IconSpec("favicon-48.png", 48, "Favicon 48px")
        entries += IconSpec("apple-touch-icon-180.png", 180, "Apple touch icon")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-20x20@1x.png", 20, "iOS 20pt @1x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-20x20@2x.png", 40, "iOS 20pt @2x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-20x20@3x.png", 60, "iOS 20pt @3x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-29x29@1x.png", 29, "iOS 29pt @1x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-29x29@2x.png", 58, "iOS 29pt @2x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-29x29@3x.png", 87, "iOS 29pt @3x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-40x40@1x.png", 40, "iOS 40pt @1x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-40x40@2x.png", 80, "iOS 40pt @2x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-40x40@3x.png", 120, "iOS 40pt @3x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-60x60@2x.png", 120, "iOS 60pt @2x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-60x60@3x.png", 180, "iOS 60pt @3x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-76x76@1x.png", 76, "iOS iPad 76pt @1x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-76x76@2x.png", 152, "iOS iPad 76pt @2x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-83.5x83.5@2x.png", 167, "iOS iPad Pro 83.5pt @2x")
        entries += IconSpec("ios/AppIcon.appiconset/Icon-App-1024x1024@1x.png", 1024, "App Store 1024px")
        return entries
    }

    /**
     * Adaptive icon XML (API 26+) for `mipmap-anydpi-v26`. Both `ic_launcher.xml` and
     * `ic_launcher_round.xml` share the one foreground shipped in the pack.
     */
    fun adaptiveIconXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
            <background android:drawable="@color/ic_launcher_background" />
            <foreground android:drawable="@mipmap/ic_launcher_foreground" />
        </adaptive-icon>
    """.trimIndent()

    // ------------------------------------------------------------- K-Means

    /**
     * Deterministic K-Means over ARGB pixels. Centroids start evenly spaced so the same
     * input always produces the same palette; result is sorted by cluster size (dominant first).
     */
    fun kMeansPalette(pixels: IntArray, k: Int, iterations: Int = 12): List<Int> {
        val clusters = k.coerceIn(1, 16)
        val opaque = pixels.filter { (it ushr 24) and 0xFF > 32 }
        val sample = opaque.ifEmpty { pixels.toList() }
        if (sample.isEmpty()) return emptyList()
        if (clusters == 1) return listOf(averageColor(sample))
        val actualK = min(clusters, sample.size)
        val step = max(1, sample.size / actualK)
        val centroids = MutableList(actualK) { index ->
            sample[(index * step).coerceAtMost(sample.lastIndex)]
        }
        val counts = IntArray(actualK)
        repeat(iterations.coerceAtLeast(1)) {
            val sumR = LongArray(actualK)
            val sumG = LongArray(actualK)
            val sumB = LongArray(actualK)
            counts.fill(0)
            for (pixel in sample) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                var best = 0
                var bestDistance = Long.MAX_VALUE
                for (c in 0 until actualK) {
                    val cr = (centroids[c] shr 16) and 0xFF
                    val cg = (centroids[c] shr 8) and 0xFF
                    val cb = centroids[c] and 0xFF
                    val dr = (r - cr).toLong()
                    val dg = (g - cg).toLong()
                    val db = (b - cb).toLong()
                    val distance = dr * dr + dg * dg + db * db
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = c
                    }
                }
                sumR[best] += r
                sumG[best] += g
                sumB[best] += b
                counts[best]++
            }
            for (c in 0 until actualK) {
                if (counts[c] > 0) {
                    centroids[c] = argb(
                        (sumR[c] / counts[c]).toInt(),
                        (sumG[c] / counts[c]).toInt(),
                        (sumB[c] / counts[c]).toInt()
                    )
                }
            }
        }
        return centroids.indices
            .filter { counts[it] > 0 }
            .sortedWith(compareByDescending<Int> { counts[it] }.thenBy { it })
            .map { centroids[it] }
            .distinct()
            .ifEmpty { listOf(averageColor(sample)) }
    }

    private fun averageColor(colors: List<Int>): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        for (pixel in colors) {
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
        }
        val size = colors.size.toLong()
        return argb((r / size).toInt(), (g / size).toInt(), (b / size).toInt())
    }

    fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
