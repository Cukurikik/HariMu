package com.harimu.app.feature.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.utilities.UtilityActionButton
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityBadgePill
import com.harimu.app.core.design.utilities.UtilityCopyIconButton
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityQuickChips
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.prayer.PrayerSchedule
import com.harimu.app.core.toolapi.ToolGroup
import java.time.LocalDate
import java.time.LocalTime

private val POPULAR_PRAYER_CITIES = listOf(
    "Jakarta",
    "Bandung",
    "Surabaya",
    "Yogyakarta",
    "Semarang",
    "Medan",
    "Makassar",
    "Denpasar"
)

@Composable
fun PrayerTimesScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: PrayerTimesViewModel = viewModel(factory = PrayerTimesViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing
    val todayLabel = remember { LocalDate.now().toString() }

    UtilityScreenScaffold(
        title = stringResource(R.string.prayer_title),
        onBack = onBack,
        onRefresh = if (state.cityQuery.isNotBlank()) {
            { vm.refresh(isEnglish) }
        } else {
            null
        },
        isRefreshing = state.isLoading,
        backTestTag = "prayer_back_button",
        refreshTestTag = "prayer_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.prayer_title),
                    description = stringResource(R.string.prayer_intro),
                    badge = stringResource(R.string.prayer_badge),
                    accent = ToolGroup.CORE.accentColor()
                )
            }

            item(key = "search-form") {
                UtilitySectionCard {
                    UtilitySearchBar(
                        value = state.cityQuery,
                        onValueChange = vm::onCityQueryChange,
                        label = stringResource(R.string.prayer_city_label),
                        placeholder = stringResource(R.string.prayer_city_placeholder),
                        tag = "prayer_city_input",
                        enabled = !state.isLoading,
                        onSearch = { vm.search(isEnglish) }
                    )
                    UtilityQuickChips(
                        items = POPULAR_PRAYER_CITIES,
                        selectedKey = state.cityQuery.trim(),
                        keyOf = { it },
                        labelOf = { it },
                        label = stringResource(R.string.prayer_presets_label),
                        tag = "prayer_city_presets",
                        accent = ToolGroup.CORE.accentColor(),
                        onSelect = { city -> vm.selectPresetCity(city, isEnglish) }
                    )
                    UtilityActionButton(
                        label = stringResource(R.string.prayer_load_cta),
                        loadingLabel = stringResource(R.string.prayer_loading),
                        isLoading = state.isLoading,
                        onClick = { vm.search(isEnglish) },
                        testTag = "prayer_load_button"
                    )
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
                    UtilityLoadingCard(message = stringResource(R.string.prayer_loading))
                }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.refresh(isEnglish) },
                        enabled = !state.isLoading,
                        testTag = "prayer_retry_button"
                    )
                }
            }

            val schedule = state.schedule
            if (schedule != null && !state.isLoading) {
                item(key = "schedule") {
                    ScheduleCard(schedule = schedule, isEnglish = isEnglish)
                }
            } else if (!state.isLoading && state.error == null) {
                item(key = "empty") {
                    UtilityEmptyState(
                        icon = Icons.Filled.Mosque,
                        title = stringResource(R.string.prayer_empty_title),
                        subtitle = stringResource(R.string.prayer_empty_desc)
                    )
                }
            }

            item(key = "attribution") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.prayer_source),
                    freshnessNote = stringResource(R.string.prayer_freshness, todayLabel),
                    fetchedAtMillis = state.fetchedAtMillis,
                    onlineOnlyNote = null
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(schedule: PrayerSchedule, isEnglish: Boolean) {
    val rows = listOf(
        "Imsak" to schedule.imsak,
        (if (isEnglish) "Fajr" else "Subuh") to schedule.subuh,
        (if (isEnglish) "Sunrise" else "Terbit") to schedule.terbit,
        (if (isEnglish) "Duha" else "Dhuha") to schedule.dhuha,
        (if (isEnglish) "Dhuhr" else "Dzuhur") to schedule.dzuhur,
        (if (isEnglish) "Asr" else "Ashar") to schedule.ashar,
        "Maghrib" to schedule.maghrib,
        (if (isEnglish) "Isha" else "Isya") to schedule.isya
    )
    val nextIdx = remember(schedule) { findNextPrayerIndex(schedule, LocalTime.now()) }
    val copyText = remember(schedule, rows) {
        buildString {
            appendLine(schedule.city.ifBlank { schedule.cityId })
            if (schedule.dateLabel.isNotBlank()) appendLine(schedule.dateLabel)
            rows.forEach { (label, time) -> appendLine("$label: ${time.ifBlank { "—" }}") }
        }.trim()
    }

    UtilitySectionCard(
        highlighted = true,
        modifier = Modifier.testTag("prayer_schedule_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = schedule.city.ifBlank { schedule.cityId },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = listOf(schedule.region, schedule.dateLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            UtilityCopyIconButton(
                textToCopy = copyText,
                clipboardLabel = "prayer_schedule",
                contentDescription = stringResource(R.string.prayer_copy_schedule),
                testTag = "prayer_copy_schedule"
            )
        }

        if (nextIdx != null) {
            val (nextLabel, nextTime) = rows[nextIdx]
            HariMuInsetPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.prayer_next_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = nextLabel,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    UtilityBadgePill(
                        text = nextTime.ifBlank { "—" },
                        accent = ToolGroup.CORE.accentColor()
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        rows.forEachIndexed { index, (label, time) ->
            val isNext = index == nextIdx
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = if (isNext) {
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = time.ifBlank { "—" },
                    style = if (isNext) MonoTextStyle.copy(fontWeight = FontWeight.Bold) else MonoTextStyle,
                    color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
