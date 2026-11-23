package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import com.harimu.app.core.engines.ImageEngine
import com.harimu.app.core.toolapi.DedicatedScreenRoute
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

fun imageTools(): List<Tool> = listOf(
    DedicatedTool(
        id = "IMG-47",
        group = ToolGroup.IMAGE,
        engineLabel = "MAT",
        icon = Icons.Filled.Tune,
        dedicatedRoute = DedicatedScreenRoute.IMAGE_MATRIX,
        titleId = "Image Matrix Studio",
        titleEn = "Image Matrix Studio",
        descId = "Editor kanvas foto interaktif: pratinjau filter langsung, putar, balik & watermark.",
        descEn = "Interactive photo canvas editor: live filters, rotate, flip & watermark."
    ),
    object : Tool {
        override val id = "IMG-01"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Crop
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.DropdownParam("ratio", "Rasio Aspek", "Aspect Ratio", listOf("1:1", "16:9", "9:16", "4:3", "3:4"), "1:1")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Image Cropper" else "Crop Gambar"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Center-crop image to standard social aspect ratios." else "Potong gambar presisi sesuai rasio 1:1, 16:9, 9:16, 4:3, atau 3:4."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            emit(ToolProgress.Running(40, "Memotong gambar..."))
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val cropped = ImageEngine.cropAspect(src, input.getString("ratio", "1:1"))
            val dest = ctx.storage.createOutputFile("IMG_CROP", "png")
            ImageEngine.saveBitmap(cropped, dest, "PNG", 100)
            val info = "${cropped.width}×${cropped.height}"
            if (cropped != src) src.recycle()
            cropped.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Hasil $info (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-02"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.PhotoSizeSelectLarge
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.SliderParam("scale", "Skala (%)", "Scale (%)", 10f, 200f, 50f, 5f, "%"),
            ToolParam.SliderParam("maxEdge", "Sisi Maks (px, 0=pakai skala)", "Max Edge (px, 0=use scale)", 0f, 2400f, 0f, 100f, "px")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Smart Image Resizer" else "Ubah Ukuran Gambar"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Resize image proportionally by percentage or max edge." else "Ubah dimensi gambar secara proporsional tanpa distorsi."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            emit(ToolProgress.Running(40, "Mengubah resolusi gambar..."))
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val resized = ImageEngine.resizeBitmap(src, input.getFloat("scale", 50f).toInt(), input.getFloat("maxEdge", 0f).toInt())
            val dest = ctx.storage.createOutputFile("IMG_RESIZE", "png")
            ImageEngine.saveBitmap(resized, dest, "PNG", 100)
            val info = "${resized.width}×${resized.height}"
            if (resized != src) src.recycle()
            resized.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Resolusi baru: $info")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-03"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.RotateRight
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.DropdownParam("deg", "Sudut Putar", "Angle", listOf("90", "180", "270"), "90")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Image Rotator" else "Putar Gambar"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Rotate image 90, 180, or 270 degrees." else "Putar gambar 90°, 180°, atau 270° searah jarum jam."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val deg = input.getString("deg", "90").toFloatOrNull() ?: 90f
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val rot = ImageEngine.rotateBitmap(src, deg)
            val dest = ctx.storage.createOutputFile("IMG_ROT", "png")
            ImageEngine.saveBitmap(rot, dest)
            src.recycle(); rot.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Diputar ${deg.toInt()}°")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-04"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Flip
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.DropdownParam("dir", "Arah Cermin", "Flip Direction", listOf("Horizontal", "Vertikal", "Keduanya"), "Horizontal")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Image Flipper" else "Balik Gambar (Flip)"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Mirror image horizontally or vertically." else "Balik gambar secara horizontal atau vertikal."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val dir = input.getString("dir", "Horizontal")
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val out = ImageEngine.flipBitmap(src, dir != "Vertikal", dir != "Horizontal")
            val dest = ctx.storage.createOutputFile("IMG_FLIP", "png")
            ImageEngine.saveBitmap(out, dest)
            src.recycle(); out.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Flip $dir selesai")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-05"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.AspectRatio
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.DropdownParam("aspect", "Rasio Kanvas", "Canvas Aspect", listOf("1:1", "4:5", "16:9", "9:16"), "1:1"),
            ToolParam.TextParam("bgHex", "Warna Latar (#Hex)", "Background (#Hex)", "#13161F")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Canvas Expander" else "Perluas Kanvas"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Pad image cleanly to fit target aspect ratio." else "Tambah latar belakang presisi tanpa memotong isi gambar."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val out = ImageEngine.expandCanvas(src, input.getString("aspect", "1:1"), input.getString("bgHex", "#13161F"))
            val dest = ctx.storage.createOutputFile("IMG_CANVAS", "png")
            ImageEngine.saveBitmap(out, dest)
            val info = "${out.width}×${out.height}"
            src.recycle(); out.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Kanvas $info")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-07"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Straighten
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.SliderParam("angle", "Koreksi Sudut (°)", "Angle Correction (°)", -25f, 25f, 3f, 0.5f, "°")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Image Straightener" else "Pelurus Horizon Gambar"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Rotate by fine degrees and auto-crop borders." else "Luruskan kemiringan foto dengan pemotongan tepi otomatis."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val angle = input.getFloat("angle", 3f)
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val out = ImageEngine.straightenBitmap(src, angle)
            val dest = ctx.storage.createOutputFile("IMG_STRAIGHT", "png")
            ImageEngine.saveBitmap(out, dest)
            src.recycle(); out.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Diluruskan ${angle}°")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-23"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.SwapHoriz
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.DropdownParam("format", "Format Tujuan", "Target Format", listOf("PNG", "JPG", "WEBP", "PDF"), "WEBP"),
            ToolParam.SliderParam("quality", "Kualitas (%)", "Quality (%)", 30f, 100f, 90f, 5f, "%")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Image Format Converter" else "Konverter Format Gambar"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Convert between PNG, JPG, WebP, and PDF." else "Konversi gambar ke PNG, JPG, WebP, atau PDF."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val fmt = input.getString("format", "WEBP")
            val ext = fmt.lowercase()
            val mime = when (ext) {
                "jpg" -> "image/jpeg"
                "webp" -> "image/webp"
                "pdf" -> "application/pdf"
                else -> "image/png"
            }
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val dest = ctx.storage.createOutputFile("IMG_CONV", ext)
            ImageEngine.saveBitmap(src, dest, fmt, input.getFloat("quality", 90f).toInt())
            src.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, mime, dest.name)), summary = "Tersimpan sebagai $fmt (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-24"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Apps
        override val inputSpec = InputSpec.SingleImage
        override val params = emptyList<ToolParam>()
        override fun title(isEnglish: Boolean) = if (isEnglish) "App Icon Pack Generator" else "Generator Ikon Android (.zip)"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Generate mdpi-xxxhdpi + 512px Play Store & favicon pack." else "Buat paket ikon mdpi–xxxhdpi, Play Store 512px & favicon dalam ZIP."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar ikon")
            emit(ToolProgress.Running(45, "Membuat 7 ukuran ikon..."))
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri, maxDim = 1024)
            val dest = ctx.storage.createOutputFile("ICON_PACK", "zip")
            ImageEngine.generateIconPackZip(src, dest)
            src.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "application/zip", dest.name)), summary = "7 ukuran ikon dikemas ke ZIP (${ctx.storage.formatBytes(dest.length())})")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-25"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Code
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.ToggleParam("dataUri", "Sertakan Data URI (data:image/...)", "Include Data URI prefix", true)
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Image to Base64 Encoder" else "Encoder Gambar ke Base64"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Encode image bytes to Base64 text & .txt file." else "Ubah file gambar menjadi string Base64 siap tempel."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val b64 = ImageEngine.encodeToBase64(ctx.appContext, uri, input.getBoolean("dataUri", true))
            val dest = ctx.storage.createOutputFile("IMG_B64", "txt")
            dest.writeText(b64)
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "text/plain", dest.name)), textOutput = b64.take(2500), summary = "Panjang Base64: ${b64.length} karakter")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-26"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Compress
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.SliderParam("quality", "Kualitas Kompresi (%)", "Compression Quality (%)", 20f, 95f, 65f, 5f, "%"),
            ToolParam.SliderParam("maxDim", "Lebar/Tinggi Maks (px)", "Max Dimension (px)", 640f, 2560f, 1600f, 160f, "px")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Smart Image Compressor" else "Kompresor Gambar Cerdas"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Shrink photo file size using WebP/JPEG compression." else "Kecilkan ukuran file gambar dengan kontrol kualitas & dimensi."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri, input.getFloat("maxDim", 1600f).toInt())
            val dest = ctx.storage.createOutputFile("IMG_COMP", "jpg")
            ImageEngine.saveBitmap(src, dest, "JPG", input.getFloat("quality", 65f).toInt())
            src.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/jpeg", dest.name)), summary = "Terkompres: ${ctx.storage.formatBytes(dest.length())}")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-28"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "K-MEANS"
        override val icon = Icons.Filled.Palette
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.SliderParam("count", "Jumlah Warna", "Color Count", 3f, 8f, 5f, 1f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Color Palette Extractor" else "Ekstraktor Palet Warna"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Extract dominant hex colors using K-Means clustering." else "Ekstrak warna dominan (Hex) dari foto menggunakan algoritma K-Means."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            emit(ToolProgress.Running(45, "Menjalankan klaster K-Means..."))
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri, maxDim = 800)
            val (colors, card) = ImageEngine.extractKMeansPalette(src, input.getFloat("count", 5f).toInt())
            val dest = ctx.storage.createOutputFile("PALETTE", "png")
            ImageEngine.saveBitmap(card, dest)
            src.recycle(); card.recycle()
            val hexList = colors.joinToString("\n") { String.format("#%06X", 0xFFFFFF and it) }
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), textOutput = hexList, summary = "${colors.size} warna dominan diekstrak")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-29"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "EXIF"
        override val icon = Icons.Filled.Info
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.ToggleParam("strip", "Hapus Semua EXIF & Simpan Foto Bersih", "Strip EXIF & Save Clean Copy", true)
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "EXIF Inspector & Stripper" else "Inspeksi & Hapus EXIF"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Read camera/GPS metadata and export a clean privacy copy." else "Baca metadata kamera/GPS dan ekspor salinan gambar tanpa jejak EXIF."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val report = ImageEngine.inspectExif(ctx.appContext, uri, ctx.storage)
            val outputs = if (input.getBoolean("strip", true)) {
                val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
                val dest = ctx.storage.createOutputFile("NO_EXIF", "jpg")
                ImageEngine.saveBitmap(src, dest, "JPG", 94)
                src.recycle()
                listOf(ToolOutputFile(dest, "image/jpeg", dest.name))
            } else emptyList()
            emit(ToolProgress.Done(ToolResult(outputs, textOutput = report, summary = "Metadata EXIF diinspeksi")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-31"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.WaterDrop
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.TextParam("text", "Teks Watermark", "Watermark Text", "© HariMu Studio"),
            ToolParam.SliderParam("opacity", "Opasitas (%)", "Opacity (%)", 10f, 95f, 45f, 5f, "%"),
            ToolParam.DropdownParam("pos", "Posisi", "Position", listOf("Kanan Bawah", "Kiri Bawah", "Kanan Atas", "Kiri Atas", "Tengah"), "Kanan Bawah"),
            ToolParam.ToggleParam("tiled", "Mode Pola Berulang (Tile)", "Tiled Pattern", false)
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Image Watermark" else "Watermark Gambar"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Overlay single or tiled text watermark on photos." else "Tambahkan watermark teks tunggal atau berulang pada foto."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val out = ImageEngine.addWatermark(
                src,
                input.getString("text", "© HariMu"),
                input.getFloat("opacity", 45f).toInt(),
                input.getString("pos", "Kanan Bawah"),
                input.getBoolean("tiled", false)
            )
            val dest = ctx.storage.createOutputFile("IMG_WM", "png")
            ImageEngine.saveBitmap(out, dest)
            src.recycle(); out.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Watermark diterapkan")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-33"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.AutoFixHigh
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.DropdownParam("preset", "Preset Filter", "Filter Preset", listOf("Normal", "Grayscale", "Sepia", "Invert", "Warm", "Cool", "Vintage"), "Vintage"),
            ToolParam.SliderParam("contrast", "Kontras", "Contrast", 0.5f, 1.8f, 1.05f, 0.05f, "x")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Image Filters" else "Filter Warna Gambar"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Apply Grayscale, Sepia, Vintage, Warm, Cool, or Invert." else "Terapkan filter warna Grayscale, Sepia, Vintage, Warm, Cool, atau Invert."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val out = ImageEngine.applyFilter(src, input.getString("preset", "Vintage"), contrast = input.getFloat("contrast", 1.05f))
            val dest = ctx.storage.createOutputFile("IMG_FLT", "png")
            ImageEngine.saveBitmap(out, dest)
            src.recycle(); out.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Filter ${input.getString("preset")} diterapkan")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-34"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.BorderOuter
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.SliderParam("pad", "Tebal Bingkai (%)", "Frame Padding (%)", 2f, 20f, 6f, 1f, "%"),
            ToolParam.TextParam("color", "Warna Bingkai (#Hex)", "Frame Color (#Hex)", "#FFFFFF"),
            ToolParam.ToggleParam("polaroid", "Gaya Polaroid (bawah lebih tebal)", "Polaroid Style", true)
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Border & Frame Generator" else "Bingkai & Frame Foto"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Add clean gallery or Polaroid borders to photos." else "Tambahkan bingkai galeri atau Polaroid rapi di sekeliling foto."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val out = ImageEngine.addBorderFrame(src, input.getFloat("pad", 6f).toInt(), input.getString("color", "#FFFFFF"), input.getBoolean("polaroid", true))
            val dest = ctx.storage.createOutputFile("IMG_FRAME", "png")
            ImageEngine.saveBitmap(out, dest)
            src.recycle(); out.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Bingkai ditambahkan")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-35"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.TextFields
        override val inputSpec = InputSpec.TextInput("Tulis kutipan atau judul poster...", "Write quote or poster headline...", "Kreativitas Tanpa Batas — HariMu")
        override val params = listOf(
            ToolParam.DropdownParam("style", "Tema Poster", "Poster Theme", listOf("Sunset Amber", "Teal Obsidian", "Editorial Paper"), "Sunset Amber")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Typography Poster Card" else "Poster Tipografi Teks"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Create 1080×1350 quote cards directly from text." else "Ubah teks kutipan menjadi poster tipografi 1080×1350 PNG."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val bmp = ImageEngine.createTypographyPoster(input.textInput, input.getString("style", "Sunset Amber"))
            val dest = ctx.storage.createOutputFile("POSTER", "png")
            ImageEngine.saveBitmap(bmp, dest)
            bmp.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Poster 1080×1350 dibuat")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-36"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Gradient
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.TextParam("startHex", "Warna Awal (#Hex)", "Start Color (#Hex)", "#FF6B2C"),
            ToolParam.TextParam("endHex", "Warna Akhir (#Hex)", "End Color (#Hex)", "#00BFA5"),
            ToolParam.SliderParam("opacity", "Intensitas Gradien (%)", "Gradient Opacity (%)", 10f, 80f, 35f, 5f, "%")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Gradient Overlay" else "Lapisan Gradien Warna"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Blend linear color gradient overlays over photos." else "Lapisi foto dengan efek gradien dua warna yang halus."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val out = ImageEngine.applyGradientOverlay(src, input.getString("startHex", "#FF6B2C"), input.getString("endHex", "#00BFA5"), input.getFloat("opacity", 35f).toInt())
            val dest = ctx.storage.createOutputFile("IMG_GRAD", "png")
            ImageEngine.saveBitmap(out, dest)
            src.recycle(); out.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Gradien diterapkan")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-42"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.Flip
        override val inputSpec = InputSpec.SingleImage
        override val params = listOf(
            ToolParam.DropdownParam("mode", "Efek Pantulan", "Reflection Mode", listOf("Refleksi Bawah", "Cermin Horizontal"), "Refleksi Bawah")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Reflection & Mirror" else "Efek Refleksi & Cermin"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Create water-like bottom reflections or symmetrical mirrors." else "Buat pantulan kaca bawah atau cermin simetris."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            val uri = input.uris.firstOrNull() ?: error("Pilih 1 gambar")
            val src = ImageEngine.decodeBitmapSafe(ctx.appContext, uri)
            val out = ImageEngine.createReflectionEffect(src, input.getString("mode", "Refleksi Bawah"))
            val dest = ctx.storage.createOutputFile("IMG_REFL", "png")
            ImageEngine.saveBitmap(out, dest)
            src.recycle(); out.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), summary = "Efek ${input.getString("mode")} selesai")))
        }.flowOn(Dispatchers.IO)
    },
    object : Tool {
        override val id = "IMG-44"
        override val group = ToolGroup.IMAGE
        override val engineLabel = "NAT"
        override val icon = Icons.Filled.GridOn
        override val inputSpec = InputSpec.MultipleImages
        override val params = listOf(
            ToolParam.SliderParam("cols", "Jumlah Kolom", "Columns", 1f, 8f, 3f, 1f, "")
        )
        override fun title(isEnglish: Boolean) = if (isEnglish) "Sprite Sheet Generator" else "Generator Sprite Sheet"
        override fun description(isEnglish: Boolean) = if (isEnglish) "Pack multiple sprites into a grid PNG + JSON atlas." else "Gabungkan banyak frame gambar menjadi grid PNG + atlas JSON."
        override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> = flow {
            require(input.uris.size >= 2) { "Pilih minimal 2 gambar" }
            val bmps = input.uris.map { ImageEngine.decodeBitmapSafe(ctx.appContext, it, maxDim = 512) }
            val (sheet, atlasJson) = ImageEngine.createSpriteSheet(bmps, input.getFloat("cols", 3f).toInt())
            bmps.forEach { it.recycle() }
            val dest = ctx.storage.createOutputFile("SPRITE", "png")
            ImageEngine.saveBitmap(sheet, dest)
            sheet.recycle()
            emit(ToolProgress.Done(ToolResult(listOf(ToolOutputFile(dest, "image/png", dest.name)), textOutput = atlasJson, summary = "Sprite sheet ${input.uris.size} frame dibuat")))
        }.flowOn(Dispatchers.IO)
    }
)
