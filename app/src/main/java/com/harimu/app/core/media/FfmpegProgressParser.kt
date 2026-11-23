package com.harimu.app.core.media

import java.util.Locale
import kotlin.math.roundToLong

object FfmpegProgressParser {

    private val TIME_REGEX = Regex("""time=\s*(\d{1,3}):(\d{2}):(\d{2}(?:\.\d+)?)""")
    private val TIME_SECONDS_REGEX = Regex("""time=\s*(\d+(?:\.\d+)?)""")
    private val DURATION_REGEX = Regex("""Duration:\s*(\d{1,3}):(\d{2}):(\d{2}(?:\.\d+)?)""")

    fun parseTimeMsFromLog(line: String): Long? {
        TIME_REGEX.find(line)?.let { match ->
            val h = match.groupValues[1].toLongOrNull() ?: return null
            val m = match.groupValues[2].toLongOrNull() ?: return null
            val s = match.groupValues[3].toDoubleOrNull() ?: return null
            return ((h * 3600.0 + m * 60.0 + s) * 1000.0).roundToLong().coerceAtLeast(0L)
        }
        TIME_SECONDS_REGEX.find(line)?.let { match ->
            val sec = match.groupValues[1].toDoubleOrNull() ?: return null
            return (sec * 1000.0).roundToLong().coerceAtLeast(0L)
        }
        return null
    }

    fun parseDurationMsFromLog(line: String): Long? {
        val match = DURATION_REGEX.find(line) ?: return null
        val h = match.groupValues[1].toLongOrNull() ?: return null
        val m = match.groupValues[2].toLongOrNull() ?: return null
        val s = match.groupValues[3].toDoubleOrNull() ?: return null
        return ((h * 3600.0 + m * 60.0 + s) * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    fun computePercent(processedMs: Double, totalDurationMs: Long): Int {
        if (totalDurationMs <= 0L || processedMs <= 0.0) return 0
        val raw = ((processedMs / totalDurationMs.toDouble()) * 100.0).toInt()
        return raw.coerceIn(0, 99)
    }

    fun formatDurationMs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
