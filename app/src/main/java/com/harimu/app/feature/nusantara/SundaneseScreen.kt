package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuDivider
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.utilities.UtilityActionButton
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityCopyIconButton
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityQuickChips
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.nusantara.SundaneseEntry

private val SUNDANESE_PRESETS = listOf("lebet", "dahar", "sumping", "tidur")

@Composable
fun SundaneseScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: SundaneseViewModel = viewModel(factory = SundaneseViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    UtilityScreenScaffold(
        title = stringResource(R.string.sundanese_title),
        onBack = onBack,
        onRefresh = if (state.query.isNotBlank()) {
            { vm.lookup(isEnglish) }
        } else {
            null
        },
        isRefreshing = state.isLoading,
        backTestTag = "sundanese_back_button",
        refreshTestTag = "sundanese_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.sundanese_title),
                    description = stringResource(R.string.sundanese_intro),
                    badge = stringResource(R.string.sundanese_badge)
                )
            }

            item(key = "search") {
                UtilitySearchBar(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    label = stringResource(R.string.sundanese_search_hint),
                    tag = "sundanese_search",
                    onSearch = { if (state.query.isNotBlank()) vm.lookup(isEnglish) }
                )
            }

            item(key = "presets") {
                UtilityQuickChips(
                    items = SUNDANESE_PRESETS,
                    selectedKey = state.lookedUpWord.lowercase(),
                    keyOf = { it },
                    labelOf = { it },
                    label = stringResource(R.string.sundanese_presets),
                    tag = "sundanese_presets",
                    onSelect = { word -> vm.lookupPreset(word, isEnglish) }
                )
            }

            item(key = "action") {
                UtilityActionButton(
                    label = stringResource(R.string.sundanese_lookup_cta),
                    onClick = { vm.lookup(isEnglish) },
                    icon = Icons.Filled.Search,
                    isLoading = state.isLoading,
                    enabled = state.query.isNotBlank(),
                    testTag = "sundanese_lookup_button"
                )
            }

            if (!state.searched && !state.isLoading) {
                item(key = "prompt") {
                    UtilityEmptyState(
                        icon = Icons.Filled.Translate,
                        title = stringResource(R.string.sundanese_title),
                        subtitle = stringResource(R.string.sundanese_intro)
                    )
                }
            }

            if (state.isLoading) {
                item(key = "loading") { UtilityLoadingCard() }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.lookup(isEnglish) },
                        testTag = "sundanese_retry_button"
                    )
                }
            }

            if (state.searched && !state.isLoading && state.error == null) {
                if (state.entries.isEmpty()) {
                    item(key = "empty") {
                        UtilityEmptyState(
                            icon = Icons.Filled.Translate,
                            title = stringResource(R.string.sundanese_empty_title),
                            subtitle = stringResource(R.string.sundanese_empty_desc, state.lookedUpWord)
                        )
                    }
                }

                itemsIndexed(
                    items = state.entries,
                    key = { idx, e -> "${idx}_${e.sorangan}_${e.loma}_${e.indonesian}" }
                ) { _, entry ->
                    SundaneseCard(entry = entry)
                }
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.sundanese_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
private fun SundaneseCard(entry: SundaneseEntry) {
    val spacing = HariMuThemeTokens.spacing
    val copyPayload = buildString {
        appendLine("Sorangan: ${entry.sorangan.ifBlank { "—" }}")
        appendLine("Batur: ${entry.batur.ifBlank { "—" }}")
        appendLine("Loma: ${entry.loma.ifBlank { "—" }}")
        appendLine("Indonesia: ${entry.indonesian.ifBlank { "—" }}")
        append("English: ${entry.english.ifBlank { "—" }}")
    }

    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.indonesian.ifBlank { entry.sorangan.ifBlank { entry.loma } },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            UtilityCopyIconButton(
                textToCopy = copyPayload,
                clipboardLabel = "sundanese_entry",
                contentDescription = stringResource(R.string.sundanese_copy_entry)
            )
        }
        HariMuInsetPanel {
            LevelRow(stringResource(R.string.sundanese_level_self), entry.sorangan)
            HariMuDivider()
            LevelRow(stringResource(R.string.sundanese_level_elder), entry.batur)
            HariMuDivider()
            LevelRow(stringResource(R.string.sundanese_level_casual), entry.loma)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Indonesia",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.indonesian.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "English",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.english.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun LevelRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
        )
    }
}
