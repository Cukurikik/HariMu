package com.harimu.app.core.engines

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import com.harimu.app.core.files.FileStorageManager
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Engine arsip universal (FASE 13).
 *
 * Prinsip keamanan:
 * - **Zip-slip dicegah** dengan [sanitizeEntryName]: nama entri dinormalisasi ke `/`, segmen `.`
 *   dibuang, dan entri dengan `..`, path absolut, drive letter, atau karakter kontrol **ditolak**.
 *   Karena hasil ekstraksi selalu ditulis ke ZIP keluaran (bukan langsung ke filesystem pengguna),
 *   nama yang lolos validasi tetap tidak bisa keluar dari folder aplikasi.
 * - **Zip bomb dibatasi** oleh [MAX_ENTRIES], [MAX_ENTRY_BYTES], [MAX_TOTAL_BYTES], dan
 *   [MAX_COMPRESSION_RATIO]. Batas dicek dari ukuran deklarasi header *dan* dari byte yang
 *   benar-benar dibaca, sehingga header bohong tidak bisa melewati batas.
 * - **Kata sandi tidak disimpan**: hanya dipakai selama operasi dan tidak ditulis ke metadata,
 *   nama file, maupun log.
 *
 * Batas jujur:
 * - RAR **hanya dibaca** (junrar). Membuat RAR butuh encoder proprietary, jadi tidak didukung.
 * - 7z hanya dibaca; menulis 7z tidak didukung (keluaran selalu ZIP).
 * - ZIP terenkripsi dibaca lewat zip4j; enkripsi ZIP AES-256 juga ditulis lewat zip4j.
 */
object ArchiveEngine {

    const val MAX_ENTRIES = 4096
    const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 128L * 1024 * 1024
    const val MAX_COMPRESSION_RATIO = 200.0

    enum class Kind(val label: String) {
        ZIP("ZIP"),
        TAR_GZ("TAR.GZ"),
        SEVEN_Z("7Z"),
        RAR("RAR")
    }

    data class Entry(val name: String, val size: Long, val isDirectory: Boolean)

    data class Result(
        val kind: Kind,
        val entries: List<Entry>,
        val extracted: Int,
        val skipped: List<String>,
        val totalBytes: Long,
        val truncated: Boolean
    ) {
        fun report(storage: FileStorageManager, isEnglish: Boolean): String = buildString {
            if (isEnglish) {
                appendLine("Archive type: ${kind.label}")
                appendLine("Entries found: ${entries.size} (${storage.formatBytes(totalBytes)})")
                appendLine("Entries extracted: $extracted")
            } else {
                appendLine("Jenis arsip: ${kind.label}")
                appendLine("Entri ditemukan: ${entries.size} (${storage.formatBytes(totalBytes)})")
                appendLine("Entri diekstrak: $extracted")
            }
            if (skipped.isNotEmpty()) {
                appendLine(if (isEnglish) "Skipped (unsafe/oversized): ${skipped.size}" else "Dilewati (tidak aman/terlalu besar): ${skipped.size}")
                skipped.take(10).forEach { appendLine("  - $it") }
            }
            if (truncated) {
                appendLine(if (isEnglish) "Note: listing truncated at $MAX_ENTRIES entries." else "Catatan: daftar dipotong pada $MAX_ENTRIES entri.")
            }
            appendLine()
            entries.take(60).forEach { entry ->
                val size = if (entry.isDirectory) "<dir>" else storage.formatBytes(entry.size.coerceAtLeast(0))
                appendLine("${entry.name}  ($size)")
            }
            if (entries.size > 60) {
                appendLine(if (isEnglish) "… and ${entries.size - 60} more entries" else "… dan ${entries.size - 60} entri lainnya")
            }
        }
    }

    // ------------------------------------------------------------ deteksi jenis

    fun kindOf(fileName: String): Kind? {
        val lower = fileName.lowercase(Locale.US)
        return when {
            lower.endsWith(".zip") -> Kind.ZIP
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> Kind.TAR_GZ
            lower.endsWith(".7z") -> Kind.SEVEN_Z
            lower.endsWith(".rar") -> Kind.RAR
            else -> null
        }
    }

