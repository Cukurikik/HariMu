package com.harimu.app.core.engines

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class MatrixLayerKind { IMAGE, TEXT, STICKER }

/**
 * One layer of the Image Matrix canvas (IMG-47). Percentages keep a composition valid when it
 * is exported at a different resolution than the preview.
 */
data class MatrixLayer(
    val id: String,
    val kind: MatrixLayerKind,
    val uri: String = "",
    val text: String = "",
    val xPct: Float = 50f,
    val yPct: Float = 50f,
    val scalePct: Float = 100f,
    val rotationDeg: Float = 0f,
    val opacityPct: Int = 100,
    val blend: String = "Normal",
    val visible: Boolean = true,
    val colorHex: String = "#FFFFFF",
    val fontSizePct: Float = 12f
) {
    fun title(): String = when (kind) {
        MatrixLayerKind.IMAGE -> "Gambar"
        MatrixLayerKind.TEXT -> text.ifBlank { "Teks" }
        MatrixLayerKind.STICKER -> text.ifBlank { "Stiker" }
    }
}

/** Pure transform math, golden-tested in `ImageLayerCompositorTest`. */
object MatrixLayerMath {

    /** Scale applied to a bitmap layer so it fits the canvas at 100%. */
    fun fitScale(canvasW: Int, canvasH: Int, contentW: Int, contentH: Int): Float {
        if (contentW <= 0 || contentH <= 0) return 1f
        return min(canvasW.toFloat() / contentW, canvasH.toFloat() / contentH)
    }

    /**
     * 3x3 matrix that maps content coordinates (0..w, 0..h) onto the canvas with the layer's
     * centre, scale and rotation. Values are directly usable by [Canvas.concat].
     */
    fun transform(
        layer: MatrixLayer,
        canvasW: Int,
        canvasH: Int,
        contentW: Float,
        contentH: Float,
        baseScale: Float
    ): FloatArray {
        val scale = baseScale * (layer.scalePct.coerceIn(5f, 800f) / 100f)
        val centerX = canvasW * layer.xPct.coerceIn(0f, 100f) / 100f
        val centerY = canvasH * layer.yPct.coerceIn(0f, 100f) / 100f
        val matrix = Matrix()
        matrix.postTranslate(-contentW / 2f, -contentH / 2f)
        matrix.postScale(scale, scale)
        matrix.postRotate(layer.rotationDeg.coerceIn(-180f, 180f))
        matrix.postTranslate(centerX, centerY)
        val values = FloatArray(9)
        matrix.getValues(values)
        return values
    }

    /** Text size in pixels for a text/sticker layer. */
    fun textSize(layer: MatrixLayer, canvasW: Int, canvasH: Int): Float =
        (min(canvasW, canvasH) * layer.fontSizePct.coerceIn(1f, 60f) / 100f)
            .coerceAtLeast(8f) * (layer.scalePct.coerceIn(5f, 800f) / 100f)

    fun moved(layer: MatrixLayer, dxPct: Float, dyPct: Float): MatrixLayer =
        layer.copy(
            xPct = (layer.xPct + dxPct).coerceIn(0f, 100f),
            yPct = (layer.yPct + dyPct).coerceIn(0f, 100f)
        )
}

/**
 * Bounded undo/redo stack for the layer list. Every mutation pushes the previous snapshot.
 */
class LayerHistory(initial: List<MatrixLayer> = emptyList(), private val limit: Int = 50) {

    private val undoStack = ArrayDeque<List<MatrixLayer>>()
    private val redoStack = ArrayDeque<List<MatrixLayer>>()

    var current: List<MatrixLayer> = initial
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(next: List<MatrixLayer>) {
        undoStack.addLast(current)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        current = next
    }

    fun undo(): List<MatrixLayer>? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = previous
        return current
    }

    fun redo(): List<MatrixLayer>? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        return current
    }

    fun reset(next: List<MatrixLayer>) {
        undoStack.clear()
        redoStack.clear()
        current = next
    }
}

/**
 * Draws the layer stack. The Image Matrix preview renders through this same function (at a
 * smaller canvas), so what the user sees is what gets exported.
 */
object ImageLayerCompositor {

