package com.harimu.app.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

@Composable
fun HariMuResponsiveContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = HariMuThemeTokens.layout.catalogMaxWidth,
    scrollable: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = HariMuThemeTokens.layout.compactScreenPadding,
        vertical = HariMuThemeTokens.spacing.md
    ),
    content: @Composable BoxScope.() -> Unit
) {
    val scrollModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(scrollModifier),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun HariMuSectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.cardBorderAlpha),
    shape: Shape = HariMuThemeTokens.shapes.container,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    val borders = HariMuThemeTokens.borders
    val elevation = HariMuThemeTokens.elevation
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.card),
        border = BorderStroke(borders.hairline, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            content = content
        )
    }
}

@Composable
fun HariMuElevatedCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    shape: Shape = HariMuThemeTokens.shapes.container,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    val borders = HariMuThemeTokens.borders
    val elevation = HariMuThemeTokens.elevation
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.subtle),
        border = BorderStroke(borders.hairline, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            content = content
        )
    }
}

@Composable
fun HariMuInsetPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = HariMuThemeTokens.shapes.card,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = HariMuThemeTokens.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            content = content
        )
    }
}

@Composable
fun HariMuDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline.copy(alpha = HariMuThemeTokens.layout.dividerAlpha)
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = HariMuThemeTokens.borders.hairline,
        color = color
    )
}
