package com.harimu.app.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.HariMuSearchBar
import com.harimu.app.core.design.HariMuTextAction
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.ToolRowCard
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry

@Composable
fun FavoritesScreen(
    isEnglish: Boolean,
    favoriteIds: Set<String>,
    onOpenTool: (Tool) -> Unit,
    onToggleFavorite: (String) -> Unit,
    isLoading: Boolean = false,
    onBrowseCatalog: () -> Unit = {}
) {
    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    val semantic = HariMuThemeTokens.semantic
    val listState = rememberLazyListState()

    val favoriteTools = remember(favoriteIds) {
        favoriteIds.mapNotNull { id ->
            ToolRegistry.findById(id)?.takeUnless { it.id.startsWith("DEV-") }
        }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedGroupName by rememberSaveable { mutableStateOf<String?>(null) }
    val availableGroups = remember(favoriteTools) {
        ToolGroup.entries.filter { group -> favoriteTools.any { it.group == group } }
    }
    val selectedGroup = remember(selectedGroupName, availableGroups) {
        selectedGroupName?.let { name -> availableGroups.firstOrNull { it.name == name } }
    }

    LaunchedEffect(availableGroups, selectedGroupName) {
        if (selectedGroupName != null && availableGroups.none { it.name == selectedGroupName }) {
            selectedGroupName = null
        }
    }

    val filteredFavorites = remember(favoriteTools, query, isEnglish, selectedGroup) {
        val q = query.trim().lowercase()
        val tokens = if (q.isEmpty()) emptyList() else q.split(Regex("\\s+")).filter { it.isNotBlank() }
        favoriteTools.filter { tool ->
            val groupMatch = selectedGroup == null || tool.group == selectedGroup
            val searchable = buildString {
                append(tool.id.lowercase()).append(' ')
                append(tool.title(isEnglish).lowercase()).append(' ')
                append(tool.description(isEnglish).lowercase()).append(' ')
                append(tool.engineLabel.lowercase())
            }
            val queryMatch = tokens.isEmpty() || tokens.all { searchable.contains(it) }
            groupMatch && queryMatch
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            isLoading && favoriteTools.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(spacing.lg)
                        .testTag("favorites_loading"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.home_loading_workspace),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.sm)
                    )
                }
            }

            favoriteTools.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = layout.catalogMaxWidth)
                        .padding(spacing.md),
                    contentAlignment = Alignment.TopCenter
                ) {
                    EmptyState(
                        icon = Icons.Filled.Star,
                        title = stringResource(R.string.favorites_empty_title),
                        subtitle = stringResource(R.string.favorites_empty_desc),
                        actionLabel = stringResource(R.string.favorites_browse_catalog),
                        onAction = onBrowseCatalog,
                        actionTestTag = "favorites_browse_catalog_button"
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = layout.catalogMaxWidth)
                ) {
                    HariMuSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.favorites_search_hint),
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                        inputTestTag = "favorites_search_input",
                        clearTestTag = "favorites_search_clear"
                    )

                    if (availableGroups.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            item(key = "favorites_filter_all") {
                                FilterChip(
                                    selected = selectedGroup == null,
                                    onClick = { selectedGroupName = null },
                                    label = { Text(stringResource(R.string.catalog_all_count, favoriteTools.size)) },
                                    shape = HariMuThemeTokens.shapes.chip,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("favorites_filter_all")
                                )
                            }
                            items(availableGroups, key = { it.name }) { group ->
                                val accent = group.accentColor()
                                val safeAccent = semantic.contrastSafeAccent(accent)
                                FilterChip(
                                    selected = selectedGroup == group,
                                    onClick = { selectedGroupName = group.name },
                                    label = { Text(stringResource(group.titleRes)) },
                                    shape = HariMuThemeTokens.shapes.chip,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.16f),
                                        selectedLabelColor = safeAccent
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("favorites_filter_${group.name}")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.xxs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.favorites_count_summary, filteredFavorites.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("favorites_count")
                        )
                        if (query.isNotBlank() || selectedGroup != null) {
                            HariMuTextAction(
                                text = stringResource(R.string.catalog_reset_filters),
                                onClick = {
                                    query = ""
                                    selectedGroupName = null
                                },
                                modifier = Modifier.testTag("favorites_clear_filters_inline")
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("favorites_list"),
                        contentPadding = PaddingValues(
                            start = spacing.md,
                            end = spacing.md,
                            top = spacing.xs,
                            bottom = spacing.lg
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        if (filteredFavorites.isEmpty()) {
                            item(key = "favorites_filter_empty") {
                                EmptyState(
                                    icon = Icons.Filled.SearchOff,
                                    title = stringResource(R.string.favorites_filter_empty_title),
                                    subtitle = stringResource(R.string.favorites_filter_empty_desc),
                                    actionLabel = stringResource(R.string.catalog_reset_filters),
                                    onAction = {
                                        query = ""
                                        selectedGroupName = null
                                    },
                                    actionTestTag = "favorites_reset_filters_button"
                                )
                            }
                        } else {
                            items(filteredFavorites, key = { it.id }) { tool ->
                                ToolRowCard(
                                    tool = tool,
                                    isEnglish = isEnglish,
                                    isFavorite = true,
                                    onClick = { onOpenTool(tool) },
                                    onToggleFavorite = { onToggleFavorite(tool.id) },
                                    showGroupBadge = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
