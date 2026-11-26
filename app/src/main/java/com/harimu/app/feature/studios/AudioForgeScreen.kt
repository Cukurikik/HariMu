package com.harimu.app.feature.studios

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Transform
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
import com.harimu.app.core.design.audio.AudioCategorySection
import com.harimu.app.core.design.audio.AudioSearchBar
import com.harimu.app.core.design.audio.AudioStudioHeroCard
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.toolscreen.ToolScreen

enum class AudioForgeCategory(
    val titleRes: Int,
    val icon: ImageVector
) {
    STUDIOS_MASTERING(R.string.audio_forge_cat_studios, Icons.Filled.Equalizer),
    EQ_TONE(R.string.audio_forge_cat_eq_tone, Icons.Filled.Equalizer),
    RESTORATION_AI(R.string.audio_forge_cat_restoration, Icons.Filled.CleaningServices),
    SPATIAL_FX(R.string.audio_forge_cat_spatial_fx, Icons.Filled.SurroundSound),
    EDIT_TIMING(R.string.audio_forge_cat_edit_split, Icons.Filled.ContentCut),
    CONVERT_METADATA(R.string.audio_forge_cat_convert_util, Icons.Filled.Transform)
}

fun categorizeAudioTool(toolId: String): AudioForgeCategory = when (toolId) {
    "AUD-21", "AUD-01", "AUD-12", "AUD-22" -> AudioForgeCategory.STUDIOS_MASTERING
    "AUD-02", "AUD-06", "AUD-07", "AUD-08" -> AudioForgeCategory.EQ_TONE
    "AUD-03", "AUD-04", "AUD-16", "AUD-20", "AUD-27" -> AudioForgeCategory.RESTORATION_AI
    "AUD-05", "AUD-24", "AUD-25", "AUD-28" -> AudioForgeCategory.SPATIAL_FX
    "AUD-09", "AUD-10", "AUD-17", "AUD-23", "AUD-26" -> AudioForgeCategory.EDIT_TIMING
    "AUD-11", "AUD-13", "AUD-14", "AUD-15", "AUD-18", "AUD-19" -> AudioForgeCategory.CONVERT_METADATA
    else -> AudioForgeCategory.CONVERT_METADATA
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioForgeScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onOpenTool: ((Tool) -> Unit)? = null,
    onOpenAudioStudio: (() -> Unit)? = null,
    onOpenEqualizer: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var fallbackScreen by remember { mutableStateOf<String?>(null) }

    val allAudioTools = remember { ToolRegistry.byGroup(ToolGroup.AUDIO) }
    val filteredTools = remember(searchQuery, allAudioTools, isEnglish) {
        if (searchQuery.isBlank()) {
            allAudioTools
        } else {
            val q = searchQuery.trim().lowercase()
            allAudioTools.filter { tool ->
                tool.id.lowercase().contains(q) ||
                    tool.title(isEnglish).lowercase().contains(q) ||
                    tool.description(isEnglish).lowercase().contains(q)
            }
        }
    }

    val groupedTools = remember(filteredTools) {
        AudioForgeCategory.entries.associateWith { cat ->
            filteredTools.filter { categorizeAudioTool(it.id) == cat }
        }.filterValues { it.isNotEmpty() }
    }

    // Standalone fallback sub-screen handling when launched outside NavHost
    if (fallbackScreen != null) {
        BackHandler { fallbackScreen = null }
        when (fallbackScreen) {
            "STUDIO" -> {
                AudioStudioScreen(
                    container = container,
                    isEnglish = isEnglish,
                    onOpenTool = { id -> fallbackScreen = id },
                    onBack = { fallbackScreen = null }
                )
                return
            }
            "EQ" -> {
                EqualizerScreen(
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
                DedicatedScreenRoute.AUDIO_STUDIO -> "STUDIO"
                DedicatedScreenRoute.EQUALIZER -> "EQ"
                else -> tool.id
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.audio_forge_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("audio_forge_back_btn")
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
                    .testTag("audio_forge_screen"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AudioStudioHeroCard(
                    onOpenAudioStudio = {
                        if (onOpenAudioStudio != null) onOpenAudioStudio() else fallbackScreen = "STUDIO"
                    },
                    onOpenEqualizer = {
                        if (onOpenEqualizer != null) onOpenEqualizer() else fallbackScreen = "EQ"
                    }
                )

                AudioSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )

                if (searchQuery.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.audio_forge_results_count, filteredTools.size),
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
                        AudioCategorySection(
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
