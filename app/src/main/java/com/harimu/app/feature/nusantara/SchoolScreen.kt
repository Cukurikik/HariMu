package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.nusantara.School

@Composable
fun SchoolScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: SchoolViewModel = viewModel(factory = SchoolViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    NusantaraScaffold(
        title = if (isEnglish) "Find a School" else "Cari Sekolah",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "search") {
                SearchField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    hint = if (isEnglish) "School name, min. 3 letters" else "Nama sekolah, minimal 3 huruf",
                    tag = "schools_search"
                )
            }

            item(key = "action") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.search(isEnglish) },
                        enabled = state.query.trim().length >= SchoolViewModel.MIN_QUERY_LENGTH && !state.isLoading
                    ) {
                        Text(if (isEnglish) "Search" else "Cari")
                    }
                }
            }

            if (!state.searched) {
                item(key = "intro") {
                    NusantaraIntroCard(
                        if (isEnglish) {
                            "Search schools from the Indonesian education ministry dataset by name. " +
                                "Results include NPSN, level, status, address, and coordinates."
                        } else {
                            "Cari sekolah dari dataset Kemendikdasmen berdasarkan nama. " +
                                "Hasil memuat NPSN, jenjang, status, alamat, dan koordinat."
                        }
                    )
                }
            }

            if (state.isLoading) item(key = "loading") { NusantaraLoading() }

            state.error?.let { message ->
                item(key = "error") {
                    NusantaraErrorCard(message = message, isEnglish = isEnglish) { vm.search(isEnglish) }
                }
            }

            if (state.searched && !state.isLoading && state.error == null) {
                item(key = "count") {
                    Text(
                        text = if (isEnglish) {
                            "${state.schools.size} of ${state.total} schools"
                        } else {
                            "${state.schools.size} dari ${state.total} sekolah"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.schools.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = if (isEnglish) {
                                "No school matches that name. Try a shorter keyword."
                            } else {
                                "Tidak ada sekolah dengan nama itu. Coba kata kunci yang lebih pendek."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                items(state.schools, key = { it.npsn.ifBlank { it.name } }) { school ->
                    SchoolCard(school = school, isEnglish = isEnglish)
                }

                if (state.hasMore) {
                    item(key = "more") {
                        OutlinedButton(
                            onClick = { vm.loadMore(isEnglish) },
                            enabled = !state.isLoadingMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (state.isLoadingMore) {
                                    if (isEnglish) "Loading…" else "Memuat…"
                                } else {
                                    if (isEnglish) "Load more" else "Muat lagi"
                                }
                            )
                        }
                    }
                }
            }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: api-sekolah-indonesia (public school dataset, no API key)."
                    } else {
                        "Sumber: api-sekolah-indonesia (dataset sekolah publik, tanpa API key)."
                    }
                )
            }
        }
    }
}

@Composable
private fun SchoolCard(school: School, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = school.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = listOf(
                    school.npsn.takeIf { it.isNotBlank() }?.let { "NPSN $it" },
                    school.form.takeIf { it.isNotBlank() },
                    school.status.takeIf { it.isNotBlank() }?.let {
                        if (isEnglish) {
                            if (it.equals("S", true)) "Private" else "Public"
                        } else {
                            if (it.equals("S", true)) "Swasta" else "Negeri"
                        }
                    }
                ).filterNotNull().joinToString(" • "),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (school.address.isNotBlank()) {
                Text(text = school.address, style = MaterialTheme.typography.bodyMedium)
            }
            val area = listOf(school.district, school.regency, school.province)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (area.isNotBlank()) {
                Text(
                    text = area,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (school.latitude.isNotBlank() && school.longitude.isNotBlank()) {
                Text(
                    text = "${school.latitude}, ${school.longitude}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
