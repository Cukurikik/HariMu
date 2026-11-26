package com.harimu.app.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.ToolRowCard
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolRegistry

@Composable
fun FavoritesScreen(
    isEnglish: Boolean,
    favoriteIds: Set<String>,
    onOpenTool: (Tool) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    val favoriteTools = remember(favoriteIds) {
        ToolRegistry.allTools.filter { it.id in favoriteIds }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = layout.catalogMaxWidth),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            item {
                Text(
                    text = stringResource(R.string.favorites_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            if (favoriteTools.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Star,
                        title = stringResource(R.string.favorites_empty_title),
                        subtitle = stringResource(R.string.favorites_empty_desc)
                    )
                }
            } else {
                items(favoriteTools, key = { it.id }) { tool ->
                    ToolRowCard(
                        tool = tool,
                        isEnglish = isEnglish,
                        isFavorite = true,
                        onClick = { onOpenTool(tool) },
                        onToggleFavorite = { onToggleFavorite(tool.id) }
                    )
                }
            }
        }
    }
}
