package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewInAr
import com.harimu.app.core.engines.ArchiveEngine
import com.harimu.app.core.engines.ConverterEngine
import com.harimu.app.core.engines.PdfEngine
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import com.harimu.app.core.toolapi.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val MAX_TEXT_CONVERTER_BYTES = 4 * 1024 * 1024

internal fun readTextInputOrUri(
    ctx: ToolContext,
    input: ToolInput,
    maxBytes: Int = MAX_TEXT_CONVERTER_BYTES
): String {
    if (input.textInput.isNotBlank()) {
        require(input.textInput.toByteArray(Charsets.UTF_8).size <= maxBytes) {
            if (ctx.isEnglish) "Input exceeds 4 MB text limit." else "Masukan melebihi batas teks 4 MB."
        }
        return input.textInput
    }
    val uri = input.uris.firstOrNull() ?: error(
        if (ctx.isEnglish) "Enter source text or pick a source file." else "Masukkan teks sumber atau pilih berkas."
    )
    val bytes = ctx.appContext.contentResolver.openInputStream(uri)?.use { stream ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            total += n
            require(total <= maxBytes) {
                if (ctx.isEnglish) "File exceeds 4 MB text conversion limit." else "Berkas melebihi batas konversi teks 4 MB."
            }
            out.write(buf, 0, n)
        }
        out.toByteArray()
    } ?: error(if (ctx.isEnglish) "Unable to read source file." else "Gagal membaca berkas sumber.")
    require(bytes.isNotEmpty()) {
        if (ctx.isEnglish) "Selected file is empty." else "Berkas yang dipilih kosong."
    }
    val looksBinary = bytes.take(4096).any { it == 0.toByte() }
    require(!looksBinary) {
        if (ctx.isEnglish) "Selected file appears binary and cannot be converted as text." else "Berkas tampak biner dan tidak dapat dikonversi sebagai teks."
    }
    return bytes.toString(Charsets.UTF_8)
}

