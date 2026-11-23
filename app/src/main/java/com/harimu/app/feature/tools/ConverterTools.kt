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
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 file PDF")
            emit(ToolProgress.Running(40, "Merender halaman PDF..."))
            val dest = ctx.storage.createOutputFile("PDF_IMGS", "zip")
            val (_, pages) = PdfEngine.pdfToImagesZip(
                ctx.appContext, ctx.storage, uri, dest,
                input.getFloat("scale", 2.0f),
                input.getString("fmt", "PNG")
            )
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/zip", dest.name)), summary = "$pages halaman dirender ke ZIP")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "CNV-16"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "ZIP"
        override val icon = Icons.Filled.FolderZip
        override val inputSpec = InputSpec.ZipFile
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "ZIP Archive Inspector" else "Inspeksi & Ekstrak ZIP"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Inspect archive file list, sizes, and repack cleanly." else "Periksa daftar file, ukuran terkompres, dan kemas ulang ZIP."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih file .zip")
            emit(ToolProgress.Running(45, "Membaca entri arsip ZIP..."))
            val dest = ctx.storage.createOutputFile("ZIP_CLEAN", "zip")
            val (_, report) = ConverterEngine.inspectAndRepackZip(ctx.appContext, ctx.storage, uri, dest)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/zip", dest.name)), textOutput = report, summary = "Arsip ZIP diinspeksi")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "CNV-18"
        override val group = ToolGroup.CONVERTER
        override val engineLabel = "AES-256"
        override val icon = Icons.Filled.Lock
        override val inputSpec = InputSpec.AnyFile
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Operasi", "Operation", listOf("Enkripsi ke .hzm", "Dekripsi .hzm ke .zip"), "Enkripsi ke .hzm"),
            ToolParam.TextParam("password", "Kata Sandi", "Password", "harimu1234", "Minimal 4 karakter", "Min 4 chars")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "AES-256 Encrypted Archive" else "Arsip Terenkripsi AES-256"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Encrypt or decrypt files using PBKDF2 + AES-256-GCM." else "Kunci file sensitif ke arsip AES-256-GCM (.hzm) atau buka kembali."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uris = input.uris
            require(uris.isNotEmpty()) { "Pilih minimal 1 file" }
            val pass = input.getString("password", "")
            val decrypt = input.getString("mode").startsWith("Dekripsi")
            if (decrypt) {
                val raw = ctx.appContext.contentResolver.openInputStream(uris.first())?.use { it.readBytes() } ?: error("Gagal membaca file")
                val dest = ctx.storage.createOutputFile("DECRYPTED", "zip")
                ConverterEngine.decryptArchiveToZip(raw, pass, dest)
                emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/zip", dest.name)), summary = "Arsip didekripsi ke ZIP")))
            } else {
                val dest = ctx.storage.createOutputFile("ENCRYPTED", "hzm")
                ConverterEngine.createEncryptedArchive(ctx.appContext, ctx.storage, uris, pass, dest)
                emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/octet-stream", dest.name)), summary = "${uris.size} file dikunci AES-256-GCM")))
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
            val isJsonToXml = input.getString("dir", "JSON -> XML") == "JSON -> XML"
            val resultText = if (isJsonToXml) ConverterEngine.jsonToXml(input.textInput) else ConverterEngine.xmlToJson(input.textInput)
            val ext = if (isJsonToXml) "xml" else "json"
            val dest = ctx.storage.createOutputFile("CONV", ext)
            dest.writeText(resultText)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/plain", dest.name)), textOutput = resultText, summary = "Konversi ${ext.uppercase()} berhasil")))
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
            val toTsv = input.getString("dir", "CSV -> TSV") == "CSV -> TSV"
            val out = if (toTsv) ConverterEngine.csvToTsv(input.textInput) else ConverterEngine.tsvToCsv(input.textInput)
            val ext = if (toTsv) "tsv" else "csv"
            val dest = ctx.storage.createOutputFile("TABLE", ext)
            dest.writeText(out)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/plain", dest.name)), textOutput = out, summary = "Konversi ${ext.uppercase()} selesai")))
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
            val mode = input.getString("mode", "Pretty JSON")
            val formatted = ConverterEngine.formatCode(input.textInput, mode)
            val ext = if (mode.contains("CSS")) "css" else "json"
            val dest = ctx.storage.createOutputFile("FMT", ext)
            dest.writeText(formatted)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/plain", dest.name)), textOutput = formatted, summary = "$mode berhasil")))
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
            val html = ConverterEngine.markdownToHtml(input.textInput)
            val dest = ctx.storage.createOutputFile("MD2HTML", "html")
            dest.writeText(html)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/html", dest.name)), textOutput = html, summary = "HTML diekspor (${ctx.storage.formatBytes(dest.length())})")))
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
            val kml = ConverterEngine.geoJsonToKml(input.textInput)
            val dest = ctx.storage.createOutputFile("GEO_KML", "kml")
            dest.writeText(kml)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/vnd.google-earth.kml+xml", dest.name)), textOutput = kml, summary = "KML dihasilkan")))
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
            val uri = input.uris.firstOrNull() ?: error("Pilih file .stl")
            val bytes = ctx.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Gagal membaca STL")
            val objText = ConverterEngine.stlToObj(bytes)
            val dest = ctx.storage.createOutputFile("MESH_OBJ", "obj")
            dest.writeText(objText)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/plain", dest.name)), textOutput = objText.take(2000), summary = "Model OBJ diekspor (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    }
)