    fun compose(
        layers: List<MatrixLayer>,
        canvasWidth: Int,
        canvasHeight: Int,
        backgroundHex: String,
        bitmapFor: (MatrixLayer) -> Bitmap?
    ): Bitmap {
        val out = Bitmap.createBitmap(
            canvasWidth.coerceAtLeast(1),
            canvasHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(out)
        canvas.drawColor(ImageEngine.parseColorSafe(backgroundHex, Color.parseColor("#11141C")))
        layers.forEach { layer ->
            if (!layer.visible) return@forEach
            when (layer.kind) {
                MatrixLayerKind.IMAGE -> drawImageLayer(canvas, layer, canvasWidth, canvasHeight, bitmapFor)
                MatrixLayerKind.TEXT, MatrixLayerKind.STICKER ->
                    drawTextLayer(canvas, layer, canvasWidth, canvasHeight)
            }
        }
        return out
    }

    private fun basePaint(layer: MatrixLayer): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = (layer.opacityPct.coerceIn(0, 100) / 100f * 255).roundToInt()
        ImageBlendModes.applyTo(this, layer.blend)
    }

    private fun drawImageLayer(
        canvas: Canvas,
        layer: MatrixLayer,
        canvasW: Int,
        canvasH: Int,
        bitmapFor: (MatrixLayer) -> Bitmap?
    ) {
        val bitmap = bitmapFor(layer) ?: return
        val baseScale = MatrixLayerMath.fitScale(canvasW, canvasH, bitmap.width, bitmap.height)
        val values = MatrixLayerMath.transform(
            layer,
            canvasW,
            canvasH,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            baseScale
        )
        val matrix = Matrix().apply { setValues(values) }
        val paint = basePaint(layer).apply { isFilterBitmap = true }
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun drawTextLayer(canvas: Canvas, layer: MatrixLayer, canvasW: Int, canvasH: Int) {
        val text = layer.text.ifBlank {
            if (layer.kind == MatrixLayerKind.STICKER) "⭐" else "Teks"
        }
        val paint = basePaint(layer).apply {
            color = ImageEngine.parseColorSafe(layer.colorHex, Color.WHITE)
            textSize = MatrixLayerMath.textSize(layer, canvasW, canvasH)
            typeface = if (layer.kind == MatrixLayerKind.STICKER) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
            setShadowLayer(textSize * 0.05f, 0f, textSize * 0.02f, Color.argb(120, 0, 0, 0))
        }
        val width = paint.measureText(text)
        val values = MatrixLayerMath.transform(
            layer,
            canvasW,
            canvasH,
            width,
            paint.textSize,
            1f
        )
        val matrix = Matrix().apply { setValues(values) }
        canvas.save()
        canvas.concat(matrix)
        canvas.drawText(text, -width / 2f, paint.textSize * 0.35f, paint)
        canvas.restore()
    }

    /** Canvas size options offered by the Image Matrix export sheet. */
    fun canvasPresets(): List<Pair<String, Pair<Int, Int>>> = listOf(
        "1080 × 1080 (1:1)" to (1080 to 1080),
        "1080 × 1350 (4:5)" to (1080 to 1350),
        "1920 × 1080 (16:9)" to (1920 to 1080),
        "1080 × 1920 (9:16)" to (1080 to 1920),
        "2048 × 2048" to (2048 to 2048)
    )

    fun layerBlendLabels(): List<String> = ImageBlendModes.labels

    fun defaultImageLayer(id: String, uri: String): MatrixLayer =
        MatrixLayer(id = id, kind = MatrixLayerKind.IMAGE, uri = uri, scalePct = 100f)

    fun defaultTextLayer(id: String, text: String): MatrixLayer =
        MatrixLayer(
            id = id,
            kind = MatrixLayerKind.TEXT,
            text = text,
            xPct = 50f,
            yPct = 82f,
            fontSizePct = 10f
        )

    fun defaultStickerLayer(id: String, emoji: String): MatrixLayer =
        MatrixLayer(
            id = id,
            kind = MatrixLayerKind.STICKER,
            text = emoji.ifBlank { "⭐" },
            xPct = 74f,
            yPct = 24f,
            fontSizePct = 16f
        )

    fun scaledCanvas(width: Int, height: Int, maxDim: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxDim) return width to height
        val scale = maxDim.toFloat() / longest
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }
}
