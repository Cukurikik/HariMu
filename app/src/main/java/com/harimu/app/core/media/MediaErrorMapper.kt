package com.harimu.app.core.media

import java.util.ArrayDeque

class BoundedLogCollector(private val maxLines: Int = 250) {
    private val lock = Any()
    private val lines = ArrayDeque<String>()

    fun append(message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            trimmed.lines().forEach { line ->
                val clean = line.trim()
                if (clean.isNotEmpty()) {
                    if (lines.size >= maxLines) {
                        lines.removeFirst()
                    }
                    lines.addLast(clean)
                }
            }
        }
    }

    fun allLines(): List<String> = synchronized(lock) { lines.toList() }

    fun tail(count: Int = 20): String = synchronized(lock) {
        if (lines.isEmpty()) return@synchronized ""
        lines.drop((lines.size - count).coerceAtLeast(0)).joinToString("\n")
    }
}

data class MappedMediaError(
    val userMessage: String,
    val technicalDetail: String
)

object MediaErrorMapper {

    fun mapFfmpegFailure(
        returnCodeValue: Int?,
        isCancelled: Boolean,
        logs: String,
        isEnglish: Boolean
    ): MappedMediaError {
        val tail = logs.lines().takeLast(12).joinToString("\n").trim()
        if (isCancelled) {
            return MappedMediaError(
                userMessage = if (isEnglish) "Media job cancelled." else "Pemrosesan media dibatalkan.",
                technicalDetail = "Cancelled (code=${returnCodeValue ?: -1})\n$tail".trim()
            )
        }
        val lower = logs.lowercase()
        val userMsg = when {
            "no space left on device" in lower ->
                if (isEnglish) "Storage is full. Free up space and retry."
                else "Ruang penyimpanan penuh. Kosongkan memori lalu coba lagi."

            "no such file or directory" in lower || "does not exist" in lower ->
                if (isEnglish) "Input file could not be accessed or was moved."
                else "File sumber tidak dapat diakses atau telah dipindahkan."

            "permission denied" in lower ->
                if (isEnglish) "Permission denied while reading or writing media."
                else "Akses ditolak saat membaca atau menulis file media."

            "invalid data found" in lower || "moov atom not found" in lower || "end of file" in lower ->
                if (isEnglish) "Media file appears corrupted or incomplete."
                else "File media rusak, tidak lengkap, atau formatnya tidak valid."

            "unknown encoder" in lower || "unknown decoder" in lower || "encoder" in lower && "not found" in lower ->
                if (isEnglish) "Required codec is unavailable in this build."
                else "Codec yang dibutuhkan tidak tersedia pada perangkat atau build ini."

            "could not find tag for codec" in lower || "muxer does not support" in lower || "invalid argument" in lower ->
                if (isEnglish) "Output format does not support this stream codec combination."
                else "Format output tidak mendukung kombinasi codec stream dari file ini."

            else ->
                if (isEnglish) {
                    "FFmpeg processing failed (code ${returnCodeValue ?: -1})."
                } else {
                    "Pemrosesan FFmpeg gagal (kode ${returnCodeValue ?: -1})."
                }
        }
        return MappedMediaError(
            userMessage = userMsg,
            technicalDetail = tail.ifBlank { "Exit code ${returnCodeValue ?: -1}" }
        )
    }

    fun mapMedia3Failure(error: Throwable, isEnglish: Boolean): MappedMediaError {
        val detail = "${error::class.java.simpleName}: ${error.message.orEmpty()}".trim()
        val lower = detail.lowercase()
        val msg = when {
            "muxer" in lower || "format" in lower ->
                if (isEnglish) "Hardware muxer could not package this format."
                else "Muxer perangkat keras tidak mendukung kombinasi track file ini."
            "codec" in lower || "decoder" in lower || "encoder" in lower ->
                if (isEnglish) "Hardware codec failed on this stream."
                else "Codec perangkat keras gagal memproses stream ini."
            "io" in lower || "file" in lower ->
                if (isEnglish) "Could not read or write the media file."
                else "Gagal membaca atau menulis file media."
            else ->
                if (isEnglish) "Media3 Transformer failed; falling back or stopping."
                else "Media3 Transformer gagal memproses file ini."
        }
        return MappedMediaError(userMessage = msg, technicalDetail = detail)
    }
}
