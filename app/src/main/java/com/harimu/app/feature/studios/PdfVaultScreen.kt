package com.harimu.app.feature.studios

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.pdf.PdfCategoryFilterRow
import com.harimu.app.core.design.pdf.PdfCategorySection
import com.harimu.app.core.design.pdf.PdfSearchBar
import com.harimu.app.core.design.pdf.PdfVaultHeroCard
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.toolscreen.ToolScreen

enum class PdfVaultCategory(
    val titleRes: Int,
    val icon: ImageVector
) {
    PAGES_ORGANIZE(R.string.pdf_vault_cat_pages, Icons.Filled.ViewAgenda),
    SECURITY_PRIVACY(R.string.pdf_vault_cat_security, Icons.Filled.Lock),
    OCR_AI(R.string.pdf_vault_cat_ocr_ai, Icons.Filled.DocumentScanner),
    CONVERT_COMPRESS(R.string.pdf_vault_cat_convert, Icons.Filled.SwapHoriz),
    FORMS_FINISHING(R.string.pdf_vault_cat_forms, Icons.Filled.Compare)
}

fun categorizePdfTool(toolId: String): PdfVaultCategory = when (toolId) {
    "PDF-01", "PDF-02", "PDF-03", "PDF-04", "PDF-05", "PDF-08" -> PdfVaultCategory.PAGES_ORGANIZE
    "PDF-10", "PDF-11", "PDF-12", "PDF-13", "PDF-14" -> PdfVaultCategory.SECURITY_PRIVACY
    "PDF-15", "PDF-16", "PDF-17" -> PdfVaultCategory.OCR_AI
    "PDF-06", "PDF-07", "PDF-09" -> PdfVaultCategory.CONVERT_COMPRESS
    "PDF-18", "PDF-19", "PDF-20", "PDF-21" -> PdfVaultCategory.FORMS_FINISHING
    else -> PdfVaultCategory.PAGES_ORGANIZE
}

private val PDF_CONVERTER_BRIDGE_IDS = listOf("CNV-13", "CNV-14", "CNV-15")

