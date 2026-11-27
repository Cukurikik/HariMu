package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.utilities.UtilityActionButton
import com.harimu.app.core.design.utilities.UtilityAttributionFooter
import com.harimu.app.core.design.utilities.UtilityBadgePill
import com.harimu.app.core.design.utilities.UtilityCopyIconButton
import com.harimu.app.core.design.utilities.UtilityEmptyState
import com.harimu.app.core.design.utilities.UtilityErrorCard
import com.harimu.app.core.design.utilities.UtilityHeroBanner
import com.harimu.app.core.design.utilities.UtilityLoadingCard
import com.harimu.app.core.design.utilities.UtilityResultHeader
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.nusantara.School

@Composable
fun SchoolScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: SchoolViewModel = viewModel(factory = SchoolViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing
    val canSearch = state.query.trim().length >= SchoolViewModel.MIN_QUERY_LENGTH

    UtilityScreenScaffold(
        title = stringResource(R.string.schools_title),
        onBack = onBack,
        onRefresh = if (state.searched && canSearch) {
            { vm.search(isEnglish) }
        } else {
            null
        },
        isRefreshing = state.isLoading,
        backTestTag = "schools_back_button",
        refreshTestTag = "schools_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.schools_title),
                    description = stringResource(R.string.schools_intro),
                    badge = stringResource(R.string.schools_badge)
                )
            }

            item(key = "search") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    UtilitySearchBar(
                        value = state.query,
                        onValueChange = vm::onQueryChange,
                        label = stringResource(R.string.schools_search_hint),
                        tag = "schools_search",
                        onSearch = { if (canSearch) vm.search(isEnglish) }
                    )
                    if (!canSearch) {
                        Text(
                            text = stringResource(R.string.schools_min_chars_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("schools_min_chars_hint")
                        )
                    }
                }
            }

            item(key = "action") {
                UtilityActionButton(
                    label = if (isEnglish) "Search Schools" else "Cari Sekolah",
                    onClick = { vm.search(isEnglish) },
                    icon = Icons.Filled.Search,
                    isLoading = state.isLoading,
                    enabled = canSearch,
                    testTag = "schools_search_button"
                )
            }

            if (!state.searched && !state.isLoading) {
                item(key = "prompt") {
                    UtilityEmptyState(
                        icon = Icons.Filled.School,
                        title = stringResource(R.string.schools_prompt_title),
                        subtitle = stringResource(R.string.schools_prompt_desc)
                    )
                }
            }

            if (state.isLoading) {
                item(key = "loading") { UtilityLoadingCard() }
            }

            state.error?.let { message ->
                item(key = "error") {
                    UtilityErrorCard(
                        message = message,
                        onRetry = { vm.search(isEnglish) },
                        testTag = "schools_retry_button"
                    )
                }
            }

            if (state.searched && !state.isLoading && state.error == null) {
                item(key = "count") {
                    UtilityResultHeader(
                        countText = stringResource(R.string.schools_count, state.schools.size, state.total)
                    )
                }

                if (state.schools.isEmpty()) {
                    item(key = "empty") {
                        UtilityEmptyState(
                            icon = Icons.Filled.School,
                            title = stringResource(R.string.schools_empty_title),
                            subtitle = stringResource(R.string.schools_empty_desc)
                        )
                    }
                }

                items(state.schools, key = { it.npsn.ifBlank { it.name } }) { school ->
                    SchoolCard(school = school, isEnglish = isEnglish)
                }

                if (state.hasMore) {
                    item(key = "more") {
                        OutlinedButton(
                            onClick = { vm.loadMore(isEnglish) },
                            enabled = !state.isLoadingMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("schools_load_more")
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (state.isLoadingMore) {
                                    if (isEnglish) "Loading…" else "Memuat…"
                                } else {
                                    stringResource(R.string.schools_load_more)
                                }
                            )
                        }
                    }
                }
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.schools_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
private fun SchoolCard(school: School, isEnglish: Boolean) {
    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = school.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (school.npsn.isNotBlank()) {
                        UtilityBadgePill(text = "NPSN ${school.npsn}")
                    }
                    if (school.form.isNotBlank()) {
                        UtilityBadgePill(text = school.form, mono = false)
                    }
                    if (school.status.isNotBlank()) {
                        val statusLabel = if (isEnglish) {
                            if (school.status.equals("S", true)) "Private" else "Public"
                        } else {
                            if (school.status.equals("S", true)) "Swasta" else "Negeri"
                        }
                        UtilityBadgePill(text = statusLabel, mono = false)
                    }
                }
            }
            if (school.npsn.isNotBlank()) {
                UtilityCopyIconButton(
                    textToCopy = school.npsn,
                    clipboardLabel = "npsn",
                    contentDescription = stringResource(R.string.schools_copy_npsn),
                    testTag = "school_copy_npsn_${school.npsn}"
                )
            }
        }

        if (school.address.isNotBlank()) {
            Text(text = school.address, style = MaterialTheme.typography.bodyMedium)
        }
        val area = listOf(school.district, school.regency, school.province)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        if (area.isNotBlank()) {
            Text(
                text = area,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (school.latitude.isNotBlank() && school.longitude.isNotBlank()) {
            val coords = "${school.latitude}, ${school.longitude}"
            HariMuInsetPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = coords,
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    UtilityCopyIconButton(
                        textToCopy = coords,
                        clipboardLabel = "school_coords",
                        contentDescription = stringResource(R.string.schools_copy_coords)
                    )
                }
            }
        }
    }
}
