package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
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

fun imageTools(): List<Tool> =
    imageForgeBatch1Tools() + imageForgeBatch2Tools() + listOf(
    DedicatedTool(
        id = "IMG-47",
        group = ToolGroup.IMAGE,
        engineLabel = "MAT",
        icon = Icons.Filled.Tune,
        dedicatedRoute = DedicatedScreenRoute.IMAGE_MATRIX,
        titleId = "Image Matrix Studio",
        titleEn = "Image Matrix Studio",
        descId = "Kanvas multilayer (gambar/teks/stiker): transform, urutan, blend, undo/redo, ekspor PNG resolusi penuh.",
        descEn = "Multilayer canvas (image/text/sticker): transform, ordering, blend, undo/redo, full-resolution PNG export."
    ),
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
    }
)
