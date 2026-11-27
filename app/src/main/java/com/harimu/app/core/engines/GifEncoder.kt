package com.harimu.app.core.engines

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Small GIF89a encoder (no third-party dependency, so no new licence obligations).
 *
 * Quality trade-off, documented in `docs/DECISIONS.md` §13: one shared 256-colour palette is
 * computed with median-cut over all frames, then mapped through a 32k-entry lookup table so
 * encoding stays O(pixels). Banding can appear on smooth gradients — GIF has no more colours
 * to offer anyway.
 */
class GifEncoder(
    private val width: Int,
    private val height: Int
) {
    init {
        require(width in 1..4096 && height in 1..4096) { "Ukuran GIF tidak valid: ${width}x$height" }
    }

    /**
     * Writes a GIF to [out]. [delayCentisec] is the per-frame delay in 1/100 s, [loop] adds the
     * NETSCAPE2.0 looping extension. Returns the number of frames written.
     */
    fun encode(
        frames: List<Bitmap>,
        delayCentisec: Int,
        loop: Boolean,
        out: OutputStream
    ): Int {
        require(frames.isNotEmpty()) { "Butuh minimal 1 frame untuk GIF" }
        val delay = delayCentisec.coerceIn(2, 1000)

        val scaled = frames.map { frame ->
            if (frame.width == width && frame.height == height) {
                frame
            } else {
                Bitmap.createScaledBitmap(frame, width, height, true)
            }
        }
        try {
            val pixelCount = width * height
            val pixels = IntArray(pixelCount)
            val histogram = IntArray(BUCKETS)
            val sampleStep = (pixelCount.toLong() / 200_000L).toInt().coerceAtLeast(1)
            scaled.forEach { frame ->
                frame.getPixels(pixels, 0, width, 0, 0, width, height)
                var i = 0
                while (i < pixels.size) {
                    val p = pixels[i]
                    if ((p ushr 24) and 0xFF > 8) histogram[bucketOf(p)]++
                    i += sampleStep
                }
            }

            val palette = medianCutPalette(histogram, 256)
            val lookup = buildLookup(palette)
            val indices = ByteArray(pixelCount)

            writeHeader(out, palette, loop)
            scaled.forEach { frame ->
                frame.getPixels(pixels, 0, width, 0, 0, width, height)
                for (i in pixels.indices) indices[i] = lookup[bucketOf(pixels[i])].toByte()
                writeGraphicControl(out, delay)
                writeImageDescriptor(out)
                writeLzw(out, indices)
            }
            out.write(0x3B)
            out.flush()
            return scaled.size
        } finally {
            for (i in scaled.indices) {
                if (scaled[i] !== frames[i] && !scaled[i].isRecycled) scaled[i].recycle()
            }
        }
    }

    private fun bucketOf(pixel: Int): Int =
        (((pixel shr 19) and 0x1F) shl 10) or
            (((pixel shr 11) and 0x1F) shl 5) or
            ((pixel shr 3) and 0x1F)

    // ------------------------------------------------------------ palette

    private class Box(
        val rMin: Int, val rMax: Int,
        val gMin: Int, val gMax: Int,
        val bMin: Int, val bMax: Int,
        val weight: Long
    ) {
        fun longestAxis(): Int {
            val r = rMax - rMin
            val g = gMax - gMin
            val b = bMax - bMin
            return when {
                r >= g && r >= b && r > 0 -> 0
                g >= r && g >= b && g > 0 -> 1
                b > 0 -> 2
                else -> -1
            }
        }
    }

    private fun medianCutPalette(histogram: IntArray, maxColors: Int): IntArray {
        var rMin = 31
        var rMax = 0
        var gMin = 31
        var gMax = 0
        var bMin = 31
        var bMax = 0
        var total = 0L
        for (bucket in histogram.indices) {
            val count = histogram[bucket]
            if (count == 0) continue
            val r = (bucket shr 10) and 0x1F
            val g = (bucket shr 5) and 0x1F
            val b = bucket and 0x1F
            if (r < rMin) rMin = r
            if (r > rMax) rMax = r
            if (g < gMin) gMin = g
            if (g > gMax) gMax = g
            if (b < bMin) bMin = b
            if (b > bMax) bMax = b
            total += count
        }
        if (total == 0L) {
            return IntArray(256) { (0xFF shl 24) or (it shl 16) or (it shl 8) or it }
        }

        val boxes = ArrayList<Box>()
        boxes += Box(rMin, rMax, gMin, gMax, bMin, bMax, total)

        while (boxes.size < maxColors) {
            var target = -1
            var bestScore = -1L
            boxes.forEachIndexed { index, box ->
                val axis = box.longestAxis()
                if (axis < 0) return@forEachIndexed
                val score = box.weight * axisLength(box, axis)
                if (score > bestScore) {
                    bestScore = score
                    target = index
                }
            }
            if (target < 0) break
            val box = boxes[target]
            val axis = box.longestAxis()
            if (axis < 0) break
            val split = splitBox(box, axis, histogram) ?: break
            boxes[target] = split.first
            boxes += split.second
        }

        val palette = IntArray(256) { 0xFF000000.toInt() }
        boxes.forEachIndexed { index, box ->
            if (index >= 256) return@forEachIndexed
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var count = 0L
            for (r in box.rMin..box.rMax) {
                for (g in box.gMin..box.gMax) {
                    for (b in box.bMin..box.bMax) {
                        val weight = histogram[(r shl 10) or (g shl 5) or b].toLong()
                        if (weight == 0L) continue
                        sumR += expand5(r) * weight
                        sumG += expand5(g) * weight
                        sumB += expand5(b) * weight
                        count += weight
                    }
                }
            }
            palette[index] = if (count == 0L) {
                0xFF000000.toInt()
            } else {
                0xFF000000.toInt() or
                    (((sumR / count).toInt().coerceIn(0, 255)) shl 16) or
                    (((sumG / count).toInt().coerceIn(0, 255)) shl 8) or
                    ((sumB / count).toInt().coerceIn(0, 255))
            }
        }
        return palette
    }

    private fun expand5(value: Int): Int = (value shl 3) or (value shr 2)

    private fun axisLength(box: Box, axis: Int): Long = when (axis) {
        0 -> (box.rMax - box.rMin).toLong()
        1 -> (box.gMax - box.gMin).toLong()
        else -> (box.bMax - box.bMin).toLong()
    }

    /** Splits [box] at the weighted median of [axis]; returns null when it cannot be split. */
    private fun splitBox(box: Box, axis: Int, histogram: IntArray): Pair<Box, Box>? {
        val min = when (axis) {
            0 -> box.rMin
            1 -> box.gMin
            else -> box.bMin
        }
        val max = when (axis) {
            0 -> box.rMax
            1 -> box.gMax
            else -> box.bMax
        }
        if (max <= min) return null

        val counts = LongArray(max - min + 1)
        var total = 0L
        for (r in box.rMin..box.rMax) {
            for (g in box.gMin..box.gMax) {
                for (b in box.bMin..box.bMax) {
                    val weight = histogram[(r shl 10) or (g shl 5) or b].toLong()
                    if (weight == 0L) continue
                    val key = when (axis) {
                        0 -> r
                        1 -> g
                        else -> b
                    } - min
                    counts[key] += weight
                    total += weight
                }
            }
        }
        if (total == 0L) return null

        var running = 0L
        var splitIndex = 0
        for (i in counts.indices) {
            running += counts[i]
            if (running * 2 >= total) {
                splitIndex = i
                break
            }
        }
        if (splitIndex >= counts.size - 1) splitIndex = counts.size - 2
        if (splitIndex < 0) splitIndex = 0
        var leftWeight = 0L
        for (i in 0..splitIndex) leftWeight += counts[i]
        if (leftWeight <= 0L || leftWeight >= total) return null
        val splitAt = min + splitIndex

        return when (axis) {
            0 -> Box(box.rMin, splitAt, box.gMin, box.gMax, box.bMin, box.bMax, leftWeight) to
                Box(splitAt + 1, box.rMax, box.gMin, box.gMax, box.bMin, box.bMax, total - leftWeight)
            1 -> Box(box.rMin, box.rMax, box.gMin, splitAt, box.bMin, box.bMax, leftWeight) to
                Box(box.rMin, box.rMax, splitAt + 1, box.gMax, box.bMin, box.bMax, total - leftWeight)
            else -> Box(box.rMin, box.rMax, box.gMin, box.gMax, box.bMin, splitAt, leftWeight) to
                Box(box.rMin, box.rMax, box.gMin, box.gMax, splitAt + 1, box.bMax, total - leftWeight)
        }
    }

    private fun buildLookup(palette: IntArray): IntArray {
        val lookup = IntArray(BUCKETS)
        for (bucket in 0 until BUCKETS) {
            val r = expand5((bucket shr 10) and 0x1F)
            val g = expand5((bucket shr 5) and 0x1F)
            val b = expand5(bucket and 0x1F)
            var best = 0
            var bestDistance = Long.MAX_VALUE
            for (i in palette.indices) {
                val color = palette[i]
                val dr = (r - ((color shr 16) and 0xFF)).toLong()
                val dg = (g - ((color shr 8) and 0xFF)).toLong()
                val db = (b - (color and 0xFF)).toLong()
                val distance = dr * dr + dg * dg + db * db
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            lookup[bucket] = best
        }
        return lookup
    }

    // ------------------------------------------------------------- writing

    private fun writeHeader(out: OutputStream, palette: IntArray, loop: Boolean) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        out.write(0xF7) // global colour table, 256 entries
        out.write(0)
        out.write(0)
        for (i in 0 until 256) {
            val color = palette.getOrElse(i) { 0xFF000000.toInt() }
            out.write((color shr 16) and 0xFF)
            out.write((color shr 8) and 0xFF)
            out.write(color and 0xFF)
        }
        if (loop) {
            out.write(0x21)
            out.write(0xFF)
            out.write(0x0B)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(0x03)
            out.write(0x01)
            writeShort(out, 0)
            out.write(0x00)
        }
    }

    private fun writeGraphicControl(out: OutputStream, delay: Int) {
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        out.write(0x04) // disposal = do not dispose
        writeShort(out, delay)
        out.write(0x00)
        out.write(0x00)
    }

    private fun writeImageDescriptor(out: OutputStream) {
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        out.write(0x00) // no local colour table, not interlaced
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    /** GIF-flavoured LZW, minimum code size 8, sub-blocks of at most 255 bytes. */
    private fun writeLzw(out: OutputStream, indices: ByteArray) {
        val minCodeSize = 8
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var nextCode = endCode + 1
        val dictionary = HashMap<Int, Int>(8192)

        val block = ByteArrayOutputStream()
        var bitBuffer = 0
        var bitCount = 0

        fun flushBlock() {
            val bytes = block.toByteArray()
            if (bytes.isEmpty()) return
            var offset = 0
            while (offset < bytes.size) {
                val chunk = minOf(255, bytes.size - offset)
                out.write(chunk)
                out.write(bytes, offset, chunk)
                offset += chunk
            }
            out.write(0x00)
            block.reset()
        }

        fun emit(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                block.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
            if (block.size() >= 4096) flushBlock()
        }

        emit(clearCode)
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val value = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or value
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            emit(prefix)
            dictionary[key] = nextCode
            nextCode++
            if (nextCode > (1 shl codeSize) - 1 && codeSize < 12) {
                codeSize++
            }
            if (nextCode >= 4096) {
                emit(clearCode)
                dictionary.clear()
                codeSize = minCodeSize + 1
                nextCode = endCode + 1
            }
            prefix = value
        }
        emit(prefix)
        emit(endCode)
        if (bitCount > 0) block.write(bitBuffer and 0xFF)
        flushBlock()
    }

    companion object {
        const val BUCKETS: Int = 32 * 32 * 32
    }
}
