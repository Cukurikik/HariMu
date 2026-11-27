package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityBadgePill
import com.harimu.app.core.design.utilities.UtilityBreadcrumbBar
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
import com.harimu.app.core.nusantara.Region

@Composable
fun RegionsScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onOpenWeather: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val vm: RegionsViewModel = viewModel(factory = RegionsViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    LaunchedEffect(Unit) { vm.loadProvinces(isEnglish) }

    val selectedDistrict = state.district
    val villageFlow = selectedDistrict?.let { district ->
        remember(district.id) { vm.villages(district.id) }
    }
    val villages = villageFlow?.collectAsLazyPagingItems()

    val breadcrumbs = listOfNotNull(
        state.province?.name,
        state.regency?.name,
        state.district?.name
    )

    UtilityScreenScaffold(
        title = stringResource(R.string.regions_title),
        onBack = onBack,
        onRefresh = { vm.retry(isEnglish) },
        isRefreshing = state.isLoading,
        backTestTag = "regions_back_button",
        refreshTestTag = "regions_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.regions_title),
                    description = stringResource(R.string.regions_intro),
                    badge = stringResource(R.string.regions_badge)
                )
            }

            if (breadcrumbs.isNotEmpty()) {
                item(key = "breadcrumbs") {
                    UtilityBreadcrumbBar(
                        segments = breadcrumbs,
                        onSegmentClick = { idx -> vm.clearToLevel(idx + 1) },
                        tag = "regions_breadcrumbs"
                    )
                }
            }

            if (state.provinces.isNotEmpty()) {
                item(key = "filter") {
                    UtilitySearchBar(
                        value = state.regionFilter,
                        onValueChange = vm::onRegionFilterChange,
                        label = stringResource(R.string.regions_filter_label),
                        tag = "regions_filter_input"
                    )
                }
            }

            if (state.visibleProvinces.isNotEmpty()) {
                item(key = "province") {
                    UtilityQuickChips(
                        items = state.visibleProvinces,
                        selectedKey = state.province?.id,
                        keyOf = { it.id },
                        labelOf = { it.name },
                        label = if (isEnglish) "Province" else "Provinsi",
                        tag = "regions_province",
                        onSelect = { vm.selectProvince(it, isEnglish) }
                    )
                }
            }

            if (state.province != null && state.visibleRegencies.isNotEmpty()) {
                item(key = "regency") {
                    UtilityQuickChips(
                        items = state.visibleRegencies,
                        selectedKey = state.regency?.id,
                        keyOf = { it.id },
                        labelOf = { it.name },
                        label = if (isEnglish) "City / Regency" else "Kabupaten / Kota",
                        tag = "regions_regency",
                        onSelect = { vm.selectRegency(it, isEnglish) }
                    )
                }
            }

            if (state.regency != null && state.visibleDistricts.isNotEmpty()) {
                item(key = "district") {
                    UtilityQuickChips(
                        items = state.visibleDistricts,
                        selectedKey = state.district?.id,
                        keyOf = { it.id },
                        labelOf = { it.name },
                        label = if (isEnglish) "District" else "Kecamatan",
                        tag = "regions_district",
                        onSelect = { vm.selectDistrict(it) }
                    )
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
                    UtilityLoadingCard()
                }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.retry(isEnglish) },
                        testTag = "regions_retry_button"
                    )
                }
            }

            if (selectedDistrict == null && !state.isLoading && state.error == null) {
                item(key = "prompt") {
                    UtilityEmptyState(
                        icon = Icons.Filled.Map,
                        title = stringResource(R.string.regions_selected_path),
                        subtitle = stringResource(R.string.weather_pick_step_hint)
                    )
                }
            }

            if (selectedDistrict != null && villages != null) {
                item(key = "village-title") {
                    UtilityResultHeader(
                        countText = stringResource(R.string.regions_villages_title, selectedDistrict.name)
                    )
                }
                when (val refreshState = villages.loadState.refresh) {
                    is LoadState.Loading -> {
                        item(key = "village-loading") {
                            UtilityLoadingCard(message = stringResource(R.string.regions_loading_villages))
                        }
                    }
                    is LoadState.Error -> {
                        item(key = "village-refresh-error") {
                            UtilityErrorCard(
                                message = refreshState.error.toNusantaraMessage(isEnglish),
                                onRetry = { villages.retry() },
                                retryLabel = stringResource(R.string.regions_load_villages_retry),
                                testTag = "regions_village_retry"
                            )
                        }
                    }
                    is LoadState.NotLoading -> Unit
                }

                items(count = villages.itemCount) { index ->
                    villages[index]?.let { region ->
                        RegionRow(
                            region = region,
                            onOpenWeather = { onOpenWeather(region.id) }
                        )
                    }
                }

                when (villages.loadState.append) {
                    is LoadState.Loading -> {
                        item(key = "village-append-loading") {
                            UtilityLoadingCard(message = stringResource(R.string.regions_loading_villages))
                        }
                    }
                    is LoadState.Error -> {
                        item(key = "village-append-error") {
                            UtilityErrorCard(
                                message = stringResource(R.string.regions_append_error),
                                onRetry = { villages.retry() },
                                testTag = "regions_append_retry"
                            )
                        }
                    }
                    is LoadState.NotLoading -> Unit
                }
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.regions_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
private fun RegionRow(
    region: Region,
    onOpenWeather: () -> Unit
) {
    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                UtilityBadgePill(text = region.id)
            }
            UtilityCopyIconButton(
                textToCopy = region.id,
                clipboardLabel = "region_code",
                contentDescription = stringResource(R.string.regions_copy_code),
                testTag = "regions_copy_${region.id}"
            )
        }
        OutlinedButton(
            onClick = onOpenWeather,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("regions_open_weather_${region.id}")
        ) {
            Icon(imageVector = Icons.Filled.Cloud, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.utility_open_weather_adm4))
        }
    }
}
