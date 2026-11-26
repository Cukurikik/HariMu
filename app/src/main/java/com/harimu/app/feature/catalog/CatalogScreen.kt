package com.harimu.app.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.GroupHeader
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.ToolRowCard
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.icon
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    initialGroup: ToolGroup?,
    isEnglish: Boolean,
    favoriteIds: Set<String>,
    onOpenTool: (Tool) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBack: () -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    var selectedGroup by remember(initialGroup) { mutableStateOf(initialGroup) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(selectedGroup, query, isEnglish) {
        ToolRegistry.search(query, isEnglish, selectedGroup)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedGroup?.let { stringResource(it.titleRes) }
                            ?: stringResource(R.string.nav_catalog),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = layout.catalogMaxWidth)
            ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs)
                    .testTag("catalog_search_input"),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                item {
                    FilterChip(
                        selected = selectedGroup == null,
                        onClick = { selectedGroup = null },
                        label = { Text(stringResource(R.string.catalog_all_count, ToolRegistry.allTools.size)) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("filter_all")
                    )
                }
                items(ToolGroup.entries, key = { it.name }) { group ->
                    val accent = group.accentColor()
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { selectedGroup = group },
                        label = { Text(stringResource(group.titleRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("filter_${group.name}")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                selectedGroup?.let { group ->
                    item {
                        GroupHeader(
                            title = stringResource(group.titleRes),
                            subtitle = stringResource(group.subtitleRes),
                            toolCount = ToolRegistry.countByGroup(group),
                            icon = group.icon(),
                            accent = group.accentColor()
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.SearchOff,
                            title = stringResource(R.string.catalog_empty_title),
                            subtitle = stringResource(R.string.catalog_empty_desc)
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { tool ->
                        ToolRowCard(
                            tool = tool,
                            isEnglish = isEnglish,
                            isFavorite = tool.id in favoriteIds,
                            onClick = { onOpenTool(tool) },
                            onToggleFavorite = { onToggleFavorite(tool.id) }
                        )
                    }
                }
            }
        }
        }
    }
}
