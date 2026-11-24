package com.harimu.app.core.toolapi

import com.harimu.app.feature.tools.InternalMediaTestTool
import com.harimu.app.feature.tools.aiCloudTools
import com.harimu.app.feature.tools.aiLocalTools
import com.harimu.app.feature.tools.audioTools
import com.harimu.app.feature.tools.converterTools
import com.harimu.app.feature.tools.coreTools
import com.harimu.app.feature.tools.imageTools
import com.harimu.app.feature.tools.pdfVaultTools
import com.harimu.app.feature.tools.stubAudioTools
import com.harimu.app.feature.tools.stubConverterTools
import com.harimu.app.feature.tools.stubCoreTools
import com.harimu.app.feature.tools.stubImageTools
import com.harimu.app.feature.tools.stubVideoTools
import com.harimu.app.feature.tools.videoTools

object ToolRegistry {

    val allTools: List<Tool> by lazy {
        val combined = coreTools() + aiCloudTools() + stubCoreTools() +
            aiLocalTools() +
            videoTools() + stubVideoTools() +
            audioTools() + stubAudioTools() +
            imageTools() + stubImageTools() +
            pdfVaultTools() +
            converterTools() + stubConverterTools()

        combined.distinctBy { it.id }.sortedWith(
            compareBy<Tool>(
                { it.group.ordinal },
                { it.id.substringAfter('-', "0").toIntOrNull() ?: 0 },
                { it.id }
            )
        )
    }

    fun findById(id: String): Tool? =
        allTools.firstOrNull { it.id == id } ?: InternalMediaTestTool.takeIf { it.id == id }

    fun byGroup(group: ToolGroup): List<Tool> = allTools.filter { it.group == group }

    fun countByGroup(group: ToolGroup): Int = allTools.count { it.group == group }

    fun search(query: String, isEnglish: Boolean, groupFilter: ToolGroup? = null): List<Tool> {
        val q = query.trim().lowercase()
        return allTools.filter { tool ->
            val groupMatch = groupFilter == null || tool.group == groupFilter
            val queryMatch = q.isEmpty() ||
                tool.id.lowercase().contains(q) ||
                tool.title(isEnglish).lowercase().contains(q) ||
                tool.description(isEnglish).lowercase().contains(q) ||
                tool.engineLabel.lowercase().contains(q)
            groupMatch && queryMatch
        }
    }
}
