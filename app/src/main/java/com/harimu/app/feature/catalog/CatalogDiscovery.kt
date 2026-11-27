package com.harimu.app.feature.catalog

import com.harimu.app.R
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolRegistry

enum class CatalogCapabilityFilter(val labelRes: Int, val testTag: String) {
    ALL(R.string.catalog_filter_cap_all, "catalog_cap_ALL"),
    ON_DEVICE(R.string.catalog_filter_cap_on_device, "catalog_cap_ON_DEVICE"),
    ONLINE(R.string.catalog_filter_cap_online, "catalog_cap_ONLINE"),
    LOCAL_MODEL(R.string.catalog_filter_cap_model, "catalog_cap_LOCAL_MODEL");

    fun matches(tool: Tool): Boolean = when (this) {
        ALL -> true
        ON_DEVICE -> !tool.requiresNetwork && tool.requiresModel == null
        ONLINE -> tool.requiresNetwork
        LOCAL_MODEL -> tool.requiresModel != null
    }
}

enum class CatalogSortOrder(val labelRes: Int) {
    DEFAULT(R.string.catalog_sort_default),
    TITLE_ASC(R.string.catalog_sort_az)
}

data class CatalogSection(
    val group: ToolGroup,
    val tools: List<Tool>
)

fun filterAndSortCatalogTools(
    query: String,
    isEnglish: Boolean,
    selectedGroup: ToolGroup?,
    capabilityFilter: CatalogCapabilityFilter = CatalogCapabilityFilter.ALL,
    sortOrder: CatalogSortOrder = CatalogSortOrder.DEFAULT
): List<Tool> {
    val searched = ToolRegistry.search(query, isEnglish, selectedGroup)
    val capabilityFiltered = if (capabilityFilter == CatalogCapabilityFilter.ALL) {
        searched
    } else {
        searched.filter { capabilityFilter.matches(it) }
    }
    return when (sortOrder) {
        CatalogSortOrder.DEFAULT -> capabilityFiltered
        CatalogSortOrder.TITLE_ASC -> capabilityFiltered.sortedWith(
            compareBy<Tool>(
                { it.title(isEnglish).lowercase() },
                { it.id }
            )
        )
    }
}

fun groupCatalogTools(tools: List<Tool>): List<CatalogSection> =
    ToolGroup.entries.mapNotNull { group ->
        val groupTools = tools.filter { it.group == group }
        if (groupTools.isEmpty()) null else CatalogSection(group = group, tools = groupTools)
    }
