package com.harimu.app.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harimu.app.R
import com.harimu.app.core.toolapi.ToolParam
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Validation result for a parameter.
 */
data class ParamValidation(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val warningMessage: String? = null
) {
    companion object {
        val Valid = ParamValidation(true)
        fun Invalid(error: String) = ParamValidation(false, errorMessage = error)
        fun Warning(warning: String) = ParamValidation(true, warningMessage = warning)
    }
}

/**
 * Validator function type for parameters.
 */
typealias ParamValidator = (Any) -> ParamValidation

/**
 * Parameter with validation support.
 */
data class ValidatedParam<out T : Any>(
    val param: ToolParam,
    val value: T,
    val validator: ParamValidator? = null
) {
    val validation: ParamValidation
        get() = validator?.invoke(value) ?: ParamValidation.Valid
}

/**
 * Shared parameter panel that renders all tool parameters with validation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolParameterPanel(
    params: List<ToolParam>,
    paramValues: Map<String, Any>,
    onParamChange: (String, Any) -> Unit,
    validators: Map<String, ParamValidator> = emptyMap(),
    modifier: Modifier = Modifier,
    isEnglish: Boolean = false
) {
    if (params.isEmpty()) return

    val spacing = HariMuThemeTokens.spacing

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.label_parameters), style = MaterialTheme.typography.titleSmall)

            params.forEach { param ->
                val currentValue = paramValues[param.key] ?: when (param) {
                    is ToolParam.SliderParam -> param.defaultValue
                    is ToolParam.DropdownParam -> param.defaultOption
                    is ToolParam.ToggleParam -> param.defaultValue
                    is ToolParam.TextParam -> param.defaultValue
                }
                val validator = validators[param.key]

                ValidatedParamField(
                    param = param,
                    value = currentValue,
                    validator = validator,
                    onValueChange = { onParamChange(param.key, it) },
                    isEnglish = isEnglish
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValidatedParamField(
    param: ToolParam,
    value: Any,
    validator: ParamValidator?,
    onValueChange: (Any) -> Unit,
    isEnglish: Boolean
) {
    val spacing = HariMuThemeTokens.spacing
    val validation = validator?.invoke(value) ?: ParamValidation.Valid

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        when (param) {
            is ToolParam.SliderParam -> {
                val floatValue = (value as? Number)?.toFloat() ?: param.defaultValue
                ValidatedSliderParam(
                    param = param,
                    value = floatValue,
                    validation = validation,
                    onValueChange = onValueChange,
                    isEnglish = isEnglish
                )
            }
            is ToolParam.DropdownParam -> {
                val stringValue = value.toString()
                ValidatedDropdownParam(
                    param = param,
                    value = stringValue,
                    validation = validation,
                    onValueChange = onValueChange,
                    isEnglish = isEnglish
                )
            }
            is ToolParam.ToggleParam -> {
                val boolValue = (value as? Boolean) ?: param.defaultValue
                ValidatedToggleParam(
                    param = param,
                    value = boolValue,
                    validation = validation,
                    onValueChange = onValueChange,
                    isEnglish = isEnglish
                )
            }
            is ToolParam.TextParam -> {
                val stringValue = value.toString()
                ValidatedTextParam(
                    param = param,
                    value = stringValue,
                    validation = validation,
                    onValueChange = onValueChange,
                    isEnglish = isEnglish
                )
            }
        }

        // Validation message
        if (!validation.isValid) {
            ValidationMessage(message = validation.errorMessage!!, tone = ValidationTone.Error)
        } else if (validation.warningMessage != null) {
            ValidationMessage(message = validation.warningMessage!!, tone = ValidationTone.Warning)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValidatedSliderParam(
    param: ToolParam.SliderParam,
    value: Float,
    validation: ParamValidation,
    onValueChange: (Any) -> Unit,
    isEnglish: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEnglish) param.labelEn else param.labelId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(HariMuThemeTokens.spacing.xs))
            Text(
                text = if (param.step >= 1f) {
                    "${value.roundToInt()}${param.unit}"
                } else {
                    "${String.format(Locale.US, "%.2f", value)}${param.unit}"
                },
                style = MonoTextStyle.copy(color = if (!validation.isValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            )
        }
        Slider(
            value = value.coerceIn(param.min, param.max),
            onValueChange = { raw ->
                val snapped = ((raw / param.step).roundToInt() * param.step).coerceIn(param.min, param.max)
                onValueChange(snapped)
            },
            valueRange = param.min..param.max,
            enabled = validation.isValid,
            colors = SliderDefaults.colors(
                thumbColor = if (!validation.isValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                activeTrackColor = if (!validation.isValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                disabledThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                disabledActiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValidatedDropdownParam(
    param: ToolParam.DropdownParam,
    value: String,
    validation: ParamValidation,
    onValueChange: (Any) -> Unit,
    isEnglish: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.xs)
    ) {
        Text(
            text = if (isEnglish) param.labelEn else param.labelId,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.xs)
        ) {
            param.options.forEach { option ->
                val isSelected = value == option
                FilterChip(
                    selected = isSelected,
                    onClick = { onValueChange(option) },
                    enabled = validation.isValid,
                    label = {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = HariMuThemeTokens.shapes.chip,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = if (!validation.isValid)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = if (!validation.isValid)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = validation.isValid,
                        selected = isSelected,
                        borderColor = if (!validation.isValid) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.borders.subtleAlpha),
                        selectedBorderColor = if (!validation.isValid) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValidatedToggleParam(
    param: ToolParam.ToggleParam,
    value: Boolean,
    validation: ParamValidation,
    onValueChange: (Any) -> Unit,
    isEnglish: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .toggleable(
                value = value,
                enabled = validation.isValid,
                role = Role.Switch,
                onValueChange = { onValueChange(it) }
            )
            .padding(vertical = HariMuThemeTokens.spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isEnglish) param.labelEn else param.labelId,
                style = MaterialTheme.typography.bodyMedium,
                color = if (!validation.isValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(HariMuThemeTokens.spacing.sm))
        Switch(
            checked = value,
            onCheckedChange = if (validation.isValid) { checked -> onValueChange(checked) } else null,
            enabled = validation.isValid
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValidatedTextParam(
    param: ToolParam.TextParam,
    value: String,
    validation: ParamValidation,
    onValueChange: (Any) -> Unit,
    isEnglish: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = !validation.isValid,
        label = { Text(if (isEnglish) param.labelEn else param.labelId) },
        placeholder = {
            if (param.placeholderId.isNotBlank() || param.placeholderEn.isNotBlank()) {
                Text(if (isEnglish) param.placeholderEn else param.placeholderId)
            }
        },
        supportingText = if (!validation.isValid) {
            { Text(validation.errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        } else if (validation.warningMessage != null) {
            { Text(validation.warningMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        shape = HariMuThemeTokens.shapes.control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedBorderColor = if (!validation.isValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = if (!validation.isValid)
                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.borders.defaultAlpha),
            errorContainerColor = MaterialTheme.colorScheme.errorContainer,
            errorLabelColor = MaterialTheme.colorScheme.onErrorContainer
        )
    )
}

/**
 * Validation message display.
 */
