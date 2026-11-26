package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry

@Composable
fun NusantaraHubScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onOpenTool: (Tool) -> Unit
) {
    val vm: NusantaraHubViewModel = viewModel(factory = NusantaraHubViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val tools = ToolRegistry.byGroup(ToolGroup.NUSANTARA)

    LaunchedEffect(isEnglish) {
        if (state.latest == null && !state.isLoading) vm.loadLatest(isEnglish)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = ToolGroup.NUSANTARA.accentColor().copy(alpha = 0.14f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isEnglish) "Indonesian public data" else "Data publik Indonesia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isEnglish) {
                            "Earthquakes, BMKG weather, regions, heroes, volcanoes, and daily duas — " +
                                "all from keyless Indonesian public APIs."
                        } else {
                            "Gempa, cuaca BMKG, wilayah, pahlawan, gunung berapi, dan doa harian — " +
                                "semuanya dari API publik Indonesia tanpa kunci."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item(key = "quake") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isEnglish) "Latest earthquake (BMKG)" else "Gempa terbaru (BMKG)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.isLoading) {
                        NusantaraLoading()
                    } else {
                        val quake = state.latest
                        if (quake != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quake.region,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "M ${quake.magnitude}",
                                    style = MonoTextStyle,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = listOf(quake.date, quake.time, quake.depth)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.error?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(onClick = { vm.loadLatest(isEnglish) }) {
                            Text(if (isEnglish) "Refresh feed" else "Segarkan data")
                        }
                    }
                }
            }
        }

        item(key = "tools-title") {
            Text(
                text = if (isEnglish) "All Nusantara tools" else "Semua tool Nusantara",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(tools, key = { it.id }) { tool ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenTool(tool) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = ToolGroup.NUSANTARA.accentColor()
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = tool.title(isEnglish), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = tool.description(isEnglish),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "source") {
            NusantaraSourceNote(
                if (isEnglish) {
                    "Sources: BMKG, emsifa, indonesia-public-static-api, doa-doa-api. " +
                        "Every call is keyless and free."
                } else {
                    "Sumber: BMKG, emsifa, indonesia-public-static-api, doa-doa-api. " +
                        "Semua panggilan tanpa kunci dan gratis."
                }
            )
        }
    }
}
