package com.harimu.app.feature.nusantara

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityBadgePill
import com.harimu.app.core.design.utilities.UtilityBreadcrumbBar
import com.harimu.app.core.design.utilities.UtilityCopyIconButton
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityResultHeader
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.nusantara.Pesantren
import com.harimu.app.core.nusantara.PesantrenRegion

@Composable
fun PesantrenScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: PesantrenViewModel = viewModel(factory = PesantrenViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    BackHandler(enabled = state.province != null || state.regency != null) {
        when {
            state.regency != null -> vm.backToRegencies()
            state.province != null -> vm.backToProvinces()
        }
    }

    LaunchedEffect(isEnglish) { vm.loadProvinces(isEnglish) }

    val title = when {
        state.regency != null -> state.regency?.name.orEmpty()
        state.province != null -> state.province?.name.orEmpty()
        else -> stringResource(R.string.pesantren_title)
    }

    val breadcrumbs = buildList {
        add(stringResource(R.string.pesantren_title))
        state.province?.name?.let { add(it) }
        state.regency?.name?.let { add(it) }
    }

    UtilityScreenScaffold(
        title = title,
        onBack = {
            when {
                state.regency != null -> vm.backToRegencies()
                state.province != null -> vm.backToProvinces()
                else -> onBack()
            }
        },
        onRefresh = { vm.retry(isEnglish) },
        isRefreshing = state.isLoading,
        backTestTag = "pesantren_back_button",
        refreshTestTag = "pesantren_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.pesantren_title),
                    description = stringResource(R.string.pesantren_intro),
                    badge = stringResource(R.string.pesantren_badge)
                )
            }

            if (state.province != null) {
                item(key = "breadcrumbs") {
                    UtilityBreadcrumbBar(
                        segments = breadcrumbs,
                        onSegmentClick = { idx ->
                            when (idx) {
                                0 -> vm.backToProvinces()
                                1 -> vm.backToRegencies()
                            }
                        },
                        tag = "pesantren_breadcrumbs"
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
                        onRetry = { vm.retry(isEnglish) },
                        testTag = "pesantren_retry_button"
                    )
                }
            }

            when {
                state.regency == null && state.province == null -> {
                    if (state.provinces.isNotEmpty()) {
                        item(key = "region-search") {
                            UtilitySearchBar(
                                value = state.regionQuery,
                                onValueChange = vm::onRegionQueryChange,
                                label = stringResource(R.string.pesantren_filter_regions),
                                tag = "pesantren_region_search"
                            )
                        }
                        item(key = "step-province") {
                            UtilityResultHeader(
                                countText = stringResource(R.string.pesantren_step_province)
                            )
                        }
                    }

                    if (state.provinces.isNotEmpty() && state.visibleProvinces.isEmpty()) {
                        item(key = "empty-provinces") {
                            UtilityEmptyState(
                                icon = Icons.Filled.AccountBalance,
                                title = stringResource(R.string.pesantren_empty_title),
                                subtitle = stringResource(R.string.pesantren_empty_desc),
                                actionLabel = stringResource(R.string.utility_hub_reset_filter),
                                onAction = { vm.onRegionQueryChange("") },
                                actionTag = "pesantren_reset_region_filter"
                            )
                        }
                    }

                    items(state.visibleProvinces, key = { it.id }) { province ->
                        RegionRow(region = province) { vm.selectProvince(province, isEnglish) }
                    }
                }

                state.regency == null -> {
                    if (state.regencies.isNotEmpty()) {
                        item(key = "region-search") {
                            UtilitySearchBar(
                                value = state.regionQuery,
                                onValueChange = vm::onRegionQueryChange,
                                label = stringResource(R.string.pesantren_filter_regions),
                                tag = "pesantren_region_search"
                            )
                        }
                        item(key = "step-regency") {
                            UtilityResultHeader(
                                countText = stringResource(R.string.pesantren_step_regency)
                            )
                        }
                    }

                    if (state.regencies.isNotEmpty() && state.visibleRegencies.isEmpty()) {
                        item(key = "empty-regencies") {
                            UtilityEmptyState(
                                icon = Icons.Filled.AccountBalance,
                                title = stringResource(R.string.pesantren_empty_title),
                                subtitle = stringResource(R.string.pesantren_empty_desc),
                                actionLabel = stringResource(R.string.utility_hub_reset_filter),
                                onAction = { vm.onRegionQueryChange("") },
                                actionTag = "pesantren_reset_region_filter"
                            )
                        }
                    }

                    items(state.visibleRegencies, key = { it.id }) { regency ->
                        RegionRow(region = regency) { vm.selectRegency(regency, isEnglish) }
                    }
                }

                else -> {
                    item(key = "search") {
                        UtilitySearchBar(
                            value = state.query,
                            onValueChange = vm::onQueryChange,
                            label = stringResource(R.string.pesantren_filter_list),
                            tag = "pesantren_search"
                        )
                    }

                    item(key = "count") {
                        UtilityResultHeader(
                            countText = stringResource(
                                R.string.pesantren_count,
                                state.visiblePesantren.size,
                                state.pesantren.size
                            )
                        )
                    }

                    if (state.visiblePesantren.isEmpty() && !state.isLoading && state.error == null) {
                        item(key = "empty") {
                            UtilityEmptyState(
                                icon = Icons.Filled.AccountBalance,
                                title = stringResource(R.string.pesantren_empty_title),
                                subtitle = stringResource(R.string.pesantren_empty_desc),
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
                                actionTag = "pesantren_reset_filter"
                            )
                        }
                    }

                    items(
                        items = state.visiblePesantren,
                        key = { "${it.registrationNumber}_${it.name}" }
                    ) { pesantren ->
                        PesantrenCard(pesantren = pesantren, isEnglish = isEnglish)
                    }
                }
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.pesantren_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
private fun RegionRow(region: PesantrenRegion, onClick: () -> Unit) {
    UtilitySectionCard(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("pesantren_region_${region.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = region.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
            UtilityBadgePill(text = region.id)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PesantrenCard(pesantren: Pesantren, isEnglish: Boolean) {
    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = pesantren.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                if (pesantren.registrationNumber.isNotBlank()) {
                    UtilityBadgePill(text = "NSPP ${pesantren.registrationNumber}")
                }
            }
            val copyText = pesantren.registrationNumber.ifBlank { pesantren.name }
            UtilityCopyIconButton(
                textToCopy = copyText,
                clipboardLabel = "pesantren_nspp",
                contentDescription = stringResource(R.string.pesantren_copy_nspp),
                testTag = "pesantren_copy_${pesantren.registrationNumber.ifBlank { pesantren.name }}"
            )
        }
        if (pesantren.address.isNotBlank()) {
            Text(text = pesantren.address, style = MaterialTheme.typography.bodyMedium)
        }
        if (pesantren.cleric.isNotBlank()) {
            HariMuInsetPanel {
                Text(
                    text = (if (isEnglish) "Cleric: " else "Pengasuh / Kiai: ") + pesantren.cleric,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
