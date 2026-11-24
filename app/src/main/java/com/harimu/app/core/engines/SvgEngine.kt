package com.harimu.app.core.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.caverock.androidsvg.SVG
import com.harimu.app.core.files.FileStorageManager
import java.io.File
import java.io.FileOutputStream

/**
 * Rasterisasi SVG (FASE 13, CNV-10) memakai AndroidSVG 1.4 (Apache-2.0).
 *
 * Batas jujur:
 * - AndroidSVG mendukung SVG 1.1 dasar + sebagian SVG 1.2 Tiny: filter efek (`feGaussianBlur`),
 *   teks yang bergantung font sistem, dan animasi **tidak** dirender.
 * - Lebar bitmap dibatasi [MAX_WIDTH_PX] agar memori aman untuk gambar vektor raksasa.
 */
object SvgEngine {

    const val MAX_WIDTH_PX = 4096

    data class Rendered(val file: File, val width: Int, val height: Int, val pageWidth: Float, val pageHeight: Float)

    fun render(
        context: Context,
        uri: Uri,
        dest: File,
        targetWidthPx: Int = 1080,
        format: String = "PNG",
        background: String = "Transparan"
    ): Rendered {
        val svg = context.contentResolver.openInputStream(uri)?.use { input ->
            SVG.getFromInputStream(input)
        } ?: error("Gagal membaca berkas SVG")

        val docWidth = svg.documentWidth
        val docHeight = svg.documentHeight
        require(docWidth > 0 && docHeight > 0) { "SVG tidak punya ukuran viewBox/width-height yang valid" }

        val width = targetWidthPx.coerceIn(64, MAX_WIDTH_PX)
        val scale = width / docWidth
        val height = (docHeight * scale).toInt().coerceIn(1, MAX_WIDTH_PX * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (background.equals("Putih", true) || background.equals("White", true)) {
            canvas.drawColor(Color.WHITE)
        } else if (background.equals("Hitam", true) || background.equals("Black", true)) {
            canvas.drawColor(Color.BLACK)
        }
        canvas.scale(scale, scale)
        svg.renderToCanvas(canvas)

        val isJpeg = format.equals("JPG", true) || format.equals("JPEG", true)
        FileOutputStream(dest).use { out ->
            if (isJpeg) {
                // JPEG tidak punya alpha; latar transparan menjadi hitam bila tidak dipilih.
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } else {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        bitmap.recycle()
        return Rendered(dest, width, height, docWidth, docHeight)
    }

    /** Ukuran dokumen tanpa merender (dipakai untuk ringkasan). */
    fun documentSize(context: Context, uri: Uri): Pair<Float, Float> {
        val svg = context.contentResolver.openInputStream(uri)?.use { SVG.getFromInputStream(it) }
            ?: error("Gagal membaca berkas SVG")
        return svg.documentWidth to svg.documentHeight
    }
}
