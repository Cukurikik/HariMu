package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.harimu.app.AppContainer
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.nusantara.Hero
import com.harimu.app.core.nusantara.Volcano

@Composable
fun HeroesScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: HeroesViewModel = viewModel(factory = HeroesViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.load(isEnglish)
    }

    NusantaraScaffold(
        title = if (isEnglish) "National Heroes" else "Pahlawan Nasional",
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
                    hint = if (isEnglish) "Search a hero name or deed" else "Cari nama pahlawan atau jasanya",
                    tag = "heroes_search"
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
                        "${state.visible.size} heroes"
                    } else {
                        "${state.visible.size} pahlawan"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(state.visible, key = { it.name }) { hero -> HeroCard(hero = hero, isEnglish = isEnglish) }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: indonesia-public-static-api (static reference data, no API key)."
                    } else {
                        "Sumber: indonesia-public-static-api (data referensi statis, tanpa API key)."
                    }
                )
            }
        }
    }
}

@Composable
fun VolcanoesScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: VolcanoesViewModel = viewModel(factory = VolcanoesViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.load(isEnglish)
    }

    NusantaraScaffold(
        title = if (isEnglish) "Indonesian Volcanoes" else "Gunung Berapi Indonesia",
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
                    hint = if (isEnglish) "Search a volcano name or coordinates" else "Cari nama gunung atau koordinat",
                    tag = "volcanoes_search"
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
                        "${state.visible.size} volcanoes"
                    } else {
                        "${state.visible.size} gunung"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(state.visible, key = { it.name }) { volcano -> VolcanoCard(volcano = volcano, isEnglish = isEnglish) }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: indonesia-public-static-api (static reference data, no API key)."
                    } else {
                        "Sumber: indonesia-public-static-api (data referensi statis, tanpa API key)."
                    }
                )
            }
        }
    }
}

@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    tag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        singleLine = true,
        label = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
    )
}

@Composable
private fun HeroCard(hero: Hero, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = hero.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = listOf(
                    (if (isEnglish) "Born " else "Lahir ") + hero.birthYear.ifBlank { "—" },
                    (if (isEnglish) "Died " else "Wafat ") + hero.deathYear.ifBlank { "—" },
                    (if (isEnglish) "Declared " else "Ditetapkan ") + hero.ascensionYear.ifBlank { "—" }
                ).joinToString(" • "),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = hero.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VolcanoCard(volcano: Volcano, isEnglish: Boolean) {
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
                    text = volcano.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = volcano.height, style = MonoTextStyle, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = listOf(volcano.form, volcano.geolocation).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = (if (isEnglish) "Last eruption: " else "Letusan terakhir: ") +
                    volcano.lastEruption.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
