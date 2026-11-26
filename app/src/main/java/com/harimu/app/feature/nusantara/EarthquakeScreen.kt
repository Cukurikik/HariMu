package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.nusantara.Earthquake

@Composable
fun EarthquakeScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: EarthquakeViewModel = viewModel(factory = EarthquakeViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(isEnglish) { vm.load(isEnglish) }

    NusantaraScaffold(
        title = if (isEnglish) "Latest Earthquakes" else "Gempa Terkini",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "intro") {
                NusantaraIntroCard(
                    if (isEnglish) {
                        "Live earthquake feed from Indonesia's official BMKG service (no API key)."
                    } else {
                        "Umpan gempa langsung dari BMKG (resmi, tanpa API key)."
                    }
                )
            }

            if (state.isLoading) {
                item(key = "loading") { NusantaraLoading() }
            }

            state.error?.let { message ->
                item(key = "error") {
                    NusantaraErrorCard(message = message, isEnglish = isEnglish) { vm.load(isEnglish) }
                }
            }

            state.snapshot?.let { snapshot ->
                item(key = "latest") {
                    LatestEarthquakeCard(earthquake = snapshot.latest, isEnglish = isEnglish)
                }
                item(key = "recent-title") {
                    Text(
                        text = if (isEnglish) "Recent earthquakes" else "Gempa terkini",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(snapshot.recent, key = { "${it.dateTime}-${it.region}" }) { quake ->
                    EarthquakeRow(quake = quake)
                }
            }

            item(key = "source") {
                NusantaraSourceNote(
                    if (isEnglish) {
                        "Source: BMKG (data.bmkg.go.id). Follow official BMKG advisories for safety decisions."
                    } else {
                        "Sumber: BMKG (data.bmkg.go.id). Ikuti imbauan resmi BMKG untuk keputusan keselamatan."
                    }
                )
            }
        }
    }
}

@Composable
private fun LatestEarthquakeCard(earthquake: Earthquake, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isEnglish) "Magnitude" else "Magnitudo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = "M ${earthquake.magnitude}", style = MonoTextStyle, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = earthquake.region,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = listOf(earthquake.date, earthquake.time, earthquake.depth)
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            if (earthquake.potential.isNotBlank()) {
                Text(
                    text = (if (isEnglish) "Potential: " else "Potensi: ") + earthquake.potential,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (earthquake.felt.isNotBlank()) {
                Text(
                    text = (if (isEnglish) "Felt in: " else "Dirasakan di: ") + earthquake.felt,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (earthquake.coordinates.isNotBlank()) {
                Text(
                    text = earthquake.coordinates,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EarthquakeRow(quake: Earthquake) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "M ${quake.magnitude}", style = MonoTextStyle, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = quake.region, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = listOf(quake.date, quake.time, quake.depth).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
