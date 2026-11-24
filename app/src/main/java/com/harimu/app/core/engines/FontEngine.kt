package com.harimu.app.core.engines

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Konverter font (FASE 13, CNV-21/CNV-22).
 *
 * Yang **benar-benar** diimplementasikan:
 * - SFNT (TrueType `0x00010000` / OpenType `OTTO`) → WOFF 1.0 (setiap tabel dikompres zlib).
 * - WOFF 1.0 → SFNT (inflate tabel, checksum & `head.checkSumAdjustment` dihitung ulang).
 * - Subsetting TrueType (glyf/loca/cmap/hmtx/maxp/hhea/head) berdasarkan karakter yang dipakai,
 *   termasuk penutupan komposit dan penulisan ulang indeks komponen. Tabel tata letak (GSUB/GPOS/
 *   kern), hinting (fpgm/prep/cvt), dan DSIG dibuang.
 *
 * Yang **tidak** didukung (ditolak dengan pesan jelas, tidak dipalsukan):
 * - WOFF2: butuh Brotli + transformasi glyf/hmtx yang belum diimplementasikan.
 * - OTF (CFF) → TTF dan TTF → OTF: konversi outline CFF ⇄ glyf butuh rasterisasi ulang glyph.
 * - Font koleksi (TTC) dan font CFF untuk subsetting.
 *
 * Kata sandi/tanda tangan tidak terlibat; tidak ada font pihak ketiga yang dibundel.
 */
object FontEngine {

    enum class Kind { TTF, OTF, WOFF, WOFF2, UNKNOWN }

    data class Table(val tag: String, val data: ByteArray)

    data class Sfnt(val flavor: Int, val tables: List<Table>) {
        fun table(tag: String): ByteArray? = tables.firstOrNull { it.tag == tag }?.data
    }

    data class SubsetResult(val data: ByteArray, val keptGlyphs: Int, val droppedCodepoints: Int)

