package com.harimu.app.core.media

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

data class Id3Metadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val coverBytes: ByteArray? = null,
    val coverMime: String = "image/jpeg"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Id3Metadata) return false
        return title == other.title &&
            artist == other.artist &&
            album == other.album &&
            year == other.year &&
            genre == other.genre &&
            coverMime == other.coverMime &&
            ((coverBytes == null && other.coverBytes == null) ||
                (coverBytes != null && other.coverBytes != null && coverBytes.contentEquals(other.coverBytes)))
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + year.hashCode()
        result = 31 * result + genre.hashCode()
        result = 31 * result + coverMime.hashCode()
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        return result
    }
}

object Id3TagHelper {

    private val ID3_MAGIC = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())

    fun encodeSynchsafeInt(value: Int): ByteArray {
        val v = value.coerceAtLeast(0)
        return byteArrayOf(
            ((v shr 21) and 0x7F).toByte(),
            ((v shr 14) and 0x7F).toByte(),
            ((v shr 7) and 0x7F).toByte(),
            (v and 0x7F).toByte()
        )
    }

    fun decodeSynchsafeInt(bytes: ByteArray, offset: Int = 0): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun writeBigEndianInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    fun readTags(context: Context, uri: Uri): Id3Metadata {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                readTagsFromStream(stream)
            } ?: Id3Metadata()
        }.getOrDefault(Id3Metadata())
    }

    fun readTags(file: File): Id3Metadata {
        if (!file.exists()) return Id3Metadata()
        return runCatching {
            FileInputStream(file).use { stream -> readTagsFromStream(stream) }
        }.getOrDefault(Id3Metadata())
    }

    fun readTagsFromStream(input: InputStream): Id3Metadata {
        val header = ByteArray(10)
        if (readExact(input, header) < 10) return Id3Metadata()
        if (header[0] != ID3_MAGIC[0] || header[1] != ID3_MAGIC[1] || header[2] != ID3_MAGIC[2]) {
            return Id3Metadata()
        }
        val majorVersion = header[3].toInt() and 0xFF
        if (majorVersion !in 2..4) return Id3Metadata()
        val flags = header[5].toInt() and 0xFF
        val tagSize = decodeSynchsafeInt(header, 6).coerceIn(0, 16 * 1024 * 1024)
        if (tagSize <= 0) return Id3Metadata()

        val tagBody = ByteArray(tagSize)
        val read = readExact(input, tagBody)
        if (read <= 0) return Id3Metadata()

        var pos = 0
        // Skip optional extended header (bit 6)
        if ((flags and 0x40) != 0 && read >= 4) {
            val extSize = if (majorVersion == 4) decodeSynchsafeInt(tagBody, 0) else readBigEndianInt(tagBody, 0) + 4
            if (extSize in 4 until read) pos = extSize
        }

        var title = ""
        var artist = ""
        var album = ""
        var year = ""
        var genre = ""
        var coverBytes: ByteArray? = null
        var coverMime = "image/jpeg"

        if (majorVersion == 2) {
            return Id3Metadata()
        }

        while (pos + 10 <= read) {
            if (tagBody[pos] == 0.toByte()) break // padding reached
            val frameId = String(tagBody, pos, 4, Charsets.US_ASCII)
            if (!frameId.all { it in 'A'..'Z' || it in '0'..'9' }) break
            val frameSize = if (majorVersion == 4) {
                decodeSynchsafeInt(tagBody, pos + 4)
            } else {
                readBigEndianInt(tagBody, pos + 4)
            }
            pos += 10
            if (frameSize <= 0 || pos + frameSize > read) break

            val payload = tagBody.copyOfRange(pos, pos + frameSize)
            pos += frameSize

            when (frameId) {
                "TIT2" -> title = decodeTextFrame(payload)
                "TPE1" -> artist = decodeTextFrame(payload)
                "TALB" -> album = decodeTextFrame(payload)
                "TYER", "TDRC" -> if (year.isBlank()) year = decodeTextFrame(payload)
                "TCON" -> genre = decodeTextFrame(payload)
                "APIC" -> {
                    parseApicFrame(payload)?.let { (mime, data) ->
                        coverMime = mime
                        coverBytes = data
                    }
                }
            }
        }

        return Id3Metadata(
            title = title,
            artist = artist,
            album = album,
            year = year,
            genre = genre,
            coverBytes = coverBytes,
            coverMime = coverMime
        )
    }

    fun writeId3v23Mp3(
        sourceMp3: File,
        destMp3: File,
        metadata: Id3Metadata
    ) {
        val frameBuffer = ByteArrayOutputStream()
        if (metadata.title.isNotBlank()) writeTextFrame(frameBuffer, "TIT2", metadata.title)
        if (metadata.artist.isNotBlank()) writeTextFrame(frameBuffer, "TPE1", metadata.artist)
        if (metadata.album.isNotBlank()) writeTextFrame(frameBuffer, "TALB", metadata.album)
        if (metadata.year.isNotBlank()) writeTextFrame(frameBuffer, "TYER", metadata.year)
        if (metadata.genre.isNotBlank()) writeTextFrame(frameBuffer, "TCON", metadata.genre)
        val art = metadata.coverBytes
        if (art != null && art.isNotEmpty()) {
            writeApicFrame(frameBuffer, metadata.coverMime.ifBlank { "image/jpeg" }, art)
        }

        val framesBytes = frameBuffer.toByteArray()
        val tagSize = framesBytes.size
        val header = ByteArray(10).apply {
            this[0] = 'I'.code.toByte()
            this[1] = 'D'.code.toByte()
            this[2] = '3'.code.toByte()
            this[3] = 3 // ID3v2.3
            this[4] = 0
            this[5] = 0
            val synch = encodeSynchsafeInt(tagSize)
            System.arraycopy(synch, 0, this, 6, 4)
        }

        val skipOffset = detectExistingId3v2Length(sourceMp3)
        FileInputStream(sourceMp3).use { fis ->
            var remainingSkip = skipOffset
            val discard = ByteArray(8192)
            while (remainingSkip > 0L) {
                val n = fis.read(discard, 0, minOf(discard.size.toLong(), remainingSkip).toInt())
                if (n <= 0) break
                remainingSkip -= n
            }

            FileOutputStream(destMp3).use { fos ->
                if (tagSize > 0) {
                    fos.write(header)
                    fos.write(framesBytes)
                }
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val read = fis.read(buf)
                    if (read <= 0) break
                    fos.write(buf, 0, read)
                }
            }
        }
    }

    fun detectExistingId3v2Length(file: File): Long {
        if (!file.exists() || file.length() < 10L) return 0L
        return runCatching {
            FileInputStream(file).use { fis ->
                val header = ByteArray(10)
                if (readExact(fis, header) < 10) return@use 0L
                if (header[0] == ID3_MAGIC[0] && header[1] == ID3_MAGIC[1] && header[2] == ID3_MAGIC[2]) {
                    val tagBodySize = decodeSynchsafeInt(header, 6)
                    val hasFooter = (header[5].toInt() and 0x10) != 0
                    val total = 10L + tagBodySize + if (hasFooter) 10L else 0L
                    total.coerceAtMost(file.length())
                } else {
                    0L
                }
            }
        }.getOrDefault(0L)
    }

    private fun writeTextFrame(out: ByteArrayOutputStream, frameId: String, text: String) {
        val textBytes = text.toByteArray(Charset.forName("UTF-16LE"))
        // encoding 0x01 + UTF-16LE BOM (0xFF 0xFE) + textBytes
        val payloadSize = 1 + 2 + textBytes.size
        out.write(frameId.toByteArray(Charsets.US_ASCII))
        writeBigEndianInt(out, payloadSize)
        out.write(0)
        out.write(0)
        out.write(0x01) // UTF-16 with BOM
        out.write(0xFF)
        out.write(0xFE)
        out.write(textBytes)
    }

    private fun writeApicFrame(out: ByteArrayOutputStream, mime: String, imageBytes: ByteArray) {
        val mimeBytes = mime.toByteArray(Charsets.ISO_8859_1)
        // [encoding=0x00] + mime + [0x00] + [picType=0x03 front cover] + [empty desc=0x00] + imageBytes
        val payloadSize = 1 + mimeBytes.size + 1 + 1 + 1 + imageBytes.size
        out.write("APIC".toByteArray(Charsets.US_ASCII))
        writeBigEndianInt(out, payloadSize)
        out.write(0)
        out.write(0)
        out.write(0x00) // ISO-8859-1 description
        out.write(mimeBytes)
        out.write(0x00)
        out.write(0x03) // Cover (front)
        out.write(0x00) // empty description
        out.write(imageBytes)
    }

    private fun decodeTextFrame(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val encoding = payload[0].toInt() and 0xFF
        val content = payload.copyOfRange(1, payload.size)
        val raw = when (encoding) {
            0 -> String(content, Charsets.ISO_8859_1)
            1 -> decodeUtf16WithBom(content)
            2 -> String(content, Charsets.UTF_16BE)
            3 -> String(content, Charsets.UTF_8)
            else -> String(content, Charsets.UTF_8)
        }
        return raw.trim('\u0000', ' ').trim()
    }

    private fun decodeUtf16WithBom(bytes: ByteArray): String {
        if (bytes.size < 2) return ""
        return if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
        } else if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16BE"))
        } else {
            String(bytes, Charsets.UTF_16)
        }
    }

    private fun parseApicFrame(payload: ByteArray): Pair<String, ByteArray>? {
        if (payload.size < 5) return null
        val encoding = payload[0].toInt() and 0xFF
        var idx = 1
        while (idx < payload.size && payload[idx] != 0.toByte()) idx++
        val mime = String(payload, 1, (idx - 1).coerceAtLeast(0), Charsets.ISO_8859_1).ifBlank { "image/jpeg" }
        idx++ // skip 0x00
        if (idx >= payload.size) return null
        idx++ // skip picture type byte
        // skip description null-terminated
        if (encoding == 1 || encoding == 2) {
            while (idx + 1 < payload.size && !(payload[idx] == 0.toByte() && payload[idx + 1] == 0.toByte())) {
                idx += 2
            }
            idx += 2
        } else {
            while (idx < payload.size && payload[idx] != 0.toByte()) idx++
            idx++
        }
        if (idx >= payload.size) return null
        val img = payload.copyOfRange(idx, payload.size)
        return if (img.isNotEmpty()) mime to img else null
    }

    private fun readExact(input: InputStream, target: ByteArray): Int {
        var total = 0
        while (total < target.size) {
            val n = input.read(target, total, target.size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    fun detectImageMimeType(bytes: ByteArray): String {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
        ) {
            return "image/webp"
        }
        return "image/jpeg"
    }

    fun buildFfmpegMetadataArgs(metadata: Id3Metadata): List<String> = buildList {
        if (metadata.title.isNotBlank()) {
            add("-metadata"); add("title=${metadata.title}")
        }
        if (metadata.artist.isNotBlank()) {
            add("-metadata"); add("artist=${metadata.artist}")
        }
        if (metadata.album.isNotBlank()) {
            add("-metadata"); add("album=${metadata.album}")
        }
        if (metadata.year.isNotBlank()) {
            add("-metadata"); add("date=${metadata.year}")
        }
        if (metadata.genre.isNotBlank()) {
            add("-metadata"); add("genre=${metadata.genre}")
        }
    }
}
