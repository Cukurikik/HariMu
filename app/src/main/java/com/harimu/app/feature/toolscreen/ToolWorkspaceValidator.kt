package com.harimu.app.feature.toolscreen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.harimu.app.R
import com.harimu.app.core.design.ParamValidation
import com.harimu.app.core.design.ParamValidator
import com.harimu.app.core.design.ParamValidators
import com.harimu.app.core.jobs.JobInputValidator
import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.ModelSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolParam

enum class WorkspaceStage {
    INPUT_MISSING,
    INVALID_INPUT,
    READY
}

data class WorkspaceReadiness(
    val stage: WorkspaceStage,
    val title: String,
    val detail: String,
    val isReadyToRun: Boolean,
    val needsNotificationPermission: Boolean = false,
    val needsUploadConsent: Boolean = false,
    val missingModel: ModelSpec? = null
)

data class WorkspaceValidationResult(
    val isValid: Boolean,
    val stage: WorkspaceStage = WorkspaceStage.READY,
    val errorMessage: String? = null
)

object ToolWorkspaceValidator {

    private val MIN_TWO_FILE_TOOLS = setOf(
        "PDF-01", "PDF-20", "VID-29", "IMG-09", "IMG-32", "IMG-44", "IMG-45"
    )

    private val TRIM_RANGE_TOOLS = setOf("VID-01", "VID-15", "VID-17", "VID-21", "AUD-09")

    fun requiresSecondaryUri(tool: Tool, paramValues: Map<String, Any>): Boolean = when (tool.inputSpec) {
        InputSpec.VideoAndAudio,
        InputSpec.VideoAndImage,
        InputSpec.VideoAndSubtitle -> true
        InputSpec.PdfAndImage -> paramValues["mode"]?.toString() == "Gambar"
        else -> false
    }

    fun minPrimaryFiles(tool: Tool): Int = when {
        tool.id in MIN_TWO_FILE_TOOLS -> 2
        tool.inputSpec == InputSpec.None || tool.inputSpec is InputSpec.TextInput -> 0
        else -> 1
    }

    fun buildParamValidators(context: Context, tool: Tool): Map<String, ParamValidator> {
        val validators = mutableMapOf<String, ParamValidator>()
        val hexError = context.getString(R.string.param_err_hex_color)
        val emailError = context.getString(R.string.param_err_email)
        val invalidOptionError = context.getString(R.string.param_err_invalid_option)
        val aesWarn = context.getString(R.string.param_err_aes_password)
        val pdfWarn = context.getString(R.string.param_err_pdf_password)

        tool.params.forEach { param ->
            when (param) {
                is ToolParam.SliderParam -> {
                    validators[param.key] = { value ->
                        if (value is Number) {
                            val v = value.toFloat()
                            if (v < param.min || v > param.max) {
                                ParamValidation.Invalid(
                                    context.getString(
                                        R.string.param_err_range,
                                        param.min.toString(),
                                        param.max.toString()
                                    )
                                )
                            } else {
                                ParamValidation.Valid
                            }
                        } else {
                            ParamValidation.Valid
                        }
                    }
                }
                is ToolParam.TextParam -> {
                    val lowerKey = param.key.lowercase()
                    if (lowerKey == "password" || lowerKey.contains("pass") || lowerKey.contains("pwd")) {
                        if (tool.id == "CNV-18") {
                            validators[param.key] = { value ->
                                val s = value.toString()
                                if (s.isNotEmpty() && s.length < 8) {
                                    ParamValidation.Warning(aesWarn)
                                } else {
                                    ParamValidation.Valid
                                }
                            }
                        } else if (tool.id == "PDF-10" || tool.id == "CNV-16") {
                            validators[param.key] = { value ->
                                val s = value.toString()
                                if (s.isNotEmpty() && s.length < 4) {
                                    ParamValidation.Warning(pdfWarn)
                                } else {
                                    ParamValidation.Valid
                                }
                            }
                        }
                    } else if (param.key == "color" || param.key.endsWith("Hex") || param.key == "shadowColor" || param.key == "glowColor" || param.key == "warm") {
                        validators[param.key] = ParamValidators.regex(
                            pattern = "^#[0-9A-Fa-f]{6}$",
                            message = hexError
                        )
                    } else if (param.key == "email") {
                        validators[param.key] = ParamValidators.regex(
                            pattern = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                            message = emailError
                        )
                    }
                }
                is ToolParam.DropdownParam -> {
                    validators[param.key] = { value ->
                        if (value.toString() !in param.options) {
                            ParamValidation.Invalid(invalidOptionError)
                        } else {
                            ParamValidation.Valid
                        }
                    }
                }
                is ToolParam.ToggleParam -> Unit
            }
        }
        return validators
    }

