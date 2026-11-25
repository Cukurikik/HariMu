package com.harimu.app

import com.harimu.app.core.engines.FormatSupportLevel
import com.harimu.app.core.engines.GeoPoint
import com.harimu.app.core.engines.ImageFormatMatrix
import com.harimu.app.core.engines.ImageGeometryMath
import com.harimu.app.core.engines.PixelRect
import com.harimu.app.core.engines.Quad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden tests for FASE 9 geometry. Every expectation is a hand-computed number, so a change
 * in the math that alters a crop/warp/rotation result fails here instead of on a device.
 */
class ImageGeometryGoldenTest {

    // ------------------------------------------------------------- crop

    @Test
    fun cropRectFromPercentages() {
        assertEquals(PixelRect(250, 50, 500, 200), ImageGeometryMath.cropRect(1000, 500, 25f, 10f, 50f, 40f))
    }

    @Test
    fun cropRectClampsToFrame() {
        assertEquals(PixelRect(0, 0, 1000, 500), ImageGeometryMath.cropRect(1000, 500, -10f, -10f, 200f, 200f))
    }

    @Test
    fun squareCropWithoutZoomKeepsWholeFrame() {
        assertEquals(
            PixelRect(0, 0, 1000, 1000),
            ImageGeometryMath.cropRectForRatio(1000, 1000, 1f, 1f, 100f, 0f, 0f)
        )
    }

    @Test
    fun zoom200TakesCenterHalf() {
        assertEquals(
            PixelRect(250, 250, 500, 500),
            ImageGeometryMath.cropRectForRatio(1000, 1000, 1f, 1f, 200f, 0f, 0f)
        )
    }

    @Test
    fun offsetPushesCropToTheRightEdge() {
        assertEquals(
            PixelRect(500, 250, 500, 500),
            ImageGeometryMath.cropRectForRatio(1000, 1000, 1f, 1f, 200f, 100f, 0f)
        )
    }

    @Test
    fun wideRatioTakesFullWidthAnd563Height() {
        assertEquals(
            PixelRect(0, 219, 1000, 563),
            ImageGeometryMath.cropRectForRatio(1000, 1000, 16f, 9f, 100f, 0f, 0f)
        )
    }

    // ------------------------------------------- EXIF orientation mapping

    @Test
    fun orientationNormalIsIdentity() {
        assertEquals(
            PixelRect(100, 200, 300, 400),
            ImageGeometryMath.cropSourceRegion(4000, 3000, PixelRect(100, 200, 300, 400), 1)
        )
    }

    @Test
    fun orientationRotate180MirrorsBothAxes() {
        assertEquals(
            PixelRect(3600, 2400, 300, 400),
            ImageGeometryMath.cropSourceRegion(4000, 3000, PixelRect(100, 200, 300, 400), 3)
        )
    }

    @Test
    fun orientationRotate90SwapsAxes() {
        // Display is 3000x4000 for a 4000x3000 source stored sideways.
        assertEquals(
            PixelRect(200, 2600, 400, 300),
            ImageGeometryMath.cropSourceRegion(4000, 3000, PixelRect(100, 200, 300, 400), 6)
        )
    }

    @Test
    fun orientationRotate270SwapsAxesTheOtherWay() {
        assertEquals(
            PixelRect(3400, 100, 400, 300),
            ImageGeometryMath.cropSourceRegion(4000, 3000, PixelRect(100, 200, 300, 400), 8)
        )
    }

    // --------------------------------------------------------- rotation

    @Test
    fun rotateSizeSwapsOnQuarterTurns() {
        assertEquals(Pair(800, 1200), ImageGeometryMath.rotateSize(1200, 800, 90f))
        assertEquals(Pair(1200, 800), ImageGeometryMath.rotateSize(1200, 800, 180f))
        assertEquals(Pair(800, 1200), ImageGeometryMath.rotateSize(1200, 800, 450f))
        assertEquals(Pair(800, 1200), ImageGeometryMath.rotateSize(1200, 800, -90f))
    }

    @Test
    fun expandCanvasPadsToWideRatio() {
        assertEquals(Pair(1778, 1000), ImageGeometryMath.expandCanvasSize(1000, 1000, 16f / 9f))
    }

