package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.harimu.app.AppContainer
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.nusantara.Region
import com.harimu.app.core.nusantara.WeatherForecast
import com.harimu.app.core.nusantara.WeatherSlot

@Composable
fun WeatherScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(isEnglish) {
        if (state.provinces.isEmpty()) vm.loadProvinces(isEnglish)
    }

    NusantaraScaffold(
        title = if (isEnglish) "BMKG Weather" else "Cuaca BMKG",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "intro") {
                NusantaraIntroCard(
                    if (isEnglish) {
                        "Pick a province down to a village; the adm4 code is sent to BMKG's official forecast API."
                    } else {
                        "Pilih provinsi sampai desa; kode adm4 dikirim ke API prakiraan resmi BMKG."
                    }
                )
            }

            item(key = "province") {
                RegionChips(
                    label = if (isEnglish) "Province" else "Provinsi",
                    regions = state.provinces,
                    selectedId = state.province?.id,
                    tag = "weather_province",
                    onSelect = { vm.selectProvince(it, isEnglish) }
                )
            }

            if (state.province != null) {
                item(key = "regency") {
                    RegionChips(
                        label = if (isEnglish) "City / Regency" else "Kabupaten / Kota",
                        regions = state.regencies,
                        selectedId = state.regency?.id,
                        tag = "weather_regency",
                        onSelect = { vm.selectRegency(it, isEnglish) }
                    )
                }
            }

            if (state.regency != null) {
                item(key = "district") {
                    RegionChips(
                        label = if (isEnglish) "District" else "Kecamatan",
                        regions = state.districts,
                        selectedId = state.district?.id,
                        tag = "weather_district",
                        onSelect = { vm.selectDistrict(it, isEnglish) }
                    )
                }
            }

            if (state.district != null) {
                item(key = "village") {
                    RegionChips(
                        label = if (isEnglish) "Village" else "Desa / Kelurahan",
                        regions = state.villages,
                        selectedId = state.village?.id,
                        tag = "weather_village",
                        onSelect = { vm.selectVillage(it, isEnglish) }
                    )
                }
            }

            if (state.isLoading) {
                item(key = "loading") { NusantaraLoading() }
            }

            state.error?.let { message ->
                item(key = "error") {
                    NusantaraErrorCard(message = message, isEnglish = isEnglish) { vm.retry(isEnglish) }
                }
            }

            state.forecast?.let { forecast ->
                item(key = "forecast-header") { ForecastHeader(forecast = forecast, isEnglish = isEnglish) }
                items(forecast.slots.take(24), key = { "${it.localTime}-${it.conditionId}" }) { slot ->
                    WeatherSlotRow(slot = slot, isEnglish = isEnglish)
                }
            }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: BMKG public forecast API (api.bmkg.go.id) and emsifa region data."
                    } else {
                        "Sumber: API prakiraan publik BMKG (api.bmkg.go.id) dan data wilayah emsifa."
                    }
                )
            }
        }
    }
}

@Composable
private fun RegionChips(
    label: String,
    regions: List<Region>,
    selectedId: String?,
    tag: String,
    onSelect: (Region) -> Unit
) {
    if (regions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag(tag)
        ) {
            items(regions, key = { it.id }) { region ->
                FilterChip(
                    selected = region.id == selectedId,
                    onClick = { onSelect(region) },
                    label = { Text(region.name) }
                )
            }
        }
    }
}

@Composable
private fun ForecastHeader(forecast: WeatherForecast, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = forecast.location.village.ifBlank { forecast.location.district },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = listOf(forecast.location.district, forecast.location.city, forecast.location.province)
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "adm4 ${forecast.location.adm4}",
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun WeatherSlotRow(slot: WeatherSlot, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (slot.iconUrl.isNotBlank()) {
                AsyncImage(
                    model = slot.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = slot.localTime,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = slot.conditionEn.ifBlank { slot.conditionId }.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = "${slot.temperatureC}°C", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (isEnglish) {
                        "Humidity ${slot.humidityPercent}%"
                    } else {
                        "Kelembapan ${slot.humidityPercent}%"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
