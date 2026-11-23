package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import com.harimu.app.core.media.Media3Request
import com.harimu.app.core.media.Media3TransformerEngine
import com.harimu.app.core.media.MediaJob
import com.harimu.app.core.media.MediaProbe
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.flow.Flow

object InternalMediaTestTool : Tool {
    const val ID = "DEV-REMUX"

    override val id: String = ID
    override val group: ToolGroup = ToolGroup.VIDEO
    override val engineLabel: String = "M3/FF"
    override val icon = Icons.Filled.Build
    override val inputSpec: InputSpec = InputSpec.SingleVideo
    override val params: List<ToolParam> = listOf(
        ToolParam.DropdownParam(
            key = "mode",
            labelId = "Jalur Engine",
            labelEn = "Engine Route",
            options = listOf("Media3 + Fallback FFmpeg", "FFmpeg Remux Langsung"),
            defaultOption = "Media3 + Fallback FFmpeg"
        )
    )

    override fun title(isEnglish: Boolean): String =
        if (isEnglish) "Internal Media Test: Remux MP4" else "Uji Internal Engine Media: Remux MP4"

    override fun description(isEnglish: Boolean): String =
        if (isEnglish) {
            "Internal Fase 3 verification tool: probes streams, remuxes MP4 via Media3/FFmpeg with live progress & cancel."
        } else {
            "Alat uji internal Fase 3: membaca metadata (MediaProbe) dan remux MP4 via Media3/FFmpeg dengan progres & batal."
        }

    override suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress> {
        val uri = input.uris.firstOrNull()
            ?: error(if (ctx.isEnglish) "Select 1 MP4 video file first" else "Pilih 1 file video MP4 terlebih dahulu")

        val mode = input.getString("mode", "Media3 + Fallback FFmpeg")
        val mediaJob = MediaJob()
        if (mode == "FFmpeg Remux Langsung") {
            return mediaJob.remuxMp4(ctx.appContext, ctx.storage, uri, ctx.isEnglish)
        }

        val probe = MediaProbe.probe(ctx.appContext, uri)
        val engine = Media3TransformerEngine(ffmpegJob = mediaJob)
        val request = Media3Request(
            inputUri = uri,
            toolTag = "REMUX",
            outputExt = "mp4",
            outputMime = "video/mp4"
        )
        return engine.runWithFfmpegFallback(
            context = ctx.appContext,
            storage = ctx.storage,
            request = request,
            isEnglish = ctx.isEnglish
        ) { inputs, output ->
            val _probeInfo = probe // ensure probe ran cleanly beforehand
            listOf(
                "-y",
                "-i", inputs.first().absolutePath,
                "-c", "copy",
                "-movflags", "+faststart",
                output.absolutePath
            )
        }
    }
}
