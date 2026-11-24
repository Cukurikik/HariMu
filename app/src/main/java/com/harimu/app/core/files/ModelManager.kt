package com.harimu.app.core.files

import android.content.Context
import com.harimu.app.core.toolapi.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class LocalModelStatus(
    val spec: ModelSpec,
    val isDownloaded: Boolean,
    val localSizeBytes: Long
)

object ModelRegistry {
    val u2NetSeg = ModelSpec(
        id = "u2net_seg",
        name = "U²-Net Subject Segmentation",
        sizeBytes = 42L * 1024L * 1024L,
        sizeLabel = "42 MB"
    )
    val whisperTiny = ModelSpec(
        id = "whisper_tiny",
        name = "Whisper Tiny Speech-to-Text",
        sizeBytes = 75L * 1024L * 1024L,
        sizeLabel = "75 MB"
    )
    val realEsrganLite = ModelSpec(
        id = "realesrgan_lite",
        name = "Real-ESRGAN Lite Upscaler",
        sizeBytes = 18L * 1024L * 1024L,
        sizeLabel = "18 MB"
    )
    val demucsLite = ModelSpec(
        id = "demucs_lite",
        name = "Demucs 4-Stem Separator",
        sizeBytes = 84L * 1024L * 1024L,
        sizeLabel = "84 MB"
    )

    val allModels: List<ModelSpec> = listOf(u2NetSeg, whisperTiny, realEsrganLite, demucsLite)

    /**
     * URL unduhan model resmi. SENGAJA kosong: belum ada berkas model berlisensi yang
     * didistribusikan bersama repo, dan mengarang URL akan membuat "unduh" palsu. Isi peta ini
     * (bersama `sha256` di [ModelSpec]) saat model benar-benar tersedia.
     */
    val downloadUrls: Map<String, String> = emptyMap()
}

class ModelManager(private val context: Context) {

    val modelsDir: File
        get() = File(context.noBackupFilesDir, "models").apply { mkdirs() }

    fun modelFile(modelId: String): File {
        val safeId = modelId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(modelsDir, "$safeId.tflite")
    }

    fun isDownloaded(modelId: String): Boolean {
        val file = modelFile(modelId)
        if (!file.exists() || file.length() <= 0L) return false
        val spec = ModelRegistry.allModels.firstOrNull { it.id == modelId } ?: return true
        // Ukuran harus sama persis dengan manifes; SHA-256 diverifikasi saat unduh.
        return spec.sizeBytes <= 0L || file.length() == spec.sizeBytes
    }

    fun listStatuses(): List<LocalModelStatus> = ModelRegistry.allModels.map { spec ->
        val file = modelFile(spec.id)
        val exists = file.exists() && file.length() > 0L
        val sizeOk = spec.sizeBytes <= 0L || file.length() == spec.sizeBytes
        LocalModelStatus(
            spec = spec,
            isDownloaded = exists && sizeOk,
            localSizeBytes = if (exists) file.length() else 0L
        )
    }

    fun totalDownloadedBytes(): Long =
        modelsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteModel(modelId: String): Boolean {
        val file = modelFile(modelId)
        return if (file.exists()) file.delete() else false
    }

    fun clearAllModels(): Long {
        val total = totalDownloadedBytes()
        modelsDir.listFiles()?.forEach { it.deleteRecursively() }
        return total
    }

    /**
     * Mengunduh model dengan progres, memverifikasi SHA-256 bila [ModelSpec.sha256] diisi, lalu
     * menaruhnya secara atomik (`.part` → nama final). Gagal verifikasi = berkas dihapus, bukan
     * dipakai diam-diam.
     */
    suspend fun downloadModel(
        spec: ModelSpec,
        url: String = ModelRegistry.downloadUrls[spec.id].orEmpty(),
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        require(url.isNotBlank()) {
            "Model '${spec.name}' (${spec.sizeLabel}) belum punya URL unduhan resmi. " +
                "Tool AI terkait berjalan dengan algoritma lokal (heuristik) sampai model disediakan."
        }
        val target = modelFile(spec.id)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
        try {
            val response = client.newCall(Request.Builder().url(url).get().build()).execute()
            response.use { res ->
                require(res.isSuccessful) { "Unduhan model gagal (HTTP ${res.code})" }
                val body = res.body ?: error("Respons model kosong")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                    }
                }
            }
            if (spec.sha256.isNotBlank()) {
                val actual = sha256Hex(temp)
                if (!actual.equals(spec.sha256, ignoreCase = true)) {
                    temp.delete()
                    throw IllegalStateException(
                        "Checksum model tidak cocok (diharapkan ${spec.sha256.take(12)}…, didapat ${actual.take(12)}…). " +
                            "Berkas dihapus."
                    )
                }
            }
            if (target.exists()) target.delete()
            require(temp.renameTo(target)) { "Tidak dapat menyimpan model ke ${target.name}" }
            target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
