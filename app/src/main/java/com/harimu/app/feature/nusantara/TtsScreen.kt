package com.harimu.app.feature.nusantara

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
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
import com.harimu.app.core.design.utilities.UtilityQuickChips
import com.harimu.app.core.design.utilities.UtilityResultHeader
import com.harimu.app.core.design.utilities.UtilityScreenScaffold
import com.harimu.app.core.design.utilities.UtilitySearchBar
import com.harimu.app.core.design.utilities.UtilitySectionCard
import com.harimu.app.core.nusantara.TtsAnswer

private val TTS_PRESETS = listOf("tidak", "ibukota", "lawan")

@Composable
fun TtsScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: TtsViewModel = viewModel(factory = TtsViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val spacing = HariMuThemeTokens.spacing

    UtilityScreenScaffold(
        title = stringResource(R.string.tts_title),
        onBack = onBack,
        onRefresh = if (state.query.isNotBlank()) {
            { vm.search(isEnglish) }
        } else {
            null
        },
        isRefreshing = state.isLoading,
        backTestTag = "tts_back_button",
        refreshTestTag = "tts_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            item(key = "hero") {
                UtilityHeroBanner(
                    title = stringResource(R.string.tts_title),
                    description = stringResource(R.string.tts_intro),
                    badge = stringResource(R.string.tts_badge)
                )
            }

            item(key = "search") {
                UtilitySearchBar(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    label = stringResource(R.string.tts_search_hint),
                    tag = "tts_search",
                    onSearch = { if (state.query.isNotBlank()) vm.search(isEnglish) }
                )
            }

            item(key = "presets") {
                UtilityQuickChips(
                    items = TTS_PRESETS,
                    selectedKey = state.query.trim().lowercase(),
                    keyOf = { it },
                    labelOf = { it },
                    label = stringResource(R.string.tts_presets),
                    tag = "tts_presets",
                    onSelect = { clue -> vm.searchPreset(clue, isEnglish) }
                )
            }

            item(key = "action") {
                UtilityActionButton(
                    label = stringResource(R.string.tts_search_cta),
                    onClick = { vm.search(isEnglish) },
                    icon = Icons.Filled.Search,
                    isLoading = state.isLoading,
                    enabled = state.query.isNotBlank(),
                    testTag = "tts_search_button"
                )
            }

            if (!state.searched && !state.isLoading) {
                item(key = "prompt") {
                    UtilityEmptyState(
                        icon = Icons.Filled.Extension,
                        title = stringResource(R.string.tts_title),
                        subtitle = stringResource(R.string.tts_intro)
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
                        testTag = "tts_retry_button"
                    )
                }
            }

            val result = state.result
            if (result != null && !state.isLoading && state.error == null) {
                item(key = "total") {
                    UtilityResultHeader(
                        countText = stringResource(R.string.tts_count, result.total)
                    )
                }

                if (result.answers.isEmpty()) {
                    item(key = "empty") {
                        UtilityEmptyState(
                            icon = Icons.Filled.Extension,
                            title = stringResource(R.string.tts_empty_title),
                            subtitle = stringResource(R.string.tts_empty_desc)
                        )
                    }
                }

                itemsIndexed(
                    items = result.answers,
                    key = { idx, answer -> "${idx}_${answer.word}_${answer.clue}" }
                ) { _, answer ->
                    TtsAnswerCard(answer)
                }
            }

            item(key = "source") {
                UtilityAttributionFooter(
                    sourceText = stringResource(R.string.tts_source),
                    fetchedAtMillis = state.fetchedAtMillis
                )
            }
        }
    }
}

@Composable
private fun TtsAnswerCard(answer: TtsAnswer) {
    val letterCount = answer.word.count { it.isLetterOrDigit() }
    UtilitySectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = answer.word,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UtilityBadgePill(
                        text = stringResource(R.string.tts_letters_count, letterCount)
                    )
                    if (answer.stars > 0) {
                        Text(
                            text = "★".repeat(answer.stars.coerceIn(0, 5)),
                            style = MonoTextStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            UtilityCopyIconButton(
                textToCopy = answer.word,
                clipboardLabel = "tts_answer",
                contentDescription = stringResource(R.string.tts_copy_answer)
            )
        }
        if (answer.clue.isNotBlank()) {
            Text(
                text = answer.clue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
