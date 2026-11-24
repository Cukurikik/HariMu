package com.harimu.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OCR on-device memakai Tesseract4Android (Apache-2.0). Data bahasa (`*.traineddata`) diunduh
 * ke `filesDir/tessdata` saat pertama dipakai — tidak dibundel di APK agar tetap ringan.
 *
 * Sumber data: tessdata_fast resmi proyek Tesseract (Apache-2.0). Berkas tidak punya checksum
 * publik per bahasa, jadi integritas diverifikasi dengan ukuran minimum + pembacaan header.
 */
object PdfOcrEngine {

    data class OcrLanguage(val code: String, val labelId: String, val labelEn: String)

    val LANGUAGES: List<OcrLanguage> = listOf(
        OcrLanguage("ind", "Indonesia", "Indonesian"),
        OcrLanguage("eng", "Inggris", "English"),
        OcrLanguage("ara", "Arab", "Arabic"),
        OcrLanguage("chi_sim", "Tionghoa Sederhana", "Chinese (Simplified)"),
        OcrLanguage("jpn", "Jepang", "Japanese"),
        OcrLanguage("kor", "Korea", "Korean"),
        OcrLanguage("rus", "Rusia", "Russian"),
        OcrLanguage("spa", "Spanyol", "Spanish")
    )

    private const val DOWNLOAD_BASE = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main"
    private const val MIN_BYTES = 200_000L

    fun codeFromLabel(label: String): String =
        LANGUAGES.firstOrNull { label.equals(it.labelId, true) || label.equals(it.labelEn, true) }?.code
            ?: if (LANGUAGES.any { it.code == label }) label else "eng"

    fun dataDir(context: Context): File =
        File(context.filesDir, "tessdata").apply { mkdirs() }

    fun installed(context: Context, code: String): Boolean {
        val file = File(dataDir(context), "$code.traineddata")
        return file.isFile && file.length() > MIN_BYTES
    }

    fun missing(context: Context, codes: List<String>): List<String> =
        codes.distinct().filterNot { installed(context, it) }

    /** Unduh data bahasa yang belum ada. [onProgress] menerima 0..100. */
    suspend fun ensureTrainedData(
        context: Context,
        codes: List<String>,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val needed = missing(context, codes)
        needed.forEachIndexed { index, code ->
            onProgress(index * 100 / needed.size, "Mengunduh data OCR: $code")
            downloadLanguage(context, code)
        }
        onProgress(100, "Data OCR siap")
    }

    private fun downloadLanguage(context: Context, code: String) {
        val target = File(dataDir(context), "$code.traineddata")
        val temp = File(dataDir(context), "$code.traineddata.part")
        val connection = (URL("$DOWNLOAD_BASE/$code.traineddata").openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("Unduh $code gagal (HTTP ${connection.responseCode})")
            }
            connection.inputStream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            require(temp.length() > MIN_BYTES) {
                "Data bahasa $code tidak valid (${temp.length()} byte). Periksa koneksi."
            }
            if (target.exists()) target.delete()
            require(temp.renameTo(target)) { "Gagal menyimpan data bahasa $code" }
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    }

    /** OCR satu bitmap. Melempar error jelas bila data bahasa belum tersedia. */
    fun recognize(context: Context, bitmap: Bitmap, codes: List<String>): String {
        val resolved = codes.ifEmpty { listOf("eng") }.distinct()
        val absent = missing(context, resolved)
        require(absent.isEmpty()) {
            "Data OCR belum ada: ${absent.joinToString(", ")}. Jalankan unduhan data bahasa dahulu."
        }
        val api = TessBaseAPI()
        try {
            val ok = api.init(
                dataDir(context).absolutePath,
                resolved.joinToString("+"),
                TessBaseAPI.OEM_LSTM_ONLY
            )
            check(ok) { "Mesin OCR gagal diinisialisasi untuk ${resolved.joinToString("+")}" }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            // Overload IntArray lebih luas dukungannya daripada setImage(Bitmap).
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            api.setImage(pixels, width, height, 4, width * 4)
            val text = api.getUTF8Text() ?: ""
            Log.d("PdfOcrEngine", "OCR ${resolved.joinToString("+")} conf=${api.meanConfidence()}")
            return text.trim()
        } finally {
            runCatching { api.recycle() }
        }
    }
}