enum class ValidationTone { Error, Warning, Info }

@Composable
private fun ValidationMessage(message: String, tone: ValidationTone) {
    val (icon, color) = when (tone) {
        ValidationTone.Error -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        ValidationTone.Warning -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
        ValidationTone.Info -> Icons.Filled.Info to MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text = message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * Common parameter validators.
 */
object ParamValidators {
    fun required(message: String = "This field is required"): ParamValidator = { value ->
        when (value) {
            is String -> if (value.isBlank()) ParamValidation.Invalid(message) else ParamValidation.Valid
            is Boolean -> if (!value) ParamValidation.Invalid(message) else ParamValidation.Valid
            else -> ParamValidation.Valid
        }
    }

    fun minLength(min: Int, message: String = "Must be at least $min characters"): ParamValidator = { value ->
        if (value is String && value.length < min) ParamValidation.Invalid(message) else ParamValidation.Valid
    }

    fun maxLength(max: Int, message: String = "Must be at most $max characters"): ParamValidator = { value ->
        if (value is String && value.length > max) ParamValidation.Invalid(message) else ParamValidation.Valid
    }

    fun range(min: Number, max: Number, message: String = "Value must be between $min and $max"): ParamValidator = { value ->
        if (value is Number) {
            val v = value.toDouble()
            if (v < min.toDouble() || v > max.toDouble()) ParamValidation.Invalid(message) else ParamValidation.Valid
        } else ParamValidation.Valid
    }

    fun regex(pattern: String, message: String = "Invalid format"): ParamValidator = { value ->
        if (value is String && !value.matches(pattern.toRegex())) ParamValidation.Invalid(message) else ParamValidation.Valid
    }

    fun email(): ParamValidator = regex(
        pattern = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
        message = "Invalid email address"
    )

    fun hexColor(): ParamValidator = regex(
        pattern = "^#[0-9A-Fa-f]{6}$",
        message = "Must be a valid hex color (e.g., #FF6B2C)"
    )

    fun positiveNumber(message: String = "Must be a positive number"): ParamValidator = { value ->
        if (value is Number && value.toDouble() <= 0) ParamValidation.Invalid(message) else ParamValidation.Valid
    }

    fun nonNegativeNumber(message: String = "Must be zero or positive"): ParamValidator = { value ->
        if (value is Number && value.toDouble() < 0) ParamValidation.Invalid(message) else ParamValidation.Valid
    }

    fun combine(vararg validators: ParamValidator): ParamValidator = { value ->
        validators.map { it(value) }.firstOrNull { !it.isValid || it.warningMessage != null }
            ?: ParamValidation.Valid
    }

    fun warnIf(condition: Boolean, warning: String): ParamValidator = { value ->
        if (condition) ParamValidation.Warning(warning) else ParamValidation.Valid
    }

    fun warnIf(predicate: (Any) -> Boolean, warning: String): ParamValidator = { value ->
        if (predicate(value)) ParamValidation.Warning(warning) else ParamValidation.Valid
    }
}