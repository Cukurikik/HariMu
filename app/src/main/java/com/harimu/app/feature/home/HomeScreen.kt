package com.harimu.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.ToolRowCard
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.icon
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry

@Composable
fun HomeScreen(
    isEnglish: Boolean,
    favoriteIds: Set<String>,
    recentToolIds: List<String>,
    onOpenTool: (Tool) -> Unit,
    onOpenGroup: (ToolGroup) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    val fontScale = LocalDensity.current.fontScale
    var searchQuery by remember { mutableStateOf("") }

    val searchResults = remember(searchQuery, isEnglish) {
        if (searchQuery.isBlank()) emptyList() else ToolRegistry.search(searchQuery, isEnglish)
    }
    val recentTools = remember(recentToolIds) {
        recentToolIds.distinct()
            .mapNotNull { id -> ToolRegistry.findById(id)?.takeUnless { it.id.startsWith("DEV-") } }
            .take(8)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val columns = when {
            maxWidth >= 840.dp -> 3
            maxWidth >= 320.dp && fontScale < 1.5f -> 2
            else -> 1
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp),
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item {
                HeroStudioBanner()
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.search_results_count, searchResults.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (searchResults.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.SearchOff,
                            title = stringResource(R.string.catalog_empty_title),
                            subtitle = stringResource(R.string.catalog_empty_desc)
                        )
                    }
                } else {
                    items(searchResults, key = { it.id }) { tool ->
                        ToolRowCard(
                            tool = tool,
                            isEnglish = isEnglish,
                            isFavorite = tool.id in favoriteIds,
                            onClick = { onOpenTool(tool) },
                            onToggleFavorite = { onToggleFavorite(tool.id) }
                        )
                    }
                }
            } else {
                // Bagian "Terakhir dipakai"
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = stringResource(R.string.recently_used_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (recentTools.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(spacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(spacing.sm))
                                    Text(
                                        text = stringResource(R.string.recently_used_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                items(recentTools, key = { it.id }) { tool ->
                                    val accent = tool.group.accentColor()
                                    Card(
                                        modifier = Modifier
                                            .width(196.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable { onOpenTool(tool) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                                    ) {
                                        Column(modifier = Modifier.padding(spacing.sm)) {
                                            ToolIdBadge(id = tool.id, accent = accent)
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = tool.title(isEnglish),
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = tool.description(isEnglish),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6 Grup Tool
                item {
                    Text(
                        text = stringResource(R.string.groups_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                val rows = ToolGroup.entries.chunked(columns)
                items(rows) { rowGroups ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        rowGroups.forEach { group ->
                            GroupGridCard(
                                group = group,
                                count = ToolRegistry.countByGroup(group),
                                onClick = { onOpenGroup(group) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - rowGroups.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStudioBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .clip(MaterialTheme.shapes.large)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_studio),
            contentDescription = stringResource(R.string.hero_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xEE0E1119),
                            Color(0xCC0E1119),
                            Color(0x660E1119)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HariMuColors.ElectricOrange.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, HariMuColors.ElectricOrange.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = stringResource(R.string.hero_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFB596),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            Text(
                text = stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD1D5DB),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GroupGridCard(
    group: ToolGroup,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = group.accentColor()
    val spacing = HariMuThemeTokens.spacing
    Card(
        modifier = modifier
            .testTag("group_card_${group.name}")
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(group.icon(), contentDescription = null, tint = accent)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(R.string.tool_count_label, count),
                        style = MonoTextStyle.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = stringResource(group.titleRes),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(group.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
