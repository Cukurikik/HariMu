package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.SundaneseEntry

@Composable
fun SundaneseScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: SundaneseViewModel = viewModel(factory = SundaneseViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    NusantaraScaffold(
        title = if (isEnglish) "Sundanese Dictionary" else "Kamus Basa Sunda",
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
                    hint = if (isEnglish) "A Sundanese or Indonesian word" else "Kata Sunda atau Indonesia",
                    tag = "sundanese_search"
                )
            }

            item(key = "action") {
                Button(
                    onClick = { vm.lookup(isEnglish) },
                    enabled = state.query.isNotBlank() && !state.isLoading
                ) {
                    Text(if (isEnglish) "Look up" else "Cari arti")
                }
            }

            if (!state.searched) {
                item(key = "intro") {
                    NusantaraIntroCard(
                        if (isEnglish) {
                            "Undak usuk basa: every entry lists the level used for oneself, for elders, " +
                                "and for peers, plus the Indonesian and English meaning."
                        } else {
                            "Undak usuk basa: setiap entri memuat tingkat ke diri sendiri, ke orang yang " +
                                "dihormati, dan ke sebaya, beserta arti Indonesia dan Inggris."
                        }
                    )
                }
            }

            if (state.isLoading) item(key = "loading") { NusantaraLoading() }

            state.error?.let { message ->
                item(key = "error") {
                    NusantaraErrorCard(message = message, isEnglish = isEnglish) { vm.lookup(isEnglish) }
                }
            }

            if (state.searched && !state.isLoading && state.error == null) {
                if (state.entries.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = if (isEnglish) {
                                "\"${state.lookedUpWord}\" is not in the dictionary yet."
                            } else {
                                "\"${state.lookedUpWord}\" belum ada di kamus."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                items(state.entries) { entry -> SundaneseCard(entry = entry, isEnglish = isEnglish) }
            }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: hibersunda by Hiberin Labs (open Sundanese dictionary, no API key)."
                    } else {
                        "Sumber: hibersunda oleh Hiberin Labs (kamus Sunda terbuka, tanpa API key)."
                    }
                )
            }
        }
    }
}

@Composable
private fun SundaneseCard(entry: SundaneseEntry, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelRow(if (isEnglish) "To oneself (sorangan)" else "Ke diri (sorangan)", entry.sorangan)
            LevelRow(if (isEnglish) "To elders (batur)" else "Ke yang dihormati (batur)", entry.batur)
            LevelRow(if (isEnglish) "Casual (loma)" else "Sehari-hari (loma)", entry.loma)
            LevelRow("Indonesia", entry.indonesian)
            LevelRow("English", entry.english)
        }
    }
}

@Composable
private fun LevelRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
