package com.harimu.app.feature.toolscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.media.FfmpegProgressParser

/**
 * Shared trim range controls (RangeSlider + start/end playhead buttons) reused by
 * video and audio preview cards without duplicating trim math or breaking testTags.
 */
@Composable
fun MediaTrimRangeControls(
    mediaKey: Any,
    title: String,
    totalSec: Float,
    startSec: Float,
    endSec: Float,
    currentPlayheadMs: () -> Long,
    onSeekToMs: (Long) -> Unit,
    onRangeChange: (newStartSec: Float, newEndSec: Float) -> Unit,
    sliderTestTag: String,
    setStartTestTag: String,
    setEndTestTag: String,
    modifier: Modifier = Modifier
) {
    val safeTotalSec = totalSec.coerceAtLeast(1f)
    val clampedStart = startSec.coerceIn(0f, (safeTotalSec - 0.5f).coerceAtLeast(0f))
    val effectiveEnd = if (endSec <= 0f || endSec > safeTotalSec) {
        safeTotalSec
    } else {
        endSec.coerceIn(clampedStart + 0.5f, safeTotalSec)
    }

    var sliderStart by remember(mediaKey, startSec) { mutableFloatStateOf(clampedStart) }
    var sliderEnd by remember(mediaKey, endSec, safeTotalSec) { mutableFloatStateOf(effectiveEnd) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge
        )

        RangeSlider(
            value = sliderStart..sliderEnd,
            onValueChange = { range ->
                val minGap = minOf(0.5f, safeTotalSec * 0.1f)
                val s = range.start.coerceIn(0f, (safeTotalSec - minGap).coerceAtLeast(0f))
                val e = range.endInclusive.coerceIn(s + minGap, safeTotalSec)
                sliderStart = s
                sliderEnd = e
                onRangeChange(s, e)
            },
            onValueChangeFinished = {
                onSeekToMs((sliderStart * 1000f).toLong())
            },
            valueRange = 0f..safeTotalSec,
            colors = SliderDefaults.colors(
                thumbColor = HariMuColors.ElectricOrange,
                activeTrackColor = HariMuColors.ElectricOrange
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(sliderTestTag)
        )

        Text(
            text = stringResource(
                R.string.video_trim_range_labels,
                FfmpegProgressParser.formatDurationMs((sliderStart * 1000f).toLong()),
                FfmpegProgressParser.formatDurationMs((sliderEnd * 1000f).toLong())
            ),
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val headSec = (currentPlayheadMs() / 1000f)
                        .coerceIn(0f, (sliderEnd - 0.5f).coerceAtLeast(0f))
                    sliderStart = headSec
                    onRangeChange(headSec, sliderEnd)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag(setStartTestTag)
            ) {
                Text(stringResource(R.string.video_set_start_playhead))
            }
            OutlinedButton(
                onClick = {
                    val headSec = (currentPlayheadMs() / 1000f)
                        .coerceIn(sliderStart + 0.5f, safeTotalSec)
                    sliderEnd = headSec
                    onRangeChange(sliderStart, headSec)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag(setEndTestTag)
            ) {
                Text(stringResource(R.string.video_set_end_playhead))
            }
        }
    }
}
