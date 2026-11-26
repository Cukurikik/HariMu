package com.harimu.app.feature.prayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.harimu.app.core.prayer.PrayerSchedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: PrayerTimesViewModel = viewModel(factory = PrayerTimesViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEnglish) "Prayer Times" else "Jadwal Sholat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isEnglish) {
                            "Look up today's prayer schedule for an Indonesian city (source: Quran API ID, no API key)."
                        } else {
                            "Cari jadwal sholat hari ini untuk kota di Indonesia (sumber: Quran API ID, tanpa API key)."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = state.cityQuery,
                        onValueChange = vm::onCityQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prayer_city_input"),
                        singleLine = true,
                        enabled = !state.isLoading,
                        label = { Text(if (isEnglish) "City / Regency" else "Kota / Kabupaten") }
                    )
                    Button(
                        onClick = { vm.search(isEnglish) },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.isLoading) {
                                if (isEnglish) "Loading…" else "Memuat…"
                            } else {
                                if (isEnglish) "Load Prayer Times" else "Muat Jadwal Sholat"
                            }
                        )
                    }
                }
            }

            state.error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isEnglish) "Could not load schedule" else "Gagal memuat jadwal",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(onClick = { vm.refresh(isEnglish) }, enabled = !state.isLoading) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isEnglish) "Retry" else "Coba Lagi")
                        }
                    }
                }
            }

            state.schedule?.let { schedule ->
                ScheduleCard(schedule = schedule, isEnglish = isEnglish)
            }

            Text(
                text = if (isEnglish) {
                    "Source: api.myquran.com (Quran API ID). Times are for the device's current date."
                } else {
                    "Sumber: api.myquran.com (Quran API ID). Jadwal mengikuti tanggal perangkat saat ini."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScheduleCard(schedule: PrayerSchedule, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = schedule.city.ifBlank { schedule.cityId },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = listOf(schedule.region, schedule.dateLabel)
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
            rows.forEach { (label, time) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(time.ifBlank { "—" }, style = MonoTextStyle)
                }
            }
        }
    }
}