    private val WOFF_MAGIC = byteArrayOf('w'.code.toByte(), 'O'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
    private val WOFF2_MAGIC = byteArrayOf('w'.code.toByte(), 'O'.code.toByte(), 'F'.code.toByte(), '2'.code.toByte())

    fun detectKind(bytes: ByteArray): Kind {
        if (bytes.size < 4) return Kind.UNKNOWN
        val b = bytes
        return when {
            b.copyOfRange(0, 4).contentEquals(WOFF2_MAGIC) -> Kind.WOFF2
            b.copyOfRange(0, 4).contentEquals(WOFF_MAGIC) -> Kind.WOFF
            b[0] == 0x00.toByte() && b[1] == 0x01.toByte() && b[2] == 0x00.toByte() && b[3] == 0x00.toByte() -> Kind.TTF
            b.copyOfRange(0, 4).contentEquals("OTTO".toByteArray(Charsets.US_ASCII)) -> Kind.OTF
            b.copyOfRange(0, 4).contentEquals("true".toByteArray(Charsets.US_ASCII)) -> Kind.TTF
            b.copyOfRange(0, 4).contentEquals("ttcf".toByteArray(Charsets.US_ASCII)) -> Kind.UNKNOWN
            else -> Kind.UNKNOWN
        }
    }

    fun describeKind(kind: Kind): String = when (kind) {
        Kind.TTF -> "TrueType (TTF)"
        Kind.OTF -> "OpenType CFF (OTF)"
        Kind.WOFF -> "WOFF 1.0"
        Kind.WOFF2 -> "WOFF 2.0"
        Kind.UNKNOWN -> "tidak dikenal"
    }

    // ------------------------------------------------------------------ SFNT

    fun readSfnt(bytes: ByteArray): Sfnt {
        require(bytes.size >= 12) { "Berkas font terlalu pendek" }
        val flavor = readInt(bytes, 0)
        val numTables = readUShort(bytes, 4)
        require(numTables in 1..512) { "Jumlah tabel font tidak masuk akal: $numTables" }
        require(bytes.size >= 12 + numTables * 16) { "Direktori tabel font terpotong" }
        val tables = mutableListOf<Table>()
        for (i in 0 until numTables) {
            val base = 12 + i * 16
            val tag = String(bytes, base, 4, Charsets.US_ASCII)
            val offset = readInt(bytes, base + 8)
            val length = readInt(bytes, base + 12)
            require(offset >= 0 && length >= 0 && offset.toLong() + length <= bytes.size) {
                "Tabel $tag keluar dari batas berkas"
            }
            tables.add(Table(tag, bytes.copyOfRange(offset, offset + length)))
        }
        return Sfnt(flavor, tables)
    }

    fun writeSfnt(sfnt: Sfnt): ByteArray {
        val tables = sfnt.tables.sortedBy { it.tag }
        val numTables = tables.size
        val headerSize = 12 + numTables * 16
        var dataSize = 0
        tables.forEach { dataSize += align4(it.data.size) }
        val out = ByteArray(headerSize + dataSize)
        writeInt(out, 0, sfnt.flavor)
        writeUShort(out, 4, numTables)
        val binarySearch = highestPowerOfTwo(numTables) * 16
        writeUShort(out, 6, binarySearch)
        writeUShort(out, 8, log2(highestPowerOfTwo(numTables)))
        writeUShort(out, 10, numTables * 16 - binarySearch)

        var dataOffset = headerSize
        tables.forEachIndexed { index, table ->
            val recordBase = 12 + index * 16
            val tagBytes = table.tag.toByteArray(Charsets.US_ASCII)
            require(tagBytes.size == 4) { "Tag tabel harus 4 karakter: ${table.tag}" }
            tagBytes.copyInto(out, recordBase)
            writeInt(out, recordBase + 4, checksum(table.data))
            writeInt(out, recordBase + 8, dataOffset)
            writeInt(out, recordBase + 12, table.data.size)
            val zeroed = if (table.tag == "head" && table.data.size >= 12) {
                table.data.copyOf().also { writeInt(it, 8, 0) }
            } else {
                table.data
            }
            zeroed.copyInto(out, dataOffset)
            dataOffset += align4(table.data.size)
        }
        // head.checkSumAdjustment = 0xB1B0AFBA - checksum(seluruh font)
        val headIndex = tables.indexOfFirst { it.tag == "head" }
        if (headIndex >= 0) {
            val headOffset = readInt(out, 12 + headIndex * 16 + 8)
            val adjustment = 0xB1B0AFBAL - (checksum(out).toLong() and 0xFFFFFFFFL)
            writeInt(out, headOffset + 8, (adjustment and 0xFFFFFFFFL).toInt())
        }
        return out
    }

    // ------------------------------------------------------------------ WOFF

    fun readWoff(bytes: ByteArray): Sfnt {
        require(bytes.size >= 44 && detectKind(bytes) == Kind.WOFF) { "Berkas bukan WOFF 1.0 yang valid" }
        val flavor = readInt(bytes, 4)
        val numTables = readUShort(bytes, 12)
        require(numTables in 1..512) { "Jumlah tabel WOFF tidak masuk akal: $numTables" }
        require(bytes.size >= 44 + numTables * 20) { "Direktori tabel WOFF terpotong" }
        val tables = mutableListOf<Table>()
        for (i in 0 until numTables) {
            val base = 44 + i * 20
            val tag = String(bytes, base, 4, Charsets.US_ASCII)
            val offset = readInt(bytes, base + 4)
            val compLength = readInt(bytes, base + 8)
            val origLength = readInt(bytes, base + 12)
            require(offset >= 0 && compLength >= 0 && origLength >= 0 && offset.toLong() + compLength <= bytes.size) {
                "Tabel WOFF $tag keluar dari batas berkas"
            }
            val raw = bytes.copyOfRange(offset, offset + compLength)
            val data = if (compLength < origLength) inflate(raw, origLength) else raw
            require(data.size == origLength) { "Panjang tabel $tag tidak cocok setelah dekompresi" }
            tables.add(Table(tag, data))
        }
        return Sfnt(flavor, tables)
    }

    fun writeWoff(sfnt: Sfnt): ByteArray {
        val tables = sfnt.tables.sortedBy { it.tag }
        val numTables = tables.size
        val headerSize = 44 + numTables * 20
        val compressed = tables.map { table ->
            val deflated = deflate(table.data)
            val stored = if (deflated.size >= table.data.size) table.data else deflated
            stored
        }
        var offset = headerSize
        val offsets = IntArray(numTables)
        for (i in 0 until numTables) {
            offsets[i] = offset
            offset += align4(compressed[i].size)
        }
        val totalSfntSize = 12 + numTables * 16 + tables.sumOf { align4(it.data.size) }
        val out = ByteArray(offset)
        WOFF_MAGIC.copyInto(out, 0)
        writeInt(out, 4, sfnt.flavor)
        writeInt(out, 8, out.size)
        writeUShort(out, 12, numTables)
        writeUShort(out, 14, 0)
        writeInt(out, 16, totalSfntSize)
        writeUShort(out, 20, 1)
        writeUShort(out, 22, 0)
        writeInt(out, 24, 0)
        writeInt(out, 28, 0)
        writeInt(out, 32, 0)
        writeInt(out, 36, 0)
        writeInt(out, 40, 0)
        tables.forEachIndexed { index, table ->
            val base = 44 + index * 20
            table.tag.toByteArray(Charsets.US_ASCII).copyInto(out, base)
            writeInt(out, base + 4, offsets[index])
            writeInt(out, base + 8, compressed[index].size)
            writeInt(out, base + 12, table.data.size)
            writeInt(out, base + 16, checksum(table.data))
            compressed[index].copyInto(out, offsets[index])
        }
        return out
    }

    // -------------------------------------------------------------- konversi

    data class Conversion(val data: ByteArray, val from: Kind, val to: Kind, val note: String)

    /** Konversi WOFF ⇄ SFNT. [target] = "WOFF", "SFNT", atau "AUTO" (WOFF→SFNT, TTF/OTF→WOFF). */
    fun convert(bytes: ByteArray, target: String): Conversion {
        val kind = detectKind(bytes)
        val upper = target.uppercase(Locale.US)
        return when {
            upper.startsWith("WOFF") -> {
                require(kind == Kind.TTF || kind == Kind.OTF) {
                    "Sumber harus TTF/OTF untuk membuat WOFF (terdeteksi: ${describeKind(kind)})"
                }
                val sfnt = readSfnt(bytes)
                Conversion(writeWoff(sfnt), kind, Kind.WOFF, "WOFF 1.0 (zlib per tabel)")
            }
            upper.startsWith("SFNT") || upper.startsWith("TTF") || upper.startsWith("OTF") -> {
                require(kind == Kind.WOFF) {
                    "Sumber harus WOFF untuk menghasilkan TTF/OTF (terdeteksi: ${describeKind(kind)}). " +
                        "WOFF2 belum didukung."
                }
                val sfnt = readWoff(bytes)
                val outKind = if (sfnt.flavor == 0x4F54544F) Kind.OTF else Kind.TTF
                Conversion(writeSfnt(sfnt), kind, outKind, "SFNT ${describeKind(outKind)}")
            }
            else -> {
                require(kind == Kind.TTF || kind == Kind.OTF || kind == Kind.WOFF) {
                    "Format font tidak didukung: ${describeKind(kind)}"
                }
                if (kind == Kind.WOFF) {
                    val sfnt = readWoff(bytes)
                    val outKind = if (sfnt.flavor == 0x4F54544F) Kind.OTF else Kind.TTF
                    Conversion(writeSfnt(sfnt), kind, outKind, "SFNT ${describeKind(outKind)}")
                } else {
                    Conversion(writeWoff(readSfnt(bytes)), kind, Kind.WOFF, "WOFF 1.0 (zlib per tabel)")
                }
            }
        }
    }

    // ------------------------------------------------------------- subsetting

    /**
     * Subset TrueType: simpan hanya glyph untuk [characters] (plus glyph 0 dan komponen komposit).
     * Bila [characters] kosong, semua glyph dipertahankan tetapi tabel tata letak/hinting dibuang.
     */
    fun subset(bytes: ByteArray, characters: String): SubsetResult {
        val kind = detectKind(bytes)
        val sfnt = when (kind) {
            Kind.TTF -> readSfnt(bytes)
            Kind.WOFF -> readWoff(bytes)
            Kind.OTF -> error("Subsetting OTF/CFF belum didukung; konversi ke TTF tidak tersedia.")
            Kind.WOFF2 -> error("WOFF2 belum didukung (butuh Brotli + transformasi glyf).")
            Kind.UNKNOWN -> error("Berkas bukan font TTF/WOFF yang dikenali.")
        }
        if (sfnt.flavor == 0x4F54544F) error("Font CFF (OTF) tidak bisa disubset oleh engine ini.")

        val glyf = sfnt.table("glyf") ?: error("Font tidak punya tabel glyf (bukan TrueType)")
        val loca = sfnt.table("loca") ?: error("Font tidak punya tabel loca")
        val head = sfnt.table("head") ?: error("Font tidak punya tabel head")
        val maxp = sfnt.table("maxp") ?: error("Font tidak punya tabel maxp")
        val hhea = sfnt.table("hhea") ?: error("Font tidak punya tabel hhea")
        val hmtx = sfnt.table("hmtx") ?: error("Font tidak punya tabel hmtx")
        val cmap = sfnt.table("cmap") ?: error("Font tidak punya tabel cmap")

        val numGlyphs = readUShort(maxp, 4)
        val longLoca = readShort(head, 50) != 0
        val offsets = IntArray(numGlyphs + 1) { i ->
            if (longLoca) readInt(loca, i * 4) else readUShort(loca, i * 2) * 2
        }
        require(numGlyphs in 1..65535) { "Jumlah glyph tidak valid: $numGlyphs" }
        require(offsets.all { it in 0..glyf.size } && offsets.last() <= glyf.size) {
            "Tabel loca tidak konsisten dengan ukuran glyf"
        }

        val codepointToGlyph = parseCmap(cmap)
        val requested = LinkedHashSet<Int>()
        var index = 0
        while (index < characters.length) {
            val cp = characters.codePointAt(index)
            index += Character.charCount(cp)
            if (cp == '\n'.code || cp == '\r'.code || cp == '\t'.code || cp == ' '.code) continue
            requested.add(cp)
        }

        val selected = sortedSetOf(0)
        var dropped = 0
        if (requested.isEmpty()) {
            (0 until numGlyphs).forEach { selected.add(it) }
        } else {
            requested.forEach { cp ->
                val glyph = codepointToGlyph[cp]
                if (glyph == null || glyph >= numGlyphs) dropped++ else selected.add(glyph)
            }
        }
        // Penutupan komposit: glyph komponen wajib ikut.
        val queue = ArrayDeque(selected.toList())
        while (queue.isNotEmpty()) {
            val glyph = queue.removeFirst()
            glyphComponents(glyf, offsets, glyph, numGlyphs).forEach { component ->
                if (selected.add(component)) queue.add(component)
            }
        }

        val orderedGlyphs = selected.toList()
        val remap = HashMap<Int, Int>()
        orderedGlyphs.forEachIndexed { newIndex, oldIndex -> remap[oldIndex] = newIndex }
        val newGlyf = ByteArrayOutputStream()
        val newOffsets = IntArray(orderedGlyphs.size + 1)
        val newHmtx = ByteArray(orderedGlyphs.size * 4)
        val numberOfHMetrics = readUShort(hhea, 34).coerceIn(1, numGlyphs)
        val advances = IntArray(numGlyphs)
        val lsbs = IntArray(numGlyphs)
        for (glyph in 0 until numGlyphs) {
            val metricIndex = if (glyph < numberOfHMetrics) glyph else numberOfHMetrics - 1
            advances[glyph] = readUShort(hmtx, metricIndex * 4)
            val lsbOffset = if (glyph < numberOfHMetrics) {
                metricIndex * 4 + 2
            } else {
                numberOfHMetrics * 4 + (glyph - numberOfHMetrics) * 2
            }
            lsbs[glyph] = if (lsbOffset + 2 <= hmtx.size) readShort(hmtx, lsbOffset) else 0
        }

        orderedGlyphs.forEachIndexed { newIndex, oldGlyph ->
            newOffsets[newIndex] = newGlyf.size()
            val start = offsets[oldGlyph]
            val end = offsets.getOrElse(oldGlyph + 1) { start }
            if (end > start) {
                val glyphData = glyf.copyOfRange(start, end)
                remapComponents(glyphData, remap)
                newGlyf.write(glyphData)
                while (newGlyf.size() % 4 != 0) newGlyf.write(0)
            }
            writeUShort(newHmtx, newIndex * 4, advances[oldGlyph])
            writeShort(newHmtx, newIndex * 4 + 2, lsbs[oldGlyph])
        }
        newOffsets[orderedGlyphs.size] = newGlyf.size()

        val newLoca = ByteArray((orderedGlyphs.size + 1) * 4)
        newOffsets.forEachIndexed { i, value -> writeInt(newLoca, i * 4, value) }

        val newHead = head.copyOf()
        writeShort(newHead, 50, 1) // indexToLocFormat = long
        val newMaxp = maxp.copyOf()
        writeUShort(newMaxp, 4, orderedGlyphs.size)
        val newHhea = hhea.copyOf()
        writeUShort(newHhea, 34, orderedGlyphs.size)

        val newCmap = buildCmap4(codepointToGlyph, remap)
        // post v2 menyimpan nama glyph per indeks, jadi harus dibuang setelah subset.
        val keptTags = setOf("head", "hhea", "hmtx", "maxp", "cmap", "glyf", "loca", "name", "OS/2")
        val postV3 = ByteArray(32).also { writeInt(it, 0, 0x00030000) }
        val tables = buildList {
            sfnt.tables.filter { it.tag in keptTags && it.tag !in setOf("head", "hmtx", "maxp", "hhea", "cmap", "glyf", "loca") }
                .forEach { add(it) }
            add(Table("head", newHead))
            add(Table("hhea", newHhea))
            add(Table("hmtx", newHmtx))
            add(Table("maxp", newMaxp))
            add(Table("cmap", newCmap))
            add(Table("glyf", newGlyf.toByteArray()))
            add(Table("loca", newLoca))
            add(Table("post", postV3))
        }
        return SubsetResult(writeSfnt(Sfnt(sfnt.flavor, tables)), orderedGlyphs.size, dropped)
    }

    fun optimizeToWoff(bytes: ByteArray, keepCharacters: String): SubsetResult {
        val subset = subset(bytes, keepCharacters)
        val sfnt = readSfnt(subset.data)
        return subset.copy(data = writeWoff(sfnt))
    }

    // --------------------------------------------------------------- cmap baca

    /** Peta codepoint → glyph id dari subtable cmap terbaik (format 4/12). Murni. */
    fun parseCmap(cmap: ByteArray): Map<Int, Int> {
        if (cmap.size < 4) return emptyMap()
        val numTables = readUShort(cmap, 2)
        var best: Pair<Int, Int>? = null // offset, score
        for (i in 0 until numTables) {
            val base = 4 + i * 8
            if (base + 8 > cmap.size) break
            val platform = readUShort(cmap, base)
            val encoding = readUShort(cmap, base + 2)
            val offset = readInt(cmap, base + 4)
            if (offset + 4 > cmap.size) continue
            val format = readUShort(cmap, offset)
            val score = when {
                platform == 3 && encoding == 10 && format == 12 -> 100
                platform == 0 && format == 12 -> 90
                platform == 3 && encoding == 1 && format == 4 -> 80
                platform == 0 && format == 4 -> 70
                format == 4 -> 50
                format == 12 -> 40
                else -> 0
            }
            if (score > 0 && (best == null || score > best!!.second)) best = offset to score
        }
        val (offset, _) = best ?: return emptyMap()
        return when (readUShort(cmap, offset)) {
            4 -> parseCmap4(cmap, offset)
            12 -> parseCmap12(cmap, offset)
            else -> emptyMap()
        }
    }

    private fun parseCmap4(cmap: ByteArray, offset: Int): Map<Int, Int> {
        if (offset + 14 > cmap.size) return emptyMap()
        val segCount = readUShort(cmap, offset + 6) / 2
        if (segCount <= 0) return emptyMap()
        val endBase = offset + 14
        val startBase = endBase + segCount * 2 + 2
        val deltaBase = startBase + segCount * 2
        val rangeBase = deltaBase + segCount * 2
        val map = HashMap<Int, Int>()
        for (i in 0 until segCount) {
            if (rangeBase + i * 2 + 2 > cmap.size) break
            val end = readUShort(cmap, endBase + i * 2)
            val start = readUShort(cmap, startBase + i * 2)
            val delta = readShort(cmap, deltaBase + i * 2)
            val rangeOffset = readUShort(cmap, rangeBase + i * 2)
            if (start > end) continue
            for (cp in start..end) {
                if (cp == 0xFFFF) continue
                val glyph = if (rangeOffset == 0) {
                    (cp + delta) and 0xFFFF
                } else {
                    val glyphOffset = rangeBase + i * 2 + rangeOffset + (cp - start) * 2
                    if (glyphOffset + 2 > cmap.size) continue
                    val value = readUShort(cmap, glyphOffset)
                    if (value == 0) 0 else (value + delta) and 0xFFFF
                }
                if (glyph != 0) map[cp] = glyph
            }
        }
        return map
    }

    private fun parseCmap12(cmap: ByteArray, offset: Int): Map<Int, Int> {
        if (offset + 16 > cmap.size) return emptyMap()
        val groups = readInt(cmap, offset + 12)
        if (groups < 0 || groups > 200_000) return emptyMap()
        val map = HashMap<Int, Int>()
        for (i in 0 until groups) {
            val base = offset + 16 + i * 12
            if (base + 12 > cmap.size) break
            val start = readInt(cmap, base)
            val end = readInt(cmap, base + 4)
            val startGlyph = readInt(cmap, base + 8)
            if (start > end || end - start > 100_000) continue
            for (cp in start..end) {
                val glyph = startGlyph + (cp - start)
                if (glyph != 0) map[cp] = glyph
            }
        }
        return map
    }

    private fun buildCmap4(
        cmapMapping: Map<Int, Int>,
        remap: Map<Int, Int>
    ): ByteArray {
        val segments = cmapMapping.entries
            .filter { it.key in 0..0xFFFE }
            .mapNotNull { entry ->
                val newGlyph = remap[entry.value]
                if (newGlyph == null || newGlyph <= 0) null else entry.key to newGlyph
            }
            .sortedBy { it.first }
        val segCount = segments.size + 1
        require(16 + 8 * segCount <= 0xFFFF) { "Terlalu banyak karakter untuk cmap format 4" }
        val out = ByteArray(14 + 8 * segCount)
        writeUShort(out, 0, 4)
        writeUShort(out, 2, out.size)
        writeUShort(out, 4, 0)
        writeUShort(out, 6, segCount * 2)
        val power = highestPowerOfTwo(segCount)
        writeUShort(out, 8, power * 2)
        writeUShort(out, 10, log2(power))
        writeUShort(out, 12, segCount * 2 - power * 2)
        val endBase = 14
        val startBase = endBase + segCount * 2 + 2
        val deltaBase = startBase + segCount * 2
        val rangeBase = deltaBase + segCount * 2
        segments.forEachIndexed { index, (cp, glyph) ->
            writeUShort(out, endBase + index * 2, cp)
            writeUShort(out, startBase + index * 2, cp)
            writeShort(out, deltaBase + index * 2, glyph - cp)
            writeUShort(out, rangeBase + index * 2, 0)
        }
        val last = segments.size
        writeUShort(out, endBase + last * 2, 0xFFFF)
        writeUShort(out, startBase + last * 2, 0xFFFF)
        writeShort(out, deltaBase + last * 2, 1)
        writeUShort(out, rangeBase + last * 2, 0)
        val header = ByteArray(12)
        writeUShort(header, 0, 0)
        writeUShort(header, 2, 1)
        writeUShort(header, 4, 3)
        writeUShort(header, 6, 1)
        writeInt(header, 8, 12)
        return header + out
    }

    private fun glyphComponents(glyf: ByteArray, offsets: IntArray, glyph: Int, numGlyphs: Int): List<Int> {
        if (glyph + 1 >= offsets.size) return emptyList()
        val start = offsets[glyph]
        val end = offsets[glyph + 1]
        if (end - start < 10) return emptyList()
        val contours = readShort(glyf, start)
        if (contours >= 0) return emptyList()
        val components = mutableListOf<Int>()
        var cursor = start + 10
        while (cursor + 4 <= end) {
            val flags = readUShort(glyf, cursor)
            val componentIndex = readUShort(glyf, cursor + 2)
            if (componentIndex < numGlyphs) components.add(componentIndex)
            cursor += 4
            cursor += if (flags and 0x0001 != 0) 4 else 2
            cursor += when {
                flags and 0x0008 != 0 -> 2
                flags and 0x0040 != 0 -> 4
                flags and 0x0080 != 0 -> 8
                else -> 0
            }
            if (flags and 0x0020 == 0) break
        }
        return components
    }

    private fun remapComponents(glyphData: ByteArray, remap: Map<Int, Int>) {
        if (glyphData.size < 10 || readShort(glyphData, 0) >= 0) return
        var cursor = 10
        while (cursor + 4 <= glyphData.size) {
            val flags = readUShort(glyphData, cursor)
            val oldIndex = readUShort(glyphData, cursor + 2)
            val newIndex = remap[oldIndex]
            if (newIndex != null) writeUShort(glyphData, cursor + 2, newIndex)
            cursor += 4
            cursor += if (flags and 0x0001 != 0) 4 else 2
            cursor += when {
                flags and 0x0008 != 0 -> 2
                flags and 0x0040 != 0 -> 4
                flags and 0x0080 != 0 -> 8
                else -> 0
            }
            if (flags and 0x0020 == 0) break
        }
    }

    // -------------------------------------------------------------- utilitas

    fun checksum(data: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i < data.size) {
            var value = 0L
            for (k in 0 until 4) {
                value = (value shl 8) or (if (i + k < data.size) (data[i + k].toLong() and 0xFF) else 0L)
            }
            sum = (sum + value) and 0xFFFFFFFFL
            i += 4
        }
        return sum.toInt()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size)
            val buffer = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                val written = deflater.deflate(buffer)
                if (written <= 0) break
                out.write(buffer, 0, written)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(data: ByteArray, expected: Int): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArray(expected)
            var written = 0
            while (!inflater.finished() && written < expected) {
                val read = inflater.inflate(out, written, expected - written)
                if (read <= 0) break
                written += read
            }
            if (written == expected) out else out.copyOf(written)
        } finally {
            inflater.end()
        }
    }

    private fun align4(value: Int): Int = (value + 3) and 3.inv()

    private fun highestPowerOfTwo(value: Int): Int {
        var result = 1
        while (result * 2 <= value) result *= 2
        return result
    }

    private fun log2(value: Int): Int {
        var result = 0
        var current = value
        while (current > 1) {
            current /= 2
            result++
        }
        return result
    }

    private fun readUShort(data: ByteArray, offset: Int): Int {
        if (offset + 2 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun readShort(data: ByteArray, offset: Int): Int {
        if (offset + 2 > data.size) return 0
        val raw = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        return if (raw > 0x7FFF) raw - 0x10000 else raw
    }

    private fun readInt(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private fun writeUShort(data: ByteArray, offset: Int, value: Int) {
        if (offset + 2 > data.size) return
        data[offset] = ((value shr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        writeUShort(data, offset, value and 0xFFFF)
    }

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        if (offset + 4 > data.size) return
        data[offset] = ((value shr 24) and 0xFF).toByte()
        data[offset + 1] = ((value shr 16) and 0xFF).toByte()
        data[offset + 2] = ((value shr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }
}
