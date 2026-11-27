package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Videocam
import com.harimu.app.core.engines.ArchiveEngine
import com.harimu.app.core.engines.EbookEngine
import com.harimu.app.core.engines.FontEngine
import com.harimu.app.core.engines.SvgEngine
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.VideoToolMath
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.core.toolapi.ToolResult
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Locale

/**
 * FASE 13 — Universal Converter.
 *
 * File ini berisi:
 * 1. [AliasTool]: entri CNV yang **mendelegasikan** pekerjaan ke tool asal (tidak ada logika
 *    duplikat). Parameter/inputSpec/requiresNetwork dibaca dari tool tujuan secara lazy agar
 *    sumber kebenaran tetap satu.
 * 2. Perluasan yang memang berbeda: CNV-03 (MP3/WAV/FLAC/AAC) dan CNV-06 (Hi-Res FLAC/WAV 24-bit).
 * 3. Implementasi baru: SVG (CNV-10), arsip lintas format (CNV-17), EPUB (CNV-19), komik (CNV-20),
 *    font (CNV-21/22).
 */
fun converterSuiteTools(mediaJob: MediaJob = MediaJob()): List<Tool> = listOf(
    // ------------------------------------------------------------- CNV-10 SVG
    object : Tool {
        override val id = "CNV-10"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "SVG"
        override val icon = Icons.Filled.Image
        override val inputSpec = InputSpec.AnyFile
        override val params = listOf(
            ToolParam.DropdownParam("fmt", "Format Keluaran", "Output Format", listOf("PNG", "JPG"), "PNG"),
            ToolParam.SliderParam("width", "Lebar (px)", "Width (px)", 64f, 4096f, 1080f, 64f, "px"),
            ToolParam.DropdownParam("bg", "Latar", "Background", listOf("Transparan", "Putih", "Hitam"), "Transparan")
        )
        override fun title(isEnglish: Boolean) =
            if (isEnglish) "SVG to Raster (PNG/JPG)" else "SVG ke Raster (PNG/JPG)"

        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Render SVG vector graphics to PNG or JPG with AndroidSVG. Filters, system-font text, and animation are not rendered."
        } else {
            "Render grafik vektor SVG ke PNG/JPG memakai AndroidSVG. Filter efek, teks berfont sistem, dan animasi tidak dirender."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 .svg file" else "Pilih 1 berkas .svg"
            )
            emit(ToolProgress.Running(35, if (ctx.isEnglish) "Rendering SVG…" else "Merender SVG…"))
            val isJpeg = input.getString("fmt", "PNG").equals("JPG", true)
            val dest = ctx.storage.createOutputFile("SVG_RASTER", if (isJpeg) "jpg" else "png")
            val rendered = SvgEngine.render(
                ctx.appContext,
                uri,
                dest,
                input.getFloat("width", 1080f).toInt(),
                if (isJpeg) "JPG" else "PNG",
                input.getString("bg", "Transparan")
            )
            emit(
                ToolProgress.Done(
                    ToolResult(
                        listOf(
                            ToolOutputFile(
                                rendered.file,
                                if (isJpeg) "image/jpeg" else "image/png",
                                rendered.file.name,
                                summary = "${rendered.width}×${rendered.height} px"
                            )
                        ),
                        summary = "SVG ${rendered.pageWidth.toInt()}×${rendered.pageHeight.toInt()} → ${rendered.width}×${rendered.height} px (${ctx.storage.formatBytes(rendered.file.length())})"
                    )
                )
            )
        }.flowOn(Dispatchers.IO)
    },

    // ------------------------------------------------------- CNV-17 Lintas arsip
    object : Tool {
        override val id = "CNV-17"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "ARCH"
        override val icon = Icons.Filled.Archive
        override val inputSpec = InputSpec.AnyFile
        override val params = listOf(
            ToolParam.TextParam(
                "password", "Kata Sandi Arsip", "Archive Password", "",
                "Kosongkan bila tidak ada", "Leave empty if none"
            )
        )
        override fun title(isEnglish: Boolean) =
            if (isEnglish) "Cross-Archive Converter" else "Konverter Lintas Arsip"

        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Convert ZIP, TAR.GZ, 7Z, and RAR into a clean ZIP with zip-slip and zip-bomb protection. RAR is read-only."
        } else {
            "Konversi ZIP, TAR.GZ, 7Z, dan RAR menjadi ZIP bersih dengan proteksi zip-slip dan zip bomb. RAR hanya dibaca."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 archive file" else "Pilih 1 berkas arsip"
            )
            val name = ctx.storage.getDisplayName(uri)
            emit(ToolProgress.Running(20, if (ctx.isEnglish) "Reading archive…" else "Membaca arsip…"))
            val dest = ctx.storage.createOutputFile("ARCHIVE", "zip")
            val result = ArchiveEngine.extractToZip(
                ctx.appContext, ctx.storage, uri, dest,
                input.getString("password", ""), name
            )
            val summaryText = if (ctx.isEnglish) {
                "${result.kind.label}: ${result.extracted}/${result.entries.size} entries extracted (${ctx.storage.formatBytes(dest.length())})"
            } else {
                "${result.kind.label}: ${result.extracted}/${result.entries.size} entri diekstrak (${ctx.storage.formatBytes(dest.length())})"
            }
            emit(
                ToolProgress.Done(
                    ToolResult(
                        listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                        textOutput = result.report(ctx.storage, ctx.isEnglish),
                        summary = summaryText
                    )
                )
            )
        }.flowOn(Dispatchers.IO)
    },

    // ------------------------------------------------------------- CNV-19 EPUB
    object : Tool {
        override val id = "CNV-19"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "EPUB"
        override val icon = Icons.Filled.Book
        override val inputSpec = InputSpec.AnyFile
        override val params = listOf(
            ToolParam.DropdownParam("target", "Format Tujuan", "Target Format", listOf("TXT", "HTML", "PDF"), "TXT")
        )
        override fun title(isEnglish: Boolean) =
            if (isEnglish) "EPUB Converter" else "Konverter EPUB"

        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Extract EPUB text to TXT/HTML/PDF following the spine. Images, CSS, and layouts are not preserved; DRM is refused."
        } else {
            "Ekstrak teks EPUB ke TXT/HTML/PDF mengikuti urutan spine. Gambar, CSS, dan tata letak tidak dipertahankan; DRM ditolak."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 .epub file" else "Pilih 1 berkas .epub"
            )
            emit(ToolProgress.Running(15, if (ctx.isEnglish) "Reading EPUB package…" else "Membaca paket EPUB…"))
            val epub = EbookEngine.readEpub(ctx.appContext, uri)
            val target = input.getString("target", "TXT").uppercase(Locale.US)
            emit(ToolProgress.Running(60, if (ctx.isEnglish) "Writing $target…" else "Menulis $target…"))
            val output: ToolOutputFile = when (target) {
                "HTML" -> {
                    val dest = ctx.storage.createOutputFile("EPUB_HTML", "html")
                    dest.writeText(EbookEngine.epubToHtml(epub))
                    ToolOutputFile(dest, "text/html", dest.name)
                }
                "PDF" -> {
                    val dest = ctx.storage.createOutputFile("EPUB_PDF", "pdf")
                    EbookEngine.epubToPdf(ctx.appContext, epub, dest)
                    ToolOutputFile(dest, "application/pdf", dest.name)
                }
                else -> {
                    val dest = ctx.storage.createOutputFile("EPUB_TXT", "txt")
                    dest.writeText(EbookEngine.epubToTxt(epub))
                    ToolOutputFile(dest, "text/plain", dest.name)
                }
            }
            val chapCount = EbookEngine.chapterCount(epub)
            val summaryText = if (ctx.isEnglish) {
                "${epub.title} • $chapCount chapters → ${output.displayName} (${ctx.storage.formatBytes(output.file.length())})"
            } else {
                "${epub.title} • $chapCount bab → ${output.displayName} (${ctx.storage.formatBytes(output.file.length())})"
            }
            emit(
                ToolProgress.Done(
                    ToolResult(
                        listOf(output),
                        textOutput = epub.chapters.take(3).joinToString("\n\n").take(4000),
                        summary = summaryText
                    )
                )
            )
        }.flowOn(Dispatchers.IO)
    },

    // ------------------------------------------------------------- CNV-20 Komik
    object : Tool {
        override val id = "CNV-20"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "CBZ"
        override val icon = Icons.Filled.Book
        override val inputSpec = InputSpec.AnyFile
        override val params = listOf(
            ToolParam.DropdownParam("target", "Format Tujuan", "Target Format", listOf("PDF", "EPUB"), "PDF"),
            ToolParam.SliderParam("width", "Sisi Terpanjang Gambar (px)", "Max Image Side (px)", 1080f, 3000f, 2200f, 100f, "px")
        )
        override fun title(isEnglish: Boolean) =
            if (isEnglish) "Comic Book Converter (CBZ/CBR)" else "Konverter Komik (CBZ/CBR)"

        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Convert CBZ or CBR comic archives into a PDF or EPUB with natural page ordering. CBR is read-only."
        } else {
            "Ubah arsip komik CBZ atau CBR menjadi PDF/EPUB dengan urutan halaman alami. CBR hanya dibaca."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 .cbz/.cbr file" else "Pilih 1 berkas .cbz/.cbr"
            )
            val name = ctx.storage.getDisplayName(uri).orEmpty()
            val header = runCatching {
                ctx.appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val buf = ByteArray(8)
                    val n = stream.read(buf)
                    if (n > 0) buf.copyOf(n) else ByteArray(0)
                }
            }.getOrNull() ?: ByteArray(0)
            val kind = when {
                header.size >= 4 && header.copyOfRange(0, 4).contentEquals("Rar!".toByteArray(Charsets.US_ASCII)) -> ArchiveEngine.Kind.RAR
                header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() -> ArchiveEngine.Kind.ZIP
                header.isEmpty() && name.endsWith(".cbr", true) -> ArchiveEngine.Kind.RAR
                header.isEmpty() && name.endsWith(".cbz", true) -> ArchiveEngine.Kind.ZIP
                else -> error(
                    if (ctx.isEnglish) {
                        "Unsupported comic archive: expected CBZ (ZIP) or CBR (RAR) header."
                    } else {
                        "Arsip komik tidak didukung: diperlukan header CBZ (ZIP) atau CBR (RAR)."
                    }
                )
            }
            emit(ToolProgress.Running(15, if (ctx.isEnglish) "Reading comic pages…" else "Membaca halaman komik…"))
            val pages = EbookEngine.readComic(ctx.appContext, uri, kind)
            val title = name.substringBeforeLast('.').ifBlank { if (ctx.isEnglish) "Comic" else "Komik" }
            val target = input.getString("target", "PDF")
            if (target == "EPUB") {
                val dest = ctx.storage.createOutputFile("COMIC_EPUB", "epub")
                emit(ToolProgress.Running(65, if (ctx.isEnglish) "Building EPUB…" else "Menyusun EPUB…"))
                EbookEngine.comicToEpub(pages, title, dest)
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            listOf(ToolOutputFile(dest, "application/epub+zip", dest.name)),
                            summary = if (ctx.isEnglish) {
                                "${pages.size} pages → EPUB (${ctx.storage.formatBytes(dest.length())})"
                            } else {
                                "${pages.size} halaman → EPUB (${ctx.storage.formatBytes(dest.length())})"
                            }
                        )
                    )
                )
            } else {
                val dest = ctx.storage.createOutputFile("COMIC_PDF", "pdf")
                emit(ToolProgress.Running(65, if (ctx.isEnglish) "Building PDF…" else "Menyusun PDF…"))
                EbookEngine.comicToPdf(ctx.appContext, pages, dest, input.getFloat("width", 2200f).toInt())
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            listOf(ToolOutputFile(dest, "application/pdf", dest.name)),
                            summary = if (ctx.isEnglish) {
                                "${pages.size} pages → PDF (${ctx.storage.formatBytes(dest.length())})"
                            } else {
                                "${pages.size} halaman → PDF (${ctx.storage.formatBytes(dest.length())})"
                            }
                        )
                    )
                )
            }
        }.flowOn(Dispatchers.IO)
    },

    // -------------------------------------------------------------- CNV-21 Font
    object : Tool {
        override val id = "CNV-21"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "FONT"
        override val icon = Icons.Filled.FontDownload
        override val inputSpec = InputSpec.AnyFile
        override val params = listOf(
            ToolParam.DropdownParam(
                "target", "Arah Konversi", "Direction",
                listOf("TTF/OTF → WOFF", "WOFF → TTF/OTF"), "TTF/OTF → WOFF"
            )
        )
        override fun title(isEnglish: Boolean) =
            if (isEnglish) "Universal Font Converter" else "Konverter Font Universal"

        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Convert TTF/OTF to WOFF 1.0 and back. WOFF2 and CFF↔TrueType outline conversion are not supported and are refused."
        } else {
            "Konversi TTF/OTF ke WOFF 1.0 dan sebaliknya. WOFF2 serta konversi outline CFF↔TrueType tidak didukung dan ditolak."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 font file" else "Pilih 1 berkas font"
            )
            val bytes = ctx.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error(if (ctx.isEnglish) "Failed to read font file" else "Gagal membaca berkas font")
            emit(ToolProgress.Running(40, if (ctx.isEnglish) "Converting font…" else "Mengubah font…"))
            val direction = input.getString("target", "TTF/OTF → WOFF")
            val conversion = FontEngine.convert(bytes, if (direction.startsWith("WOFF")) "SFNT" else "WOFF")
            val ext = when (conversion.to) {
                FontEngine.Kind.WOFF -> "woff"
                FontEngine.Kind.OTF -> "otf"
                else -> "ttf"
            }
            val dest = ctx.storage.createOutputFile("FONT", ext)
            dest.writeBytes(conversion.data)
            emit(
                ToolProgress.Done(
                    ToolResult(
                        listOf(
                            ToolOutputFile(
                                dest,
                                when (ext) {
                                    "woff" -> "font/woff"
                                    "otf" -> "font/otf"
                                    else -> "font/ttf"
                                },
                                dest.name
                            )
                        ),
                        summary = "${FontEngine.describeKind(conversion.from)} → ${conversion.note} (${ctx.storage.formatBytes(dest.length())})"
                    )
                )
            )
        }.flowOn(Dispatchers.IO)
    },

    // ------------------------------------------------- CNV-22 Web-font optimizer
    object : Tool {
        override val id = "CNV-22"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "SUBSET"
        override val icon = Icons.Filled.FontDownload
        override val inputSpec = InputSpec.AnyFile
        override val params = listOf(
            ToolParam.DropdownParam(
                "mode", "Mode Optimasi", "Optimization Mode",
                listOf("Subset karakter", "Optimasi tabel saja"), "Subset karakter"
            ),
            ToolParam.TextParam(
                "chars", "Karakter yang Dipertahankan", "Characters to Keep",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,!?-()&",
                "Karakter yang dipakai di web", "Characters used on the web"
            )
        )
        override fun title(isEnglish: Boolean) =
            if (isEnglish) "Web-Font Optimizer (Subsetting)" else "Optimizer Web-Font (Subsetting)"

        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Subset TrueType glyphs to the characters you need and export WOFF. Hinting/layout tables are dropped; WOFF2 input and CFF fonts are refused."
        } else {
            "Pangkas glif TrueType ke karakter yang dipakai lalu ekspor WOFF. Tabel hinting/tata letak dibuang; masukan WOFF2 dan font CFF ditolak."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 TTF/WOFF font file" else "Pilih 1 berkas font TTF/WOFF"
            )
            val bytes = ctx.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error(if (ctx.isEnglish) "Failed to read font file" else "Gagal membaca berkas font")
            emit(ToolProgress.Running(30, if (ctx.isEnglish) "Reading font tables…" else "Membaca tabel font…"))
            val subsetMode = input.getString("mode", "Subset karakter").startsWith("Subset")
            val chars = if (subsetMode) input.getString("chars", "") else ""
            val result = FontEngine.optimizeToWoff(bytes, chars)
            val dest = ctx.storage.createOutputFile("WEBFONT", "woff")
            dest.writeBytes(result.data)
            val note = if (subsetMode) {
                if (ctx.isEnglish) {
                    "Glyphs kept: ${result.keptGlyphs}" + if (result.droppedCodepoints > 0) ", ${result.droppedCodepoints} missing from font" else ""
                } else {
                    "Glyph dipertahankan: ${result.keptGlyphs}" + if (result.droppedCodepoints > 0) ", ${result.droppedCodepoints} karakter tidak ada di font" else ""
                }
            } else {
                if (ctx.isEnglish) {
                    "All glyphs kept; hinting/layout tables dropped"
                } else {
                    "Semua glyph dipertahankan; tabel hinting/tata letak dibuang"
                }
            }
            emit(
                ToolProgress.Done(
                    ToolResult(
                        listOf(ToolOutputFile(dest, "font/woff", dest.name)),
                        textOutput = note,
                        summary = "WOFF ${ctx.storage.formatBytes(dest.length())} • $note"
                    )
                )
            )
        }.flowOn(Dispatchers.IO)
    },

    // --------------------------------------- CNV-03 Ekstrak audio (MP3/WAV/FLAC/AAC)
    object : Tool {
        override val id = "CNV-03"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "FF"
        override val icon = Icons.Filled.AudioFile
        override val inputSpec = InputSpec.SingleVideo
        override val params = listOf(
            ToolParam.DropdownParam("format", "Format Audio", "Audio Format", listOf("MP3", "WAV", "FLAC", "AAC"), "MP3"),
            ToolParam.DropdownParam("bitrate", "Bitrate (MP3/AAC)", "Bitrate (MP3/AAC)", listOf("128k", "192k", "256k", "320k"), "192k")
        )
        override fun title(isEnglish: Boolean) =
            if (isEnglish) "Video to Audio Extractor" else "Ekstraktor Audio Video"

        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Extract a video's audio track to MP3, WAV, FLAC, or AAC. WAV/FLAC are lossless containers of the decoded track, not better-than-source audio."
        } else {
            "Ekstrak trek audio video ke MP3, WAV, FLAC, atau AAC. WAV/FLAC hanya wadah lossless dari audio hasil dekode, bukan peningkatan kualitas sumber."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = VideoToolMath.validateVideoInputs(
                context = ctx.appContext,
                storage = ctx.storage,
                uris = input.uris,
                requireAudio = true,
                isEnglish = ctx.isEnglish
            )
            if (!validation.valid) {
                emit(
                    ToolProgress.Failed(
                        validation.errorMessage ?: if (ctx.isEnglish) "Invalid video" else "Video tidak valid"
                    )
                )
                return@flow
            }
            val probe = validation.probes.first()
            val format = input.getString("format", "MP3").uppercase(Locale.US)
            val bitrate = input.getString("bitrate", "192k").ifBlank { "192k" }
            val (ext, mime) = when (format) {
                "WAV" -> "wav" to "audio/wav"
                "FLAC" -> "flac" to "audio/flac"
                "AAC" -> "m4a" to "audio/mp4"
                else -> "mp3" to "audio/mpeg"
            }
            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "CNV_AUDIO_$format",
                    outputExt = ext,
                    outputMime = mime,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vn")
                        when (format) {
                            "WAV" -> {
                                add("-c:a"); add("pcm_s16le")
                                add("-ar"); add("44100")
                            }
                            "FLAC" -> {
                                add("-c:a"); add("flac")
                                add("-compression_level"); add("8")
                            }
                            "AAC" -> {
                                add("-c:a"); add("aac")
                                add("-b:a"); add(bitrate)
                            }
                            else -> {
                                add("-c:a"); add("libmp3lame")
                                add("-b:a"); add(bitrate)
                                add("-ar"); add("44100")
                            }
                        }
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // ------------------------------------------------------ CNV-06 Hi-Res lossless
    object : Tool {
        override val id = "CNV-06"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "FF"
        override val icon = Icons.Filled.AudioFile
        override val inputSpec = InputSpec.SingleAudio
        override val params = listOf(
            ToolParam.DropdownParam("format", "Format Lossless", "Lossless Format", listOf("FLAC 24-bit", "WAV 24-bit"), "FLAC 24-bit"),
            ToolParam.DropdownParam("rate", "Sample Rate", "Sample Rate", listOf("44100", "48000", "96000"), "48000")
        )
        override fun title(isEnglish: Boolean) =
            if (isEnglish) "Hi-Res Lossless Converter" else "Konverter Hi-Res Lossless"

        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Encode audio to 24-bit FLAC/WAV (up to 96 kHz). Up-sampling a lossy source cannot restore lost detail."
        } else {
            "Enkode audio ke FLAC/WAV 24-bit (hingga 96 kHz). Upsampling sumber lossy tidak mengembalikan detail yang hilang."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val validation = com.harimu.app.core.media.AudioToolMath.validateAudioInputs(
                ctx.appContext, ctx.storage, input.uris, 1, ctx.isEnglish
            )
            if (!validation.valid) {
                emit(
                    ToolProgress.Failed(
                        validation.errorMessage ?: if (ctx.isEnglish) "Invalid audio" else "Audio tidak valid"
                    )
                )
                return@flow
            }
            val probe = validation.probes.first()
            val flac = !input.getString("format", "FLAC 24-bit").startsWith("WAV")
            val rate = input.getString("rate", "48000").ifBlank { "48000" }
            val ext = if (flac) "flac" else "wav"
            val mime = if (flac) "audio/flac" else "audio/wav"
            emitAll(
                mediaJob.runCommand(
                    context = ctx.appContext,
                    storage = ctx.storage,
                    inputUris = input.uris,
                    toolTag = "CNV_HIRES",
                    outputExt = ext,
                    outputMime = mime,
                    expectedDurationMs = probe.durationMs,
                    isEnglish = ctx.isEnglish
                ) { inputs, output ->
                    buildList {
                        add("-y")
                        add("-i"); add(inputs.first().absolutePath)
                        add("-vn")
                        if (flac) {
                            add("-c:a"); add("flac")
                            add("-sample_fmt"); add("s32")
                            add("-compression_level"); add("8")
                        } else {
                            add("-c:a"); add("pcm_s24le")
                        }
                        add("-ar"); add(rate)
                        add(output.absolutePath)
                    }
                }
            )
        }.flowOn(Dispatchers.IO)
    },

    // ------------------------------------------------------------- Alias entri
    AliasTool("CNV-01", "VID-18", "FF", Icons.Filled.Videocam,
        "Universal Video Transcoder", "Universal Video Transcoder",
        "Alias VID-18: transkode video lintas wadah MP4/WebM/AVI/MKV.",
        "Alias VID-18: transcode video across MP4/WebM/AVI/MKV containers."),
    AliasTool("CNV-02", "VID-06", "FF", Icons.Filled.Gif,
        "Video ke GIF Animasi", "Video to Animated GIF",
        "Alias VID-06: ubah klip video menjadi GIF animasi berpalet optimal.",
        "Alias VID-06: convert video clips into palette-optimized animated GIF."),
    AliasTool("CNV-04", "VID-07", "ML", Icons.Filled.Videocam,
        "Video Upscaler (AI) 2x", "Video Upscaler (AI) 2x",
        "Alias VID-07: upscale 2x berbasis FFmpeg lanczos + unsharp (bukan model neural).",
        "Alias VID-07: 2x upscale via FFmpeg lanczos + unsharp (not a neural model)."),
    AliasTool("CNV-05", "AUD-13", "FF", Icons.Filled.AudioFile,
        "Universal Audio Transcoder", "Universal Audio Transcoder",
        "Alias AUD-13: konversi MP3/WAV/OGG/FLAC/AAC/OPUS.",
        "Alias AUD-13: convert MP3/WAV/OGG/FLAC/AAC/OPUS."),
    AliasTool("CNV-07", "AUD-27", "ML/CL", Icons.Filled.AudioFile,
        "Suara ke Teks", "Voice to Text Converter",
        "Alias AUD-27: transkripsi ucapan lewat gateway cloud.",
        "Alias AUD-27: speech transcription through the cloud gateway."),
    AliasTool("CNV-08", "AUD-05", "FF", Icons.Filled.SurroundSound,
        "Generator Audio Spasial 8D", "8D / Spatial Audio Generator",
        "Alias AUD-05: efek audio spasial 8D.",
        "Alias AUD-05: 8D spatial audio effect."),
    AliasTool("CNV-09", "IMG-23", "NAT", Icons.Filled.Image,
        "Konverter Gambar Universal", "Universal Image Converter",
        "Alias IMG-23: konversi PNG/JPG/WebP/AVIF sesuai dukungan platform.",
        "Alias IMG-23: convert PNG/JPG/WebP/AVIF within platform support."),
    AliasTool("CNV-11", "IMG-24", "NAT", Icons.Filled.Image,
        "Generator Aset Ikon (+Favicon)", "Icon Asset Generator (+Favicon)",
        "Alias IMG-24: paket ikon multi-ukuran dan favicon.",
        "Alias IMG-24: multi-size icon and favicon pack."),
    AliasTool("CNV-12", "IMG-25", "NAT", Icons.Filled.Code,
        "Encoder Gambar Base64", "Base64 Image Encoder",
        "Alias IMG-25: ubah gambar ke string Base64.",
        "Alias IMG-25: encode images to Base64 strings."),
    AliasTool("CNV-15", "PDF-15", "OCR", Icons.Filled.DocumentScanner,
        "Scanner OCR ke .txt", "OCR Document Scanner to .txt",
        "Alias PDF-15: OCR 8 bahasa untuk PDF hasil pindai.",
        "Alias PDF-15: 8-language OCR for scanned PDFs."),
    object : Tool {
        override val id = "CNV-13"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "LIB"
        override val icon = Icons.Filled.PictureAsPdf
        override val inputSpec = InputSpec.AnyFile
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) =
            if (isEnglish) "PDF to Word / Word to PDF" else "PDF ke Word / Word ke PDF"

        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Routes .docx to PDF-07 and PDF files to PDF-06 (Basic on-device conversion)."
        } else {
            "Mengarahkan .docx ke PDF-07 dan PDF ke PDF-06 (konversi Dasar on-device)."
        }

        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select a PDF or .docx file" else "Pilih berkas PDF atau .docx"
            )
            val header = runCatching {
                ctx.appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val buf = ByteArray(8)
                    val n = stream.read(buf)
                    if (n > 0) buf.copyOf(n) else ByteArray(0)
                }
            }.getOrNull() ?: ByteArray(0)
            val targetId = when {
                header.size >= 5 && header.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII)) -> "PDF-06"
                header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() -> {
                    val temp = ctx.storage.copyUriToTemp(uri, "cnv13_chk", "zip")
                    try {
                        val hasDocxEntry = runCatching {
                            java.util.zip.ZipFile(temp).use { zip -> zip.getEntry("word/document.xml") != null }
                        }.getOrDefault(false)
                        if (hasDocxEntry) "PDF-07"
                        else error(
                            if (ctx.isEnglish) {
                                "ZIP archive is not a .docx document (missing word/document.xml)."
                            } else {
                                "Arsip ZIP ini bukan dokumen .docx (tidak ada word/document.xml)."
                            }
                        )
                    } finally {
                        temp.delete()
                    }
                }
                else -> error(
                    if (ctx.isEnglish) {
                        "Unsupported file: select a valid PDF or Word (.docx) document."
                    } else {
                        "Format berkas tidak didukung: pilih dokumen PDF atau Word (.docx) yang valid."
                    }
                )
            }
            val target = ToolRegistry.findById(targetId)
                ?: error(if (ctx.isEnglish) "Target tool $targetId unavailable" else "Tool tujuan $targetId tidak tersedia")
            emitAll(target.run(ctx, input))
        }.flowOn(Dispatchers.IO)
    }
)

/** Alias tanpa logika sendiri: semua properti dibaca dari tool tujuan. */
class AliasTool(
    override val id: String,
    private val targetId: String,
    override val engineLabel: String,
    override val icon: ImageVector,
    private val titleId: String,
    private val titleEn: String,
    private val descId: String,
    private val descEn: String
) : Tool {

    private val delegate: Tool by lazy {
        ToolRegistry.findById(targetId) ?: error("Tool tujuan $targetId tidak ditemukan")
    }

    override val group = ToolGroup.CONVERTER
    override val aliasOfId: String get() = targetId
    override val isStub: Boolean get() = false
    override val inputSpec: InputSpec get() = delegate.inputSpec
    override val params: List<ToolParam> get() = delegate.params
    override val requiresNetwork: Boolean get() = delegate.requiresNetwork
    override val requiresModel: com.harimu.app.core.toolapi.ModelSpec? get() = delegate.requiresModel
    override val dedicatedRoute: com.harimu.app.core.toolapi.DedicatedScreenRoute? get() = delegate.dedicatedRoute

    override fun title(isEnglish: Boolean): String = if (isEnglish) titleEn else titleId

    override fun description(isEnglish: Boolean): String = if (isEnglish) descEn else descId

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = delegate.run(ctx, input)
}
