package com.harimu.app.feature.nusantara

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.design.HariMuDivider
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.MonoTextStyle
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
import com.harimu.app.core.nusantara.QuranSurah
import com.harimu.app.core.nusantara.QuranVerse

@Composable
fun QuranScreen(
    container: AppContainer,
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val vm: QuranViewModel = viewModel(factory = QuranViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val detail = state.detail
    val spacing = HariMuThemeTokens.spacing
    val context = LocalContext.current
    val audioErrorMsg = stringResource(R.string.quran_audio_error)

    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingVerseNumber by remember { mutableStateOf<Int?>(null) }

    fun stopAudio() {
        runCatching {
            activePlayer?.stop()
        }
        runCatching {
            activePlayer?.release()
        }
        activePlayer = null
        playingVerseNumber = null
    }

    fun toggleVerseAudio(verse: QuranVerse) {
        if (playingVerseNumber == verse.number) {
            stopAudio()
            return
        }
        stopAudio()
        if (verse.audioUrl.isBlank()) {
            Toast.makeText(context, audioErrorMsg, Toast.LENGTH_SHORT).show()
            return
        }
        val started = runCatching {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(verse.audioUrl)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { stopAudio() }
            player.setOnErrorListener { _, _, _ ->
                stopAudio()
                Toast.makeText(context, audioErrorMsg, Toast.LENGTH_SHORT).show()
                true
            }
            activePlayer = player
            playingVerseNumber = verse.number
            player.prepareAsync()
        }
        if (started.isFailure) {
            stopAudio()
            Toast.makeText(context, audioErrorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopAudio() }
    }

    BackHandler(enabled = detail != null) {
        stopAudio()
        vm.closeSurah()
    }

    LaunchedEffect(isEnglish) {
        if (!state.loaded && !state.isLoading) vm.load(isEnglish)
    }

    UtilityScreenScaffold(
        title = detail?.let { "${it.surah.number}. ${it.surah.name}" }
            ?: stringResource(R.string.quran_title),
        onBack = {
            if (detail != null) {
                stopAudio()
                vm.closeSurah()
            } else {
                onBack()
            }
        },
        onRefresh = {
            if (detail != null) {
                vm.openSurah(detail.surah.number, isEnglish)
            } else {
                vm.load(isEnglish)
            }
        },
        isRefreshing = state.isLoading || state.isLoadingDetail,
        backTestTag = "quran_back_button",
        refreshTestTag = "quran_refresh_button"
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            if (detail == null) {
                item(key = "hero") {
                    UtilityHeroBanner(
                        title = stringResource(R.string.quran_title),
                        description = if (isEnglish) {
                            "Browse all 114 surahs with Kemenag Arabic text, transliteration, bilingual translation, and verse audio."
                        } else {
                            "Baca 114 surah Al-Qur'an dengan teks Arab Kemenag, transliterasi, terjemahan, dan audio per ayat."
                        },
                        badge = stringResource(R.string.quran_badge)
                    )
                }

                item(key = "search") {
                    UtilitySearchBar(
                        value = state.query,
                        onValueChange = vm::onQueryChange,
                        label = stringResource(R.string.quran_search_hint),
                        tag = "quran_search"
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
                            testTag = "quran_retry_button"
                        )
                    }
                }

                item(key = "count") {
                    UtilityResultHeader(
                        countText = stringResource(R.string.quran_count, state.visible.size)
                    )
                }

                if (state.loaded && !state.isLoading && state.error == null && state.visible.isEmpty()) {
                    item(key = "empty") {
                        UtilityEmptyState(
                            icon = Icons.Filled.MenuBook,
                            title = stringResource(R.string.quran_empty_title),
                            subtitle = stringResource(R.string.quran_empty_desc),
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
                            actionTag = "quran_reset_filter"
                        )
                    }
                }

                items(state.visible, key = { it.number }) { surah ->
                    SurahRow(
                        surah = surah,
                        isEnglish = isEnglish,
                        onClick = { vm.openSurah(surah.number, isEnglish) }
                    )
                }

                item(key = "source") {
                    UtilityAttributionFooter(
                        sourceText = stringResource(R.string.quran_source),
                        fetchedAtMillis = state.fetchedAtMillis
                    )
                }
            } else {
                if (state.isLoadingDetail) {
                    item(key = "detail-loading") { UtilityLoadingCard() }
                }

                item(key = "detail-head") {
                    UtilitySectionCard(highlighted = true) {
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
                                    text = "${detail.surah.number}. ${detail.surah.name}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = listOf(
                                        if (isEnglish) detail.surah.translationEn else detail.surah.translationId,
                                        detail.surah.revelationId
                                    ).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            UtilityBadgePill(
                                text = if (isEnglish) {
                                    "${detail.surah.verseCount} verses"
                                } else {
                                    "${detail.surah.verseCount} ayat"
                                },
                                mono = false
                            )
                        }
                        HariMuInsetPanel {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.quran_verify_mushaf),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(detail.verses, key = { it.number }) { verse ->
                    VerseCard(
                        surahName = detail.surah.name,
                        verse = verse,
                        isEnglish = isEnglish,
                        isPlaying = playingVerseNumber == verse.number,
                        onToggleAudio = { toggleVerseAudio(verse) }
                    )
                }

                item(key = "back") {
                    OutlinedButton(
                        onClick = {
                            stopAudio()
                            vm.closeSurah()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("quran_back_to_list")
                    ) {
                        Text(stringResource(R.string.quran_back_list))
                    }
                }

                item(key = "source") {
                    UtilityAttributionFooter(
                        sourceText = stringResource(R.string.quran_source),
                        fetchedAtMillis = state.detailFetchedAtMillis ?: state.fetchedAtMillis
                    )
                }
            }
        }
    }
}

@Composable
private fun SurahRow(surah: QuranSurah, isEnglish: Boolean, onClick: () -> Unit) {
    UtilitySectionCard(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("quran_surah_${surah.number}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UtilityBadgePill(text = surah.number.toString().padStart(3, '0'))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = surah.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = listOf(
                        if (isEnglish) surah.translationEn else surah.translationId,
                        surah.revelationId
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (isEnglish) "${surah.verseCount} vv" else "${surah.verseCount} ayat",
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VerseCard(
    surahName: String,
    verse: QuranVerse,
    isEnglish: Boolean,
    isPlaying: Boolean,
    onToggleAudio: () -> Unit
) {
    val translation = (if (isEnglish) verse.translationEn else verse.translationId)
        .ifBlank { verse.translationId.ifBlank { verse.translationEn } }
    val copyPayload = buildString {
        appendLine("$surahName : ${verse.number}")
        appendLine(verse.arabic)
        if (verse.latin.isNotBlank()) appendLine(verse.latin)
        append(translation)
    }

    UtilitySectionCard(highlighted = isPlaying) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UtilityBadgePill(text = "${verse.number}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (verse.audioUrl.isNotBlank()) {
                    IconButton(
                        onClick = onToggleAudio,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("quran_audio_${verse.number}")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.quran_stop_audio else R.string.quran_play_audio
                            ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                UtilityCopyIconButton(
                    textToCopy = copyPayload,
                    clipboardLabel = "quran_verse",
                    contentDescription = stringResource(R.string.quran_copy_verse),
                    testTag = "quran_copy_${verse.number}"
                )
            }
        }
        Text(
            text = verse.arabic,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        if (verse.latin.isNotBlank()) {
            Text(
                text = verse.latin,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HariMuDivider(Modifier.padding(vertical = 2.dp))
        Text(
            text = translation,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
