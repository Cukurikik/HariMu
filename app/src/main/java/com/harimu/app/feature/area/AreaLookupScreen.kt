package com.harimu.app.feature.area

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.core.area.AreaEntry
import com.harimu.app.core.design.MonoTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaLookupScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: AreaLookupViewModel = viewModel(factory = AreaLookupViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    fun runSearch() {
        keyboard?.hide()
        vm.search(isEnglish)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "Postal Code & Area Search" else "Cari Kode Pos & Wilayah") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "search-form") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isEnglish) {
                                "Find a village, district, city, or postal code across Indonesia " +
                                    "(source: thecloudalert, no API key)."
                            } else {
                                "Cari desa/kelurahan, kecamatan, kabupaten/kota, atau kode pos " +
                                    "di seluruh Indonesia (sumber: thecloudalert, tanpa API key)."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = state.keyword,
                            onValueChange = vm::onKeywordChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("area_keyword_input"),
                            singleLine = true,
                            enabled = !state.isLoading,
                            label = {
                                Text(if (isEnglish) "Area or postal code" else "Wilayah atau kode pos")
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch() })
                        )
                        Button(
                            onClick = { runSearch() },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.isLoading) {
                                    if (isEnglish) "Searching…" else "Mencari…"
                                } else {
                                    if (isEnglish) "Search" else "Cari"
                                }
                            )
                        }
                    }
                }
            }

            state.error?.let { message ->
                item(key = "error") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "Could not search" else "Gagal mencari",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            OutlinedButton(onClick = { vm.refresh(isEnglish) }, enabled = !state.isLoading) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isEnglish) "Retry" else "Coba Lagi")
                            }
                        }
                    }
                }
            }

            if (state.hasSearched && state.entries.isEmpty() && state.error == null) {
                item(key = "empty") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isEnglish) {
                                "No area matched that keyword. Try a village name, district, city, or postal code."
                            } else {
                                "Tidak ada wilayah yang cocok. Coba nama desa, kecamatan, kota, atau kode pos."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            if (state.entries.isNotEmpty()) {
                item(key = "result-count") {
                    Text(
                        text = if (isEnglish) {
                            "${state.entries.size} result(s)"
                        } else {
                            "${state.entries.size} hasil"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(state.entries, key = { entryKey(it) }) { entry ->
                    AreaCard(entry = entry, isEnglish = isEnglish)
                }
            }

            item(key = "source") {
                Text(
                    text = if (isEnglish) {
                        "Source: alamat.thecloudalert.com (Indonesian Address Provider API, MIT)."
                    } else {
                        "Sumber: alamat.thecloudalert.com (Indonesian Address Provider API, MIT)."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun AreaCard(entry: AreaEntry, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.desakel.ifBlank { entry.kecamatan.ifBlank { "—" } },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = entry.kodepos.ifBlank { "—" },
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = listOf(entry.kecamatan, entry.kabkota).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text(
                text = if (isEnglish) {
                    "Province: ${entry.provinsi.ifBlank { "—" }}"
                } else {
                    "Provinsi: ${entry.provinsi.ifBlank { "—" }}"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
