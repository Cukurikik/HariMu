package com.harimu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harimu.app.core.engines.ImageIO
import com.harimu.app.core.toolapi.ToolInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Image tools: IMG-01 crop, IMG-23 konversi format, IMG-29 hapus EXIF.
 * Sumber: `sample_image.png` (PNG kecil, tanpa orientasi EXIF).
 */
@RunWith(AndroidJUnit4::class)
class ImageToolsInstrumentedTest {

    private val support = InstrumentedTestSupport
    private val sourceImage get() = support.sampleUri("sample_image.png")

    @Before
    fun setUp() {
        support.clearTemp()
    }

    @Test
    fun cropToOneToOneProducesSquareImage() = runBlocking {
        val sourceInfo = ImageIO.readSourceInfo(support.context, sourceImage)
        val result = support.runTool(
            "IMG-01",
            ToolInput(
                uris = listOf(sourceImage),
                params = mapOf(
                    "ratio" to "1:1",
                    "format" to "PNG",
                    "keepExif" to false
                )
            )
        )
        val out = support.singleOutput(result)
        val cropped = ImageIO.readSourceInfo(support.context, support.outputUri(result))

        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_IMG_CROP_"))
        assertTrue("lebar hasil ${cropped.width} harus > 0", cropped.width > 0)
        assertEquals("crop 1:1 harus persegi", cropped.width, cropped.height)
        assertTrue(
            "hasil ${cropped.width}px tidak boleh melebihi sumber ${sourceInfo.width}px",
            cropped.width <= sourceInfo.width.coerceAtLeast(1)
        )
    }

    @Test
    fun formatConverterKeepsPixelDimensions() = runBlocking {
        val sourceInfo = ImageIO.readSourceInfo(support.context, sourceImage)
        val result = support.runTool(
            "IMG-23",
            ToolInput(
                uris = listOf(sourceImage),
                params = mapOf("format" to "JPG", "quality" to 85f, "keepMeta" to false)
            )
        )
        val out = support.singleOutput(result)
        val converted = ImageIO.readSourceInfo(support.context, support.outputUri(result))

        assertEquals("image/jpeg", result.outputs.first().mimeType)
        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_IMG_CONV_") && out.name.endsWith(".jpg"))
        assertEquals("lebar harus sama", sourceInfo.width, converted.width)
        assertEquals("tinggi harus sama", sourceInfo.height, converted.height)
    }

    @Test
    fun exifStripperRemovesMetadataWithoutChangingPixels() = runBlocking {
        val sourceInfo = ImageIO.readSourceInfo(support.context, sourceImage)
        val result = support.runTool(
            "IMG-29",
            ToolInput(
                uris = listOf(sourceImage),
                params = mapOf(
                    "strip" to true,
                    "removeGps" to true,
                    "artist" to "",
                    "copyright" to "",
                    "description" to ""
                )
            )
        )
        val out = support.singleOutput(result)
        val stripped = ImageIO.readSourceInfo(support.context, support.outputUri(result))

        assertTrue("nama keluaran ${out.name}", out.name.startsWith("HariMu_IMG_META_"))
        assertEquals("lebar tidak boleh berubah", sourceInfo.width, stripped.width)
        assertEquals("tinggi tidak boleh berubah", sourceInfo.height, stripped.height)
        assertTrue(
            "EXIF masih terbaca setelah strip: ${ImageIO.exifReport(support.context, support.outputUri(result))}",
            ImageIO.exifReport(support.context, support.outputUri(result)).isEmpty()
        )
    }
}
