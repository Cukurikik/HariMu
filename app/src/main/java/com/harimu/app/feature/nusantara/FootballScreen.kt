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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.harimu.app.core.nusantara.FootballLeague
import com.harimu.app.core.nusantara.StandingRow

@Composable
fun FootballScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: FootballViewModel = viewModel(factory = FootballViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val selected = state.selected

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.loadLeagues(isEnglish)
    }

    NusantaraScaffold(
        title = selected?.name ?: if (isEnglish) "Football Standings" else "Klasemen Sepak Bola",
        onBack = {
            if (selected != null) vm.closeLeague() else onBack()
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selected == null) {
                item(key = "search") {
                    SearchField(
                        value = state.query,
                        onValueChange = vm::onQueryChange,
                        hint = if (isEnglish) "League name, e.g. Premier" else "Nama liga, mis. Premier",
                        tag = "football_search"
                    )
                }

                if (state.isLoading) item(key = "loading") { NusantaraLoading() }

                state.error?.let { message ->
                    item(key = "error") {
                        NusantaraErrorCard(message = message, isEnglish = isEnglish) {
                            vm.loadLeagues(isEnglish)
                        }
                    }
                }

                item(key = "count") {
                    Text(
                        text = if (isEnglish) {
                            "${state.visibleLeagues.size} leagues"
                        } else {
                            "${state.visibleLeagues.size} liga"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(state.visibleLeagues, key = { it.id }) { league ->
                    LeagueRow(league = league, onOpen = { vm.selectLeague(league, isEnglish) })
                }

                item(key = "source") {
                    NusantaraSourceNote(
                        if (isEnglish) {
                            "Source: football-standings-api by azharimm (ESPN data, no API key)."
                        } else {
                            "Sumber: football-standings-api oleh azharimm (data ESPN, tanpa API key)."
                        }
                    )
                }
            } else {
                item(key = "seasons") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FootballUiState.SEASONS.forEach { season ->
                            val label = season.toString()
                            if (season == state.season) {
                                Button(onClick = { vm.selectSeason(season, isEnglish) }) { Text(label) }
                            } else {
                                OutlinedButton(onClick = { vm.selectSeason(season, isEnglish) }) { Text(label) }
                            }
                        }
                    }
                }

                if (state.isLoadingStandings) item(key = "loading") { NusantaraLoading() }

                state.error?.let { message ->
                    item(key = "error") {
                        NusantaraErrorCard(message = message, isEnglish = isEnglish) {
                            vm.loadStandings(isEnglish)
                        }
                    }
                }

                val standings = state.standings
                if (standings != null && !state.isLoadingStandings) {
                    item(key = "head") {
                        Text(
                            text = listOf(standings.league, standings.season)
                                .filter { it.isNotBlank() }
                                .joinToString(" "),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (standings.rows.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = if (isEnglish) {
                                    "No standings for that season yet."
                                } else {
                                    "Belum ada klasemen untuk musim itu."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    items(standings.rows, key = { it.rank }) { row ->
                        StandingCard(row = row, isEnglish = isEnglish)
                    }
                }

                item(key = "source") {
                    NusantaraSourceNote(
                        if (isEnglish) {
                            "Source: football-standings-api by azharimm (ESPN data, no API key)."
                        } else {
                            "Sumber: football-standings-api oleh azharimm (data ESPN, tanpa API key)."
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LeagueRow(league: FootballLeague, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = league.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (league.abbreviation.isNotBlank()) {
                    Text(
                        text = league.abbreviation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(text = "›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StandingCard(row: StandingRow, isEnglish: Boolean) {
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
                    text = "${row.rank}. ${row.team}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${row.points}",
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = listOf(
                    if (isEnglish) "${row.played} played" else "${row.played} main",
                    if (isEnglish) "${row.wins} W" else "${row.wins} M",
                    if (isEnglish) "${row.draws} D" else "${row.draws} S",
                    if (isEnglish) "${row.losses} L" else "${row.losses} K",
                    if (row.goalDifference.isNotBlank()) {
                        (if (isEnglish) "GD " else "SG ") + row.goalDifference
                    } else {
                        null
                    }
                ).filterNotNull().joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
