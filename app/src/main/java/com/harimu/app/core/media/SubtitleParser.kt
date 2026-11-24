package com.harimu.app.core.media

import java.util.Locale

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object SubtitleParser {

    private val TIME_ARROW_REGEX = Regex("""(\d{1,2}:)?\d{2}:\d{2}[.,]\d{1,3}\s*-->\s*(\d{1,2}:)?\d{2}:\d{2}[.,]\d{1,3}""")
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")

    fun parseSrtOrVtt(rawContent: String): List<SubtitleCue> {
        val clean = rawContent.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val lines = clean.lines()
        val cues = mutableListOf<SubtitleCue>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("WEBVTT") || line.startsWith("NOTE") || line.startsWith("STYLE")) {
                if (line.startsWith("NOTE") || line.startsWith("STYLE")) {
                    while (i < lines.size && lines[i].isNotBlank()) i++
                }
                i++
                continue
            }

            if (TIME_ARROW_REGEX.containsMatchIn(line)) {
                val arrowParts = line.split("-->")
                if (arrowParts.size >= 2) {
                    val startMs = parseTimestampMs(arrowParts[0].trim())
                    val endRaw = arrowParts[1].trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                    val endMs = parseTimestampMs(endRaw)
                    i++
                    val textLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].isNotBlank()) {
                        val stripped = lines[i].replace(HTML_TAG_REGEX, "").trim()
                        if (stripped.isNotEmpty()) textLines.add(stripped)
                        i++
                    }
                    if (startMs != null && endMs != null && endMs > startMs && textLines.isNotEmpty()) {
                        cues.add(SubtitleCue(startMs, endMs, textLines.joinToString("\n")))
                    }
                    continue
                }
            }
            i++
        }
        return cues
    }

    fun parseTimestampMs(token: String): Long? {
        val clean = token.trim().replace(',', '.')
        val parts = clean.split(':')
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val secParts = parts[2].split('.')
                val s = secParts[0].toLongOrNull() ?: return null
                val ms = parseMillisFraction(secParts.getOrElse(1) { "0" })
                ((h * 3600L + m * 60L + s) * 1000L) + ms
            }
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val secParts = parts[1].split('.')
                val s = secParts[0].toLongOrNull() ?: return null
                val ms = parseMillisFraction(secParts.getOrElse(1) { "0" })
                ((m * 60L + s) * 1000L) + ms
            }
            else -> null
        }
    }

    private fun parseMillisFraction(frac: String): Long {
        val padded = frac.filter { it.isDigit() }.padEnd(3, '0').take(3)
        return padded.toLongOrNull() ?: 0L
    }

    fun formatAssTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalCentis = clamped / 10L
        val cs = totalCentis % 100L
        val totalSec = totalCentis / 100L
        val sec = totalSec % 60L
        val min = (totalSec / 60L) % 60L
        val hr = totalSec / 3600L
        return String.format(Locale.US, "%d:%02d:%02d.%02d", hr, min, sec, cs)
    }

    fun toAssColorBgr(colorName: String): String = when (colorName) {
        "Kuning", "Yellow" -> "&H0000FFFF"
        "Oranye", "Orange" -> "&H002C6BFF"
        "Cyan" -> "&H00FFFF00"
        "Hijau", "Green" -> "&H0066FF66"
        else -> "&H00FFFFFF"
    }

    fun toAssContent(
        cues: List<SubtitleCue>,
        playResW: Int = 1280,
        playResH: Int = 720,
        fontSize: Int = 36,
        colorName: String = "Putih",
        styleMode: String = "Outline Hitam",
        position: String = "Bawah"
    ): String {
        val safeW = playResW.coerceAtLeast(320)
        val safeH = playResH.coerceAtLeast(240)
        val alignment = when (position) {
            "Atas", "Top" -> 8
            "Tengah", "Center" -> 5
            else -> 2
        }
        val borderStyle = if (styleMode.contains("Kotak", ignoreCase = true) || styleMode.contains("Box", ignoreCase = true)) 3 else 1
        val outline = if (styleMode.contains("Tanpa", ignoreCase = true) || styleMode.contains("Clean", ignoreCase = true)) 0 else 2
        val shadow = if (outline > 0) 1 else 0
        val primaryCol = toAssColorBgr(colorName)
        val backCol = if (borderStyle == 3) "&H88000000" else "&H80000000"

        return buildString {
            appendLine("[Script Info]")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: $safeW")
            appendLine("PlayResY: $safeH")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            appendLine("Style: Default,Roboto,$fontSize,$primaryCol,&H000000FF,&H00000000,$backCol,-1,0,0,0,100,100,0,0,$borderStyle,$outline,$shadow,$alignment,28,28,32,1")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            cues.forEach { cue ->
                val escaped = cue.text
                    .replace("\\", "\\\\")
                    .replace("{", "(")
                    .replace("}", ")")
                    .replace("\n", "\\N")
                appendLine("Dialogue: 0,${formatAssTimestamp(cue.startMs)},${formatAssTimestamp(cue.endMs)},Default,,0,0,0,,$escaped")
            }
        }
    }
}