    fun detectKind(context: Context, uri: Uri, displayName: String?): Kind? {
        kindOf(displayName.orEmpty())?.let { return it }
        val header = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ByteArray(8).also { buf ->
                    var read = 0
                    while (read < buf.size) {
                        val n = input.read(buf, read, buf.size - read)
                        if (n <= 0) break
                        read += n
                    }
                }
            }
        }.getOrNull() ?: return null
        return when {
            header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() -> Kind.ZIP
            header.size >= 2 && header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte() -> Kind.TAR_GZ
            header.size >= 6 && header.copyOfRange(0, 6).contentEquals(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)) -> Kind.SEVEN_Z
            header.size >= 7 && header.copyOfRange(0, 7).contentEquals("Rar!\u001A\u0007".toByteArray(Charsets.ISO_8859_1)) -> Kind.RAR
            else -> null
        }
    }

    // ---------------------------------------------------------------- keamanan

    /**
     * Normalisasi nama entri arsip. Mengembalikan `null` bila entri tidak aman untuk ditulis
     * (path absolut, `..`, drive letter, karakter kontrol, atau nama kosong).
     */
    fun sanitizeEntryName(raw: String): String? {
        val replaced = raw.replace('\\', '/')
        if (replaced.isBlank()) return null
        if (replaced.any { it.code < 32 || it == '\u0000' }) return null
        if (replaced.startsWith("/")) return null
        if (replaced.length >= 2 && replaced[1] == ':') return null
        val parts = replaced.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    /** Nama file unik di dalam folder kerja, dipakai saat menulis ZIP AES. */
    fun uniqueChildName(raw: String, used: MutableSet<String>): String {
        val base = sanitizeEntryName(raw)?.substringAfterLast('/')?.ifBlank { null } ?: "file"
        if (used.add(base)) return base
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = "$stem ($index)$ext"
            if (used.add(candidate)) return candidate
            index++
        }
    }

    // ------------------------------------------------------------ ekstraksi

    /** Baca daftar isi sekaligus ekstrak entri aman ke [destZip]. */
    fun extractToZip(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        destZip: File,
        password: String = "",
        displayName: String? = null
    ): Result {
        val kind = detectKind(context, uri, displayName) ?: error(
            "Jenis arsip tidak dikenali. Didukung: ZIP, TAR.GZ, 7Z, RAR."
        )
        return when (kind) {
            Kind.ZIP -> extractZip(context, storage, uri, destZip, password)
            Kind.TAR_GZ -> extractTarGz(context, storage, uri, destZip)
            Kind.SEVEN_Z -> extract7z(context, storage, uri, destZip, password)
            Kind.RAR -> extractRar(context, storage, uri, destZip, password)
        }
    }

    private class Budget {
        var totalBytes = 0L
        var count = 0
        val skipped = mutableListOf<String>()
        var truncated = false
        val entries = mutableListOf<Entry>()
        var extracted = 0

        fun register(entry: Entry, declaredSize: Long, compressedSize: Long): Boolean {
            if (count >= MAX_ENTRIES) {
                truncated = true
                return false
            }
            count++
            entries.add(entry)
            val reason = when {
                declaredSize > MAX_ENTRY_BYTES -> "entri >128 MB"
                declaredSize > 0 && compressedSize > 0 &&
                    declaredSize.toDouble() / compressedSize.toDouble() > MAX_COMPRESSION_RATIO -> "rasio kompresi >200x (indikasi zip bomb)"
                totalBytes + declaredSize.coerceAtLeast(0) > MAX_TOTAL_BYTES -> "total >512 MB"
                else -> null
            }
            if (reason != null) {
                skipped.add("${entry.name} ($reason)")
                return false
            }
            return true
        }

    }

    private fun copyBounded(input: InputStream, out: ZipOutputStream, entryName: String, budget: Budget): Long {
        val buffer = ByteArray(32 * 1024)
        var written = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            written += read
            if (written > MAX_ENTRY_BYTES) error("Entri $entryName melebihi batas 128 MB")
            if (budget.totalBytes + written > MAX_TOTAL_BYTES) error("Total ekstraksi melebihi batas 512 MB")
            out.write(buffer, 0, read)
        }
        budget.totalBytes += written
        return written
    }

    private fun extractZip(context: Context, storage: FileStorageManager, uri: Uri, destZip: File, password: String): Result {
        // ZipInputStream Java tidak bisa mendekripsi; pakai zip4j lalu ulangi sebagai ZIP biasa.
        if (password.isNotBlank()) {
            val plain = storage.createTempFile("zipdec", "zip")
            try {
                decryptZipToPlain(context, storage, uri, password, plain)
                return extractZip(context, storage, android.net.Uri.fromFile(plain), destZip, "")
            } finally {
                plain.delete()
            }
        }
        val budget = Budget()
        val input = context.contentResolver.openInputStream(uri) ?: error("Gagal membuka arsip ZIP")
        input.use { raw ->
            ZipArchiveInputStream(raw, "UTF-8", true, true).use { zis ->
                ZipOutputStream(FileOutputStream(destZip).buffered()).use { zos ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = sanitizeEntryName(entry.name)
                        val isDirectory = entry.isDirectory
                        val encrypted = entry.generalPurposeBit?.usesEncryption() == true
                        if (name == null) {
                            budget.skipped.add("${entry.name} (nama tidak aman)")
                        } else if (isDirectory) {
                            budget.register(Entry(name, 0, true), 0, 0)
                        } else if (encrypted) {
                            budget.skipped.add("$name (terenkripsi, perlu kata sandi)")
                        } else if (budget.register(Entry(name, entry.size, false), entry.size, entry.compressedSize)) {
                            zos.putNextEntry(ZipEntry(name))
                            copyBounded(zis, zos, name, budget)
                            zos.closeEntry()
                            budget.extracted++
                        }
                        if (budget.truncated) break
                        entry = zis.nextEntry
                    }
                }
            }
        }
        return Result(Kind.ZIP, budget.entries.toList(), budget.extracted, budget.skipped.toList(), budget.totalBytes, budget.truncated)
    }

    private fun extractTarGz(context: Context, storage: FileStorageManager, uri: Uri, destZip: File): Result {
        val budget = Budget()
        val input = context.contentResolver.openInputStream(uri) ?: error("Gagal membuka arsip TAR.GZ")
        input.use { raw ->
            GzipCompressorInputStream(raw).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    ZipOutputStream(FileOutputStream(destZip).buffered()).use { zos ->
                        var entry = tar.nextEntry
                        while (entry != null) {
                            val name = sanitizeEntryName(entry.name)
                            if (name == null) {
                                budget.skipped.add("${entry.name} (nama tidak aman)")
                            } else if (entry.isDirectory) {
                                budget.register(Entry(name, 0, true), 0, 0)
                            } else if (budget.register(Entry(name, entry.size, false), entry.size, entry.size)) {
                                zos.putNextEntry(ZipEntry(name))
                                copyBounded(tar, zos, name, budget)
                                zos.closeEntry()
                                budget.extracted++
                            }
                            if (budget.truncated) break
                            entry = tar.nextEntry
                        }
                    }
                }
            }
        }
        return Result(Kind.TAR_GZ, budget.entries.toList(), budget.extracted, budget.skipped.toList(), budget.totalBytes, budget.truncated)
    }

    private fun extract7z(context: Context, storage: FileStorageManager, uri: Uri, destZip: File, password: String): Result {
        val budget = Budget()
        val temp = storage.copyUriToTemp(uri, "archive7z", "7z")
        try {
            val sevenZ = if (password.isBlank()) SevenZFile(temp) else SevenZFile(temp, password.toCharArray())
            sevenZ.use { archive ->
                ZipOutputStream(FileOutputStream(destZip).buffered()).use { zos ->
                    var entry = archive.nextEntry
                    while (entry != null) {
                        val name = sanitizeEntryName(entry.name)
                        if (name == null) {
                            budget.skipped.add("${entry.name} (nama tidak aman)")
                        } else if (entry.isDirectory) {
                            budget.register(Entry(name, 0, true), 0, 0)
                        } else if (budget.register(Entry(name, entry.size, false), entry.size, entry.size)) {
                            zos.putNextEntry(ZipEntry(name))
                            val buffer = ByteArray(32 * 1024)
                            var written = 0L
                            while (true) {
                                val read = archive.read(buffer, 0, buffer.size)
                                if (read <= 0) break
                                written += read
                                if (written > MAX_ENTRY_BYTES) error("Entri $name melebihi batas 128 MB")
                                if (budget.totalBytes + written > MAX_TOTAL_BYTES) error("Total ekstraksi melebihi batas 512 MB")
                                zos.write(buffer, 0, read)
                            }
                            budget.totalBytes += written
                            zos.closeEntry()
                            budget.extracted++
                        }
                        if (budget.truncated) break
                        entry = archive.nextEntry
                    }
                }
            }
        } finally {
            temp.delete()
        }
        return Result(Kind.SEVEN_Z, budget.entries.toList(), budget.extracted, budget.skipped.toList(), budget.totalBytes, budget.truncated)
    }

    private fun extractRar(context: Context, storage: FileStorageManager, uri: Uri, destZip: File, password: String): Result {
        val budget = Budget()
        val temp = storage.copyUriToTemp(uri, "archiverar", "rar")
        try {
            val archive = if (password.isBlank()) Archive(temp) else Archive(temp, password)
            archive.use { rar ->
                ZipOutputStream(FileOutputStream(destZip).buffered()).use { zos ->
                    for (header in rar.fileHeaders) {
                        if (budget.truncated) break
                        val name = sanitizeEntryName(header.fileName)
                        if (name == null) {
                            budget.skipped.add("${header.fileName} (nama tidak aman)")
                            continue
                        }
                        if (header.isDirectory) {
                            budget.register(Entry(name, 0, true), 0, 0)
                            continue
                        }
                        if (header.isEncrypted && password.isBlank()) {
                            budget.skipped.add("$name (RAR terenkripsi, kata sandi kosong)")
                            continue
                        }
                        val declared = if (header.fullUnpackSize > 0) header.fullUnpackSize else header.unpSize
                        if (!budget.register(Entry(name, declared, false), declared, header.fullPackSize)) continue
                        zos.putNextEntry(ZipEntry(name))
                        archive.getInputStream(header)?.use { input -> copyBounded(input, zos, name, budget) }
                        zos.closeEntry()
                        budget.extracted++
                    }
                }
            }
        } finally {
            temp.delete()
        }
        return Result(Kind.RAR, budget.entries.toList(), budget.extracted, budget.skipped.toList(), budget.totalBytes, budget.truncated)
    }

    // ------------------------------------------------------------- ZIP AES-256

    /** Buat ZIP terenkripsi AES-256 standar (kompatibel 7-Zip/WinRAR/zip4j). */
    fun createAesZip(
        context: Context,
        storage: FileStorageManager,
        uris: List<Uri>,
        password: String,
        dest: File
    ): Int {
        require(password.length >= 8) { "Kata sandi minimal 8 karakter" }
        val jobDir = storage.createJobTempDir("aes_${System.currentTimeMillis()}")
        try {
            val used = mutableSetOf<String>()
            val files = uris.mapIndexed { index, uri ->
                val name = uniqueChildName(storage.getDisplayName(uri) ?: "file_${index + 1}", used)
                val target = File(jobDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { out -> input.copyTo(out, 32 * 1024) }
                } ?: error("Gagal membaca berkas masukan")
                target
            }
            require(files.isNotEmpty()) { "Pilih minimal satu berkas" }
            val params = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            Zip4jFile(dest).apply {
                setPassword(password.toCharArray())
                addFiles(files, params)
            }
            return files.size
        } finally {
            jobDir.deleteRecursively()
        }
    }

    /** Baca ZIP terenkripsi (AES/zipcrypto) lalu tulis ulang sebagai ZIP biasa. */
    fun decryptZipToPlain(
        context: Context,
        storage: FileStorageManager,
        uri: Uri,
        password: String,
        destZip: File
    ): Int {
        val source = storage.copyUriToTemp(uri, "aesdec", "zip")
        val jobDir = storage.createJobTempDir("aesdec_${System.currentTimeMillis()}")
        try {
            val zip = Zip4jFile(source).apply {
                if (password.isNotBlank()) setPassword(password.toCharArray())
            }
            // Batas dicek dari header SEBELUM ekstraksi supaya zip bomb tidak sempat ditulis ke disk.
            try {
                var total = 0L
                var files = 0
                zip.fileHeaders.forEach { header ->
                    if (header.isDirectory) return@forEach
                    files++
                    if (files > MAX_ENTRIES) error("Arsip melebihi batas $MAX_ENTRIES entri")
                    if (header.uncompressedSize > MAX_ENTRY_BYTES) {
                        error("Entri ${header.fileName} melebihi batas 128 MB")
                    }
                    total += header.uncompressedSize
                    if (total > MAX_TOTAL_BYTES) error("Total ekstraksi melebihi batas 512 MB")
                }
                zip.extractAll(jobDir.absolutePath)
            } catch (error: IllegalStateException) {
                throw error
            } catch (error: Exception) {
                throw IllegalStateException("Kata sandi salah atau arsip ZIP rusak/tidak didukung", error)
            }
            var count = 0
            ZipOutputStream(FileOutputStream(destZip).buffered()).use { zos ->
                jobDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    if (count >= MAX_ENTRIES) return@forEach
                    val relative = file.relativeTo(jobDir).path.replace('\\', '/')
                    val safe = sanitizeEntryName(relative) ?: return@forEach
                    zos.putNextEntry(ZipEntry(safe))
                    file.inputStream().use { it.copyTo(zos, 32 * 1024) }
                    zos.closeEntry()
                    count++
                }
            }
            return count
        } finally {
            jobDir.deleteRecursively()
            source.delete()
        }
    }
}
