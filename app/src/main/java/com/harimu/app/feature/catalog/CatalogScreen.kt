package com.harimu.app.feature.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.GroupHeader
import com.harimu.app.core.design.HariMuNavigationDefaults
import com.harimu.app.core.design.HariMuSearchBar
import com.harimu.app.core.design.HariMuTextAction
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
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
    onBack: (() -> Unit)? = null,
    onOpenAudioForge: (() -> Unit)? = null,
    onOpenImageForge: (() -> Unit)? = null,
    onOpenImageMatrix: (() -> Unit)? = null,
    onOpenPdfVault: (() -> Unit)? = null,
    onOpenPdfStudio: (() -> Unit)? = null,
    onOpenUniversalConverter: (() -> Unit)? = null,
    onOpenVideoForge: (() -> Unit)? = null,
    initialQuery: String = ""
) {
    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    val semantic = HariMuThemeTokens.semantic
    val listState = rememberLazyListState()

    var selectedGroupName by rememberSaveable(initialGroup?.name) { mutableStateOf(initialGroup?.name) }
    val selectedGroup = remember(selectedGroupName) {
        selectedGroupName?.let { name -> ToolGroup.entries.firstOrNull { it.name == name } }
    }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var capabilityName by rememberSaveable { mutableStateOf(CatalogCapabilityFilter.ALL.name) }
    val capabilityFilter = remember(capabilityName) {
        CatalogCapabilityFilter.entries.firstOrNull { it.name == capabilityName } ?: CatalogCapabilityFilter.ALL
    }
    var sortOrderName by rememberSaveable { mutableStateOf(CatalogSortOrder.DEFAULT.name) }
    val sortOrder = remember(sortOrderName) {
        CatalogSortOrder.entries.firstOrNull { it.name == sortOrderName } ?: CatalogSortOrder.DEFAULT
    }

    val filtered = remember(selectedGroup, query, isEnglish, capabilityFilter, sortOrder) {
        filterAndSortCatalogTools(
            query = query,
            isEnglish = isEnglish,
            selectedGroup = selectedGroup,
            capabilityFilter = capabilityFilter,
            sortOrder = sortOrder
        )
    }

    val showGroupedSections = selectedGroup == null &&
        query.isBlank() &&
        sortOrder == CatalogSortOrder.DEFAULT

    val groupedSections = remember(filtered, showGroupedSections) {
        if (showGroupedSections) groupCatalogTools(filtered) else emptyList()
    }

    val hasActiveFilters = query.isNotBlank() ||
        selectedGroup != null ||
        capabilityFilter != CatalogCapabilityFilter.ALL ||
        sortOrder != CatalogSortOrder.DEFAULT

    fun resetFilters() {
        query = ""
        selectedGroupName = null
        capabilityName = CatalogCapabilityFilter.ALL.name
        sortOrderName = CatalogSortOrder.DEFAULT.name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = HariMuNavigationDefaults.topBarColors(),
                title = {
                    Text(
                        text = selectedGroup?.let { stringResource(it.titleRes) }
                            ?: stringResource(R.string.nav_catalog),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .testTag("catalog_back_button")
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = layout.catalogMaxWidth)
            ) {
                HariMuSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                    inputTestTag = "catalog_search_input",
                    clearTestTag = "catalog_search_clear"
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    item(key = "filter_all") {
                        FilterChip(
                            selected = selectedGroup == null,
                            onClick = { selectedGroupName = null },
                            label = { Text(stringResource(R.string.catalog_all_count, ToolRegistry.allTools.size)) },
                            shape = HariMuThemeTokens.shapes.chip,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("filter_all")
                        )
                    }
                    items(ToolGroup.entries, key = { it.name }) { group ->
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
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedGroup == group,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.borders.subtleAlpha),
                                selectedBorderColor = safeAccent.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("filter_${group.name}")
                        )
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    items(CatalogCapabilityFilter.entries, key = { it.name }) { cap ->
                        val isSelected = capabilityFilter == cap
                        FilterChip(
                            selected = isSelected,
                            onClick = { capabilityName = cap.name },
                            label = { Text(stringResource(cap.labelRes)) },
                            shape = HariMuThemeTokens.shapes.chip,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(cap.testTag)
                        )
                    }
                    item(key = "sort_toggle") {
                        val isTitleSort = sortOrder == CatalogSortOrder.TITLE_ASC
                        FilterChip(
                            selected = isTitleSort,
                            onClick = {
                                sortOrderName = if (isTitleSort) {
                                    CatalogSortOrder.DEFAULT.name
                                } else {
                                    CatalogSortOrder.TITLE_ASC.name
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(stringResource(sortOrder.labelRes)) },
                            shape = HariMuThemeTokens.shapes.chip,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("catalog_sort_toggle")
                        )
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
                        text = stringResource(
                            R.string.catalog_results_summary,
                            filtered.size,
                            ToolRegistry.allTools.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("catalog_result_count")
                    )
                    if (hasActiveFilters) {
                        HariMuTextAction(
                            text = stringResource(R.string.catalog_reset_filters),
                            onClick = ::resetFilters,
                            modifier = Modifier.testTag("catalog_clear_filters_inline")
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("catalog_tool_list"),
                    contentPadding = PaddingValues(
                        start = spacing.md,
                        end = spacing.md,
                        top = spacing.xs,
                        bottom = spacing.lg
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    selectedGroup?.let { group ->
                        item(key = "selected_group_header_${group.name}") {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                GroupHeader(
                                    title = stringResource(group.titleRes),
                                    subtitle = stringResource(group.subtitleRes),
                                    toolCount = ToolRegistry.countByGroup(group),
                                    icon = group.icon(),
                                    accent = group.accentColor()
                                )
                                CatalogStudioLaunchers(
                                    group = group,
                                    onOpenVideoForge = onOpenVideoForge,
                                    onOpenAudioForge = onOpenAudioForge,
                                    onOpenImageForge = onOpenImageForge,
                                    onOpenImageMatrix = onOpenImageMatrix,
                                    onOpenPdfVault = onOpenPdfVault,
                                    onOpenPdfStudio = onOpenPdfStudio,
                                    onOpenUniversalConverter = onOpenUniversalConverter
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item(key = "catalog_empty_state") {
                            EmptyState(
                                icon = Icons.Filled.SearchOff,
                                title = stringResource(R.string.catalog_empty_title),
                                subtitle = stringResource(R.string.catalog_empty_desc),
                                actionLabel = if (hasActiveFilters) stringResource(R.string.catalog_reset_filters) else null,
                                onAction = if (hasActiveFilters) ({ resetFilters() }) else null,
                                actionTestTag = "catalog_reset_filters_button"
                            )
                        }
                    } else if (showGroupedSections) {
                        groupedSections.forEach { section ->
                            item(key = "section_${section.group.name}") {
                                CatalogSectionHeader(
                                    group = section.group,
                                    count = section.tools.size,
                                    onSelectGroup = { selectedGroupName = section.group.name }
                                )
                            }
                            items(section.tools, key = { it.id }) { tool ->
                                ToolRowCard(
                                    tool = tool,
                                    isEnglish = isEnglish,
                                    isFavorite = tool.id in favoriteIds,
                                    onClick = { onOpenTool(tool) },
                                    onToggleFavorite = { onToggleFavorite(tool.id) }
                                )
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { tool ->
                            ToolRowCard(
                                tool = tool,
                                isEnglish = isEnglish,
                                isFavorite = tool.id in favoriteIds,
                                onClick = { onOpenTool(tool) },
                                onToggleFavorite = { onToggleFavorite(tool.id) },
                                showGroupBadge = selectedGroup == null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSectionHeader(
    group: ToolGroup,
    count: Int,
    onSelectGroup: () -> Unit
) {
    val accent = group.accentColor()
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    val spacing = HariMuThemeTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.xs)
            .testTag("catalog_section_${group.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Icon(
            imageVector = group.icon(),
            contentDescription = null,
            tint = safeAccent,
            modifier = Modifier.size(HariMuThemeTokens.icons.md)
        )
        Text(
            text = stringResource(group.titleRes),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = HariMuThemeTokens.shapes.badge,
            color = accent.copy(alpha = 0.12f),
            border = BorderStroke(HariMuThemeTokens.borders.hairline, accent.copy(alpha = 0.34f))
        ) {
            Text(
                text = count.toString(),
                style = MonoTextStyle.copy(fontWeight = FontWeight.Bold),
                color = safeAccent,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        HariMuTextAction(
            text = stringResource(R.string.catalog_filter_group_action),
            onClick = onSelectGroup,
            color = safeAccent
        )
    }
}

@Composable
private fun CatalogStudioLaunchers(
    group: ToolGroup,
    onOpenVideoForge: (() -> Unit)?,
    onOpenAudioForge: (() -> Unit)?,
    onOpenImageForge: (() -> Unit)?,
    onOpenImageMatrix: (() -> Unit)?,
    onOpenPdfVault: (() -> Unit)?,
    onOpenPdfStudio: (() -> Unit)?,
    onOpenUniversalConverter: (() -> Unit)?
) {
    if (group == ToolGroup.VIDEO && onOpenVideoForge != null) {
        OutlinedButton(
            onClick = onOpenVideoForge,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("catalog_open_video_forge_btn")
        ) {
            Icon(Icons.Filled.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.video_forge_title))
        }
    }
    if (group == ToolGroup.AUDIO && onOpenAudioForge != null) {
        OutlinedButton(
            onClick = onOpenAudioForge,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("catalog_open_audio_forge_btn")
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.audio_forge_title))
        }
    }
    if (group == ToolGroup.IMAGE && (onOpenImageForge != null || onOpenImageMatrix != null)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onOpenImageForge != null) {
                OutlinedButton(
                    onClick = onOpenImageForge,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("catalog_open_image_forge_btn")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.image_forge_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onOpenImageMatrix != null) {
                OutlinedButton(
                    onClick = onOpenImageMatrix,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("catalog_open_image_matrix_btn")
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.image_matrix_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    if (group == ToolGroup.PDF && (onOpenPdfVault != null || onOpenPdfStudio != null)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onOpenPdfVault != null) {
                OutlinedButton(
                    onClick = onOpenPdfVault,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("catalog_open_pdf_vault_btn")
                ) {
                    Icon(Icons.Filled.ViewAgenda, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pdf_vault_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onOpenPdfStudio != null) {
                OutlinedButton(
                    onClick = onOpenPdfStudio,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("catalog_open_pdf_studio_btn")
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pdf_vault_open_studio),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    if (group == ToolGroup.CONVERTER && onOpenUniversalConverter != null) {
        OutlinedButton(
            onClick = onOpenUniversalConverter,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("catalog_open_converter_btn")
        ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.converter_catalog_open_studio),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
