package com.harimu.app.feature.nusantara

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuDivider
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityResultHeader
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.design.utilities.copyUtilityText
import com.harimu.app.core.nusantara.Doa

@Composable
fun DoasScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: DoasViewModel = viewModel(factory = DoasViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.load(isEnglish)
    }

    UtilityScreenScaffold(
        title = stringResource(R.string.doas_title),
        onBack = onBack,
        onRefresh = { vm.load(isEnglish) },
        isRefreshing = state.isLoading,
        backTestTag = "doas_back_button",
        refreshTestTag = "doas_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.doas_title),
                    description = stringResource(R.string.doas_tap_expand),
                    badge = stringResource(R.string.doas_badge)
                )
            }

            item(key = "search") {
                UtilitySearchBar(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    label = stringResource(R.string.doas_search_hint),
                    tag = "doas_search"
                )
            }

            if (state.isLoading) {
                item(key = "loading") { UtilityLoadingCard() }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.load(isEnglish) },
                        testTag = "doas_retry_button"
                    )
                }
            }

            item(key = "count") {
                UtilityResultHeader(
                    countText = stringResource(R.string.doas_count, state.visible.size)
                )
            }

            if (state.loaded && !state.isLoading && state.error == null && state.visible.isEmpty()) {
                item(key = "empty") {
                    UtilityEmptyState(
                        icon = Icons.Filled.MenuBook,
                        title = stringResource(R.string.doas_empty_title),
                        subtitle = stringResource(R.string.doas_empty_desc),
                        actionLabel = if (state.query.isNotBlank()) {
                            stringResource(R.string.utility_hub_reset_filter)
                        } else {
                            null
                        },
                        onAction = if (state.query.isNotBlank()) {
                            { vm.onQueryChange("") }
                        } else {
                            null
                        },
                        actionTag = "doas_reset_filter"
                    )
                }
            }

            items(state.visible, key = { it.id }) { doa ->
                DoaCard(
                    doa = doa,
                    expanded = state.selected?.id == doa.id,
                    isEnglish = isEnglish,
                    onToggle = { vm.select(doa) }
                )
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.doas_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
private fun DoaCard(
    doa: Doa,
    expanded: Boolean,
    isEnglish: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val toastCopied = stringResource(R.string.toast_copied)

    UtilitySectionCard(
        highlighted = expanded,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onToggle)
            .testTag("doa_card_${doa.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = doa.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) {
                    if (isEnglish) "Collapse prayer" else "Tutup doa"
                } else {
                    if (isEnglish) "Expand prayer" else "Buka doa"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            if (doa.arabic.isNotBlank()) {
                HariMuInsetPanel {
                    Text(
                        text = doa.arabic,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (doa.latin.isNotBlank()) {
                Text(
                    text = doa.latin,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HariMuDivider(Modifier.padding(vertical = 2.dp))
            if (doa.meaning.isNotBlank()) {
                Text(text = doa.meaning, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = {
                    val formatted = listOfNotNull(
                        doa.title,
                        doa.arabic.takeIf { it.isNotBlank() },
                        doa.latin.takeIf { it.isNotBlank() },
                        doa.meaning.takeIf { it.isNotBlank() }
                    ).joinToString("\n\n")
                    copyUtilityText(context, "doa", formatted, toastCopied)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("doa_copy_${doa.id}")
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.doas_copy_btn))
            }
        } else if (doa.meaning.isNotBlank()) {
            Text(
                text = doa.meaning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
