package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.harimu.app.AppContainer
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.nusantara.Region

@Composable
fun RegionsScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: RegionsViewModel = viewModel(factory = RegionsViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(isEnglish) { vm.loadProvinces(isEnglish) }

    // Paging desa harus dikumpulkan di luar lambda LazyColumn (bukan scope composable).
    val selectedDistrict = state.district
    val villageFlow = selectedDistrict?.let { district ->
        remember(district.id) { vm.villages(district.id) }
    }
    val villages = villageFlow?.collectAsLazyPagingItems()

    NusantaraScaffold(
        title = if (isEnglish) "Indonesian Regions" else "Wilayah Indonesia",
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
                        "Province → city → district → village, with the hierarchical code you can reuse as BMKG adm4."
                    } else {
                        "Provinsi → kab/kota → kecamatan → desa, lengkap dengan kode hierarkis yang bisa dipakai sebagai adm4 BMKG."
                    }
                )
            }

            item(key = "province") {
                LevelChips(
                    label = if (isEnglish) "Province" else "Provinsi",
                    regions = state.provinces,
                    selectedId = state.province?.id,
                    tag = "regions_province",
                    onSelect = { vm.selectProvince(it, isEnglish) }
                )
            }

            if (state.province != null) {
                item(key = "regency") {
                    LevelChips(
                        label = if (isEnglish) "City / Regency" else "Kabupaten / Kota",
                        regions = state.regencies,
                        selectedId = state.regency?.id,
                        tag = "regions_regency",
                        onSelect = { vm.selectRegency(it, isEnglish) }
                    )
                }
            }

            if (state.regency != null) {
                item(key = "district") {
                    LevelChips(
                        label = if (isEnglish) "District" else "Kecamatan",
                        regions = state.districts,
                        selectedId = state.district?.id,
                        tag = "regions_district",
                        onSelect = { vm.selectDistrict(it) }
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

            if (selectedDistrict != null && villages != null) {
                item(key = "village-title") {
                    Text(
                        text = if (isEnglish) {
                            "Villages in ${selectedDistrict.name}"
                        } else {
                            "Desa di ${selectedDistrict.name}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(count = villages.itemCount) { index ->
                    villages[index]?.let { region ->
                        RegionRow(region = region) { clipboard.setText(AnnotatedString(region.id)) }
                    }
                }
                if (villages.loadState.refresh is LoadState.Loading) {
                    item(key = "village-loading") { NusantaraLoading() }
                }
            }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: emsifa api-wilayah-indonesia (static, no API key). Tap the copy icon to save a code."
                    } else {
                        "Sumber: emsifa api-wilayah-indonesia (statis, tanpa API key). Ketuk ikon salin untuk menyimpan kode."
                    }
                )
            }
        }
    }
}

@Composable
private fun LevelChips(
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(tag)) {
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
private fun RegionRow(region: Region, onCopy: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = region.name, style = MaterialTheme.typography.bodyMedium)
                Text(text = region.id, style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code")
            }
        }
    }
}
