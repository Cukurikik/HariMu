package com.harimu.app.core.media

import android.content.Context
import android.net.Uri
import com.harimu.app.core.files.ModelManager
import com.harimu.app.core.files.ModelRegistry
import com.harimu.app.core.toolapi.ModelSpec
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Target audio stems produced by AUD-20 (AI Stem Splitter) and AI vocal separation (AUD-04).
 * Inference (ONNX Demucs / Cloudflare AI Gateway) is scheduled for Phase 11.
 */
enum class AudioStemType(val id: String, val labelId: String, val labelEn: String) {
    VOCALS("vocals", "Vokal", "Vocals"),
    DRUMS("drums", "Drum", "Drums"),
    BASS("bass", "Bass", "Bass"),
    OTHER("other", "Instrumen Lain", "Other")
}

enum class StemExecutionMode {
    ON_DEVICE_MODEL,
    CLOUD_GATEWAY
}

data class StemSplitRequest(
    val inputUri: Uri,
    val requestedStems: Set<AudioStemType> = AudioStemType.entries.toSet(),
    val executionMode: StemExecutionMode = StemExecutionMode.ON_DEVICE_MODEL,
    val outputFormat: AudioFormatSpec = AudioToolMath.FORMAT_SPECS.getValue("WAV")
)

data class StemSplitOutputs(
    val stems: Map<AudioStemType, ToolOutputFile>,
    val executionMode: StemExecutionMode,
    val elapsedMs: Long
)

sealed interface StemReadiness {
    data object Ready : StemReadiness
    data class MissingModel(val model: ModelSpec) : StemReadiness
    data class RequiresCloudConfig(val gatewayPath: String) : StemReadiness
    data class InferenceDeferred(val plannedPhase: String, val reasonId: String, val reasonEn: String) : StemReadiness
}

interface StemSplitterEngine {
    val engineId: String
    val requiredModel: ModelSpec
    val supportedStems: Set<AudioStemType>

    suspend fun checkReadiness(context: Context, mode: StemExecutionMode): StemReadiness
    fun splitStems(ctx: ToolContext, request: StemSplitRequest): Flow<ToolProgress>
}

/**
 * Phase 7 contract implementation for AUD-20.
 * Checks model availability cleanly without mocking inference results; real ONNX/Cloud execution lands in Phase 11.
 */
object DeferredStemSplitterEngine : StemSplitterEngine {
    override val engineId: String = "demucs_lite_p11"
    override val requiredModel: ModelSpec = ModelRegistry.demucsLite
    override val supportedStems: Set<AudioStemType> = AudioStemType.entries.toSet()

    override suspend fun checkReadiness(context: Context, mode: StemExecutionMode): StemReadiness {
        return when (mode) {
            StemExecutionMode.ON_DEVICE_MODEL -> {
                val manager = ModelManager(context)
                if (!manager.isDownloaded(requiredModel.id)) {
                    StemReadiness.MissingModel(requiredModel)
                } else {
                    StemReadiness.InferenceDeferred(
                        plannedPhase = "P11",
                        reasonId = "Model ${requiredModel.name} siap, namun runtime inferensi ONNX Demucs akan diaktifkan pada Fase 11.",
                        reasonEn = "Model ${requiredModel.name} is present, and ONNX Demucs inference runtime activates in Phase 11."
                    )
                }
            }
            StemExecutionMode.CLOUD_GATEWAY -> StemReadiness.RequiresCloudConfig("/v1/audio-stems")
        }
    }

    override fun splitStems(ctx: ToolContext, request: StemSplitRequest): Flow<ToolProgress> = flow {
        val msg = when (val readiness = checkReadiness(ctx.appContext, request.executionMode)) {
            StemReadiness.Ready -> ""
            is StemReadiness.MissingModel -> if (ctx.isEnglish) {
                "Model ${readiness.model.name} (${readiness.model.sizeLabel}) is not downloaded. Stem inference activates in Phase 11."
            } else {
                "Model ${readiness.model.name} (${readiness.model.sizeLabel}) belum diunduh. Inferensi stem diaktifkan di Fase 11."
            }
            is StemReadiness.RequiresCloudConfig -> if (ctx.isEnglish) {
                "Cloud Stem Splitter (${readiness.gatewayPath}) requires Cloudflare AI Gateway configuration in Phase 11/14."
            } else {
                "Stem Splitter Cloud (${readiness.gatewayPath}) membutuhkan konfigurasi Cloudflare AI Gateway pada Fase 11/14."
            }
            is StemReadiness.InferenceDeferred -> if (ctx.isEnglish) readiness.reasonEn else readiness.reasonId
        }
        emit(ToolProgress.Failed(msg))
    }
}
