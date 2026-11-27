package com.harimu.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.harimu.app.core.files.FileStorageManager
import kotlin.math.roundToInt

/** Rasterisasi halaman PDF dengan PdfRenderer bawaan Android (tanpa dependensi tambahan). */
object PdfRenderUtils {

    data class Rendered(val bitmap: Bitmap, val pageWidthPts: Float, val pageHeightPts: Float)

    fun renderPages(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dpi: Int,
        maxPages: Int = Int.MAX_VALUE,
        password: String = "",
        onPage: (index: Int, page: Rendered, totalPages: Int) -> Boolean
    ): Int {
        val temp = PdfVaultEngine.prepareRenderableTemp(context, storage, uri, password)
        try {
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val total = minOf(renderer.pageCount, maxPages)
                    for (index in 0 until total) {
                        var bitmap: Bitmap? = null
                        val page = renderer.openPage(index)
                        val wPts: Float
                        val hPts: Float
                        try {
                            val scale = dpi.coerceIn(72, 300) / 72f
                            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            wPts = page.width.toFloat()
                            hPts = page.height.toFloat()
                        } finally {
                            page.close()
                        }
                        val rendered = Rendered(bitmap!!, wPts, hPts)
                        val keepGoing = try {
                            onPage(index, rendered, total)
                        } catch (t: Throwable) {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            throw t
                        }
                        if (!keepGoing) {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            return index + 1
                        }
                    }
                    return total
                }
            }
        } finally {
            temp.delete()
        }
    }

    fun bitmap(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        dpi: Int,
        pageIndex: Int = 0,
        password: String = ""
    ): Bitmap? {
        val temp = PdfVaultEngine.prepareRenderableTemp(context, storage, uri, password)
        try {
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    val page = renderer.openPage(pageIndex)
                    return try {
                        val scale = dpi.coerceIn(72, 300) / 72f
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        bitmap
                    } finally {
                        page.close()
                    }
                }
            }
        } finally {
            temp.delete()
        }
    }

    fun pageCount(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        password: String = ""
    ): Int {
        val temp = PdfVaultEngine.prepareRenderableTemp(context, storage, uri, password)
        try {
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> return renderer.pageCount }
            }
        } finally {
            temp.delete()
        }
    }
}
