package com.harimu.app.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filed.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harimu.app.R
import com.harimu.app.core.toolapi.ToolProgress
import kotlinx.coroutines.delay

/**
 * Progress state for tool execution.
 */
sealed interface ToolProgressState {
    data object Idle : ToolProgressState
    data class Queued(val message: String = "") : ToolProgressState
    data class Running(val percent: Int, val message: String) : ToolProgressState
    data class Paused(val percent: Int, val message: String) : ToolProgressState
    data class Done(val result: ToolResultSummary) : ToolProgressState
    data class Failed(val error: String, val isRetryable: Boolean = true) : ToolProgressState
    data class Cancelled : ToolProgressState
}

data class ToolResultSummary(
    val summary: String,
    val outputCount: Int,
    val primaryMimeType: String? = null,
    val hasTextOutput: Boolean = false
)

/**
 * Callbacks for progress interactions.
 */
data class ProgressCallbacks(
    val onCancel: (() -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onPause: (() -> Unit)? = null,
    val onResume: (() -> Unit)? = null
)

/**
 * Main progress view component showing real-time progress with cancellation support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolProgressView(
    state: ToolProgressState,
    callbacks: ProgressCallbacks = ProgressCallbacks(),
    modifier: Modifier = Modifier,
    showDetails: Boolean = true
) {
    when (state) {
        is ToolProgressState.Idle -> Unit
        is ToolProgressState.Queued -> QueuedProgressCard(message = state.message, onCancel = callbacks.onCancel, modifier = modifier)
        is ToolProgressState.Running -> RunningProgressCard(
            percent = state.percent,
            message = state.message,
            onCancel = callbacks.onCancel,
            onPause = callbacks.onPause,
            modifier = modifier,
            showDetails = showDetails
        )
        is ToolProgressState.Paused -> PausedProgressCard(
            percent = state.percent,
            message = state.message,
            onResume = callbacks.onResume,
            onCancel = callbacks.onCancel,
            modifier = modifier
        )
        is ToolProgressState.Done -> DoneProgressCard(
            summary = state.result,
            onRetry = callbacks.onRetry,
            modifier = modifier
        )
        is ToolProgressState.Failed -> FailedProgressCard(
            error = state.error,
            isRetryable = state.isRetryable,
            onRetry = callbacks.onRetry,
            modifier = modifier
        )
        is ToolProgressState.Cancelled -> CancelledProgressCard(onRetry = callbacks.onRetry, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueuedProgressCard(
    message: String,
    onCancel: (() -> Unit)?,
    modifier: Modifier
) {
    val context = LocalContext.current
    val displayMessage = if (message.isBlank()) stringResource(R.string.progress_queued) else message
    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.progress_queued), style = MaterialTheme.typography.titleSmall)
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = displayMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            onCancel?.let {
                HariMuTextAction(text = stringResource(R.string.action_cancel), onClick = it, icon = Icons.Filled.Close)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunningProgressCard(
    percent: Int,
    message: String,
    onCancel: (() -> Unit)?,
    onPause: (() -> Unit)?,
    modifier: Modifier,
    showDetails: Boolean
) {
    val clampedPercent = percent.coerceIn(0, 100)
    val spacing = HariMuThemeTokens.spacing

    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.progress_running_title), style = MaterialTheme.typography.titleSmall)
            Surface(shape = HariMuThemeTokens.shapes.badge, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(text = "$clampedPercent%", style = MonoTextStyle.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
        LinearProgressIndicator(progress = { (clampedPercent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
        if (showDetails) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    onPause?.let {
                        HariMuTextAction(text = stringResource(R.string.action_pause), onClick = it, icon = Icons.Filled.Pause)
                    }
                    onCancel?.let {
                        HariMuTextAction(text = stringResource(R.string.action_cancel), onClick = it, icon = Icons.Filled.Close)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                onCancel?.let {
                    HariMuTextAction(text = stringResource(R.string.action_cancel), onClick = it, icon = Icons.Filled.Close)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PausedProgressCard(
    percent: Int,
    message: String,
    onResume: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    modifier: Modifier
) {
    val clampedPercent = percent.coerceIn(0, 100)
    val spacing = HariMuThemeTokens.spacing

    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.warningContainer,
        borderColor = MaterialTheme.colorScheme.warning.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.progress_paused), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onWarningContainer)
            Surface(shape = HariMuThemeTokens.shapes.badge, color = MaterialTheme.colorScheme.warning) {
                Text(text = "$clampedPercent%", style = MonoTextStyle.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onWarning, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
        LinearProgressIndicator(progress = { (clampedPercent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round, color = MaterialTheme.colorScheme.warning)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onWarningContainer, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                onResume?.let {
                    HariMuPrimaryButton(text = stringResource(R.string.action_resume), onClick = it, icon = Icons.Filled.PlayArrow, modifier = Modifier.minimumInteractiveComponentSize())
                }
                onCancel?.let {
                    HariMuTextAction(text = stringResource(R.string.action_cancel), onClick = it, icon = Icons.Filled.Close)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoneProgressCard(
    summary: ToolResultSummary,
    onRetry: (() -> Unit)?,
    modifier: Modifier
) {
    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = HariMuThemeTokens.semantic.successBorder.copy(alpha = 0.65f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = HariMuThemeTokens.semantic.success, modifier = Modifier.size(HariMuThemeTokens.icons.md))
            Spacer(modifier = Modifier.width(HariMuThemeTokens.spacing.xs))
            Text(text = summary.summary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
        if (summary.outputCount > 1) {
            Text(text = stringResource(R.string.progress_multiple_outputs, summary.outputCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onRetry?.let {
            HariMuTextAction(text = stringResource(R.string.action_run_again), onClick = it, icon = Icons.Filled.Refresh)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FailedProgressCard(
    error: String,
    isRetryable: Boolean,
    onRetry: (() -> Unit)?,
    modifier: Modifier
) {
    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        borderColor = MaterialTheme.colorScheme.error
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(HariMuThemeTokens.icons.md))
            Spacer(modifier = Modifier.width(HariMuThemeTokens.spacing.xs))
            Text(text = stringResource(R.string.error_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        if (isRetryable && onRetry != null) {
            HariMuSecondaryButton(text = stringResource(R.string.action_retry), onClick = onRetry, icon = Icons.Filled.Refresh)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelledProgressCard(
    onRetry: (() -> Unit)?,
    modifier: Modifier
) {
    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(HariMuThemeTokens.icons.md))
            Spacer(modifier = Modifier.width(HariMuThemeTokens.spacing.xs))
            Text(text = stringResource(R.string.progress_cancelled), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(text = stringResource(R.string.progress_cancelled_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        onRetry?.let {
            HariMuSecondaryButton(text = stringResource(R.string.action_run_again), onClick = it, icon = Icons.Filled.Refresh)
        }
    }
}

/**
 * Converts ToolProgress from toolapi to ToolProgressState for UI.
 */
fun ToolProgress.toProgressState(): ToolProgressState = when (this) {
    is ToolProgress.Queued -> ToolProgressState.Queued(message = "")
    is ToolProgress.Running -> ToolProgressState.Running(percent = percent.coerceIn(0, 100), message = message)
    is ToolProgress.Done -> ToolProgressState.Done(
        result = ToolResultSummary(
            summary = result.summary,
            outputCount = result.outputs.size,
            primaryMimeType = result.outputs.firstOrNull()?.mimeType,
            hasTextOutput = result.textOutput != null
        )
    )
    is ToolProgress.Failed -> ToolProgressState.Failed(error = error, isRetryable = true)
}

/**
 * Indeterminate progress spinner for quick operations.
 */
@Composable
fun ToolIndeterminateProgress(
    message: String,
    modifier: Modifier = Modifier
) {
    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.sm)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Step-based progress for multi-stage operations.
 */
@Composable
fun ToolStepProgress(
    steps: List<String>,
    currentStep: Int,
    currentStepProgress: Int = 0,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = HariMuThemeTokens.spacing
    HariMuSectionCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            // Step indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                steps.forEachIndexed { index, step ->
                    val isCompleted = index < currentStep
                    val isCurrent = index == currentStep
                    val isPending = index > currentStep
                    val stepColor = when {
                        isCompleted -> HariMuThemeTokens.semantic.success
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val textColor = when {
                        isCompleted -> HariMuThemeTokens.semantic.onSuccess
                        isCurrent -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                    val containerColor = when {
                        isCompleted -> HariMuThemeTokens.semantic.successContainer
                        isCurrent -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    Column(modifier = Modifier.weight(1f, fill = true), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xxxs)) {
                        Surface(shape = RoundedCornerShape(100.dp), color = containerColor) {
                            Text(text = "${index + 1}", style = MaterialTheme.typography.labelSmall.copy(color = textColor), modifier = Modifier.padding(10.dp))
                        }
                        Text(text = step, style = MaterialTheme.typography.labelSmall.copy(color = textColor), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    if (index < steps.lastIndex) {
                        Spacer(modifier = Modifier.weight(1f, fill = true).height(2.dp).padding(top = 10.dp).background(if (isCompleted) HariMuThemeTokens.semantic.success else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
            }
            // Current step progress
            if (currentStep < steps.size) {
                LinearProgressIndicator(
                    progress = { (currentStepProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round
                )
                Text(text = steps[currentStep], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            onCancel?.let {
                HariMuTextAction(text = stringResource(R.string.action_cancel), onClick = it, icon = Icons.Filled.Close, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}