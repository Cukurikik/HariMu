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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
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
import com.harimu.app.core.design.image.ImageCategoryFilterRow
import com.harimu.app.core.design.image.ImageCategorySection
import com.harimu.app.core.design.image.ImageMatrixHeroCard
import com.harimu.app.core.design.image.ImageSearchBar
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.toolscreen.ToolScreen

enum class ImageForgeCategory(
    val titleRes: Int,
    val icon: ImageVector
) {
    STUDIO_COMPOSITOR(R.string.image_forge_cat_studio, Icons.Filled.Tune),
    GEOMETRY_CROP(R.string.image_forge_cat_geometry, Icons.Filled.Crop),
    COLOR_TYPOGRAPHY(R.string.image_forge_cat_color_film, Icons.Filled.Palette),
    EFFECTS_OVERLAYS(R.string.image_forge_cat_effects_overlay, Icons.Filled.Layers),
    AI_ENHANCE(R.string.image_forge_cat_ai, Icons.Filled.AutoAwesome),
    CONVERT_OPTIMIZE(R.string.image_forge_cat_convert_meta, Icons.Filled.SwapHoriz),
    BATCH_ANIMATION(R.string.image_forge_cat_batch_anim, Icons.Filled.BurstMode)
}

fun categorizeImageTool(toolId: String): ImageForgeCategory = when (toolId) {
    "IMG-47" -> ImageForgeCategory.STUDIO_COMPOSITOR
    "IMG-01", "IMG-02", "IMG-03", "IMG-04", "IMG-05",
    "IMG-06", "IMG-07", "IMG-08", "IMG-09", "IMG-10" -> ImageForgeCategory.GEOMETRY_CROP
    "IMG-28", "IMG-33", "IMG-35", "IMG-36", "IMG-39", "IMG-40" -> ImageForgeCategory.COLOR_TYPOGRAPHY
    "IMG-31", "IMG-32", "IMG-34", "IMG-37", "IMG-38", "IMG-41", "IMG-42" -> ImageForgeCategory.EFFECTS_OVERLAYS
    "IMG-11", "IMG-12", "IMG-13", "IMG-14", "IMG-15", "IMG-16",
    "IMG-17", "IMG-18", "IMG-19", "IMG-20", "IMG-21", "IMG-22" -> ImageForgeCategory.AI_ENHANCE
    "IMG-23", "IMG-24", "IMG-25", "IMG-26", "IMG-27", "IMG-29", "IMG-30" -> ImageForgeCategory.CONVERT_OPTIMIZE
    "IMG-43", "IMG-44", "IMG-45", "IMG-46" -> ImageForgeCategory.BATCH_ANIMATION
    else -> ImageForgeCategory.CONVERT_OPTIMIZE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageForgeScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onOpenTool: ((Tool) -> Unit)? = null,
    onOpenImageMatrix: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ImageForgeCategory?>(null) }
    var fallbackScreen by remember { mutableStateOf<String?>(null) }

    val allImageTools = remember { ToolRegistry.byGroup(ToolGroup.IMAGE) }
    val filteredTools = remember(searchQuery, selectedCategory, allImageTools, isEnglish) {
        allImageTools.filter { tool ->
            val matchesCategory = selectedCategory == null || categorizeImageTool(tool.id) == selectedCategory
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

    val groupedTools = remember(filteredTools) {
        ImageForgeCategory.entries.associateWith { cat ->
            filteredTools.filter { categorizeImageTool(it.id) == cat }
        }.filterValues { it.isNotEmpty() }
    }

    if (fallbackScreen != null) {
        BackHandler { fallbackScreen = null }
        when (fallbackScreen) {
            "MATRIX" -> {
                ImageMatrixScreen(
                    container = container,
                    isEnglish = isEnglish,
                    onBack = { fallbackScreen = null }
                )
                return
            }

            else -> {
                ToolScreen(
                    toolId = fallbackScreen!!,
                    container = container,
                    isEnglish = isEnglish,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onBack = { fallbackScreen = null }
                )
                return
            }
        }
    }

    fun handleToolClick(tool: Tool) {
        if (onOpenTool != null) {
            onOpenTool(tool)
        } else {
            fallbackScreen = when (tool.dedicatedRoute) {
                DedicatedScreenRoute.IMAGE_MATRIX -> "MATRIX"
                else -> tool.id
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.image_forge_title),
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
                            .testTag("image_forge_back_btn")
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
                    .testTag("image_forge_screen"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ImageMatrixHeroCard(
                    onOpenImageMatrix = {
                        if (onOpenImageMatrix != null) onOpenImageMatrix() else fallbackScreen = "MATRIX"
                    }
                )

                ImageSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )

                ImageCategoryFilterRow(
                    categories = ImageForgeCategory.entries,
                    selectedCategory = selectedCategory,
                    labelProvider = { cat -> stringResource(cat.titleRes) },
                    keyProvider = { cat -> cat.name },
                    onSelectCategory = { selectedCategory = it }
                )

                if (searchQuery.isNotBlank() || selectedCategory != null) {
                    Text(
                        text = stringResource(R.string.image_forge_results_count, filteredTools.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                if (groupedTools.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.catalog_empty_title),
                        subtitle = stringResource(R.string.catalog_empty_desc)
                    )
                } else {
                    groupedTools.forEach { (category, tools) ->
                        ImageCategorySection(
                            categoryTitle = stringResource(category.titleRes),
                            categoryIcon = category.icon,
                            tools = tools,
                            isEnglish = isEnglish,
                            columns = columns,
                            onToolClick = ::handleToolClick
                        )
                    }
                }
            }
        }
    }
}
