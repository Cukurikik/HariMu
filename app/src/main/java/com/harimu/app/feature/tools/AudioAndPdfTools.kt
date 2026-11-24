package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup

fun audioTools(): List<Tool> = listOf(
    DedicatedTool(
        id = "AUD-02",
        group = ToolGroup.AUDIO,
        engineLabel = "NAT+FF",
        icon = Icons.Filled.Equalizer,
        dedicatedRoute = DedicatedScreenRoute.EQUALIZER,
        titleId = "Pro Equalizer 10-Band (Live + Ekspor)",
        titleEn = "Pro 10-Band Equalizer (Live + Export)",
        descId = "Pratinjau live 10-band (DynamicsProcessing/Equalizer) & ekspor FFmpeg setara.",
        descEn = "Live 10-band AudioFX preview with matching FFmpeg 10-band EQ export."
    ),
    DedicatedTool(
        id = "AUD-21",
        group = ToolGroup.AUDIO,
        engineLabel = "NAT+FF",
        icon = Icons.Filled.GraphicEq,
        dedicatedRoute = DedicatedScreenRoute.AUDIO_STUDIO,
        titleId = "Audio Studio Pro (DAW)",
        titleEn = "Audio Studio Pro (Multitrack DAW)",
        descId = "Mixer multitrack dengan kontrol gain, pan, dan trim.",
        descEn = "Multitrack mixer with per-track gain, pan, and trim."
    )
) + audioForgeBatch1CoreTools() +
    audioForgeBatch1ExtraTools() +
    audioForgeBatch2CoreTools() +
    audioForgeBatch2ExtraTools()