private val STUDIO_FIRST_PDF_IDS = setOf(
    "PDF-01", "PDF-02", "PDF-03", "PDF-04", "PDF-05", "PDF-08",
    "PDF-10", "PDF-11", "PDF-12", "PDF-13", "PDF-14",
    "PDF-18", "PDF-19", "PDF-20", "PDF-21"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfVaultScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onOpenTool: ((Tool) -> Unit)? = null,
    onOpenPdfStudio: ((initialTab: PdfStudioTab, toolId: String?) -> Unit)? = null,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<PdfVaultCategory?>(null) }
    var fallbackScreen by remember { mutableStateOf<Pair<String, PdfStudioTab?>?>(null) }

    val allPdfTools = remember { ToolRegistry.byGroup(ToolGroup.PDF) }
    val bridgeTools = remember {
        PDF_CONVERTER_BRIDGE_IDS.mapNotNull { ToolRegistry.findById(it) }
    }

    val filteredTools = remember(searchQuery, selectedCategory, allPdfTools, isEnglish) {
        allPdfTools.filter { tool ->
            val matchesCategory = selectedCategory == null || categorizePdfTool(tool.id) == selectedCategory
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                tool.id.lowercase().contains(q) ||
                    tool.title(isEnglish).lowercase().contains(q) ||
                    tool.description(isEnglish).lowercase().contains(q)
            }
            matchesCategory && matchesQuery
        }
    }

    val filteredBridgeTools = remember(searchQuery, selectedCategory, bridgeTools, isEnglish) {
        if (selectedCategory != null && selectedCategory != PdfVaultCategory.CONVERT_COMPRESS) {
            emptyList()
        } else if (searchQuery.isBlank()) {
            bridgeTools
        } else {
            val q = searchQuery.trim().lowercase()
            bridgeTools.filter { tool ->
                tool.id.lowercase().contains(q) ||
                    tool.title(isEnglish).lowercase().contains(q) ||
                    tool.description(isEnglish).lowercase().contains(q)
            }
        }
    }

    val groupedTools = remember(filteredTools) {
        PdfVaultCategory.entries.associateWith { cat ->
            filteredTools.filter { categorizePdfTool(it.id) == cat }
        }.filterValues { it.isNotEmpty() }
    }

    if (fallbackScreen != null) {
        val (targetId, targetTab) = fallbackScreen!!
        BackHandler { fallbackScreen = null }
        if (targetId == "STUDIO") {
            PdfStudioScreen(
                container = container,
                isEnglish = isEnglish,
                initialTabName = targetTab?.name,
                onOpenTool = { fallbackScreen = it to null },
                onBack = { fallbackScreen = null }
            )
            return
        } else {
            ToolScreen(
                toolId = targetId,
                container = container,
                isEnglish = isEnglish,
                isFavorite = false,
                onToggleFavorite = {},
                onBack = { fallbackScreen = null },
                onOpenPdfStudio = { tab, id ->
                    fallbackScreen = "STUDIO" to runCatching { PdfStudioTab.valueOf(tab) }.getOrNull()
                }
            )
            return
        }
    }

    fun launchStudio(tab: PdfStudioTab, toolId: String? = null) {
        if (onOpenPdfStudio != null) {
            onOpenPdfStudio(tab, toolId)
        } else {
            fallbackScreen = "STUDIO" to tab
        }
    }

    fun handleToolClick(tool: Tool) {
        if (tool.id in STUDIO_FIRST_PDF_IDS) {
            launchStudio(tabForPdfTool(tool.id), tool.id)
        } else if (onOpenTool != null) {
            onOpenTool(tool)
        } else {
            fallbackScreen = tool.id to null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.pdf_vault_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_vault_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val columns = if (maxWidth >= 720.dp) 2 else 1

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 980.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .testTag("pdf_vault_screen"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PdfVaultHeroCard(
                    onOpenStudio = { launchStudio(PdfStudioTab.INSPECT, null) },
                    onQuickPages = { launchStudio(PdfStudioTab.PAGES_MERGE, "PDF-01") },
                    onQuickSecurity = { launchStudio(PdfStudioTab.SECURITY_REDACT, "PDF-14") },
                    onQuickCompare = { launchStudio(PdfStudioTab.COMPARE, "PDF-20") }
                )

                PdfSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )

                PdfCategoryFilterRow(
                    categories = PdfVaultCategory.entries,
                    selectedCategory = selectedCategory,
                    labelProvider = { cat -> stringResource(cat.titleRes) },
                    keyProvider = { cat -> cat.name },
                    onSelectCategory = { selectedCategory = it }
                )

                if (searchQuery.isNotBlank() || selectedCategory != null) {
                    Text(
                        text = stringResource(R.string.pdf_vault_results_count, filteredTools.size + filteredBridgeTools.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                if (groupedTools.isEmpty() && filteredBridgeTools.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.catalog_empty_title),
                        subtitle = stringResource(R.string.catalog_empty_desc)
                    )
                } else {
                    groupedTools.forEach { (category, tools) ->
                        PdfCategorySection(
                            categoryTitle = stringResource(category.titleRes),
                            categoryIcon = category.icon,
                            tools = tools,
                            isEnglish = isEnglish,
                            columns = columns,
                            onToolClick = ::handleToolClick
                        )
                    }

                    if (filteredBridgeTools.isNotEmpty()) {
                        PdfCategorySection(
                            categoryTitle = stringResource(R.string.pdf_vault_converter_bridge_title),
                            categoryIcon = Icons.Filled.SwapHoriz,
                            tools = filteredBridgeTools,
                            isEnglish = isEnglish,
                            columns = columns,
                            onToolClick = { tool ->
                                if (onOpenTool != null) onOpenTool(tool) else fallbackScreen = tool.id to null
                            },
                            initiallyExpanded = false
                        )
                    }
                }
            }
        }
    }
}
