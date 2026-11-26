package com.harimu.app.feature.studios

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.video.ToolCategorySection
import com.harimu.app.core.design.video.ToolLauncherCard
import com.harimu.app.core.design.video.ToolLauncherVariant
import com.harimu.app.core.design.video.ToolSearchBar
import com.harimu.app.feature.tools.CoreAndVideoTools
import com.harimu.app.feature.toolscreen.ToolScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoForgeScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedToolId by remember { mutableStateOf<String?>(null) }

    val allVideoTools = remember { CoreAndVideoTools.videoTools() }
    val filteredTools = remember(searchQuery, allVideoTools) {
        if (searchQuery.isBlank()) allVideoTools else allVideoTools.filter { tool ->
            tool.title(isEnglish).lowercase().contains(searchQuery.lowercase()) ||
                tool.description(isEnglish).lowercase().contains(searchQuery.lowercase()) ||
                tool.id.lowercase().contains(searchQuery.lowercase())
        }
    }

    // Group tools by category
    val categorizedTools = remember(filteredTools) {
        val categories = mutableMapOf<String, MutableList<com.harimu.app.core.toolapi.Tool>>()
        filteredTools.forEach { tool ->
            val category = getToolCategory(tool.id)
            categories.getOrPut(category) { mutableListOf() }.add(tool)
        }
        categories.toMap()
    }

    // Navigation to ToolScreen
    if (selectedToolId != null) {
        val tool = allVideoTools.find { it.id == selectedToolId }
        tool?.let {
            ToolScreen(
                toolId = it.id,
                container = container,
                isEnglish = isEnglish,
                isFavorite = false,
                onToggleFavorite = {},
                onBack = { selectedToolId = null }
            )
            return@VideoForgeScreen
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.video_forge_title),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Search bar
                ToolSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = if (isEnglish) "Search video tools..." else "Cari alat video..."
                )

                // Tool count indicator
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.video_forge_results_count, filteredTools.size),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Categories
                if (categorizedTools.isEmpty()) {
                    // Empty search results
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f))
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (isEnglish) "No tools found" else "Tidak ada alat ditemukan",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = if (isEnglish) "Try a different search term" else "Coba kata kunci lain",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                } else {
                    // Category order
                    val categoryOrder = listOf(
                        "Core Editing" to Icons.Filled.ContentCut,
                        "Audio" to Icons.Filled.MusicNote,
                        "Transform & Convert" to Icons.Filled.Transform,
                        "Overlays & Effects" to Icons.Filled.AutoFixHigh,
                        "Creative" to Icons.Filled.Palette
                    )

                    categoryOrder.forEach { (categoryName, categoryIcon) ->
                        categorizedTools[categoryName]?.let { tools ->
                            ToolCategorySection(
                                categoryTitle = stringResource(categoryName.toStringResource()),
                                categoryIcon = categoryIcon,
                                tools = tools,
                                isEnglish = isEnglish,
                                onToolClick = { tool ->
                                    selectedToolId = tool.id
                                },
                                variant = ToolLauncherVariant.Standard,
                                initiallyExpanded = true
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getToolCategory(toolId: String): String = when (toolId) {
    "VID-01", "VID-02", "VID-03", "VID-04", "VID-05", "VID-09", "VID-10" -> "Core Editing"
    "VID-08", "VID-12", "VID-13", "VID-16", "VID-24" -> "Audio"
    "VID-18", "VID-19", "VID-20", "VID-21", "VID-22" -> "Transform & Convert"
    "VID-11", "VID-14", "VID-15", "VID-17", "VID-23", "VID-25", "VID-26" -> "Overlays & Effects"
    "VID-06", "VID-27", "VID-28", "VID-29", "VID-30" -> "Creative"
    else -> "Other"
}

private fun String.toStringResource(): Int = when (this) {
    "Core Editing" -> R.string.video_forge_category_core
    "Audio" -> R.string.video_forge_category_audio
    "Transform & Convert" -> R.string.video_forge_category_transform
    "Overlays & Effects" -> R.string.video_forge_category_effects
    "Creative" -> R.string.video_forge_category_creative
    else -> R.string.video_forge_category_other
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoForgeScreenPreview(
    container: AppContainer,
    isEnglish: Boolean
) {
    VideoForgeScreen(
        container = container,
        isEnglish = isEnglish,
        onBack = {}
    )
}