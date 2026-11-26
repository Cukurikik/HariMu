package com.harimu.app.feature.nusantara

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.core.nusantara.Doa

@Composable
fun DoasScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: DoasViewModel = viewModel(factory = DoasViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.load(isEnglish)
    }

    NusantaraScaffold(
        title = if (isEnglish) "Daily Duas" else "Doa Harian",
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
                    hint = if (isEnglish) "Search a dua" else "Cari doa",
                    tag = "doas_search"
                )
            }

            if (state.isLoading) item(key = "loading") { NusantaraLoading() }

            state.error?.let { message ->
                item(key = "error") {
                    NusantaraErrorCard(message = message, isEnglish = isEnglish) { vm.load(isEnglish) }
                }
            }

            items(state.visible, key = { it.id }) { doa ->
                DoaCard(doa = doa, expanded = state.selected?.id == doa.id) { vm.select(doa) }
            }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: doa-doa-api (open dataset, no API key). Verify wording with a trusted teacher."
                    } else {
                        "Sumber: doa-doa-api (dataset terbuka, tanpa API key). Cocokkan bacaan dengan guru tepercaya."
                    }
                )
            }
        }
    }
}

@Composable
private fun DoaCard(doa: Doa, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = doa.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (expanded) {
                if (doa.arabic.isNotBlank()) {
                    Text(
                        text = doa.arabic,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                if (doa.latin.isNotBlank()) {
                    Text(text = doa.latin, style = MaterialTheme.typography.bodySmall)
                }
                if (doa.meaning.isNotBlank()) {
                    Text(text = doa.meaning, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    text = doa.meaning,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
    }
}
