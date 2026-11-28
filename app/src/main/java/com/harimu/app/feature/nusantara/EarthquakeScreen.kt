package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuStatusBanner
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.StatusTone
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityBadgePill
import com.harimu.app.core.design.utilities.UtilityCopyIconButton
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityResultHeader
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.nusantara.Earthquake
import com.harimu.app.core.toolapi.ToolGroup

@Composable
fun EarthquakeScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: EarthquakeViewModel = viewModel(factory = EarthquakeViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    LaunchedEffect(Unit) {
        if (state.snapshot == null && !state.isLoading) vm.load(isEnglish)
    }

    UtilityScreenScaffold(
        title = stringResource(R.string.earthquake_title),
        onBack = onBack,
        onRefresh = { vm.load(isEnglish) },
        isRefreshing = state.isLoading,
        backTestTag = "earthquake_back_button",
        refreshTestTag = "earthquake_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.earthquake_title),
                    description = stringResource(R.string.earthquake_intro),
                    badge = stringResource(R.string.earthquake_badge)
                )
            }

            if (state.isLoading) {
                item(key = "loading") {
                    UtilityLoadingCard(message = stringResource(R.string.earthquake_loading))
                }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.load(isEnglish) },
                        testTag = "earthquake_retry_button"
                    )
                }
            }

            if (state.isStale) {
                item(key = "stale") {
                    HariMuStatusBanner(
                        message = stringResource(R.string.utility_stale_data_notice),
                        tone = StatusTone.WARNING
                    )
                }
            }

            state.snapshot?.let { snapshot ->
                item(key = "latest") {
                    LatestEarthquakeCard(earthquake = snapshot.latest)
                }
                item(key = "recent-title") {
                    UtilityResultHeader(
                        countText = stringResource(R.string.earthquake_recent_title, snapshot.recent.size)
                    )
                }
                if (snapshot.recent.isEmpty()) {
                    item(key = "recent-empty") {
                        UtilityEmptyState(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.earthquake_title),
                            subtitle = stringResource(R.string.earthquake_no_recent)
                        )
                    }
                } else {
                    items(snapshot.recent, key = { "${it.dateTime}-${it.date}-${it.time}-${it.region}" }) { quake ->
                        EarthquakeRow(quake = quake)
                    }
                }
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.earthquake_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
private fun LatestEarthquakeCard(earthquake: Earthquake) {
    val accent = ToolGroup.NUSANTARA.accentColor()
    val hasTsunamiWarning = earthquake.potential.contains("tsunami", ignoreCase = true) &&
        !earthquake.potential.contains("tidak berpotensi", ignoreCase = true)

    UtilitySectionCard(
        highlighted = true,
        modifier = Modifier.testTag("earthquake_latest_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.earthquake_latest_tag),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
            )
            UtilityBadgePill(text = "M ${earthquake.magnitude}", accent = accent)
        }

        Text(
            text = earthquake.region,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        HariMuInsetPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = listOf(earthquake.date, earthquake.time)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        style = MonoTextStyle
                    )
                    if (earthquake.depth.isNotBlank()) {
                        Text(
                            text = "${stringResource(R.string.earthquake_depth)}: ${earthquake.depth}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (earthquake.potential.isNotBlank()) {
            HariMuStatusBanner(
                tone = if (hasTsunamiWarning) StatusTone.WARNING else StatusTone.INFO,
                message = stringResource(R.string.earthquake_potential, earthquake.potential)
            )
        }

        if (earthquake.felt.isNotBlank()) {
            Text(
                text = stringResource(R.string.earthquake_felt, earthquake.felt),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (earthquake.coordinates.isNotBlank()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.earthquake_coordinates),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = earthquake.coordinates,
                        style = MonoTextStyle
                    )
                }
                UtilityCopyIconButton(
                    textToCopy = earthquake.coordinates,
                    clipboardLabel = "earthquake_coordinates",
                    contentDescription = stringResource(R.string.earthquake_copy_coords),
                    testTag = "earthquake_copy_coords"
                )
            }
        }

        if (earthquake.shakemap.isNotBlank()) {
            val shakemapUrl = "https://data.bmkg.go.id/DataMKG/TEWS/${earthquake.shakemap}"
            AsyncImage(
                model = shakemapUrl,
                contentDescription = stringResource(R.string.earthquake_shakemap),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(HariMuThemeTokens.shapes.card)
                    .testTag("earthquake_shakemap_image")
            )
        }
    }
}

@Composable
private fun EarthquakeRow(quake: Earthquake) {
    val accent = ToolGroup.NUSANTARA.accentColor()
    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UtilityBadgePill(text = "M ${quake.magnitude}", accent = accent)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = quake.region,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = listOf(quake.date, quake.time, quake.depth)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (quake.felt.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.earthquake_felt, quake.felt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (quake.coordinates.isNotBlank()) {
                UtilityCopyIconButton(
                    textToCopy = quake.coordinates,
                    clipboardLabel = "earthquake_coordinates",
                    contentDescription = stringResource(R.string.earthquake_copy_coords)
                )
            }
        }
    }
}
