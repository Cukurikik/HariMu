package com.harimu.app.core.design.converter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.converter.ConversionTargetOption
import com.harimu.app.core.converter.ConverterFormatMatrix
import com.harimu.app.core.converter.ConverterWorkflow
import com.harimu.app.core.converter.DetectedSource
import com.harimu.app.core.converter.SourceFormat
import com.harimu.app.core.design.CapabilityBadge
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuStatusBanner
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.StatusTone
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.files.FileStorageManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterHeroHeader(
    activeJobCount: Int,
    queuedJobCount: Int,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupConverter
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("converter_hero_card"),
        shape = HariMuThemeTokens.shapes.container,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.14f),
                            HariMuColors.TealEmerald.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = HariMuThemeTokens.shapes.iconTile,
                        color = accent.copy(alpha = 0.18f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = safeAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.converter_hub_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.converter_hub_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CapabilityBadge(
                        label = stringResource(R.string.converter_badge_engines),
                        containerColor = accent.copy(alpha = 0.16f),
                        contentColor = safeAccent
                    )
                    CapabilityBadge(
                        label = stringResource(R.string.converter_badge_workflows),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (activeJobCount > 0 || queuedJobCount > 0) {
                        CapabilityBadge(
                            label = stringResource(R.string.converter_hero_queue_fmt, activeJobCount, queuedJobCount),
                            containerColor = HariMuThemeTokens.semantic.infoContainer,
                            contentColor = HariMuThemeTokens.semantic.onInfoContainer,
                            modifier = Modifier.testTag("converter_hero_queue_badge")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConverterWorkflowStrip(
    selectedWorkflow: ConverterWorkflow?,
    onSelectWorkflow: (ConverterWorkflow?) -> Unit,
    isEnglish: Boolean,
    showAllChip: Boolean = true,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupConverter
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showAllChip) {
            item(key = "ALL") {
                FilterChip(
                    selected = selectedWorkflow == null,
                    onClick = { onSelectWorkflow(null) },
                    label = { Text(stringResource(R.string.converter_workflow_all, ConverterWorkflow.allMappedToolIds().size)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.16f),
                        selectedLabelColor = safeAccent
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("converter_workflow_chip_ALL")
                )
            }
        }
        items(ConverterWorkflow.entries, key = { it.name }) { workflow ->
            val selected = selectedWorkflow == workflow
            FilterChip(
                selected = selected,
                onClick = { onSelectWorkflow(if (selected && showAllChip) null else workflow) },
                leadingIcon = {
                    Icon(
                        imageVector = workflow.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = workflow.title(isEnglish),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.16f),
                    selectedLabelColor = safeAccent,
                    selectedLeadingIconColor = safeAccent
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("converter_workflow_chip_${workflow.name}")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterSourceCard(
    isTextMode: Boolean,
    sources: List<DetectedSource>,
    textInput: String,
    detectedTextFormat: SourceFormat,
    isEnglish: Boolean,
    onSetTextMode: (Boolean) -> Unit,
    onPickMedia: () -> Unit,
    onPickFiles: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onClearFiles: () -> Unit,
    onTextChange: (String) -> Unit,
    onLoadTextFromFile: () -> Unit,
    onLoadSample: (SourceFormat) -> Unit,
    sourceWarningMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupConverter
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("converter_source_card"),
        shape = HariMuThemeTokens.shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTextMode) {
                        stringResource(R.string.converter_input_text)
                    } else if (sources.isEmpty()) {
                        stringResource(R.string.converter_input_files)
                    } else {
                        stringResource(R.string.converter_detected_sources_title, sources.size)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !isTextMode,
                        onClick = { onSetTextMode(false) },
                        label = { Text(stringResource(R.string.converter_input_files)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.16f),
                            selectedLabelColor = safeAccent
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("converter_source_mode_files")
                    )
                    FilterChip(
                        selected = isTextMode,
                        onClick = { onSetTextMode(true) },
                        label = { Text(stringResource(R.string.converter_input_text)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.16f),
                            selectedLabelColor = safeAccent
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("converter_source_mode_text")
                    )
                }
            }

            if (!isTextMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickFiles,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("converter_pick_files_btn")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.converter_pick_files),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onPickMedia,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("converter_pick_media_btn")
                    ) {
                        Icon(Icons.Filled.PermMedia, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.converter_pick_media),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (sources.isEmpty()) {
                    Surface(
                        shape = HariMuThemeTokens.shapes.control,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.converter_empty_source_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.converter_empty_source_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    sources.forEachIndexed { index, src ->
                        Surface(
                            shape = HariMuThemeTokens.shapes.control,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("converter_source_item_$index")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = HariMuThemeTokens.shapes.badge,
                                    color = accent.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = src.format.badge,
                                        style = MonoTextStyle.copy(fontWeight = FontWeight.Bold),
                                        color = safeAccent,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = src.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeLabel = if (src.byteSize >= 0L) {
                                        FileStorageManager.companionFormatBytes(src.byteSize)
                                    } else {
                                        "?"
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${src.format.label(isEnglish)} • $sizeLabel • ${src.detectionMethod}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (src.extensionMismatch) {
                                                HariMuThemeTokens.semantic.warning
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (src.extensionMismatch) {
                                            Icon(
                                                imageVector = Icons.Filled.Warning,
                                                contentDescription = src.note(isEnglish),
                                                tint = HariMuThemeTokens.semantic.warning,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .testTag("converter_source_mismatch_$index")
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveFile(index) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("converter_remove_file_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.converter_cd_remove_file)
                                    )
                                }
                            }
                        }
                    }

                    if (sources.size > 1) {
                        val formatSummary = sources.map { it.format.badge }.distinct().joinToString(" • ")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.converter_batch_summary_formats, formatSummary),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = onClearFiles,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("converter_clear_files_btn")
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.converter_clear_files))
                            }
                        }
                    }

                    val sourceNotes = sources.mapNotNull { it.note(isEnglish) }.distinct()
                    sourceNotes.forEach { note ->
                        ConverterCapabilityNoticeBanner(
                            title = stringResource(R.string.converter_honest_limits_title),
                            message = note,
                            isWarning = true
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CapabilityBadge(
                        label = detectedTextFormat.label(isEnglish),
                        containerColor = accent.copy(alpha = 0.16f),
                        contentColor = safeAccent,
                        modifier = Modifier.testTag("converter_detected_text_badge")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onLoadTextFromFile,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_load_text_file_btn")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.converter_load_text_file))
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        SourceFormat.JSON to "JSON",
                        SourceFormat.XML to "XML",
                        SourceFormat.CSV to "CSV",
                        SourceFormat.MARKDOWN to "Markdown",
                        SourceFormat.GEOJSON to "GeoJSON",
                        SourceFormat.CSS to "CSS"
                    ).forEach { (fmt, label) ->
                        FilterChip(
                            selected = detectedTextFormat == fmt,
                            onClick = { onLoadSample(fmt) },
                            label = { Text(label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_sample_${fmt.name}")
                        )
                    }
                }

                OutlinedTextField(
                    value = textInput,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp, max = 220.dp)
                        .testTag("converter_text_input"),
                    textStyle = MonoTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    placeholder = {
                        Text(text = stringResource(R.string.converter_text_placeholder))
                    },
                    shape = HariMuThemeTokens.shapes.control,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
            }

            if (!sourceWarningMessage.isNullOrBlank()) {
                HariMuStatusBanner(
                    message = sourceWarningMessage,
                    tone = StatusTone.WARNING,
                    modifier = Modifier.testTag("converter_source_warning_banner")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterTargetMatrix(
    targets: List<ConversionTargetOption>,
    selectedTargetId: String?,
    unsupportedReasons: List<String>,
    isEnglish: Boolean,
    onSelectTarget: (ConversionTargetOption) -> Unit,
    mixedIncompatibleReason: String? = null,
    modifier: Modifier = Modifier
) {
    val accent = HariMuColors.GroupConverter
    val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)

    val formatTargets = targets.filter { !it.isArchiveSecurityAction }
    val securityTargets = targets.filter { it.isArchiveSecurityAction }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("converter_target_matrix"),
        shape = HariMuThemeTokens.shapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.converter_target_section),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            if (unsupportedReasons.isNotEmpty()) {
                unsupportedReasons.forEach { reason ->
                    ConverterCapabilityNoticeBanner(
                        title = stringResource(R.string.converter_honest_limits_title),
                        message = reason,
                        isWarning = true,
                        modifier = Modifier.testTag("converter_unsupported_reason_banner")
                    )
                }
            }

            if (formatTargets.isEmpty()) {
                if (!mixedIncompatibleReason.isNullOrBlank()) {
                    ConverterCapabilityNoticeBanner(
                        title = stringResource(R.string.converter_honest_limits_title),
                        message = mixedIncompatibleReason,
                        isWarning = true,
                        modifier = Modifier.testTag("converter_mixed_incompatible_banner")
                    )
                }
                Text(
                    text = stringResource(R.string.converter_no_format_targets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("converter_no_format_targets_text")
                )
            } else {
                Text(
                    text = stringResource(R.string.converter_target_conversion_group),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    formatTargets.forEach { option ->
                        val isSelected = option.id == selectedTargetId
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectTarget(option) },
                            leadingIcon = if (option.isLimitedOrBasic) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            label = {
                                Text(
                                    text = "${option.label(isEnglish)} • ${option.toolId}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.18f),
                                selectedLabelColor = safeAccent,
                                selectedLeadingIconColor = safeAccent
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_target_chip_${option.id}")
                        )
                    }
                }
            }

            if (securityTargets.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.converter_target_security_group),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    securityTargets.forEach { option ->
                        val isSelected = option.id == selectedTargetId
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectTarget(option) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "${option.label(isEnglish)} • ${option.toolId}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HariMuThemeTokens.semantic.infoContainer,
                                selectedLabelColor = HariMuThemeTokens.semantic.onInfoContainer,
                                selectedLeadingIconColor = HariMuThemeTokens.semantic.onInfoContainer
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("converter_target_chip_${option.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConverterBatchSummaryBanner(
    fileCount: Int,
    isSingleArchiveJob: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = HariMuThemeTokens.shapes.control,
        color = HariMuThemeTokens.semantic.infoContainer,
        border = BorderStroke(1.dp, HariMuThemeTokens.semantic.infoBorder.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("converter_batch_summary")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = HariMuThemeTokens.semantic.onInfoContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (isSingleArchiveJob) {
                    stringResource(R.string.converter_batch_archive_notice, fileCount)
                } else {
                    stringResource(R.string.converter_batch_queue_notice, fileCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = HariMuThemeTokens.semantic.onInfoContainer
            )
        }
    }
}

@Composable
fun ConverterCapabilityNoticeBanner(
    title: String,
    message: String,
    isWarning: Boolean,
    modifier: Modifier = Modifier
) {
    val container = if (isWarning) HariMuThemeTokens.semantic.warningContainer else HariMuThemeTokens.semantic.infoContainer
    val onContainer = if (isWarning) HariMuThemeTokens.semantic.onWarningContainer else HariMuThemeTokens.semantic.onInfoContainer
    val borderColor = if (isWarning) HariMuThemeTokens.semantic.warningBorder else HariMuThemeTokens.semantic.infoBorder

    Surface(
        shape = HariMuThemeTokens.shapes.control,
        color = container,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (isWarning) Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = onContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            }
        }
    }
}

@Composable
fun ConverterWorkflowSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag("converter_search_input"),
        singleLine = true,
        shape = HariMuThemeTokens.shapes.control,
        placeholder = { Text(stringResource(R.string.converter_workflow_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.minimumInteractiveComponentSize()
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
}

@Composable
fun ConverterFocusedToolBanner(
    toolId: String,
    toolTitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = HariMuThemeTokens.semantic
    Surface(
        shape = HariMuThemeTokens.shapes.control,
        color = semantic.infoContainer,
        border = BorderStroke(1.dp, semantic.infoBorder.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("converter_focused_tool_banner")
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = semantic.onInfoContainer,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.converter_focused_tool_title, toolId, toolTitle),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = semantic.onInfoContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.converter_focused_tool_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.onInfoContainer
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("converter_focused_tool_dismiss")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.converter_dismiss_focus),
                    tint = semantic.onInfoContainer
                )
            }
        }
    }
}
