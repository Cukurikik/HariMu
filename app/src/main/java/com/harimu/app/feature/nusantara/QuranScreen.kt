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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.nusantara.QuranSurah
import com.harimu.app.core.nusantara.QuranVerse

@Composable
fun QuranScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: QuranViewModel = viewModel(factory = QuranViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val detail = state.detail

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.load(isEnglish)
    }

    NusantaraScaffold(
        title = detail?.let { "Surah ${it.surah.name}" }
            ?: if (isEnglish) "Qur'an" else "Al-Qur'an",
        onBack = {
            if (detail != null) vm.closeSurah() else onBack()
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (detail == null) {
                item(key = "search") {
                    SearchField(
                        value = state.query,
                        onValueChange = vm::onQueryChange,
                        hint = if (isEnglish) "Surah name or number" else "Nama atau nomor surah",
                        tag = "quran_search"
                    )
                }

                if (state.isLoading) item(key = "loading") { NusantaraLoading() }

                state.error?.let { message ->
                    item(key = "error") {
                        NusantaraErrorCard(message = message, isEnglish = isEnglish) { vm.load(isEnglish) }
                    }
                }

                item(key = "count") {
                    Text(
                        text = if (isEnglish) {
                            "${state.visible.size} surahs"
                        } else {
                            "${state.visible.size} surah"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.loaded && state.visible.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = if (isEnglish) {
                                "No surah matches that search."
                            } else {
                                "Tidak ada surah yang cocok dengan pencarian itu."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                items(state.visible, key = { it.number }) { surah ->
                    SurahRow(surah = surah, isEnglish = isEnglish) { vm.openSurah(surah.number, isEnglish) }
                }

                item(key = "source") {
                    NusantaraSourceNote(
                        if (isEnglish) {
                            "Source: Quran API ID (Kemenag text, translation, and audio, no API key)."
                        } else {
                            "Sumber: Quran API ID (teks Kemenag, terjemahan, dan audio, tanpa API key)."
                        }
                    )
                }
            } else {
                if (state.isLoadingDetail) item(key = "detail-loading") { NusantaraLoading() }

                item(key = "detail-head") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${detail.surah.number}. ${detail.surah.name}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = listOf(
                                    if (isEnglish) detail.surah.translationEn else detail.surah.translationId,
                                    detail.surah.revelationId,
                                    if (isEnglish) {
                                        "${detail.surah.verseCount} verses"
                                    } else {
                                        "${detail.surah.verseCount} ayat"
                                    }
                                ).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(detail.verses) { verse -> VerseCard(verse = verse, isEnglish = isEnglish) }

                item(key = "back") {
                    OutlinedButton(
                        onClick = { vm.closeSurah() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isEnglish) "Back to surah list" else "Kembali ke daftar surah")
                    }
                }

                item(key = "source") {
                    NusantaraSourceNote(
                        if (isEnglish) {
                            "Source: Quran API ID. For worship, verify with a printed mushaf."
                        } else {
                            "Sumber: Quran API ID. Untuk ibadah, cocokkan dengan mushaf tercetak."
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SurahRow(surah: QuranSurah, isEnglish: Boolean, onClick: () -> Unit) {
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
                text = surah.number.toString().padStart(3, '0'),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = surah.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOf(
                        if (isEnglish) surah.translationEn else surah.translationId,
                        surah.revelationId
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = surah.verseCount.toString(),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VerseCard(verse: QuranVerse, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${verse.number}.",
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = verse.arabic,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            if (verse.latin.isNotBlank()) {
                Text(
                    text = verse.latin,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val translation = if (isEnglish) verse.translationEn else verse.translationId
            Text(
                text = translation.ifBlank { verse.translationId.ifBlank { verse.translationEn } },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
