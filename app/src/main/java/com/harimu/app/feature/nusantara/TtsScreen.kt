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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.nusantara.TtsAnswer

@Composable
fun TtsScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: TtsViewModel = viewModel(factory = TtsViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    NusantaraScaffold(
        title = if (isEnglish) "Crossword Helper" else "Bantu TTS",
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
                    hint = if (isEnglish) "Clue, e.g. \"tidak\"" else "Petunjuk, mis. \"tidak\"",
                    tag = "tts_search"
                )
            }

            item(key = "action") {
                Button(
                    onClick = { vm.search(isEnglish) },
                    enabled = state.query.isNotBlank() && !state.isLoading
                ) {
                    Text(if (isEnglish) "Find answers" else "Cari jawaban")
                }
            }

            if (!state.searched) {
                item(key = "intro") {
                    NusantaraIntroCard(
                        if (isEnglish) {
                            "Type a crossword clue and get candidate words with a star rating. " +
                                "Stars show how often the answer fits the clue."
                        } else {
                            "Tulis petunjuk TTS dan dapatkan kata-kata yang mungkin, lengkap dengan bintang. " +
                                "Bintang menunjukkan seberapa sering jawaban itu cocok."
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

            val result = state.result
            if (result != null && !state.isLoading && state.error == null) {
                item(key = "total") {
                    Text(
                        text = if (isEnglish) {
                            "${result.total} answers found"
                        } else {
                            "${result.total} jawaban ditemukan"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (result.answers.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = if (isEnglish) {
                                "No answer yet for that clue. Try another wording."
                            } else {
                                "Belum ada jawaban untuk petunjuk itu. Coba kata lain."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                items(result.answers) { answer -> TtsAnswerCard(answer) }
            }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: kunci-tts-api (community crossword answer bank, no API key)."
                    } else {
                        "Sumber: kunci-tts-api (bank jawaban TTS komunitas, tanpa API key)."
                    }
                )
            }
        }
    }
}

@Composable
private fun TtsAnswerCard(answer: TtsAnswer) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = answer.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "★".repeat(answer.stars.coerceIn(0, 5)),
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (answer.clue.isNotBlank()) {
                Text(
                    text = answer.clue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
