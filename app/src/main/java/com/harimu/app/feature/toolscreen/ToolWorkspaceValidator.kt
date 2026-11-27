package com.harimu.app.feature.toolscreen

import android.content.Context
import android.net.Uri
import com.harimu.app.R
import com.harimu.app.core.design.ParamValidation
import com.harimu.app.core.design.ParamValidator
import com.harimu.app.core.design.ParamValidators
import com.harimu.app.core.jobs.JobInputValidator
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

    private val TRIM_RANGE_TOOLS = setOf("VID-01", "VID-21", "AUD-09")

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

        when (tool.inputSpec) {
            InputSpec.None -> Unit
            is InputSpec.TextInput -> {
                if (input.textInput.isBlank()) {
                    return WorkspaceValidationResult(
                        isValid = false,
                        stage = WorkspaceStage.INPUT_MISSING,
                        errorMessage = context.getString(R.string.workspace_state_input_missing_text)
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
