package com.harimu.app.core.design.utilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.HariMuTopBar
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.toolapi.ToolGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilityScreenScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    backTestTag: String = "utility_back_button",
    refreshTestTag: String = "utility_refresh_button",
    maxWidth: Dp = HariMuThemeTokens.layout.catalogMaxWidth,
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Shared app bar: same colors, title typography, heading semantics and back
            // affordance as every top-level screen instead of a local copy.
            HariMuTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                backTestTag = backTestTag,
                actions = {
                    extraActions()
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !isRefreshing,
                            modifier = Modifier
                                .testTag(refreshTestTag)
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.utility_cd_refresh)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = maxWidth)
            ) {
                content(PaddingValues(0.dp))
            }
        }
    }
}

@Composable
fun UtilityHeroBanner(
    title: String,
    description: String,
    badge: String,
    modifier: Modifier = Modifier,
    accent: Color = ToolGroup.NUSANTARA.accentColor()
) {
    val spacing = HariMuThemeTokens.spacing
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = HariMuThemeTokens.shapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            HariMuThemeTokens.borders.hairline,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Surface(
                shape = HariMuThemeTokens.shapes.badge,
                color = accent.copy(alpha = 0.14f),
                border = BorderStroke(HariMuThemeTokens.borders.hairline, accent.copy(alpha = 0.28f))
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = safeAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UtilityBadgePill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = ToolGroup.NUSANTARA.accentColor(),
    mono: Boolean = true
) {
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    Surface(
        modifier = modifier,
        shape = HariMuThemeTokens.shapes.badge,
        color = accent.copy(alpha = 0.13f),
        border = BorderStroke(HariMuThemeTokens.borders.hairline, accent.copy(alpha = 0.28f))
    ) {
        Text(
            text = text,
            style = if (mono) {
                MonoTextStyle.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
            },
            color = safeAccent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun UtilitySearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag),
        singleLine = true,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        placeholder = placeholder?.let { hint ->
            {
                Text(
                    text = hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier
                        .testTag("${tag}_clear")
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_clear)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboard?.hide()
                onSearch?.invoke()
            },
            onDone = {
                keyboard?.hide()
                onSearch?.invoke()
            }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun UtilityActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Search,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    loadingLabel: String? = null,
    testTag: String? = null
) {
    val controls = HariMuThemeTokens.controls
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = HariMuThemeTokens.shapes.control,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = controls.buttonMinHeight)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isLoading && !loadingLabel.isNullOrBlank()) loadingLabel else label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun <T> UtilityQuickChips(
    items: List<T>,
    selectedKey: String?,
    keyOf: (T) -> String,
    labelOf: (T) -> String,
    tag: String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    accent: Color = ToolGroup.NUSANTARA.accentColor()
) {
    if (items.isEmpty()) return
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag(tag)
        ) {
            items(items, key = { keyOf(it) }) { item ->
                val selected = keyOf(item) == selectedKey
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(item) },
                    label = {
                        Text(
                            text = labelOf(item),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.16f),
                        selectedLabelColor = safeAccent
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UtilityBreadcrumbBar(
    segments: List<String>,
    modifier: Modifier = Modifier,
    onSegmentClick: ((Int) -> Unit)? = null,
    tag: String? = null
) {
    if (segments.isEmpty()) return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .let { if (tag != null) it.testTag(tag) else it },
        shape = HariMuThemeTokens.shapes.chip,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(HariMuThemeTokens.borders.hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEachIndexed { index, segment ->
                val isLast = index == segments.lastIndex
                val clickableModifier = if (!isLast && onSegmentClick != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button) { onSegmentClick(index) }
                } else {
                    Modifier
                }
                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isLast) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = clickableModifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isLast) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UtilityResultHeader(
    countText: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = countText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}

@Composable
fun UtilityLoadingCard(
    message: String = stringResource(R.string.utility_loading_default),
    modifier: Modifier = Modifier
) {
    val spacing = HariMuThemeTokens.spacing
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(HariMuThemeTokens.borders.hairline, MaterialTheme.colorScheme.outlineVariant),
        shape = HariMuThemeTokens.shapes.card,
        elevation = CardDefaults.cardElevation(defaultElevation = HariMuThemeTokens.elevation.card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UtilityErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.utility_error_title),
    retryLabel: String = stringResource(R.string.action_retry),
    enabled: Boolean = true,
    testTag: String = "utility_retry_button"
) {
    val spacing = HariMuThemeTokens.spacing
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = HariMuThemeTokens.shapes.card,
        elevation = CardDefaults.cardElevation(defaultElevation = HariMuThemeTokens.elevation.card),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(
                onClick = onRetry,
                enabled = enabled,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(testTag)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(retryLabel)
            }
        }
    }
}

@Composable
fun UtilityEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionTag: String = "utility_empty_action"
) {
    val spacing = HariMuThemeTokens.spacing
    val accent = ToolGroup.NUSANTARA.accentColor()
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            HariMuThemeTokens.borders.hairline,
            MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = HariMuThemeTokens.elevation.card),
        shape = HariMuThemeTokens.shapes.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.12f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = safeAccent,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
            Spacer(Modifier.size(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.size(4.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(actionTag)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun UtilitySectionCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = HariMuThemeTokens.shapes.card,
        elevation = CardDefaults.cardElevation(defaultElevation = HariMuThemeTokens.elevation.card),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            HariMuThemeTokens.borders.hairline,
            if (highlighted) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            content = content
        )
    }
}

@Composable
fun UtilityCopyIconButton(
    textToCopy: String,
    clipboardLabel: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    toastText: String = stringResource(R.string.toast_copied),
    testTag: String? = null
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    IconButton(
        onClick = {
            clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, textToCopy))
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier
            .let { if (testTag != null) it.testTag(testTag) else it }
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun UtilityAttributionFooter(
    sourceText: String,
    modifier: Modifier = Modifier,
    freshnessNote: String? = null,
    fetchedAtMillis: Long? = null,
    onlineOnlyNote: String? = stringResource(R.string.utility_online_only_note),
    showSessionStatus: Boolean = true
) {
    val spacing = HariMuThemeTokens.spacing
    val fetchedLabel = remember(fetchedAtMillis) {
        fetchedAtMillis?.let { millis ->
            SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = HariMuThemeTokens.shapes.card,
        elevation = CardDefaults.cardElevation(defaultElevation = HariMuThemeTokens.elevation.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(HariMuThemeTokens.borders.hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).padding(top = 2.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!freshnessNote.isNullOrBlank()) {
                    Text(
                        text = freshnessNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!onlineOnlyNote.isNullOrBlank()) {
                    Text(
                        text = onlineOnlyNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showSessionStatus) {
                    Text(
                        text = if (fetchedLabel != null) {
                            stringResource(R.string.utility_fetched_this_session, fetchedLabel)
                        } else {
                            stringResource(R.string.utility_not_fetched_yet)
                        },
                        style = MonoTextStyle,
                        color = if (fetchedLabel != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

internal fun copyUtilityText(context: Context, label: String, text: String, toastMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