    @Test
    fun expandCanvasPadsSquareFromWidePhoto() {
        assertEquals(Pair(1600, 1600), ImageGeometryMath.expandCanvasSize(1600, 900, 1f))
    }

    @Test
    fun rotatedBoundsOfSquareAt45Degrees() {
        assertEquals(Pair(141, 141), ImageGeometryMath.rotatedBounds(100, 100, 45f))
    }

    @Test
    fun straightenSafeRectAt45DegreesIsCentered() {
        assertEquals(PixelRect(35, 35, 71, 71), ImageGeometryMath.straightenSafeRect(100, 100, 45f))
    }

    @Test
    fun straightenWithoutAngleIsFullFrame() {
        assertEquals(PixelRect(0, 0, 100, 100), ImageGeometryMath.straightenSafeRect(100, 100, 0f))
    }

    // ----------------------------------------------------- perspective

    @Test
    fun homographyOfIdenticalSquaresIsIdentity() {
        val square = Quad.fullFrame(1, 1)
        val values = ImageGeometryMath.homography(square.points(), square.points())
        assertNotNull(values)
        val expected = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        expected.forEachIndexed { index, value ->
            assertEquals("h[$index]", value, values!![index], 1e-4f)
        }
    }

    @Test
    fun homographyScalesUniformly() {
        val source = Quad.fullFrame(1, 1)
        val target = Quad(
            GeoPoint(0f, 0f),
            GeoPoint(2f, 0f),
            GeoPoint(2f, 2f),
            GeoPoint(0f, 2f)
        )
        val values = ImageGeometryMath.homography(source.points(), target.points())
        assertNotNull(values)
        val expected = floatArrayOf(2f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 1f)
        expected.forEachIndexed { index, value ->
            assertEquals("h[$index]", value, values!![index], 1e-4f)
        }
    }

    @Test
    fun perspectiveOutputSizeUsesLongestEdges() {
        val quad = Quad(
            GeoPoint(0f, 0f),
            GeoPoint(200f, 0f),
            GeoPoint(200f, 100f),
            GeoPoint(0f, 100f)
        )
        assertEquals(Pair(200, 100), ImageGeometryMath.perspectiveOutputSize(quad))
    }

    @Test
    fun fullFrameQuadIsValid() {
        assertNull(ImageGeometryMath.quadValidationError(Quad.fullFrame(200, 100), 200, 100))
    }

    @Test
    fun crossingQuadIsRejected() {
        val crossing = Quad(
            GeoPoint(10f, 10f),
            GeoPoint(190f, 90f),
            GeoPoint(190f, 10f),
            GeoPoint(10f, 90f)
        )
        assertEquals(
            "Urutan titik tidak membentuk segi empat cembung",
            ImageGeometryMath.quadValidationError(crossing, 200, 100)
        )
    }

    @Test
    fun pointOutsideFrameIsRejected() {
        val outside = Quad(
            GeoPoint(-20f, 0f),
            GeoPoint(200f, 0f),
            GeoPoint(200f, 100f),
            GeoPoint(0f, 100f)
        )
        assertEquals(
            "Titik harus berada di dalam gambar",
            ImageGeometryMath.quadValidationError(outside, 200, 100)
        )
    }

    // ------------------------------------------------------ multi crop

    @Test
    fun parseCropRegionsReadsNamesAndValues() {
        val regions = ImageGeometryMath.parseCropRegions("kiri:0,0,50,100; kanan:50,0,50,100")
        assertEquals(2, regions.size)
        assertEquals("kiri", regions[0].name)
        assertEquals(0f, regions[0].leftPct, 1e-4f)
        assertEquals(50f, regions[0].widthPct, 1e-4f)
        assertEquals("kanan", regions[1].name)
        assertEquals(50f, regions[1].leftPct, 1e-4f)
        assertEquals(100f, regions[1].heightPct, 1e-4f)
    }

