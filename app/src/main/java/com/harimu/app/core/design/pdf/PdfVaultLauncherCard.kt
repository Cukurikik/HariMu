package com.harimu.app.core.design.pdf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.toolapi.Tool

enum class PdfCapabilityMode(val labelRes: Int) {
    LOSSLESS_OBJECT(R.string.pdf_cap_lossless),
    RASTERIZED(R.string.pdf_cap_raster),
    BASIC_CONVERSION(R.string.pdf_cap_basic),
    ON_DEVICE_OCR(R.string.pdf_cap_ocr),
    AI_GATEWAY(R.string.pdf_cap_cloud)
}

fun pdfToolCapability(toolId: String): PdfCapabilityMode = when (toolId) {
    "PDF-06", "PDF-07", "CNV-13" -> PdfCapabilityMode.BASIC_CONVERSION
    "PDF-09", "PDF-14", "CNV-14" -> PdfCapabilityMode.RASTERIZED
    "PDF-15", "PDF-16", "CNV-15" -> PdfCapabilityMode.ON_DEVICE_OCR
    "PDF-17" -> PdfCapabilityMode.AI_GATEWAY
    else -> PdfCapabilityMode.LOSSLESS_OBJECT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfVaultHeroCard(
    onOpenStudio: () -> Unit,
    onQuickPages: () -> Unit,
    onQuickSecurity: () -> Unit,
    onQuickCompare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupPdf
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.16f),
                            HariMuColors.ElectricOrange.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.pdf_vault_hero_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = safeAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.pdf_vault_hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenStudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_vault_open_studio_btn")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.pdf_vault_open_studio),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(
                        onClick = onQuickPages,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_vault_quick_pages_btn")
                    ) {
                        Icon(Icons.Filled.ViewAgenda, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pdf_vault_quick_organize))
                    }
                    OutlinedButton(
                        onClick = onQuickSecurity,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_vault_quick_security_btn")
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pdf_vault_quick_security))
                    }
                    OutlinedButton(
                        onClick = onQuickCompare,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_vault_quick_compare_btn")
                    ) {
                        Icon(Icons.Filled.Compare, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pdf_vault_quick_compare))
                    }
                }
            }
        }
    }
}

@Composable
fun PdfSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag("pdf_vault_search_input"),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        placeholder = {
            Text(
                text = stringResource(R.string.pdf_vault_search_hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
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

@Composable
fun <T> PdfCategoryFilterRow(
    categories: List<T>,
    selectedCategory: T?,
    labelProvider: @Composable (T) -> String,
    keyProvider: (T) -> String,
    onSelectCategory: (T?) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupPdf
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text(stringResource(R.string.pdf_vault_cat_all)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.18f),
                    selectedLabelColor = safeAccent
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("pdf_filter_ALL")
            )
        }
        items(categories, key = keyProvider) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = {
                    onSelectCategory(if (selectedCategory == category) null else category)
                },
                label = { Text(labelProvider(category)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.18f),
                    selectedLabelColor = safeAccent
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("pdf_filter_${keyProvider(category)}")
            )
        }
    }
}

@Composable
fun PdfCategorySection(
    categoryTitle: String,
    categoryIcon: ImageVector,
    tools: List<Tool>,
    isEnglish: Boolean,
    columns: Int,
    onToolClick: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true
) {
    var expanded by remember(categoryTitle) { mutableStateOf(initiallyExpanded) }
    val accent = HariMuColors.GroupPdf
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .minimumInteractiveComponentSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = safeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = categoryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${tools.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = safeAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded) {
            val rows = tools.chunked(columns.coerceAtLeast(1))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowTools ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTools.forEach { tool ->
                            PdfToolLauncherCard(
                                tool = tool,
                                isEnglish = isEnglish,
                                onClick = { onToolClick(tool) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns.coerceAtLeast(1) - rowTools.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfToolLauncherCard(
    tool: Tool,
    isEnglish: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupPdf
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
    val capability = remember(tool.id) { pdfToolCapability(tool.id) }
    val (capBg, capFg) = when (capability) {
        PdfCapabilityMode.LOSSLESS_OBJECT -> HariMuThemeTokens.semantic.successContainer to HariMuThemeTokens.semantic.onSuccessContainer
        PdfCapabilityMode.BASIC_CONVERSION,
        PdfCapabilityMode.RASTERIZED -> HariMuThemeTokens.semantic.warningContainer to HariMuThemeTokens.semantic.onWarningContainer
        PdfCapabilityMode.ON_DEVICE_OCR,
        PdfCapabilityMode.AI_GATEWAY -> HariMuThemeTokens.semantic.infoContainer to HariMuThemeTokens.semantic.onInfoContainer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("pdf_tool_card_${tool.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = safeAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolIdBadge(id = tool.id, accent = accent)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = capBg) {
                            Text(
                                text = stringResource(capability.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = capFg,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        EngineBadge(engine = tool.engineLabel)
                    }
                }
                Text(
                    text = tool.title(isEnglish),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
