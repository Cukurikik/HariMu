package com.harimu.app.feature.nusantara

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityBadgePill
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityQuickChips
import com.harimu.app.core.design.utilities.UtilityResultHeader
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
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
    val spacing = HariMuThemeTokens.spacing

    BackHandler(enabled = selected != null) {
        vm.closeLeague()
    }

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.loadLeagues(isEnglish)
    }

    UtilityScreenScaffold(
        title = selected?.name ?: stringResource(R.string.football_title),
        onBack = {
            if (selected != null) vm.closeLeague() else onBack()
        },
        onRefresh = {
            if (selected != null) vm.loadStandings(isEnglish) else vm.loadLeagues(isEnglish)
        },
        isRefreshing = state.isLoading || state.isLoadingStandings,
        backTestTag = "football_back_button",
        refreshTestTag = "football_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            if (selected == null) {
                item(key = "hero") {
                    UtilityHeroBanner(
                        title = stringResource(R.string.football_title),
                        description = if (isEnglish) {
                            "Check league standings across seasons from domestic Indonesian competitions to major international leagues."
                        } else {
                            "Pantau klasemen liga sepak bola lintas musim dari kompetisi domestik hingga liga utama dunia."
                        },
                        badge = stringResource(R.string.football_badge)
                    )
                }

                item(key = "search") {
                    UtilitySearchBar(
                        value = state.query,
                        onValueChange = vm::onQueryChange,
                        label = stringResource(R.string.football_search_hint),
                        tag = "football_search"
                    )
                }

                if (state.isLoading) {
                    item(key = "loading") { UtilityLoadingCard() }
                }

                state.error?.let { message ->
                    item(key = "error") {
                        UtilityErrorCard(
                            message = message,
                            onRetry = { vm.loadLeagues(isEnglish) },
                            testTag = "football_retry_button"
                        )
                    }
                }

                item(key = "count") {
                    UtilityResultHeader(
                        countText = stringResource(R.string.football_count, state.visibleLeagues.size)
                    )
                }

                if (state.loaded && !state.isLoading && state.error == null && state.visibleLeagues.isEmpty()) {
                    item(key = "empty") {
                        UtilityEmptyState(
                            icon = Icons.Filled.SportsSoccer,
                            title = stringResource(R.string.football_empty_leagues_title),
                            subtitle = stringResource(R.string.football_empty_leagues),
                            actionLabel = if (state.query.isNotBlank()) {
                                stringResource(R.string.utility_hub_reset_filter)
                            } else {
                                null
                            },
                            onAction = if (state.query.isNotBlank()) {
                                { vm.onQueryChange("") }
                            } else {
                                null
                            },
                            actionTag = "football_reset_filter"
                        )
                    }
                }

                items(state.visibleLeagues, key = { it.id }) { league ->
                    LeagueRow(league = league, onOpen = { vm.selectLeague(league, isEnglish) })
                }

                item(key = "source") {
                    UtilityAttributionFooter(
                        sourceText = stringResource(R.string.football_source),
                        fetchedAtMillis = state.fetchedAtMillis
                    )
                }
            } else {
                item(key = "seasons") {
                    UtilityQuickChips(
                        items = FootballUiState.SEASONS,
                        selectedKey = state.season.toString(),
                        keyOf = { it.toString() },
                        labelOf = { it.toString() },
                        label = stringResource(R.string.football_season_label),
                        tag = "football_seasons",
                        onSelect = { season -> vm.selectSeason(season, isEnglish) }
                    )
                }

                if (state.isLoadingStandings) {
                    item(key = "loading") { UtilityLoadingCard() }
                }

                state.error?.let { message ->
                    item(key = "error") {
                        UtilityErrorCard(
                            message = message,
                            onRetry = { vm.loadStandings(isEnglish) },
                            testTag = "football_standings_retry"
                        )
                    }
                }

                val standings = state.standings
                if (standings != null && !state.isLoadingStandings) {
                    item(key = "head") {
                        UtilityResultHeader(
                            countText = listOf(standings.league, standings.season)
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                        )
                    }

                    if (standings.rows.isEmpty()) {
                        item(key = "empty") {
                            UtilityEmptyState(
                                icon = Icons.Filled.SportsSoccer,
                                title = selected.name,
                                subtitle = stringResource(R.string.football_empty_standings)
                            )
                        }
                    }

                    items(standings.rows, key = { "${it.rank}_${it.team}" }) { row ->
                        StandingCard(row = row, isEnglish = isEnglish)
                    }
                }

                item(key = "back-leagues") {
                    OutlinedButton(
                        onClick = { vm.closeLeague() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("football_back_to_leagues")
                    ) {
                        Text(stringResource(R.string.football_back_leagues))
                    }
                }

                item(key = "source") {
                    UtilityAttributionFooter(
                        sourceText = stringResource(R.string.football_source),
                        fetchedAtMillis = state.standingsFetchedAtMillis ?: state.fetchedAtMillis
                    )
                }
            }
        }
    }
}

@Composable
private fun LeagueRow(league: FootballLeague, onOpen: () -> Unit) {
    UtilitySectionCard(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag("football_league_${league.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = league.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                if (league.abbreviation.isNotBlank()) {
                    Text(
                        text = league.abbreviation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StandingCard(row: StandingRow, isEnglish: Boolean) {
    UtilitySectionCard(highlighted = row.rank <= 4) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UtilityBadgePill(text = "#${row.rank}")
                Text(
                    text = row.team,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
            }
            UtilityBadgePill(
                text = if (isEnglish) "${row.points} pts" else "${row.points} poin"
            )
        }
        HariMuInsetPanel {
            Text(
                text = listOfNotNull(
                    if (isEnglish) "${row.played} played" else "${row.played} main",
                    if (isEnglish) "${row.wins} W" else "${row.wins} M",
                    if (isEnglish) "${row.draws} D" else "${row.draws} S",
                    if (isEnglish) "${row.losses} L" else "${row.losses} K",
                    row.goalDifference.takeIf { it.isNotBlank() }?.let {
                        (if (isEnglish) "GD " else "SG ") + it
                    }
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
