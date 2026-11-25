package com.harimu.app.core.media

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode

/**
 * Probe kapabilitas build FFmpeg saat runtime (`ffmpeg-kit-full:8.1.9`, LGPL).
 *
 * Build LGPL **tidak** memuat encoder/filter GPL (x264, x265, vidstab, rubberband, delogo, boxblur), sehingga
 * tool tidak boleh berasumsi encoder/filter tertentu ada. [snapshot] menjalankan
 * `ffmpeg -hide_banner -encoders` dan `-filters` sekali per proses (lewat
 * [FFmpegKit.executeWithArguments], bukan overload `execute(String)`), lalu menyimpan hasilnya.
 *
 * Perilaku bila probe gagal: daftar kosong dianggap "tidak diketahui" dan guard **meloloskan**
 * pemanggilan (fail-open). Tujuannya agar regresi probe tidak mematikan tool yang sebelumnya
 * berjalan; pesan error FFmpeg asli tetap muncul lewat jalur normal.
 *
 * Panggil dari `Dispatchers.IO`: [FFmpegKit.executeWithArguments] bersifat blocking.
 *
 * Batas: tidak memeriksa opsi/muxer, hanya nama encoder & filter.
 */
object FfmpegCapabilities {

    data class Snapshot(
        val encoders: Set<String>,
        val filters: Set<String>
    ) {
        /** False bila kedua daftar kosong (probe gagal / output tidak terbaca). */
        val known: Boolean get() = encoders.isNotEmpty() || filters.isNotEmpty()
    }

    @Volatile
    private var cached: Snapshot? = null
    private val lock = Any()

    fun snapshot(): Snapshot = cached ?: synchronized(lock) {
        cached ?: probe().also { cached = it }
    }

    fun hasEncoder(name: String): Boolean {
        val snap = snapshot()
        return !snap.known || name in snap.encoders
    }

    fun hasFilter(name: String): Boolean {
        val snap = snapshot()
        return !snap.known || name in snap.filters
    }

    /** Melempar pesan ramah (bukan crash teknis) bila encoder tidak tersedia. */
    fun requireEncoder(name: String, isEnglish: Boolean) {
        if (hasEncoder(name)) return
        throw IllegalStateException(
            if (isEnglish) {
                "This build of FFmpeg does not include the \"$name\" encoder. The task was stopped " +
                    "before writing any file. Try the hardware (Media3) mode instead."
            } else {
                "Build FFmpeg di perangkat ini tidak menyertakan encoder \"$name\". Tugas dihentikan " +
                    "sebelum menulis berkas. Coba mode perangkat keras (Media3) sebagai gantinya."
            }
        )
    }

    /** Melempar pesan ramah bila filter tidak tersedia. */
    fun requireFilter(name: String, isEnglish: Boolean) {
        if (hasFilter(name)) return
        throw IllegalStateException(
            if (isEnglish) {
                "This build of FFmpeg does not include the \"$name\" filter. The task was stopped " +
                    "before writing any file."
            } else {
                "Build FFmpeg di perangkat ini tidak menyertakan filter \"$name\". Tugas dihentikan " +
                    "sebelum menulis berkas."
            }
        )
    }

    /** Untuk pengujian/log: daftar nama yang berhasil dibaca. */
    internal fun resetForTests() {
        synchronized(lock) { cached = null }
    }

    private fun probe(): Snapshot {
        val encoders = runCatching { namesFor("-encoders") }.getOrDefault(emptySet())
        val filters = runCatching { namesFor("-filters") }.getOrDefault(emptySet())
        return Snapshot(encoders = encoders, filters = filters)
    }

    private fun namesFor(flag: String): Set<String> {
        val session = FFmpegKit.executeWithArguments(arrayOf("-hide_banner", flag))
        if (!ReturnCode.isSuccess(session.returnCode)) return emptySet()
        return parseNames(session.output ?: "")
    }

    /**
     * Baris daftar diawali kolom flag lalu nama:
     * ` V....D a64multi ...` (encoder) atau ` TSC avgblur V->V ...` (filter).
     * Header (`Encoders:`, `Filters:`) dan baris legenda (` = Name ...`) dilewati karena
     * token pertamanya bukan kumpulan flag.
     */
    internal fun parseNames(output: String): Set<String> {
        val names = mutableSetOf<String>()
        for (rawLine in output.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue
            val flags = parts[0]
            if (flags.isEmpty() || !flags.all { it in FLAG_CHARS }) continue
            val name = parts[1]
            if (name.isEmpty() || !name[0].isLetter()) continue
            names.add(name)
        }
        return names
    }

    /** Huruf yang dipakai ffmpeg untuk kolom flag encoder/filter (V/A/S/F/X/D/B/I/L/T/C/N/P/E/dll.). */
    private const val FLAG_CHARS = "VASFXD.BILTSCNPEvabcdefghijklmnopqrstuwxyz"
}
