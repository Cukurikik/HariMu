package com.harimu.app.feature.area

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.area.AreaEntry
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.utilities.UtilityActionButton
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityBadgePill
import com.harimu.app.core.design.utilities.UtilityCopyIconButton
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityQuickChips
import com.harimu.app.core.design.utilities.UtilityResultHeader
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.toolapi.ToolGroup

private val AREA_PRESET_KEYWORDS = listOf("Soreang", "40915", "Gambir", "Surabaya", "Yogyakarta")

@Composable
fun AreaLookupScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: AreaLookupViewModel = viewModel(factory = AreaLookupViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    UtilityScreenScaffold(
        title = stringResource(R.string.area_title),
        onBack = onBack,
        onRefresh = if (state.keyword.trim().length >= 2) {
            { vm.refresh(isEnglish) }
        } else {
            null
        },
        isRefreshing = state.isLoading,
        backTestTag = "area_back_button",
        refreshTestTag = "area_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.area_title),
                    description = stringResource(R.string.area_intro),
                    badge = stringResource(R.string.area_badge),
                    accent = ToolGroup.CORE.accentColor()
                )
            }

            item(key = "search-form") {
                UtilitySectionCard {
                    UtilitySearchBar(
                        value = state.keyword,
                        onValueChange = vm::onKeywordChange,
                        label = stringResource(R.string.area_keyword_label),
                        placeholder = stringResource(R.string.area_keyword_placeholder),
                        tag = "area_keyword_input",
                        enabled = !state.isLoading,
                        onSearch = { vm.search(isEnglish) }
                    )
                    UtilityQuickChips(
                        items = AREA_PRESET_KEYWORDS,
                        selectedKey = state.keyword.trim(),
                        keyOf = { it },
                        labelOf = { it },
                        label = stringResource(R.string.area_presets_label),
                        tag = "area_presets",
                        accent = ToolGroup.CORE.accentColor(),
                        onSelect = { preset -> vm.selectPresetAndSearch(preset, isEnglish) }
                    )
                    UtilityActionButton(
                        label = stringResource(R.string.area_search_cta),
                        loadingLabel = stringResource(R.string.area_loading),
                        isLoading = state.isLoading,
                        onClick = { vm.search(isEnglish) },
                        testTag = "area_search_button"
                    )
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
                    UtilityLoadingCard(message = stringResource(R.string.area_loading))
                }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.refresh(isEnglish) },
                        enabled = !state.isLoading,
                        testTag = "area_retry_button"
                    )
                }
            }

            if (!state.hasSearched && !state.isLoading && state.error == null) {
                item(key = "prompt") {
                    UtilityEmptyState(
                        icon = Icons.Filled.LocationOn,
                        title = stringResource(R.string.area_prompt_title),
                        subtitle = stringResource(R.string.area_prompt_desc)
                    )
                }
            }

            if (state.hasSearched && state.entries.isEmpty() && state.error == null && !state.isLoading) {
                item(key = "empty") {
                    UtilityEmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.area_empty_title),
                        subtitle = stringResource(R.string.area_empty_desc),
                        actionLabel = stringResource(R.string.utility_clear_filters),
                        onAction = { vm.clearSearch() },
                        actionTag = "area_clear_button"
                    )
                }
            }

            if (state.entries.isNotEmpty() && !state.isLoading) {
                item(key = "result-count") {
                    UtilityResultHeader(
                        countText = stringResource(R.string.area_results_count, state.entries.size)
                    )
                }
                items(state.entries, key = { entryKey(it) }) { entry ->
                    AreaCard(entry = entry)
                }
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.area_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

private fun entryKey(entry: AreaEntry): String = listOf(
    entry.provinsi,
    entry.kabkota,
    entry.kecamatan,
    entry.desakel,
    entry.kodepos
).joinToString("|")

@Composable
private fun AreaCard(entry: AreaEntry) {
    val postal = entry.kodepos.ifBlank { "—" }
    val copyAddress = remember(entry) {
        listOf(entry.desakel, entry.kecamatan, entry.kabkota, entry.provinsi, entry.kodepos)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }
    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = entry.desakel.ifBlank { entry.kecamatan.ifBlank { "—" } },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = listOf(entry.kecamatan, entry.kabkota)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UtilityBadgePill(text = postal, accent = ToolGroup.CORE.accentColor())
                UtilityCopyIconButton(
                    textToCopy = if (entry.kodepos.isNotBlank()) entry.kodepos else copyAddress,
                    clipboardLabel = "postal_code",
                    contentDescription = stringResource(R.string.area_copy_postal),
                    testTag = "area_copy_${entry.kodepos.ifBlank { entry.desakel }}"
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        Text(
            text = stringResource(R.string.area_province_label, entry.provinsi.ifBlank { "—" }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
