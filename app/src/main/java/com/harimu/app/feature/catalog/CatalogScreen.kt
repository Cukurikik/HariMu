package com.harimu.app.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.GroupHeader
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.ToolRowCard
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.design.icon
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    initialGroup: ToolGroup?,
    isEnglish: Boolean,
    favoriteIds: Set<String>,
    onOpenTool: (Tool) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenAudioForge: (() -> Unit)? = null,
    onOpenImageForge: (() -> Unit)? = null,
    onOpenImageMatrix: (() -> Unit)? = null,
    onOpenPdfVault: (() -> Unit)? = null,
    onOpenPdfStudio: (() -> Unit)? = null,
    onOpenUniversalConverter: (() -> Unit)? = null,
    onOpenVideoForge: (() -> Unit)? = null
) {
    val spacing = HariMuThemeTokens.spacing
    val layout = HariMuThemeTokens.layout
    var selectedGroupName by rememberSaveable(initialGroup?.name) { mutableStateOf(initialGroup?.name) }
    val selectedGroup = remember(selectedGroupName) {
        selectedGroupName?.let { name -> ToolGroup.entries.firstOrNull { it.name == name } }
    }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(selectedGroup, query, isEnglish) {
        ToolRegistry.search(query, isEnglish, selectedGroup)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedGroup?.let { stringResource(it.titleRes) }
                            ?: stringResource(R.string.nav_catalog),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .testTag("catalog_back_button")
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = layout.catalogMaxWidth)
            ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs)
                    .testTag("catalog_search_input"),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                item {
                    FilterChip(
                        selected = selectedGroup == null,
                        onClick = { selectedGroupName = null },
                        label = { Text(stringResource(R.string.catalog_all_count, ToolRegistry.allTools.size)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("filter_all")
                    )
                }
                items(ToolGroup.entries, key = { it.name }) { group ->
                    val accent = group.accentColor()
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { selectedGroupName = group.name },
                        label = { Text(stringResource(group.titleRes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("filter_${group.name}")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                selectedGroup?.let { group ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            GroupHeader(
                                title = stringResource(group.titleRes),
                                subtitle = stringResource(group.subtitleRes),
                                toolCount = ToolRegistry.countByGroup(group),
                                icon = group.icon(),
                                accent = group.accentColor()
                            )
                            if (group == ToolGroup.VIDEO && onOpenVideoForge != null) {
                                OutlinedButton(
                                    onClick = onOpenVideoForge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("catalog_open_video_forge_btn")
                                ) {
                                    Icon(Icons.Filled.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.video_forge_title))
                                }
                            }
                            if (group == ToolGroup.AUDIO && onOpenAudioForge != null) {
                                OutlinedButton(
                                    onClick = onOpenAudioForge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("catalog_open_audio_forge_btn")
                                ) {
                                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.audio_forge_title))
                                }
                            }
                            if (group == ToolGroup.IMAGE && (onOpenImageForge != null || onOpenImageMatrix != null)) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (onOpenImageForge != null) {
                                        OutlinedButton(
                                            onClick = onOpenImageForge,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("catalog_open_image_forge_btn")
                                        ) {
                                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.image_forge_title),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (onOpenImageMatrix != null) {
                                        OutlinedButton(
                                            onClick = onOpenImageMatrix,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("catalog_open_image_matrix_btn")
                                        ) {
                                            Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.image_matrix_title),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            if (group == ToolGroup.PDF && (onOpenPdfVault != null || onOpenPdfStudio != null)) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (onOpenPdfVault != null) {
                                        OutlinedButton(
                                            onClick = onOpenPdfVault,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("catalog_open_pdf_vault_btn")
                                        ) {
                                            Icon(Icons.Filled.ViewAgenda, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.pdf_vault_title),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (onOpenPdfStudio != null) {
                                        OutlinedButton(
                                            onClick = onOpenPdfStudio,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("catalog_open_pdf_studio_btn")
                                        ) {
                                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.pdf_vault_open_studio),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            if (group == ToolGroup.CONVERTER && onOpenUniversalConverter != null) {
                                OutlinedButton(
                                    onClick = onOpenUniversalConverter,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("catalog_open_converter_btn")
                                ) {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.converter_catalog_open_studio),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.SearchOff,
                            title = stringResource(R.string.catalog_empty_title),
                            subtitle = stringResource(R.string.catalog_empty_desc)
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { tool ->
                        ToolRowCard(
                            tool = tool,
                            isEnglish = isEnglish,
                            isFavorite = tool.id in favoriteIds,
                            onClick = { onOpenTool(tool) },
                            onToggleFavorite = { onToggleFavorite(tool.id) }
                        )
                    }
                }
            }
        }
        }
    }
}
