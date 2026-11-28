package com.harimu.app.feature.nusantara

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuStatusBanner
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.StatusTone
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityBadgePill
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityQuickChips
import com.harimu.app.core.design.utilities.UtilityResultHeader
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry

private enum class UtilityHubCategory(val labelRes: Int) {
    ALL(R.string.utility_hub_cat_all),
    LIVE(R.string.utility_hub_cat_live),
    WORSHIP(R.string.utility_hub_cat_worship),
    DIRECTORY(R.string.utility_hub_cat_directory),
    REFERENCE(R.string.utility_hub_cat_reference)
}

private val QUICK_ACCESS_IDS = listOf(
    "CORE-15", // Prayer Times
    "CORE-16", // Area Lookup
    "NUS-01",  // BMKG Earthquake
    "NUS-02",  // BMKG Weather
    "NUS-10",  // Qur'an
    "NUS-06",  // Daily Duas
    "CORE-13", // Currency Converter
    "CORE-12"  // AI Weather
)

private val EVERYDAY_COMPANION_IDS = listOf("CORE-15", "CORE-16", "CORE-12", "CORE-13", "CORE-14")

@Composable
fun NusantaraHubScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onOpenTool: (Tool) -> Unit,
    onBack: () -> Unit = {}
) {
    val vm: NusantaraHubViewModel = viewModel(factory = NusantaraHubViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(UtilityHubCategory.ALL) }

    val ecosystemTools = remember {
        val nusantara = ToolRegistry.byGroup(ToolGroup.NUSANTARA)
        val everyday = EVERYDAY_COMPANION_IDS.mapNotNull { ToolRegistry.findById(it) }
        val priorityIds = listOf("CORE-15", "CORE-16")
        val priority = priorityIds.mapNotNull { id -> everyday.firstOrNull { it.id == id } }
        val remainingEveryday = everyday.filterNot { it.id in priorityIds }
        priority + nusantara + remainingEveryday
    }

    val quickTools = remember(ecosystemTools) {
        QUICK_ACCESS_IDS.mapNotNull { id -> ecosystemTools.firstOrNull { it.id == id } }
    }

    val filteredTools = remember(ecosystemTools, searchQuery, selectedCategory, isEnglish) {
        val query = searchQuery.trim().lowercase()
        ecosystemTools.filter { tool ->
            val categoryMatch = when (selectedCategory) {
                UtilityHubCategory.ALL -> true
                UtilityHubCategory.LIVE -> tool.id in setOf("NUS-01", "NUS-02", "CORE-12")
                UtilityHubCategory.WORSHIP -> tool.id in setOf("CORE-15", "NUS-06", "NUS-08", "NUS-10")
                UtilityHubCategory.DIRECTORY -> tool.id in setOf("CORE-16", "NUS-03", "NUS-07", "NUS-12")
                UtilityHubCategory.REFERENCE -> tool.id in setOf("NUS-04", "NUS-05", "NUS-09", "NUS-11", "CORE-13", "CORE-14")
            }
            val queryMatch = query.isEmpty() ||
                tool.id.lowercase().contains(query) ||
                tool.title(isEnglish).lowercase().contains(query) ||
                tool.description(isEnglish).lowercase().contains(query) ||
                tool.engineLabel.lowercase().contains(query)
            categoryMatch && queryMatch
        }
    }

    val quakeTool = remember { ToolRegistry.findById("NUS-01") }

    LaunchedEffect(Unit) {
        if (state.latest == null && !state.isLoading) vm.loadLatest(isEnglish)
    }

    UtilityScreenScaffold(
        title = stringResource(R.string.utility_hub_title),
        onBack = onBack,
        onRefresh = { vm.loadLatest(isEnglish) },
        isRefreshing = state.isLoading,
        backTestTag = "nusantara_back_button",
        refreshTestTag = "nusantara_refresh_button",
        maxWidth = HariMuThemeTokens.layout.dashboardMaxWidth
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("nusantara_hub_list"),
                contentPadding = PaddingValues(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                item(key = "hero") {
                    UtilityHeroBanner(
                        title = stringResource(R.string.utility_hub_hero_title),
                        description = stringResource(R.string.utility_hub_hero_desc),
                        badge = stringResource(R.string.utility_hub_badge)
                    )
                }

                item(key = "search") {
                    UtilitySearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = stringResource(R.string.utility_hub_title),
                        placeholder = stringResource(R.string.utility_hub_search_hint),
                        tag = "nusantara_hub_search"
                    )
                }

                item(key = "categories") {
                    val categoryItems = UtilityHubCategory.entries.map { it to stringResource(it.labelRes) }
                    UtilityQuickChips(
                        items = categoryItems,
                        selectedKey = selectedCategory.name,
                        keyOf = { it.first.name },
                        labelOf = { it.second },
                        tag = "nusantara_hub_categories",
                        onSelect = { selectedCategory = it.first }
                    )
                }

                if (searchQuery.isBlank() && selectedCategory == UtilityHubCategory.ALL) {
                    item(key = "quick-title") {
                        Text(
                            text = stringResource(R.string.utility_hub_quick_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    item(key = "quick-row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            modifier = Modifier.testTag("nusantara_quick_access")
                        ) {
                            items(quickTools, key = { "quick_${it.id}" }) { tool ->
                                QuickAccessChipCard(
                                    tool = tool,
                                    isEnglish = isEnglish,
                                    onClick = { onOpenTool(tool) }
                                )
                            }
                        }
                    }

                    item(key = "quake") {
                        LiveQuakeBanner(
                            state = state,
                            isEnglish = isEnglish,
                            onRefresh = { vm.loadLatest(isEnglish) },
                            onOpenDetails = { quakeTool?.let(onOpenTool) }
                        )
                    }
                }

                item(key = "tools-title") {
                    UtilityResultHeader(
                        countText = stringResource(R.string.utility_hub_tools_title, filteredTools.size),
                        trailing = if (searchQuery.isNotBlank() || selectedCategory != UtilityHubCategory.ALL) {
                            {
                                OutlinedButton(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategory = UtilityHubCategory.ALL
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("nusantara_hub_clear_inline")
                                ) {
                                    Text(stringResource(R.string.utility_hub_reset_filter))
                                }
                            }
                        } else {
                            null
                        }
                    )
                }

                if (filteredTools.isEmpty()) {
                    item(key = "empty") {
                        UtilityEmptyState(
                            icon = Icons.Filled.SearchOff,
                            title = stringResource(R.string.utility_hub_empty_title),
                            subtitle = stringResource(R.string.utility_hub_empty_desc),
                            actionLabel = stringResource(R.string.utility_hub_reset_filter),
                            onAction = {
                                searchQuery = ""
                                selectedCategory = UtilityHubCategory.ALL
                            },
                            actionTag = "nusantara_hub_reset"
                        )
                    }
                } else if (isWide) {
                    val rows = filteredTools.chunked(2)
                    items(rows, key = { pair -> pair.joinToString("_") { it.id } }) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            pair.forEach { tool ->
                                UtilityDirectoryCard(
                                    tool = tool,
                                    isEnglish = isEnglish,
                                    onClick = { onOpenTool(tool) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(filteredTools, key = { it.id }) { tool ->
                        UtilityDirectoryCard(
                            tool = tool,
                            isEnglish = isEnglish,
                            onClick = { onOpenTool(tool) }
                        )
                    }
                }

                item(key = "source") {
                    UtilityAttributionFooter(
                        sourceText = if (isEnglish) {
                            "Sources: BMKG, Quran API ID (myquran & Kemenag), alamat.thecloudalert.com, emsifa, " +
                                "indonesia-public-static-api, doa-doa-api, api-sekolah-indonesia, hibersunda, " +
                                "kunci-tts-api, football-standings-api, api-pesantren-indonesia."
                        } else {
                            "Sumber: BMKG, Quran API ID (myquran & Kemenag), alamat.thecloudalert.com, emsifa, " +
                                "indonesia-public-static-api, doa-doa-api, api-sekolah-indonesia, hibersunda, " +
                                "kunci-tts-api, football-standings-api, api-pesantren-indonesia."
                        },
                        fetchedAtMillis = state.fetchedAtMillis
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessChipCard(
    tool: Tool,
    isEnglish: Boolean,
    onClick: () -> Unit
) {
    val accent = tool.group.accentColor()
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("nusantara_quick_${tool.id}")
            .clickable(role = Role.Button, onClick = onClick),
        shape = HariMuThemeTokens.shapes.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(HariMuThemeTokens.borders.hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = safeAccent,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = tool.title(isEnglish),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tool.id,
                    style = MonoTextStyle,
                    color = safeAccent
                )
            }
        }
    }
}

@Composable
private fun LiveQuakeBanner(
    state: NusantaraHubUiState,
    isEnglish: Boolean,
    onRefresh: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val accent = ToolGroup.NUSANTARA.accentColor()
    UtilitySectionCard(
        highlighted = true,
        modifier = Modifier.testTag("nusantara_quake_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = HariMuThemeTokens.semantic.contrastSafeAccent(accent),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.earthquake_latest_tag),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
                )
            }
            state.latest?.let { quake ->
                UtilityBadgePill(text = "M ${quake.magnitude}", accent = accent)
            }
        }

        if (state.isLoading) {
            UtilityLoadingCard(message = stringResource(R.string.earthquake_loading))
        } else if (state.error != null && state.latest == null) {
            UtilityErrorCard(
                message = state.error,
                onRetry = onRefresh,
                testTag = "nusantara_quake_retry"
            )
        } else {
            val quake = state.latest
            if (quake != null) {
                HariMuInsetPanel {
                    Text(
                        text = quake.region,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = listOf(quake.date, quake.time, quake.depth)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (quake.potential.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.earthquake_potential, quake.potential),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenDetails,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("nusantara_quake_open_detail")
                ) {
                    Text(stringResource(R.string.utility_open_detail))
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("nusantara_quake_refresh")
                ) {
                    Text(stringResource(R.string.utility_refresh_btn))
                }
            }
            if (state.isStale) {
                HariMuStatusBanner(
                    message = stringResource(R.string.utility_stale_data_notice),
                    tone = StatusTone.WARNING
                )
            }
        }
    }
}

@Composable
private fun UtilityDirectoryCard(
    tool: Tool,
    isEnglish: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = tool.group.accentColor()
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("nusantara_tool_${tool.id}")
            .clickable(role = Role.Button, onClick = onClick),
        shape = HariMuThemeTokens.shapes.card,
        elevation = CardDefaults.cardElevation(defaultElevation = HariMuThemeTokens.elevation.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            HariMuThemeTokens.borders.hairline,
            MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = HariMuThemeTokens.shapes.iconTile,
                color = accent.copy(alpha = 0.13f),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = safeAccent,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UtilityBadgePill(text = tool.id, accent = accent)
                    UtilityBadgePill(text = tool.engineLabel, accent = MaterialTheme.colorScheme.secondary)
                }
                Text(
                    text = tool.title(isEnglish),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
