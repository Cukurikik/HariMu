package com.harimu.app.core.toolapi

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import com.harimu.app.R
import com.harimu.app.core.files.FileStorageManager
import kotlinx.coroutines.flow.Flow
import java.io.File

enum class ToolGroup(
    val titleRes: Int,
    val subtitleRes: Int,
    val titleId: String,
    val titleEn: String,
    val subtitleId: String,
    val subtitleEn: String
) {
    CORE(
        R.string.group_core_title,
        R.string.group_core_subtitle,
        "Core",
        "Core",
        "Perekam, TTS, Key Vault & Backup",
        "Recorder, TTS, Key Vault & Backup"
    ),
    VIDEO(
        R.string.group_video_title,
        R.string.group_video_subtitle,
        "Video Forge",
        "Video Forge",
        "Potong, gabung, ekstrak audio & frame",
        "Trim, merge, extract audio & frames"
    ),
    AUDIO(
        R.string.group_audio_title,
        R.string.group_audio_subtitle,
        "Audio Forge",
        "Audio Forge",
        "Mastering, EQ 10-band, 8D, Reverb & Visualizer",
        "Mastering, 10-band EQ, 8D, Reverb & Visualizer"
    ),
    IMAGE(
        R.string.group_image_title,
        R.string.group_image_subtitle,
        "Image Forge",
        "Image Forge",
        "Crop, filter, EXIF, palet & ikon",
        "Crop, filters, EXIF, palette & icons"
    ),
    PDF(
        R.string.group_pdf_title,
        R.string.group_pdf_subtitle,
        "PDF Vault",
        "PDF Vault",
        "Gabung, pisah, putar, watermark & banding",
        "Merge, split, rotate, watermark & compare"
    ),
    CONVERTER(
        R.string.group_converter_title,
        R.string.group_converter_subtitle,
        "Universal Converter",
        "Universal Converter",
        "Arsip ZIP/AES, JSON/XML, 3D & kode",
        "ZIP/AES archives, JSON/XML, 3D & code"
    )
}

sealed interface InputSpec {
    data object None : InputSpec
    data class TextInput(val hintId: String, val hintEn: String, val sample: String = "") : InputSpec
    data object SingleImage : InputSpec
    data object MultipleImages : InputSpec
    data object SingleVideo : InputSpec
    data object MultipleVideos : InputSpec
    data object SingleAudio : InputSpec
    data object MultipleAudios : InputSpec
    data object SinglePdf : InputSpec
    data object MultiplePdfs : InputSpec
    data object VideoAndAudio : InputSpec
    data object VideoAndImage : InputSpec
    data object VideoAndSubtitle : InputSpec
    data object VideoAndMedia : InputSpec
    data object AudioAndImage : InputSpec
    data object AudioAndWatermarkAudio : InputSpec
    data object ZipFile : InputSpec
    data object AnyFile : InputSpec
}

sealed interface ToolParam {
    val key: String
    val labelId: String
    val labelEn: String

    data class SliderParam(
        override val key: String,
        override val labelId: String,
        override val labelEn: String,
        val min: Float,
        val max: Float,
        val defaultValue: Float,
        val step: Float = 1f,
        val unit: String = ""
    ) : ToolParam

    data class DropdownParam(
        override val key: String,
        override val labelId: String,
        override val labelEn: String,
        val options: List<String>,
        val defaultOption: String
    ) : ToolParam

    data class ToggleParam(
        override val key: String,
        override val labelId: String,
        override val labelEn: String,
        val defaultValue: Boolean
    ) : ToolParam

    data class TextParam(
        override val key: String,
        override val labelId: String,
        override val labelEn: String,
        val defaultValue: String,
        val placeholderId: String = "",
        val placeholderEn: String = ""
    ) : ToolParam
}

data class ToolOutputFile(
    val file: File,
    val mimeType: String,
    val displayName: String,
    val summary: String = ""
)

data class ToolResult(
    val outputs: List<ToolOutputFile> = emptyList(),
    val textOutput: String? = null,
    val summary: String
)

sealed interface ToolProgress {
    data object Queued : ToolProgress
    data class Running(val percent: Int, val message: String) : ToolProgress
    data class Done(val result: ToolResult) : ToolProgress
    data class Failed(val error: String) : ToolProgress
}

data class ToolInput(
    val uris: List<Uri> = emptyList(),
    val secondaryUri: Uri? = null,
    val textInput: String = "",
    val params: Map<String, Any> = emptyMap()
) {
    fun getString(key: String, default: String = ""): String =
        params[key]?.toString() ?: default

    fun getFloat(key: String, default: Float = 0f): Float =
        (params[key] as? Number)?.toFloat() ?: default

    fun getInt(key: String, default: Int = 0): Int =
        (params[key] as? Number)?.toInt() ?: default

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        params[key] as? Boolean ?: default
}

class ToolContext(
    val appContext: Context,
    val storage: FileStorageManager,
    val isEnglish: Boolean
)

data class ModelSpec(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val sizeLabel: String,
    val sha256: String = ""
)

enum class DedicatedScreenRoute {
    VIDEO_EDITOR,
    VOICE_RECORDER,
    IMAGE_MATRIX,
    EQUALIZER,
    AUDIO_STUDIO,
    KEY_VAULT,
    WORKSPACE_BACKUP,
    SETTINGS
}


interface Tool {
    val id: String
    val group: ToolGroup
    val engineLabel: String
    val icon: ImageVector
    val inputSpec: InputSpec
    val params: List<ToolParam>
    val titleRes: Int get() = 0
    val descriptionRes: Int get() = 0
    val requiresNetwork: Boolean get() = false
    val requiresModel: ModelSpec? get() = null
    val dedicatedRoute: DedicatedScreenRoute? get() = null
    val isStub: Boolean get() = false
    val plannedPhase: String get() = ""
    val aliasOfId: String? get() = null

    fun title(isEnglish: Boolean): String
    fun description(isEnglish: Boolean): String
    suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress>
}