    fun validateWorkspaceInput(
        context: Context,
        tool: Tool,
        input: ToolInput,
        checkUriReadability: Boolean = true
    ): WorkspaceValidationResult {
        if (tool.isStub) {
            return WorkspaceValidationResult(isValid = true, stage = WorkspaceStage.READY)
        }

        val isDecodeBase64 = (tool.id == "IMG-25" || tool.id == "CNV-12") &&
            input.getString("mode", "Encode").startsWith("Decode")

        when {
            tool.inputSpec == InputSpec.None -> Unit
            tool.inputSpec is InputSpec.TextInput -> {
                if (input.textInput.isBlank() && input.uris.isEmpty()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INPUT_MISSING,
                        errorMessage = context.getString(R.string.workspace_state_input_missing_text)
                    )
                }
                if (input.textInput.isBlank() && checkUriReadability && input.uris.any { !JobInputValidator.isUriReadable(context, it) }) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.workspace_state_invalid_uri)
                    )
                }
            }
            isDecodeBase64 -> {
                val hasTextData = input.getString("data", "").isNotBlank() || input.textInput.isNotBlank()
                if (!hasTextData && input.uris.isEmpty()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INPUT_MISSING,
                        errorMessage = context.getString(R.string.workspace_state_input_missing_text)
                    )
                }
                if (!hasTextData && checkUriReadability && input.uris.any { !JobInputValidator.isUriReadable(context, it) }) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.workspace_state_invalid_uri)
                    )
                }
            }
            else -> {
                val minFiles = minPrimaryFiles(tool)
                if (input.uris.isEmpty()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INPUT_MISSING,
                        errorMessage = context.getString(R.string.workspace_state_input_missing_file)
                    )
                }
                if (input.uris.size < minFiles) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INPUT_MISSING,
                        errorMessage = context.getString(
                            R.string.workspace_state_input_missing_files_min,
                            minFiles,
                            input.uris.size
                        )
                    )
                }
                if (tool.id == "IMG-45" && input.uris.size > 40) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.workspace_state_invalid_param)
                    )
                }
                if (requiresSecondaryUri(tool, input.params) && input.secondaryUri == null) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INPUT_MISSING,
                        errorMessage = context.getString(R.string.workspace_state_input_missing_secondary)
                    )
                }
                if (checkUriReadability) {
                    val primaryReadable = input.uris.all { JobInputValidator.isUriReadable(context, it) }
                    val secondaryReadable = input.secondaryUri == null ||
                        JobInputValidator.isUriReadable(context, input.secondaryUri)
                    if (!primaryReadable || !secondaryReadable) {
                        return WorkspaceValidationResult(
                            isValid = false,
                            stage = WorkspaceStage.INVALID_INPUT,
                            errorMessage = context.getString(R.string.workspace_state_invalid_uri)
                        )
                    }
                }
            }
        }

        // Tool-specific parameter checks matching actual engine preconditions
        if (tool.id == "IMG-39") {
            val mode = input.getString("mode", "Preset bawaan")
            if (mode.startsWith("File") && input.getString("cubeText").isBlank()) {
                return WorkspaceValidationResult(
                    isValid = false,
                    stage = WorkspaceStage.INVALID_INPUT,
                    errorMessage = context.getString(R.string.workspace_state_invalid_param)
                )
            }
            if (mode.startsWith("Strip") && input.uris.size < 2) {
                return WorkspaceValidationResult(
                    isValid = false,
                    stage = WorkspaceStage.INPUT_MISSING,
                    errorMessage = context.getString(
                        R.string.workspace_state_input_missing_files_min,
                        2,
                        input.uris.size
                    )
                )
            }
        }

        if (tool.id in TRIM_RANGE_TOOLS) {
            val checkTrim = tool.id != "VID-21" || input.getString("mode") == "Rentang Pilihan"
            if (checkTrim) {
                val startSec = input.getFloat("startSec", 0f)
                val endSec = input.getFloat("endSec", 0f)
                if (endSec > 0f && endSec <= startSec) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(
                            R.string.workspace_state_invalid_trim_range,
                            endSec,
                            startSec
                        )
                    )
                }
            }
        }

        when (tool.id) {
            "PDF-04" -> {
                val order = PdfVaultEngine.parseOrder(input.getString("order", ""), 20_000)
                if (order.isEmpty()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.pdf_err_invalid_order)
                    )
                }
            }
            "PDF-05" -> {
                val pagesRaw = input.getString("pages", "")
                if (pagesRaw.isBlank() || PdfVaultEngine.parsePagesLoose(pagesRaw).isEmpty()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.pdf_err_invalid_pages)
                    )
                }
            }
            "PDF-07" -> {
                val firstUri = input.uris.firstOrNull()
                if (firstUri != null) {
                    val header = if (checkUriReadability) readHeaderBytes(context, firstUri) else null
                    val looksZip = header != null && header.size >= 2 &&
                        header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
                    val displayName = resolveDisplayName(context, firstUri).orEmpty()
                    val invalidDocx = if (header != null && header.isNotEmpty()) {
                        !looksZip
                    } else {
                        displayName.isNotBlank() && !displayName.endsWith(".docx", ignoreCase = true)
                    }
                    if (invalidDocx) {
                        return WorkspaceValidationResult(
                            isValid = false,
                            stage = WorkspaceStage.INVALID_INPUT,
                            errorMessage = context.getString(R.string.pdf_err_docx_only)
                        )
                    }
                }
            }
            "CNV-13" -> {
                val firstUri = input.uris.firstOrNull()
                if (firstUri != null && checkUriReadability) {
                    val header = readHeaderBytes(context, firstUri)
                    if (header != null && header.isNotEmpty()) {
                        val isPdf = header.size >= 5 && header.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))
                        val isZip = header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
                        if (!isPdf && !isZip) {
                            return WorkspaceValidationResult(
                                isValid = false,
                                stage = WorkspaceStage.INVALID_INPUT,
                                errorMessage = context.getString(R.string.converter_honest_cnv13)
                            )
                        }
                    }
                }
            }
            "CNV-18" -> {
                val mode = input.getString("mode", "Enkripsi ZIP AES-256")
                val pwd = input.getString("password", "")
                val isDecrypt = mode.startsWith("Dekripsi")
                if (isDecrypt && pwd.isBlank()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.pdf_err_password_required)
                    )
                }
                if (!isDecrypt && pwd.length < 8) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.param_err_aes_password)
                    )
                }
            }
            "PDF-10" -> {
                if (input.getString("userPwd", "").isBlank() && input.getString("ownerPwd", "").isBlank()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.pdf_err_password_required)
                    )
                }
            }
            "PDF-12" -> {
                if (input.getString("mode", "Teks") != "Gambar" && input.getString("text", "").isBlank()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.pdf_err_watermark_text_required)
                    )
                }
            }
            "PDF-14" -> {
                val terms = input.getString("terms", "")
                    .split(',', ';', '\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (terms.isEmpty()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.pdf_err_redact_terms_required)
                    )
                }
            }
            "PDF-19" -> {
                val anyMeta = listOf("title", "author", "subject", "keywords")
                    .any { input.getString(it, "").isNotBlank() }
                if (!anyMeta) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INVALID_INPUT,
                        errorMessage = context.getString(R.string.pdf_err_metadata_empty)
                    )
                }
            }
        }

        val validators = buildParamValidators(context, tool)
        for (param in tool.params) {
            val validator = validators[param.key] ?: continue
            val value = input.params[param.key] ?: defaultParamValue(param)
            val result = validator(value)
            if (!result.isValid) {
                return WorkspaceValidationResult(
                    isValid = false,
                    stage = WorkspaceStage.INVALID_INPUT,
                    errorMessage = result.errorMessage ?: context.getString(R.string.workspace_state_invalid_param)
                )
            }
        }

        return WorkspaceValidationResult(isValid = true, stage = WorkspaceStage.READY)
    }

    private fun readHeaderBytes(context: Context, uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(16)
            val n = stream.read(buf)
            if (n > 0) buf.copyOf(n) else ByteArray(0)
        }
    }.getOrNull()

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    fun evaluateReadiness(
        context: Context,
        tool: Tool,
        selectedUris: List<Uri>,
        secondaryUri: Uri?,
        textInput: String,
        paramValues: Map<String, Any>,
        usesWorker: Boolean,
        hasNotificationPermission: Boolean,
        hasUploadConsent: Boolean,
        isModelDownloaded: Boolean,
        checkUriReadability: Boolean = true
    ): WorkspaceReadiness {
        val input = ToolInput(
            uris = selectedUris,
            secondaryUri = secondaryUri,
            textInput = textInput,
            params = paramValues
        )
        val validation = validateWorkspaceInput(
            context = context,
            tool = tool,
            input = input,
            checkUriReadability = checkUriReadability
        )
        val missingModel = tool.requiresModel?.takeIf { !isModelDownloaded }
        val needsNotifications = (usesWorker || tool.isStub) && !hasNotificationPermission
        val needsConsent = tool.requiresNetwork && !hasUploadConsent

        return when (validation.stage) {
            WorkspaceStage.INPUT_MISSING -> WorkspaceReadiness(
                stage = WorkspaceStage.INPUT_MISSING,
                title = context.getString(R.string.workspace_state_input_missing_title),
                detail = validation.errorMessage ?: context.getString(R.string.workspace_state_input_missing_file),
                isReadyToRun = false,
                needsNotificationPermission = needsNotifications,
                needsUploadConsent = needsConsent,
                missingModel = missingModel
            )
            WorkspaceStage.INVALID_INPUT -> WorkspaceReadiness(
                stage = WorkspaceStage.INVALID_INPUT,
                title = context.getString(R.string.workspace_state_invalid_title),
                detail = validation.errorMessage ?: context.getString(R.string.workspace_state_invalid_param),
                isReadyToRun = false,
                needsNotificationPermission = needsNotifications,
                needsUploadConsent = needsConsent,
                missingModel = missingModel
            )
            WorkspaceStage.READY -> WorkspaceReadiness(
                stage = WorkspaceStage.READY,
                title = context.getString(R.string.workspace_state_ready_title),
                detail = when {
                    needsConsent -> context.getString(R.string.workspace_consent_needed_body)
                    usesWorker -> context.getString(R.string.workspace_state_ready_worker)
                    else -> context.getString(R.string.workspace_state_ready_in_process)
                },
                isReadyToRun = true,
                needsNotificationPermission = needsNotifications,
                needsUploadConsent = needsConsent,
                missingModel = missingModel
            )
        }
    }

    private fun defaultParamValue(param: ToolParam): Any = when (param) {
        is ToolParam.SliderParam -> param.defaultValue
        is ToolParam.DropdownParam -> param.defaultOption
        is ToolParam.ToggleParam -> param.defaultValue
        is ToolParam.TextParam -> param.defaultValue
    }
}
