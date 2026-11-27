package com.harimu.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.HariMuSearchBar
import com.harimu.app.core.design.HariMuTextAction
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.ToolRowCard
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.icon
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry

@Composable
fun HomeScreen(
    isEnglish: Boolean,
    favoriteIds: Set<String>,
    recentToolIds: List<String>,
    onOpenTool: (Tool) -> Unit,
    onOpenGroup: (ToolGroup) -> Unit,
    onToggleFavorite: (String) -> Unit,
    activeJobCount: Int = 0,
    attentionJobCount: Int = 0,
    onOpenJobCenter: () -> Unit = {},
    isLoadingWorkspace: Boolean = false,
    onOpenCatalog: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onSearchInCatalog: (String) -> Unit = {}
) {
    val spacing = HariMuThemeTokens.spacing
    val fontScale = LocalDensity.current.fontScale
    val listState = rememberLazyListState()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val searchResults = remember(searchQuery, isEnglish) {
        if (searchQuery.isBlank()) emptyList() else ToolRegistry.search(searchQuery, isEnglish)
    }
    val favoriteTools = remember(favoriteIds) {
        favoriteIds
            .mapNotNull { id -> ToolRegistry.findById(id)?.takeUnless { it.id.startsWith("DEV-") } }
    }
    val recentTools = remember(recentToolIds) {
        recentToolIds.distinct()
            .mapNotNull { id -> ToolRegistry.findById(id)?.takeUnless { it.id.startsWith("DEV-") } }
            .take(8)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val columns = when {
            maxWidth >= 840.dp -> 3
            maxWidth >= 320.dp && fontScale < 1.5f -> 2
            else -> 1
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = HariMuThemeTokens.layout.dashboardMaxWidth),
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "home_discovery_header") {
                HomeDiscoveryHeader(
                    totalTools = ToolRegistry.allTools.size,
                    totalGroups = ToolGroup.entries.size,
                    onOpenCatalog = onOpenCatalog
                )
            }

            item(key = "home_search_bar") {
                HariMuSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    inputTestTag = "home_search_input",
                    clearTestTag = "home_search_clear",
                    onSearch = {
                        if (searchQuery.isNotBlank()) onSearchInCatalog(searchQuery)
                    }
                )
            }

            if (searchQuery.isNotBlank()) {
                item(key = "home_search_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.search_results_count, searchResults.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (searchResults.isNotEmpty()) {
                            HariMuTextAction(
                                text = stringResource(R.string.home_open_in_catalog),
                                onClick = { onSearchInCatalog(searchQuery) },
                                modifier = Modifier.testTag("home_search_in_catalog_button")
                            )
                        }
                    }
                }
                if (searchResults.isEmpty()) {
                    item(key = "home_search_empty") {
                        EmptyState(
                            icon = Icons.Filled.SearchOff,
                            title = stringResource(R.string.catalog_empty_title),
                            subtitle = stringResource(R.string.catalog_empty_desc),
                            actionLabel = stringResource(R.string.action_clear_search),
                            onAction = { searchQuery = "" },
                            actionTestTag = "home_clear_search_button"
                        )
                    }
                } else {
                    items(searchResults, key = { it.id }) { tool ->
                        ToolRowCard(
                            tool = tool,
                            isEnglish = isEnglish,
                            isFavorite = tool.id in favoriteIds,
                            onClick = { onOpenTool(tool) },
                            onToggleFavorite = { onToggleFavorite(tool.id) },
                            showGroupBadge = true
                        )
                    }
                }
            } else {
                if (activeJobCount > 0 || attentionJobCount > 0) {
                    item(key = "job_center_banner") {
                        HomeJobCenterBanner(
                            activeCount = activeJobCount,
                            attentionCount = attentionJobCount,
                            onClick = onOpenJobCenter
                        )
                    }
                }

                item(key = "home_favorites_section") {
                    HomeFavoritesSection(
                        isEnglish = isEnglish,
                        isLoading = isLoadingWorkspace,
                        favoriteTools = favoriteTools,
                        onOpenTool = onOpenTool,
                        onToggleFavorite = onToggleFavorite,
                        onOpenFavorites = onOpenFavorites
                    )
                }

                item(key = "home_recent_section") {
                    HomeRecentToolsSection(
                        isEnglish = isEnglish,
                        isLoading = isLoadingWorkspace,
                        recentTools = recentTools,
                        onOpenTool = onOpenTool
                    )
                }

                item(key = "home_groups_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.groups_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        HariMuTextAction(
                            text = stringResource(R.string.catalog_all_count, ToolRegistry.allTools.size),
                            onClick = onOpenCatalog,
                            modifier = Modifier.testTag("home_groups_see_all_button")
                        )
                    }
                }

                val rows = ToolGroup.entries.chunked(columns)
                items(rows, key = { row -> row.joinToString("_") { it.name } }) { rowGroups ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        rowGroups.forEach { group ->
                            GroupGridCard(
                                group = group,
                                count = ToolRegistry.countByGroup(group),
                                onClick = { onOpenGroup(group) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - rowGroups.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeDiscoveryHeader(
    totalTools: Int,
    totalGroups: Int,
    onOpenCatalog: () -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_discovery_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.home_discover_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.home_discover_subtitle, totalTools, totalGroups),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(spacing.sm))
        OutlinedButton(
            onClick = onOpenCatalog,
            shape = HariMuThemeTokens.shapes.control,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = HariMuThemeTokens.controls.buttonMinHeight)
                .testTag("home_open_catalog_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = null,
                modifier = Modifier.size(HariMuThemeTokens.icons.sm)
            )
            Spacer(modifier = Modifier.width(spacing.xxs))
            Text(
                text = stringResource(R.string.home_open_catalog),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeFavoritesSection(
    isEnglish: Boolean,
    isLoading: Boolean,
    favoriteTools: List<Tool>,
    onOpenTool: (Tool) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenFavorites: () -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    val semantic = HariMuThemeTokens.semantic

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_favorites_section"),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.favorites_title),
                style = MaterialTheme.typography.titleLarge
            )
            if (favoriteTools.isNotEmpty()) {
                HariMuTextAction(
                    text = stringResource(R.string.home_see_all_count, favoriteTools.size),
                    onClick = onOpenFavorites,
                    modifier = Modifier.testTag("home_favorites_see_all")
                )
            }
        }

        when {
            isLoading && favoriteTools.isEmpty() -> {
                HomeInlineStatusRow(
                    text = stringResource(R.string.home_loading_workspace),
                    isLoading = true,
                    modifier = Modifier.testTag("home_favorites_loading")
                )
            }
            favoriteTools.isEmpty() -> {
                HomeInlineStatusRow(
                    text = stringResource(R.string.home_favorites_empty_inline),
                    icon = Icons.Outlined.StarOutline,
                    modifier = Modifier.testTag("home_favorites_empty")
                )
            }
            else -> {
                val visibleFavorites = remember(favoriteTools) { favoriteTools.take(10) }
                LazyRow(
                    modifier = Modifier.testTag("home_favorites_list"),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    items(visibleFavorites, key = { it.id }) { tool ->
                        val accent = tool.group.accentColor()
                        val title = tool.title(isEnglish)
                        Card(
                            modifier = Modifier
                                .width(204.dp)
                                .testTag("home_fav_card_${tool.id}")
                                .clip(HariMuThemeTokens.shapes.card)
                                .clickable(role = Role.Button) { onOpenTool(tool) },
                            shape = HariMuThemeTokens.shapes.card,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                HariMuThemeTokens.borders.hairline,
                                accent.copy(alpha = 0.42f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    start = spacing.sm,
                                    top = spacing.xs,
                                    end = spacing.xxs,
                                    bottom = spacing.sm
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ToolIdBadge(id = tool.id, accent = accent)
                                    IconButton(
                                        onClick = { onToggleFavorite(tool.id) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .semantics { selected = true }
                                            .testTag("home_fav_toggle_${tool.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = stringResource(R.string.cd_remove_favorite, title),
                                            tint = semantic.favoriteAccent,
                                            modifier = Modifier.size(HariMuThemeTokens.icons.md)
                                        )
                                    }
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = spacing.xs)
                                )
                                Text(
                                    text = stringResource(tool.group.titleRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.contrastSafeAccent(accent),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = spacing.xs)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRecentToolsSection(
    isEnglish: Boolean,
    isLoading: Boolean,
    recentTools: List<Tool>,
    onOpenTool: (Tool) -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    val semantic = HariMuThemeTokens.semantic

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_recent_section"),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Text(
            text = stringResource(R.string.recently_used_title),
            style = MaterialTheme.typography.titleLarge
        )

        when {
            isLoading && recentTools.isEmpty() -> {
                HomeInlineStatusRow(
                    text = stringResource(R.string.home_loading_workspace),
                    isLoading = true,
                    modifier = Modifier.testTag("home_recent_loading")
                )
            }
            recentTools.isEmpty() -> {
                HomeInlineStatusRow(
                    text = stringResource(R.string.recently_used_empty),
                    icon = Icons.Filled.History,
                    modifier = Modifier.testTag("home_recent_empty")
                )
            }
            else -> {
                LazyRow(
                    modifier = Modifier.testTag("home_recent_list"),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    items(recentTools, key = { it.id }) { tool ->
                        val accent = tool.group.accentColor()
                        Card(
                            modifier = Modifier
                                .width(196.dp)
                                .testTag("home_recent_card_${tool.id}")
                                .clip(HariMuThemeTokens.shapes.card)
                                .clickable(role = Role.Button) { onOpenTool(tool) },
                            shape = HariMuThemeTokens.shapes.card,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                HariMuThemeTokens.borders.hairline,
                                MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ToolIdBadge(id = tool.id, accent = accent)
                                    Text(
                                        text = stringResource(tool.group.titleRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = semantic.contrastSafeAccent(accent),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = tool.title(isEnglish),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tool.description(isEnglish),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeInlineStatusRow(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false
) {
    val spacing = HariMuThemeTokens.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = HariMuThemeTokens.shapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(HariMuThemeTokens.icons.sm),
                    strokeWidth = 2.dp
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(HariMuThemeTokens.icons.md)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupGridCard(
    group: ToolGroup,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = group.accentColor()
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    val spacing = HariMuThemeTokens.spacing
    Card(
        modifier = modifier
            .testTag("group_card_${group.name}")
            .clip(HariMuThemeTokens.shapes.card)
            .clickable(role = Role.Button, onClick = onClick),
        shape = HariMuThemeTokens.shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            HariMuThemeTokens.borders.hairline,
            MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha)
        )
    ) {
        Column(modifier = Modifier.padding(spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(HariMuThemeTokens.icons.tile)
                        .clip(HariMuThemeTokens.shapes.iconTile)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(group.icon(), contentDescription = null, tint = safeAccent)
                }
                Surface(
                    shape = HariMuThemeTokens.shapes.badge,
                    color = accent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(R.string.tool_count_label, count),
                        style = MonoTextStyle.copy(fontWeight = FontWeight.Bold),
                        color = safeAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = stringResource(group.titleRes),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(group.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.cd_open_group),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeJobCenterBanner(
    activeCount: Int,
    attentionCount: Int,
    onClick: () -> Unit
) {
    val semantic = HariMuThemeTokens.semantic
    val isAlert = attentionCount > 0 && activeCount == 0
    val container = if (isAlert) semantic.warningContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isAlert) semantic.onWarningContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HariMuThemeTokens.shapes.card)
            .clickable(onClick = onClick)
            .testTag("home_job_center_banner"),
        shape = HariMuThemeTokens.shapes.card,
        color = container
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WorkHistory,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.home_job_banner_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusParts = buildList {
                    if (activeCount > 0) add(stringResource(R.string.home_job_banner_active, activeCount))
                    if (attentionCount > 0) add(stringResource(R.string.home_job_banner_attention, attentionCount))
                }
                Text(
                    text = statusParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.home_job_banner_cta))
            }
        }
    }
}
