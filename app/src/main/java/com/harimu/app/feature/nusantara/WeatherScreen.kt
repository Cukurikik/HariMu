package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuStatusBanner
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.StatusTone
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
import com.harimu.app.core.nusantara.WeatherForecast
import com.harimu.app.core.nusantara.WeatherSlot

@Composable
fun WeatherScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit,
    initialAdm4: String? = null
) {
    val vm: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    LaunchedEffect(initialAdm4) {
        if (!initialAdm4.isNullOrBlank()) {
            vm.loadForecastForAdm4(initialAdm4, isEnglish)
        }
    }

    LaunchedEffect(Unit) {
        if (state.provinces.isEmpty()) vm.loadProvinces(isEnglish)
    }

    val breadcrumbs = listOfNotNull(
        state.province?.name,
        state.regency?.name,
        state.district?.name,
        state.village?.name,
        state.directAdm4?.let { "adm4 $it" }
    )

    UtilityScreenScaffold(
        title = stringResource(R.string.weather_title),
        onBack = onBack,
        onRefresh = { vm.retry(isEnglish) },
        isRefreshing = state.isLoading,
        backTestTag = "weather_back_button",
        refreshTestTag = "weather_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.weather_title),
                    description = stringResource(R.string.weather_intro),
                    badge = stringResource(R.string.weather_badge)
                )
            }

            if (breadcrumbs.isNotEmpty()) {
                item(key = "breadcrumbs") {
                    UtilityBreadcrumbBar(segments = breadcrumbs, tag = "weather_breadcrumbs")
                }
            }

            if (state.provinces.isNotEmpty()) {
                item(key = "filter") {
                    UtilitySearchBar(
                        value = state.regionFilter,
                        onValueChange = vm::onRegionFilterChange,
                        label = stringResource(R.string.weather_filter_label),
                        tag = "weather_region_filter"
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
                        tag = "weather_province",
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
                        tag = "weather_regency",
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
                        tag = "weather_district",
                        onSelect = { vm.selectDistrict(it, isEnglish) }
                    )
                }
            }

            if (state.district != null && state.visibleVillages.isNotEmpty()) {
                item(key = "village") {
                    UtilityQuickChips(
                        items = state.visibleVillages,
                        selectedKey = state.village?.id,
                        keyOf = { it.id },
                        labelOf = { it.name },
                        label = if (isEnglish) "Village" else "Desa / Kelurahan",
                        tag = "weather_village",
                        onSelect = { vm.selectVillage(it, isEnglish) }
                    )
                }
            }

            val activeListEmptyAfterFilter = state.regionFilter.isNotBlank() && when {
                state.district != null -> state.villages.isNotEmpty() && state.visibleVillages.isEmpty()
                state.regency != null -> state.districts.isNotEmpty() && state.visibleDistricts.isEmpty()
                state.province != null -> state.regencies.isNotEmpty() && state.visibleRegencies.isEmpty()
                else -> state.provinces.isNotEmpty() && state.visibleProvinces.isEmpty()
            }
            if (activeListEmptyAfterFilter) {
                item(key = "filter-empty") {
                    Text(
                        text = stringResource(R.string.weather_filter_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
                    UtilityLoadingCard(message = stringResource(R.string.weather_loading))
                }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.retry(isEnglish) },
                        testTag = "weather_retry_button"
                    )
                }
            }

            if (state.isStale) {
                item(key = "stale") {
                    HariMuStatusBanner(
                        message = stringResource(R.string.utility_stale_data_notice),
                        tone = StatusTone.WARNING
                    )
                }
            }

            val forecast = state.forecast
            if (forecast != null) {
                item(key = "forecast-header") {
                    ForecastHeader(forecast = forecast)
                }
                item(key = "slots-count") {
                    UtilityResultHeader(
                        countText = stringResource(R.string.weather_slots_count, forecast.slots.size)
                    )
                }
                items(forecast.slots) { slot ->
                    WeatherSlotRow(slot = slot, isEnglish = isEnglish)
                }
            } else if (!state.isLoading && state.error == null) {
                item(key = "empty-prompt") {
                    UtilityEmptyState(
                        icon = Icons.Filled.Cloud,
                        title = stringResource(R.string.weather_empty_prompt_title),
                        subtitle = stringResource(R.string.weather_empty_prompt_desc)
                    )
                }
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.weather_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
private fun ForecastHeader(forecast: WeatherForecast) {
    UtilitySectionCard(
        highlighted = true,
        modifier = Modifier.testTag("weather_forecast_header")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = forecast.location.village.ifBlank { forecast.location.district },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = listOf(forecast.location.district, forecast.location.city, forecast.location.province)
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
                UtilityBadgePill(text = "adm4 ${forecast.location.adm4}")
                UtilityCopyIconButton(
                    textToCopy = forecast.location.adm4,
                    clipboardLabel = "adm4",
                    contentDescription = stringResource(R.string.regions_copy_code),
                    testTag = "weather_copy_adm4"
                )
            }
        }
    }
}

@Composable
private fun WeatherSlotRow(slot: WeatherSlot, isEnglish: Boolean) {
    val conditionText = if (isEnglish) {
        slot.conditionEn.ifBlank { slot.conditionDescId.ifBlank { "—" } }
    } else {
        slot.conditionDescId.ifBlank { slot.conditionEn.ifBlank { "—" } }
    }
    val temperatureText = slot.temperatureC?.let { "$it°C" } ?: "—"
    val humidity = slot.humidityPercent
    val humidityText = if (humidity != null) {
        stringResource(R.string.weather_humidity, humidity)
    } else {
        stringResource(R.string.weather_humidity_unknown)
    }
    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (slot.iconUrl.isNotBlank()) {
                AsyncImage(
                    model = slot.iconUrl,
                    contentDescription = conditionText,
                    modifier = Modifier.size(38.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = slot.localTime,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = conditionText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                if (slot.windSpeed.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.weather_wind, slot.windSpeed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = temperatureText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = humidityText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
