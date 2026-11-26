package com.harimu.app.core.toolapi

import com.harimu.app.feature.tools.InternalMediaTestTool
import com.harimu.app.feature.tools.aiCloudTools
import com.harimu.app.feature.tools.aiLocalTools
import com.harimu.app.feature.tools.audioTools
import com.harimu.app.feature.tools.converterSuiteTools
import com.harimu.app.feature.tools.converterTools
import com.harimu.app.feature.tools.coreTools
import com.harimu.app.feature.tools.imageTools
import com.harimu.app.feature.tools.nusantaraTools
import com.harimu.app.feature.tools.pdfVaultTools
import com.harimu.app.feature.tools.stubAudioTools
import com.harimu.app.feature.tools.stubImageTools
import com.harimu.app.feature.tools.stubVideoTools
import com.harimu.app.feature.tools.utilityApiTools
import com.harimu.app.feature.tools.videoTools

object ToolRegistry {

    /** Daftar mentah sebelum dedup — dipakai [duplicateIds] untuk menangkap tabrakan ID. */
    private val requestedTools: List<Tool> by lazy {
        coreTools() + aiCloudTools() + utilityApiTools() +
            aiLocalTools() +
            videoTools() + stubVideoTools() +
            audioTools() + stubAudioTools() +
            imageTools() + stubImageTools() +
            pdfVaultTools() +
            converterTools() + converterSuiteTools() +
            nusantaraTools()
    }

    /**
     * ID yang didaftarkan lebih dari sekali. `allTools` memakai `distinctBy`, jadi tanpa pemeriksaan
     * ini tool kedua dengan ID sama akan hilang diam-diam dari registry (pernah terjadi pada
     * CORE-05/06/07 di Fase 11). Tes regresi wajib memastikan daftar ini kosong.
     */
    fun duplicateIds(): List<String> =
        requestedTools.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys.sorted()

    val allTools: List<Tool> by lazy {
        requestedTools.distinctBy { it.id }.sortedWith(
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
