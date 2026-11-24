package com.harimu.app

import com.harimu.app.core.engines.ImageBlendModes
import com.harimu.app.core.engines.ImageLayerCompositor
import com.harimu.app.core.engines.ImageLutEngine
import com.harimu.app.core.engines.LayerHistory
import com.harimu.app.core.engines.MatrixLayerKind
import com.harimu.app.core.engines.MatrixLayerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 10 tests. Only the parts that are pure Kotlin live here (LUT parsing/lookup, layer
 * history, canvas math) so the suite still runs on the JVM without Robolectric. Bitmap/Matrix
 * work (compositor, filters, GIF bytes) is exercised on-device — see the phase report.
 */
class ImageForgeBatch2Test {

    // ------------------------------------------------------------ LUT .cube

    private fun cube(size: Int, mapping: (r: Int, g: Int, b: Int) -> Triple<Float, Float, Float>): String {
        val builder = StringBuilder("TITLE \"Test LUT\"\nLUT_3D_SIZE $size\n")
        // Red varies fastest, as in Adobe/Resolve exports.
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val (pr, pg, pb) = mapping(r, g, b)
                    builder.append("$pr $pg $pb\n")
                }
            }
        }
        return builder.toString()
    }

    @Test
    fun cubeHeaderAndTitleAreParsed() {
        val lut = ImageLutEngine.parseCube(cube(2) { r, g, b ->
            Triple(r.toFloat(), g.toFloat(), b.toFloat())
        })
        assertEquals(2, lut.size)
        assertEquals("Test LUT", lut.title)
        assertEquals(2 * 2 * 2 * 3, lut.data.size)
    }

    @Test
    fun identityCubeRoundTripsThroughTrilinearLookup() {
        val lut = ImageLutEngine.parseCube(cube(2) { r, g, b ->
            Triple(r.toFloat(), g.toFloat(), b.toFloat())
        })
        val corner = lut.lookupNormalized(1f, 0f, 1f)
        assertEquals(1f, corner[0], 1e-5f)
        assertEquals(0f, corner[1], 1e-5f)
        assertEquals(1f, corner[2], 1e-5f)

        val probe = lut.lookupNormalized(0.25f, 0.5f, 0.75f)
        assertEquals(0.25f, probe[0], 1e-4f)
        assertEquals(0.5f, probe[1], 1e-4f)
        assertEquals(0.75f, probe[2], 1e-4f)
    }

    @Test
    fun lookupAveragesTheEightCornersMidCube() {
        // Constant 0.2 red, 0.4 green, 0.6 blue everywhere: any interpolation must return it.
        val lut = ImageLutEngine.parseCube(cube(2) { _, _, _ -> Triple(0.2f, 0.4f, 0.6f) })
        val probe = lut.lookupNormalized(0.5f, 0.5f, 0.5f)
        assertEquals(0.2f, probe[0], 1e-5f)
        assertEquals(0.4f, probe[1], 1e-5f)
        assertEquals(0.6f, probe[2], 1e-5f)
    }

    @Test
    fun outOfRangeChannelsAreClamped() {
        val lut = ImageLutEngine.parseCube(cube(2) { r, g, b ->
            Triple(r.toFloat(), g.toFloat(), b.toFloat())
        })
        val probe = lut.lookupNormalized(-3f, 4f, 0.5f)
        assertEquals(0f, probe[0], 1e-5f)
        assertEquals(1f, probe[1], 1e-5f)
        assertEquals(0.5f, probe[2], 1e-4f)
    }

    @Test
    fun commentsAndDomainLinesAreIgnored() {
        val text = "# dibuat manual\nTITLE \"Ignored?\n" +
            "LUT_3D_SIZE 2\nDOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 1 1\n" +
            "0 0 0\n0 0 1\n0 1 0\n0 1 1\n1 0 0\n1 0 1\n1 1 0\n1 1 1\n"
        val lut = ImageLutEngine.parseCube(text)
        assertEquals(2, lut.size)
        assertEquals(1f, lut.lookupNormalized(1f, 1f, 1f)[0], 1e-5f)
    }

    @Test
    fun oneDimensionalCubeIsRejectedWithAReason() {
        val error = runCatching { ImageLutEngine.parseCube("LUT_1D_SIZE 16\n0 0 0\n") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("1D") == true)
    }

    @Test
    fun missingSizeHeaderIsRejected() {
        val error = runCatching { ImageLutEngine.parseCube("0 0 0\n1 1 1\n") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun valueCountMustMatchSizeCubed() {
        val error = runCatching {
            ImageLutEngine.parseCube("LUT_3D_SIZE 2\n0 0 0\n1 1 1\n")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("tidak cocok") == true)
    }

    @Test
    fun builtInPresetsAreAvailableAndSized17() {
        assertTrue(ImageLutEngine.presetNames.size >= 4)
        val preset = ImageLutEngine.preset(ImageLutEngine.presetNames.first())
        assertEquals(17, preset.size)
        assertEquals(17 * 17 * 17 * 3, preset.data.size)
        // Every preset must keep channels inside [0,1] so output is never clipped oddly.
        assertTrue(preset.data.all { it in 0f..1f })
    }

    // ------------------------------------------------------- layer history

    private fun layer(id: String) = ImageLayerCompositor.defaultTextLayer(id, id)

    @Test
    fun historyUndoRedoWalksTheStack() {
        val history = LayerHistory()
        history.push(listOf(layer("a")))
        history.push(listOf(layer("a"), layer("b")))

        assertTrue(history.canUndo)
        assertFalse(history.canRedo)

        val undone = history.undo()
        assertEquals(1, undone?.size)
        assertTrue(history.canRedo)

        val redone = history.redo()
        assertEquals(2, redone?.size)
        assertFalse(history.canRedo)
    }

    @Test
    fun newEditClearsTheRedoStack() {
        val history = LayerHistory()
        history.push(listOf(layer("a")))
        history.undo()
        assertTrue(history.canRedo)
        history.push(listOf(layer("c")))
        assertFalse(history.canRedo)
        assertEquals("c", history.current.single().id)
    }

    @Test
    fun historyIsCappedAndDropsOldestSnapshots() {
        val history = LayerHistory(limit = 2)
        history.push(listOf(layer("a")))
        history.push(listOf(layer("b")))
        history.push(listOf(layer("c")))
        assertTrue(history.undo() != null)
        assertTrue(history.undo() != null)
        assertNull(history.undo())
    }

    @Test
    fun resetClearsBothStacks() {
        val history = LayerHistory()
        history.push(listOf(layer("a")))
        history.undo()
        history.reset(listOf(layer("z")))
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals("z", history.current.single().id)
    }

    // ------------------------------------------------------- canvas & math

    @Test
    fun scaledPreviewKeepsAspectRatio() {
        assertEquals(720 to 540, ImageLayerCompositor.scaledCanvas(4000, 3000, 720))
        assertEquals(540 to 720, ImageLayerCompositor.scaledCanvas(3000, 4000, 720))
        assertEquals(500 to 400, ImageLayerCompositor.scaledCanvas(500, 400, 720))
    }

    @Test
    fun fitScaleUsesTheTighterAxis() {
        // 2000x500 content inside a 1000x1000 canvas is limited by width.
        assertEquals(0.5f, MatrixLayerMath.fitScale(1000, 1000, 2000, 500), 1e-5f)
        assertEquals(1f, MatrixLayerMath.fitScale(1000, 1000, 0, 0), 1e-5f)
    }

    @Test
    fun movedLayerStaysInsideTheCanvas() {
        val moved = MatrixLayerMath.moved(ImageLayerCompositor.defaultTextLayer("t", "Halo"), 80f, -200f)
        assertEquals(100f, moved.xPct, 1e-4f)
        assertEquals(0f, moved.yPct, 1e-4f)
    }

    @Test
    fun defaultLayersCarrySensibleGeometry() {
        val text = ImageLayerCompositor.defaultTextLayer("t1", "Judul")
        assertEquals(MatrixLayerKind.TEXT, text.kind)
        assertEquals(50f, text.xPct, 1e-4f)
        assertEquals(82f, text.yPct, 1e-4f)
        assertEquals(100, text.opacityPct)

        val sticker = ImageLayerCompositor.defaultStickerLayer("s1", "⭐")
        assertEquals(MatrixLayerKind.STICKER, sticker.kind)
        assertEquals(16f, sticker.fontSizePct, 1e-4f)

        val image = ImageLayerCompositor.defaultImageLayer("i1", "content://x")
        assertEquals(MatrixLayerKind.IMAGE, image.kind)
        assertEquals(100f, image.scalePct, 1e-4f)
    }

    @Test
    fun canvasPresetsAreAllPositiveAndCoverCommonRatios() {
        val presets = ImageLayerCompositor.canvasPresets()
        assertTrue(presets.size >= 4)
        assertTrue(presets.all { it.second.first > 0 && it.second.second > 0 })
        assertTrue(presets.any { it.second == (1080 to 1080) })
        assertTrue(presets.any { it.second == (1920 to 1080) })
    }

    @Test
    fun blendModeLabelsMatchTheEngineContract() {
        assertEquals(9, ImageBlendModes.labels.size)
        assertTrue(ImageBlendModes.labels.first() == "Normal")
        assertTrue(ImageBlendModes.labels.containsAll(listOf("Multiply", "Overlay", "Screen")))
    }

    @Test
    fun layerTitlesFallBackWhenTextIsBlank() {
        assertEquals("Teks", ImageLayerCompositor.defaultTextLayer("t", "").title())
        assertEquals("Gambar", ImageLayerCompositor.defaultImageLayer("i", "content://x").title())
    }

}
