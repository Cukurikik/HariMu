package com.harimu.app.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.toolapi.ModelSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup

fun ToolGroup.accentColor(): Color = when (this) {
    ToolGroup.CORE -> HariMuColors.GroupCore
    ToolGroup.VIDEO -> HariMuColors.GroupVideo
    ToolGroup.AUDIO -> HariMuColors.GroupAudio
    ToolGroup.IMAGE -> HariMuColors.GroupImage
    ToolGroup.PDF -> HariMuColors.GroupPdf
    ToolGroup.CONVERTER -> HariMuColors.GroupConverter
    ToolGroup.NUSANTARA -> HariMuColors.GroupNusantara
}

fun ToolGroup.icon(): ImageVector = when (this) {
    ToolGroup.CORE -> Icons.Filled.Build
    ToolGroup.VIDEO -> Icons.Filled.Videocam
    ToolGroup.AUDIO -> Icons.Filled.AudioFile
    ToolGroup.IMAGE -> Icons.Filled.Image
    ToolGroup.PDF -> Icons.Filled.PictureAsPdf
    ToolGroup.CONVERTER -> Icons.Filled.SwapHoriz
    ToolGroup.NUSANTARA -> Icons.Filled.Public
}

enum class StatusTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

@Composable
fun ToolIdBadge(
    id: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val foreground = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    Surface(
        modifier = modifier,
        shape = HariMuThemeTokens.shapes.badge,
        color = accent.copy(alpha = 0.13f),
        border = BorderStroke(HariMuThemeTokens.borders.hairline, accent.copy(alpha = 0.34f))
    ) {
        Text(
            text = id,
            style = MonoTextStyle.copy(fontWeight = FontWeight.Bold),
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun EngineBadge(
    engine: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = HariMuThemeTokens.shapes.badge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = engine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CapabilityBadge(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        modifier = modifier,
        shape = HariMuThemeTokens.shapes.badge,
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun HariMuStatusBanner(
    message: String,
    tone: StatusTone = StatusTone.INFO,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val semantic = HariMuThemeTokens.semantic
    val spacing = HariMuThemeTokens.spacing
    val (container, onContainer, accent, icon) = when (tone) {
        StatusTone.INFO -> BannerPalette(
            semantic.infoContainer,
            semantic.onInfoContainer,
            semantic.info,
            Icons.Filled.Info
        )
        StatusTone.SUCCESS -> BannerPalette(
            semantic.successContainer,
            semantic.onSuccessContainer,
            semantic.success,
            Icons.Filled.CheckCircle
        )
        StatusTone.WARNING -> BannerPalette(
            semantic.warningContainer,
            semantic.onWarningContainer,
            semantic.warning,
            Icons.Filled.Warning
        )
        StatusTone.ERROR -> BannerPalette(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error,
            Icons.Filled.ErrorOutline
        )
    }
    val resolvedTitle = title ?: when (tone) {
        StatusTone.INFO -> stringResource(R.string.status_info)
        StatusTone.SUCCESS -> stringResource(R.string.status_success)
        StatusTone.WARNING -> stringResource(R.string.status_warning)
        StatusTone.ERROR -> stringResource(R.string.error_title)
    }

    Card(
        // The banner appears in reaction to a load, refresh or failed action, so screen readers
        // must be told about the new state instead of leaving it to be discovered by swiping.
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = if (tone == StatusTone.ERROR) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            },
        shape = HariMuThemeTokens.shapes.card,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = HariMuThemeTokens.elevation.none),
        border = BorderStroke(HariMuThemeTokens.borders.hairline, accent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(HariMuThemeTokens.icons.sm)
                )
                Spacer(modifier = Modifier.width(spacing.xs))
                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                HariMuTextAction(
                    text = actionLabel,
                    onClick = onAction,
                    color = accent
                )
            }
        }
    }
}

private data class BannerPalette(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val icon: ImageVector
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolCard(
    id: String,
    title: String,
    description: String,
    engineLabel: String,
    icon: ImageVector,
    accent: Color,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    isStub: Boolean = false,
    requiresNetwork: Boolean = false,
    requiresModel: ModelSpec? = null,
    groupLabel: String? = null
) {
    val spacing = HariMuThemeTokens.spacing
    val shapes = HariMuThemeTokens.shapes
    val icons = HariMuThemeTokens.icons
    val borders = HariMuThemeTokens.borders
    val semantic = HariMuThemeTokens.semantic
    val iconTint = semantic.contrastSafeAccent(accent)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tool_card_$id")
            .clip(shapes.card)
            .clickable(role = Role.Button, onClick = onClick),
        shape = shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HariMuThemeTokens.elevation.card),
        border = BorderStroke(
            borders.hairline,
            MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(icons.tile)
                    .clip(shapes.iconTile)
                    .background(accent.copy(alpha = 0.13f))
                    .border(borders.hairline, accent.copy(alpha = 0.30f), shapes.iconTile),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(icons.md)
                )
            }

            Spacer(modifier = Modifier.width(spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ToolIdBadge(id = id, accent = accent)
                    EngineBadge(engine = engineLabel)
                    if (!groupLabel.isNullOrBlank()) {
                        CapabilityBadge(
                            label = groupLabel,
                            containerColor = accent.copy(alpha = 0.12f),
                            contentColor = iconTint
                        )
                    }
                    when {
                        isStub -> CapabilityBadge(label = stringResource(R.string.badge_coming_soon))
                        requiresModel != null -> CapabilityBadge(
                            label = stringResource(R.string.badge_needs_model, requiresModel.name),
                            containerColor = semantic.infoContainer,
                            contentColor = semantic.onInfoContainer
                        )
                        requiresNetwork -> CapabilityBadge(
                            label = stringResource(R.string.badge_needs_internet),
                            containerColor = semantic.warningContainer,
                            contentColor = semantic.onWarningContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { selected = isFavorite }
                    .testTag("fav_button_$id")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.cd_remove_favorite else R.string.cd_add_favorite,
                        title
                    ),
                    tint = if (isFavorite) semantic.favoriteAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ToolRowCard(
    tool: Tool,
    isEnglish: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    showGroupBadge: Boolean = false
) {
    ToolCard(
        id = tool.id,
        title = tool.title(isEnglish),
        description = tool.description(isEnglish),
        engineLabel = tool.engineLabel,
        icon = tool.icon,
        accent = tool.group.accentColor(),
        isFavorite = isFavorite,
        onClick = onClick,
        onToggleFavorite = onToggleFavorite,
        modifier = modifier,
        isStub = tool.isStub,
        requiresNetwork = tool.requiresNetwork,
        requiresModel = tool.requiresModel,
        groupLabel = if (showGroupBadge) stringResource(tool.group.titleRes) else null
    )
}

@Composable
fun GroupHeader(
    title: String,
    subtitle: String,
    toolCount: Int,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val spacing = HariMuThemeTokens.spacing
    val shapes = HariMuThemeTokens.shapes
    val icons = HariMuThemeTokens.icons
    val semantic = HariMuThemeTokens.semantic
    val safeAccent = semantic.contrastSafeAccent(accent)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shapes.container,
        color = accent.copy(alpha = 0.09f),
        border = BorderStroke(HariMuThemeTokens.borders.hairline, accent.copy(alpha = 0.34f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(icons.tile)
                    .clip(shapes.iconTile)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = safeAccent,
                    modifier = Modifier.size(icons.lg)
                )
            }
            Spacer(modifier = Modifier.width(spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(spacing.xs))
            Surface(
                shape = shapes.badge,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(HariMuThemeTokens.borders.hairline, accent.copy(alpha = 0.45f))
            ) {
                Text(
                    text = stringResource(R.string.tool_count_label, toolCount),
                    style = MonoTextStyle.copy(fontWeight = FontWeight.Bold),
                    color = safeAccent,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

