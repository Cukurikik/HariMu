package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityBadgePill
import com.harimu.app.core.design.utilities.UtilityCopyIconButton
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityResultHeader
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.nusantara.Hero
import com.harimu.app.core.nusantara.Volcano

@Composable
fun HeroesScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: HeroesViewModel = viewModel(factory = HeroesViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.load(isEnglish)
    }

    UtilityScreenScaffold(
        title = stringResource(R.string.heroes_title),
        onBack = onBack,
        onRefresh = { vm.load(isEnglish) },
        isRefreshing = state.isLoading,
        backTestTag = "heroes_back_button",
        refreshTestTag = "heroes_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.heroes_title),
                    description = if (isEnglish) {
                        "Browse official Indonesian national heroes with birth, passing, decree year, and historical contribution."
                    } else {
                        "Telusuri daftar Pahlawan Nasional Indonesia lengkap dengan tahun lahir, wafat, penetapan, dan jasa sejarah."
                    },
                    badge = stringResource(R.string.heroes_badge)
                )
            }

            item(key = "search") {
                UtilitySearchBar(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    label = stringResource(R.string.heroes_search_hint),
                    tag = "heroes_search"
                )
            }

            if (state.isLoading) {
                item(key = "loading") { UtilityLoadingCard() }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.load(isEnglish) },
                        testTag = "heroes_retry_button"
                    )
                }
            }

            item(key = "count") {
                UtilityResultHeader(
                    countText = stringResource(R.string.heroes_count, state.visible.size)
                )
            }

            if (state.loaded && !state.isLoading && state.error == null && state.visible.isEmpty()) {
                item(key = "empty") {
                    UtilityEmptyState(
                        icon = Icons.Filled.PersonSearch,
                        title = stringResource(R.string.heroes_empty_title),
                        subtitle = stringResource(R.string.heroes_empty_desc),
                        actionLabel = if (state.query.isNotBlank()) {
                            stringResource(R.string.utility_hub_reset_filter)
                        } else {
                            null
                        },
                        onAction = if (state.query.isNotBlank()) {
                            { vm.onQueryChange("") }
                        } else {
                            null
                        },
                        actionTag = "heroes_reset_filter"
                    )
                }
            }

            items(state.visible, key = { it.name }) { hero ->
                HeroCard(hero = hero, isEnglish = isEnglish)
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.heroes_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
fun VolcanoesScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: VolcanoesViewModel = viewModel(factory = VolcanoesViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.load(isEnglish)
    }

    UtilityScreenScaffold(
        title = stringResource(R.string.volcanoes_title),
        onBack = onBack,
        onRefresh = { vm.load(isEnglish) },
        isRefreshing = state.isLoading,
        backTestTag = "volcanoes_back_button",
        refreshTestTag = "volcanoes_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.volcanoes_title),
                    description = if (isEnglish) {
                        "Static directory of Indonesian volcanoes with morphology, elevation, coordinates, and recorded last eruption."
                    } else {
                        "Direktori statis gunung berapi Indonesia beserta bentuk, ketinggian, koordinat, dan catatan letusan terakhir."
                    },
                    badge = stringResource(R.string.volcanoes_badge)
                )
            }

            item(key = "search") {
                UtilitySearchBar(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    label = stringResource(R.string.volcanoes_search_hint),
                    tag = "volcanoes_search"
                )
            }

            if (state.isLoading) {
                item(key = "loading") { UtilityLoadingCard() }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.load(isEnglish) },
                        testTag = "volcanoes_retry_button"
                    )
                }
            }

            item(key = "count") {
                UtilityResultHeader(
                    countText = stringResource(R.string.volcanoes_count, state.visible.size)
                )
            }

            if (state.loaded && !state.isLoading && state.error == null && state.visible.isEmpty()) {
                item(key = "empty") {
                    UtilityEmptyState(
                        icon = Icons.Filled.Landscape,
                        title = stringResource(R.string.volcanoes_empty_title),
                        subtitle = stringResource(R.string.volcanoes_empty_desc),
                        actionLabel = if (state.query.isNotBlank()) {
                            stringResource(R.string.utility_hub_reset_filter)
                        } else {
                            null
                        },
                        onAction = if (state.query.isNotBlank()) {
                            { vm.onQueryChange("") }
                        } else {
                            null
                        },
                        actionTag = "volcanoes_reset_filter"
                    )
                }
            }

            items(state.visible, key = { it.name }) { volcano ->
                VolcanoCard(volcano = volcano, isEnglish = isEnglish)
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.volcanoes_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
private fun HeroCard(hero: Hero, isEnglish: Boolean) {
    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = hero.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            if (hero.ascensionYear.isNotBlank()) {
                UtilityBadgePill(
                    text = (if (isEnglish) "Decree " else "SK ") + hero.ascensionYear
                )
            }
        }
        HariMuInsetPanel {
            Text(
                text = listOf(
                    (if (isEnglish) "Born " else "Lahir ") + hero.birthYear.ifBlank { "—" },
                    (if (isEnglish) "Died " else "Wafat ") + hero.deathYear.ifBlank { "—" }
                ).joinToString(" • "),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (hero.description.isNotBlank()) {
            Text(text = hero.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VolcanoCard(volcano: Volcano, isEnglish: Boolean) {
    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = volcano.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (volcano.form.isNotBlank()) {
                    Text(
                        text = volcano.form,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (volcano.height.isNotBlank()) {
                UtilityBadgePill(text = volcano.height)
            }
        }
        if (volcano.geolocation.isNotBlank()) {
            HariMuInsetPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = volcano.geolocation,
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    UtilityCopyIconButton(
                        textToCopy = volcano.geolocation,
                        clipboardLabel = "volcano_coords",
                        contentDescription = stringResource(R.string.earthquake_copy_coords)
                    )
                }
            }
        }
        Text(
            text = (if (isEnglish) "Last eruption: " else "Letusan terakhir: ") +
                volcano.lastEruption.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
