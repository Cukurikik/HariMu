package com.harimu.app.core.toolapi

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import com.harimu.app.R
import com.harimu.app.core.ai.AiClient
import com.harimu.app.core.files.FileStorageManager
import com.harimu.app.core.files.ModelManager
import kotlinx.coroutines.flow.Flow
import java.io.File

enum class ToolGroup(
    val titleRes: Int,
    val subtitleId: String,
    val subtitleEn: String
) {
    CORE(
        titleRes = R.string.group_core,
        subtitleId = "Editor, perekam, keamanan Vault, AI Gateway & layanan publik",
        subtitleEn = "Editors, recorders, Vault security, AI Gateway & public tools"
    ),
    VIDEO(
        titleRes = R.string.group_video,
        subtitleId = "Potong, gabung, konversi, stabilisasi & efek video",
        subtitleEn = "Trim, merge, transcode, stabilize & video effects"
    ),
    AUDIO(
        titleRes = R.string.group_audio,
        subtitleId = "DAW multitrack, mastering, EQ, reduksi derau & konverter",
        subtitleEn = "Multitrack DAW, mastering, EQ, noise reduction & converters"
    ),
    IMAGE(
        titleRes = R.string.group_image,
        subtitleId = "Kanvas berlapis, segmentasi AI, LUT, EXIF & pengoptimal gambar",
        subtitleEn = "Multi-layer canvas, AI segmentation, LUT, EXIF & optimizers"
    ),
    PDF(
        titleRes = R.string.group_pdf,
        subtitleId = "Gabung, pisah, redaksi permanen, OCR 8 bahasa & formulir",
        subtitleEn = "Merge, split, permanent redaction, 8-lang OCR & forms"
    ),
    CONVERTER(
        titleRes = R.string.group_converter,
        subtitleId = "28 konverter media, dokumen, arsip aman, font, data & 3D",
        subtitleEn = "28 converters for media, docs, secure archives, fonts, data & 3D"
    ),
    NUSANTARA(
        titleRes = R.string.group_nusantara,
        subtitleId = "Gempa, cuaca BMKG, wilayah, Al-Qur'an, doa & data publik Indonesia",
        subtitleEn = "Quakes, BMKG weather, regions, Qur'an, prayers & public data"
    );

    fun subtitle(isEnglish: Boolean): String = if (isEnglish) subtitleEn else subtitleId
}

enum class DedicatedScreenRoute {
    VIDEO_EDITOR,
    VOICE_RECORDER,
    SCREEN_RECORDER,
    IMAGE_MATRIX,
    EQUALIZER,
    AUDIO_STUDIO,
    KEY_VAULT,
    WORKSPACE_BACKUP,
    PRAYER_TIMES,
    AREA_LOOKUP,
    EARTHQUAKE,
    BMKG_WEATHER,
    REGIONS,
    HEROES,
    VOLCANOES,
    DUAS,
    SCHOOLS,
    SUNDANESE,
    CROSSWORD,
    QURAN,
    FOOTBALL,
    PESANTREN,
    SETTINGS
}

sealed interface InputSpec {
    data object None : InputSpec
    data object SingleImage : InputSpec
    data object MultipleImages : InputSpec
    data object SingleVideo : InputSpec
    data object MultipleVideos : InputSpec
    data object SingleAudio : InputSpec
    data object MultipleAudios : InputSpec
    data object SinglePdf : InputSpec
    data object MultiplePdfs : InputSpec
    data object ZipFile : InputSpec
    data object AnyFile : InputSpec
    data object VideoAndAudio : InputSpec
    data object VideoAndImage : InputSpec
    data object VideoAndSubtitle : InputSpec
    data object VideoAndMedia : InputSpec
    data object AudioAndImage : InputSpec
    data object AudioAndWatermarkAudio : InputSpec
    data object PdfAndImage : InputSpec
    data class TextInput(val hintId: String, val hintEn: String) : InputSpec
}

sealed class ToolParam {
    abstract val key: String
    abstract val labelId: String
    abstract val labelEn: String

    data class SliderParam(
        override val key: String,
        override val labelId: String,
        override val labelEn: String,
        val min: Float,
        val max: Float,
        val defaultValue: Float,
        val step: Float = 1f,
        val unit: String = ""
    ) : ToolParam()

    data class DropdownParam(
        override val key: String,
        override val labelId: String,
        override val labelEn: String,
        val options: List<String>,
        val defaultOption: String = options.firstOrNull().orEmpty()
    ) : ToolParam()

    data class ToggleParam(
        override val key: String,
        override val labelId: String,
        override val labelEn: String,
        val defaultValue: Boolean = false
    ) : ToolParam()

    data class TextParam(
        override val key: String,
        override val labelId: String,
        override val labelEn: String,
        val defaultValue: String = "",
        val placeholderId: String = "",
        val placeholderEn: String = ""
    ) : ToolParam()
}

data class ModelSpec(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val sizeLabel: String,
    val sha256: String = ""
)

data class ToolContext(
    val appContext: Context,
    val storage: FileStorageManager,
    val isEnglish: Boolean,
    val ai: AiClient? = null,
    val models: ModelManager? = null
)

data class ToolInput(
    val uris: List<Uri> = emptyList(),
    val secondaryUri: Uri? = null,
    val textInput: String = "",
    val params: Map<String, Any> = emptyMap()
) {
    fun getString(key: String, default: String = ""): String =
        params[key]?.toString() ?: default

    fun getFloat(key: String, default: Float = 0f): Float =
        when (val v = params[key]) {
            is Number -> v.toFloat()
            is String -> v.toFloatOrNull() ?: default
            else -> default
        }

    fun getInt(key: String, default: Int = 0): Int =
        when (val v = params[key]) {
            is Number -> v.toInt()
            is String -> v.toDoubleOrNull()?.toInt() ?: default
            else -> default
        }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        when (val v = params[key]) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> default
        }
}

data class ToolOutputFile(
    val file: File,
    val mimeType: String,
    val displayName: String = file.name
)

data class ToolResult(
    val outputs: List<ToolOutputFile> = emptyList(),
    val summary: String,
    val textOutput: String? = null
)

sealed interface ToolProgress {
    data object Queued : ToolProgress
    data class Running(val percent: Int, val message: String) : ToolProgress
    data class Done(val result: ToolResult) : ToolProgress
    data class Failed(val error: String) : ToolProgress
}

interface Tool {
    val id: String
    val group: ToolGroup
    val icon: ImageVector
    val engineLabel: String
    val inputSpec: InputSpec
    val params: List<ToolParam> get() = emptyList()
    val isStub: Boolean get() = false
    val requiresNetwork: Boolean get() = false
    val requiresModel: ModelSpec? get() = null
    val dedicatedRoute: DedicatedScreenRoute? get() = null
    val heavyBackground: Boolean get() = false

    fun title(isEnglish: Boolean): String
    fun description(isEnglish: Boolean): String
    fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress>
}