fun converterTools(): List<Tool> = listOf(
    object : Tool {
        override val id = "CNV-14"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.PhotoLibrary
        override val inputSpec = InputSpec.SinglePdf
        override val params = listOf(
            ToolParam.DropdownParam("fmt", "Format Gambar", "Image Format", listOf("PNG", "JPG"), "PNG"),
            ToolParam.SliderParam("scale", "Skala Render", "Render Scale", 1.0f, 3.0f, 2.0f, 0.5f, "x")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "PDF to Image Matrix" else "PDF ke Gambar (.zip)"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Render all PDF pages to PNG/JPG inside a ZIP." else "Render setiap halaman PDF menjadi gambar PNG/JPG beresolusi tinggi."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 PDF file" else "Pilih 1 file PDF"
            )
            emit(ToolProgress.Running(40, if (ctx.isEnglish) "Rendering PDF pages..." else "Merender halaman PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_IMGS", "zip")
            val (_, pages) = PdfEngine.pdfToImagesZip(
                ctx.appContext, ctx.storage, uri, dest,
                input.getFloat("scale", 2.0f),
                input.getString("fmt", "PNG")
            )
            emit(
                ToolProgress.Done(
                    ToolResult(
                        listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                        summary = if (ctx.isEnglish) "$pages pages rendered to ZIP" else "$pages halaman dirender ke ZIP"
                    )
                )
            )
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "CNV-16"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "ZIP"
        override val icon = Icons.Filled.FolderZip
        override val inputSpec = InputSpec.ZipFile
        override val params = listOf(
            ToolParam.TextParam(
                "password", "Kata Sandi ZIP", "ZIP Password", "",
                "Kosongkan bila tidak ada", "Leave empty if none"
            )
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Universal Archive Extractor" else "Ekstraktor Arsip Universal"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "List and extract ZIP contents into a clean ZIP. Entry paths are sanitized (zip-slip) and size/ratio caps block zip bombs."
        } else {
            "Tampilkan daftar isi dan ekstrak ZIP menjadi ZIP bersih. Path entri divalidasi (zip-slip) dan batas ukuran/rasio mencegah zip bomb."
        }
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 .zip file" else "Pilih file .zip"
            )
            emit(ToolProgress.Running(40, if (ctx.isEnglish) "Validating and extracting ZIP entries…" else "Memvalidasi dan mengekstrak entri ZIP…"))
            val dest = ctx.storage.createOutputFile("ZIP_EXTRACT", "zip")
            val result = ArchiveEngine.extractToZip(
                ctx.appContext, ctx.storage, uri, dest,
                input.getString("password", ""), ctx.storage.getDisplayName(uri)
            )
            val summaryText = if (ctx.isEnglish) {
                "${result.extracted}/${result.entries.size} entries extracted (${ctx.storage.formatBytes(dest.length())})"
            } else {
                "${result.extracted}/${result.entries.size} entri diekstrak (${ctx.storage.formatBytes(dest.length())})"
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
    object : Tool {
        override val id = "CNV-18"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "AES-256"
        override val icon = Icons.Filled.Lock
        override val inputSpec = InputSpec.AnyFile
        override val params = listOf(
            ToolParam.DropdownParam(
                "mode", "Operasi", "Operation",
                listOf("Enkripsi ZIP AES-256", "Dekripsi ZIP AES-256"), "Enkripsi ZIP AES-256"
            ),
            ToolParam.TextParam("password", "Kata Sandi", "Password", "", "Minimal 8 karakter (enkripsi)", "Min 8 chars (encrypt)")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "AES-256 Encrypted Archive" else "Arsip Terenkripsi AES-256"
        override fun description(isEnglish: Boolean) = if (isEnglish) {
            "Create or open standard AES-256 ZIP archives (zip4j). The password is never stored in the output, name, or logs."
        } else {
            "Buat atau buka ZIP AES-256 standar (zip4j). Kata sandi tidak pernah disimpan di keluaran, nama berkas, maupun log."
        }
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uris = input.uris
            require(uris.isNotEmpty()) {
                if (ctx.isEnglish) "Select at least 1 file" else "Pilih minimal 1 file"
            }
            val pass = input.getString("password", "")
            val decrypt = input.getString("mode", "").startsWith("Dekripsi")
            if (decrypt) {
                require(pass.isNotBlank()) {
                    if (ctx.isEnglish) "Enter password to decrypt" else "Masukkan kata sandi untuk mendekripsi"
                }
                val raw = ctx.appContext.contentResolver.openInputStream(uris.first())?.use { it.readBytes() }
                    ?: error(if (ctx.isEnglish) "Failed to read file" else "Gagal membaca file")
                val dest = ctx.storage.createOutputFile("DECRYPTED", "zip")
                val legacy = raw.size > 8 && raw.copyOfRange(0, 8).contentEquals("HARIMUA1".toByteArray(Charsets.UTF_8))
                if (legacy) {
                    ConverterEngine.decryptArchiveToZip(raw, pass, dest)
                    emit(
                        ToolProgress.Done(
                            ToolResult(
                                listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                                summary = if (ctx.isEnglish) {
                                    "Legacy .hzm archive (AES-256-GCM) decrypted to ZIP"
                                } else {
                                    "Arsip .hzm lama (AES-256-GCM) didekripsi ke ZIP"
                                }
                            )
                        )
                    )
                } else {
                    emit(ToolProgress.Running(50, if (ctx.isEnglish) "Decrypting AES-256 ZIP…" else "Mendekripsi ZIP AES-256…"))
                    val count = ArchiveEngine.decryptZipToPlain(ctx.appContext, ctx.storage, uris.first(), pass, dest)
                    emit(
                        ToolProgress.Done(
                            ToolResult(
                                listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                                summary = if (ctx.isEnglish) "$count entries decrypted to ZIP" else "$count entri didekripsi ke ZIP"
                            )
                        )
                    )
                }
            } else {
                require(pass.length >= 8) {
                    if (ctx.isEnglish) {
                        "AES-256 ZIP encryption requires a password of at least 8 characters."
                    } else {
                        "Enkripsi ZIP AES-256 membutuhkan kata sandi minimal 8 karakter."
                    }
                }
                val dest = ctx.storage.createOutputFile("ENCRYPTED", "zip")
                emit(ToolProgress.Running(50, if (ctx.isEnglish) "Encrypting AES-256 ZIP…" else "Mengenkripsi ZIP AES-256…"))
                val count = ArchiveEngine.createAesZip(ctx.appContext, ctx.storage, uris, pass, dest)
                emit(
                    ToolProgress.Done(
                        ToolResult(
                            listOf(ToolOutputFile(dest, "application/zip", dest.name)),
                            summary = if (ctx.isEnglish) {
                                "$count files locked in AES-256 ZIP (${ctx.storage.formatBytes(dest.length())})"
                            } else {
                                "$count file dikunci ZIP AES-256 (${ctx.storage.formatBytes(dest.length())})"
                            }
                        )
                    )
                )
            }
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "CNV-23"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Code
        override val inputSpec = InputSpec.TextInput(
            "Tempel JSON atau XML di sini...",
            "Paste JSON or XML here...",
            "{\n  \"app\": \"HariMu\",\n  \"tools\": 55,\n  \"offline\": true\n}"
        )
        override val params = listOf(
            ToolParam.DropdownParam("dir", "Arah Konversi", "Direction", listOf("JSON -> XML", "XML -> JSON"), "JSON -> XML")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "JSON ⇄ XML Converter" else "Konverter JSON ⇄ XML"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Bidirectional JSON and XML conversion with indentation." else "Konversi dua arah JSON ke XML atau XML ke JSON secara rapi."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val sourceText = readTextInputOrUri(ctx, input)
            val isJsonToXml = input.getString("dir", "JSON -> XML") == "JSON -> XML"
            val resultText = if (isJsonToXml) ConverterEngine.jsonToXml(sourceText) else ConverterEngine.xmlToJson(sourceText)
            val ext = if (isJsonToXml) "xml" else "json"
            val dest = ctx.storage.createOutputFile("CONV", ext)
            dest.writeText(resultText)
            val msg = if (ctx.isEnglish) "Converted to ${ext.uppercase()}" else "Konversi ${ext.uppercase()} berhasil"
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/plain", dest.name)), textOutput = resultText, summary = msg)))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "CNV-24"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.TableChart
        override val inputSpec = InputSpec.TextInput(
            "Tempel data CSV atau TSV...",
            "Paste CSV or TSV rows...",
            "id,tool,engine\nVID-01,Trim Video,M3\nIMG-28,Palette,K-MEANS"
        )
        override val params = listOf(
            ToolParam.DropdownParam("dir", "Arah Konversi", "Direction", listOf("CSV -> TSV", "TSV -> CSV"), "CSV -> TSV")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "CSV ⇄ TSV Converter" else "Konverter CSV ⇄ TSV"
        override fun description(isEnglish: Boolean) = if (isEnglish) "RFC-4180 aware comma/tab table conversion." else "Konversi tabel CSV ke TSV atau TSV ke CSV dengan dukungan kutip."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val sourceText = readTextInputOrUri(ctx, input)
            val toTsv = input.getString("dir", "CSV -> TSV") == "CSV -> TSV"
            val out = if (toTsv) ConverterEngine.csvToTsv(sourceText) else ConverterEngine.tsvToCsv(sourceText)
            val ext = if (toTsv) "tsv" else "csv"
            val dest = ctx.storage.createOutputFile("TABLE", ext)
            dest.writeText(out)
            val msg = if (ctx.isEnglish) "Converted to ${ext.uppercase()}" else "Konversi ${ext.uppercase()} selesai"
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/plain", dest.name)), textOutput = out, summary = msg)))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "CNV-25"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Code
        override val inputSpec = InputSpec.TextInput(
            "Tempel JSON atau CSS...",
            "Paste JSON or CSS...",
            "{\"harimu\":{\"version\":1,\"active\":true,\"groups\":[\"CORE\",\"VIDEO\",\"IMAGE\"]}}"
        )
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Mode Format", "Format Mode", listOf("Pretty JSON", "Minify JSON", "Pretty CSS"), "Pretty JSON")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Code & JSON Formatter" else "Formatter Kode & JSON"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Beautify or minify JSON and CSS with validation." else "Rapikan (pretty-print) atau minify JSON & CSS dengan validasi."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val sourceText = readTextInputOrUri(ctx, input)
            val mode = input.getString("mode", "Pretty JSON")
            val formatted = ConverterEngine.formatCode(sourceText, mode)
            val ext = if (mode.contains("CSS")) "css" else "json"
            val dest = ctx.storage.createOutputFile("FMT", ext)
            dest.writeText(formatted)
            val msg = if (ctx.isEnglish) "$mode completed" else "$mode berhasil"
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/plain", dest.name)), textOutput = formatted, summary = msg)))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "CNV-26"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Description
        override val inputSpec = InputSpec.TextInput(
            "Tulis atau tempel Markdown...",
            "Write or paste Markdown...",
            "# HariMu Studio\n\nAplikasi **pisau lipat digital** untuk Android.\n\n- Cepat & Lokal\n- Material 3"
        )
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "Markdown to HTML" else "Markdown ke HTML"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Convert Markdown text into a styled standalone HTML file." else "Konversi teks Markdown menjadi dokumen HTML lengkap siap buka."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val sourceText = readTextInputOrUri(ctx, input)
            val html = ConverterEngine.markdownToHtml(sourceText)
            val dest = ctx.storage.createOutputFile("MD2HTML", "html")
            dest.writeText(html)
            val msg = if (ctx.isEnglish) {
                "HTML exported (${ctx.storage.formatBytes(dest.length())})"
            } else {
                "HTML diekspor (${ctx.storage.formatBytes(dest.length())})"
            }
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/html", dest.name)), textOutput = html, summary = msg)))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "CNV-27"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Map
        override val inputSpec = InputSpec.TextInput(
            "Tempel GeoJSON...",
            "Paste GeoJSON...",
            "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"name\":\"Monas Jakarta\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":[106.8271,-6.1754]}}]}"
        )
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "GeoJSON to KML" else "GeoJSON ke KML"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Convert Point, LineString & Polygon GeoJSON into Google Earth KML." else "Konversi koordinat GeoJSON menjadi file KML untuk peta."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val sourceText = readTextInputOrUri(ctx, input)
            val kml = ConverterEngine.geoJsonToKml(sourceText)
            val dest = ctx.storage.createOutputFile("GEO_KML", "kml")
            dest.writeText(kml)
            val msg = if (ctx.isEnglish) "KML generated" else "KML dihasilkan"
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/vnd.google-earth.kml+xml", dest.name)), textOutput = kml, summary = msg)))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "CNV-28"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "3D"
        override val icon = Icons.Filled.ViewInAr
        override val inputSpec = InputSpec.AnyFile
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "STL to OBJ 3D Mesh" else "Konverter 3D STL ke OBJ"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Parse ASCII or binary STL 3D models into Wavefront OBJ." else "Konversi model 3D STL (ASCII maupun binary) menjadi Wavefront OBJ."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error(
                if (ctx.isEnglish) "Select 1 .stl file" else "Pilih file .stl"
            )
            val bytes = ctx.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error(if (ctx.isEnglish) "Failed to read STL file" else "Gagal membaca STL")
            val objText = ConverterEngine.stlToObj(bytes)
            val dest = ctx.storage.createOutputFile("MESH_OBJ", "obj")
            dest.writeText(objText)
            val msg = if (ctx.isEnglish) {
                "OBJ model exported (${ctx.storage.formatBytes(dest.length())})"
            } else {
                "Model OBJ diekspor (${ctx.storage.formatBytes(dest.length())})"
            }
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/plain", dest.name)), textOutput = objText.take(2000), summary = msg)))
        }.flowOn(Dispatchers.IO)
    }
)
