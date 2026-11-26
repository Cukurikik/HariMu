package com.harimu.app.feature.nusantara

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.core.design.MonoTextStyle
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

    LaunchedEffect(isEnglish) { vm.loadProvinces(isEnglish) }

    val title = when {
        state.regency != null -> state.regency?.name.orEmpty()
        state.province != null -> state.province?.name.orEmpty()
        else -> if (isEnglish) "Islamic Boarding Schools" else "Data Pesantren"
    }

    NusantaraScaffold(
        title = title,
        onBack = {
            when {
                state.regency != null -> vm.backToRegencies()
                state.province != null -> vm.backToProvinces()
                else -> onBack()
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) item(key = "loading") { NusantaraLoading() }

            state.error?.let { message ->
                item(key = "error") {
                    NusantaraErrorCard(message = message, isEnglish = isEnglish) { vm.retry(isEnglish) }
                }
            }

            when {
                state.regency == null && state.province == null -> {
                    item(key = "intro") {
                        NusantaraIntroCard(
                            if (isEnglish) {
                                "Browse pesantren data from the Ministry of Religious Affairs: " +
                                    "pick a province, then a regency or city."
                            } else {
                                "Telusuri data pesantren Kementerian Agama: pilih provinsi, " +
                                    "lalu kabupaten atau kota."
                            }
                        )
                    }

                    items(state.provinces, key = { it.id }) { province ->
                        RegionRow(region = province) { vm.selectProvince(province, isEnglish) }
                    }
                }

                state.regency == null -> {
                    item(key = "province") {
                        Text(
                            text = state.province?.name.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(state.regencies, key = { it.id }) { regency ->
                        RegionRow(region = regency) { vm.selectRegency(regency, isEnglish) }
                    }
                }

                else -> {
                    item(key = "search") {
                        SearchField(
                            value = state.query,
                            onValueChange = vm::onQueryChange,
                            hint = if (isEnglish) "Filter by name, address, or cleric" else {
                                "Saring nama, alamat, atau kiai"
                            },
                            tag = "pesantren_search"
                        )
                    }

                    item(key = "count") {
                        Text(
                            text = if (isEnglish) {
                                "${state.visiblePesantren.size} of ${state.pesantren.size} pesantren"
                            } else {
                                "${state.visiblePesantren.size} dari ${state.pesantren.size} pesantren"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (state.pesantren.isEmpty() && !state.isLoading && state.error == null) {
                        item(key = "empty") {
                            Text(
                                text = if (isEnglish) {
                                    "No pesantren recorded for this regency."
                                } else {
                                    "Belum ada pesantren yang tercatat untuk kabupaten/kota ini."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    items(state.visiblePesantren, key = { it.name + it.registrationNumber }) { pesantren ->
                        PesantrenCard(pesantren = pesantren, isEnglish = isEnglish)
                    }
                }
            }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: api-pesantren-indonesia by nasrul21 (Kemenag data, no API key)."
                    } else {
                        "Sumber: api-pesantren-indonesia oleh nasrul21 (data Kemenag, tanpa API key)."
                    }
                )
            }
        }
    }
}

@Composable
private fun RegionRow(region: PesantrenRegion, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = region.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = region.id,
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PesantrenCard(pesantren: Pesantren, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pesantren.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (pesantren.registrationNumber.isNotBlank()) {
                Text(
                    text = "NSPP ${pesantren.registrationNumber}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pesantren.address.isNotBlank()) {
                Text(text = pesantren.address, style = MaterialTheme.typography.bodyMedium)
            }
            if (pesantren.cleric.isNotBlank()) {
                Text(
                    text = (if (isEnglish) "Cleric: " else "Kiai: ") + pesantren.cleric,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