    @Test
    fun parseCropRegionsRejectsMalformedEntries() {
        val error = runCatching { ImageGeometryMath.parseCropRegions("rusak:10,20,30") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    // ------------------------------------------------ saliency auto crop

    @Test
    fun saliencyFindsTheDarkBlock() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        for (y in 4..5) {
            for (x in 4..5) {
                pixels[y * width + x] = 0xFF000000.toInt()
            }
        }
        assertEquals(
            PixelRect(4, 4, 2, 2),
            ImageGeometryMath.saliencyBoundingBox(pixels, width, height, 10)
        )
    }

    @Test
    fun paddingGrowsBoxAndStaysInsideFrame() {
        assertEquals(
            PixelRect(3, 3, 4, 4),
            ImageGeometryMath.padBoundingBox(PixelRect(4, 4, 2, 2), 10, 10, 50f)
        )
    }

    // ------------------------------------------------------- alignment

    @Test
    fun alignmentRecoversTwoOnePixelShift() {
        val width = 8
        val height = 8
        val reference = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val value = (x * 7 + y * 13) % 200 + 20
            (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }
        val shifted = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val sourceX = x - 2
            val sourceY = y - 1
            if (sourceX in 0 until width && sourceY in 0 until height) {
                reference[sourceY * width + sourceX]
            } else {
                0
            }
        }
        assertEquals(Pair(2, 1), ImageGeometryMath.alignmentOffset(reference, shifted, width, height, 3))
    }

    // -------------------------------------------------------- icon pack

    @Test
    fun iconPackCoversAndroidPlayIosAndFavicon() {
        val entries = ImageGeometryMath.iconPackEntries()
        assertEquals(35, entries.size)
        fun sizeOf(path: String): Int = entries.first { it.path == path }.sizePx
        assertEquals(192, sizeOf("mipmap-xxxhdpi/ic_launcher.png"))
        assertEquals(192, sizeOf("mipmap-xxxhdpi/ic_launcher_round.png"))
        assertEquals(432, sizeOf("mipmap-xxxhdpi/ic_launcher_foreground.png"))
        assertEquals(512, sizeOf("playstore-icon-512.png"))
        assertEquals(32, sizeOf("favicon-32.png"))
        assertEquals(1024, sizeOf("ios/AppIcon.appiconset/Icon-App-1024x1024@1x.png"))
        assertEquals(5, entries.count { it.path.endsWith("/ic_launcher.png") })
        assertEquals(5, entries.count { it.path.contains("_round.png") })
    }

    // ----------------------------------------------------------- K-Means

    @Test
    fun kMeansSeparatesTwoExactColours() {
        val red = 0xFFE53935.toInt()
        val blue = 0xFF1E88E5.toInt()
        val pixels = IntArray(100) { index -> if (index < 50) red else blue }
        assertEquals(listOf(red, blue), ImageGeometryMath.kMeansPalette(pixels, 2))
    }

    @Test
    fun kMeansIgnoresFullyTransparentPixels() {
        val green = 0xFF43A047.toInt()
        val pixels = IntArray(30) { index -> if (index < 15) green else 0x00000000 }
        assertEquals(listOf(green), ImageGeometryMath.kMeansPalette(pixels, 3))
    }

    // --------------------------------------------------- format matrix

    @Test
    fun encodableFormatsAreExactlyThePlatformWriters() {
        assertEquals(listOf("JPG", "PNG", "WebP", "PDF"), ImageFormatMatrix.encodeLabels)
    }

    @Test
    fun heicIsDecodeLimitedAndNotEncodable() {
        val heic = ImageFormatMatrix.byLabel("HEIC")
        assertNotNull(heic)
        assertEquals(FormatSupportLevel.LIMITED, heic!!.decode)
        assertEquals(FormatSupportLevel.NONE, heic.encode)
    }

    @Test
    fun unsupportedTargetsAreRefusedWithReason() {
        listOf("HEIC", "AVIF", "SVG", "TIFF", "PSD", "BMP", "RAW").forEach { label ->
            val error = runCatching { ImageFormatMatrix.requireEncodable(label) }.exceptionOrNull()
            assertTrue("$label harus ditolak", error is IllegalStateException)
        }
    }

    @Test
    fun extensionLookupFindsJpegAndPdf() {
        assertEquals("JPG", ImageFormatMatrix.forExtension("jpeg")?.id)
        assertEquals("PDF", ImageFormatMatrix.forExtension(".PDF")?.id)
        assertEquals("PNG", ImageFormatMatrix.requireEncodable("png").id)
    }
}
