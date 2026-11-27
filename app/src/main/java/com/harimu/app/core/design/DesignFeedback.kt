package com.harimu.app.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harimu.app.R

@Composable
fun ProgressSheet(
    percent: Int?,
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Text(
                text = stringResource(R.string.progress_running_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (percent != null) {
                Surface(
                    shape = HariMuThemeTokens.shapes.badge,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$percent%",
                        style = MonoTextStyle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (percent != null) {
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            HariMuTextAction(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                icon = Icons.Filled.Close
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultCard(
    summary: String,
    onOpen: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onUploadToCloud: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    previewContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val spacing = HariMuThemeTokens.spacing
    val semantic = HariMuThemeTokens.semantic

    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = semantic.successBorder.copy(alpha = 0.65f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = semantic.success,
                modifier = Modifier.size(HariMuThemeTokens.icons.md)
            )
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(
                text = summary,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        previewContent?.invoke(this)
        if (onOpen != null || onShare != null || onSave != null) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                if (onOpen != null) {
                    HariMuSecondaryButton(
                        text = stringResource(R.string.action_open),
                        onClick = onOpen,
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        modifier = Modifier
                            .widthIn(min = 104.dp)
                            .weight(1f, fill = true)
                    )
                }
                if (onShare != null) {
                    HariMuSecondaryButton(
                        text = stringResource(R.string.action_share),
                        onClick = onShare,
                        icon = Icons.Filled.Share,
                        modifier = Modifier
                            .widthIn(min = 104.dp)
                            .weight(1f, fill = true)
                    )
                }
                if (onSave != null) {
                    HariMuPrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = onSave,
                        icon = Icons.Filled.Download,
                        containerColor = semantic.success,
                        contentColor = semantic.onSuccess,
                        modifier = Modifier
                            .widthIn(min = 104.dp)
                            .weight(1f, fill = true)
                    )
                }
            }
        }
        if (onUploadToCloud != null) {
            HariMuSecondaryButton(
                text = stringResource(R.string.action_upload_cloud),
                onClick = onUploadToCloud,
                icon = Icons.Filled.CloudUpload,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_to_cloud_button")
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionTestTag: String? = null
) {
    val spacing = HariMuThemeTokens.spacing
    val icons = HariMuThemeTokens.icons
    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(icons.emptyContainer)
                    .clip(HariMuThemeTokens.shapes.card)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(icons.xl)
                )
            }
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.xxs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(spacing.sm))
                HariMuSecondaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) = EmptyState(icon = icon, title = title, subtitle = subtitle, modifier = modifier)

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = HariMuThemeTokens.spacing
    HariMuSectionCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        borderColor = MaterialTheme.colorScheme.error
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(HariMuThemeTokens.icons.md)
            )
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(
                text = stringResource(R.string.error_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        if (onRetry != null) {
            HariMuSecondaryButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
                icon = Icons.Filled.Refresh
            )
        }
    }
}
