package com.harimu.app.feature.toolscreen

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

data class ToolPrefillData(
    val toolId: String,
    val uris: List<Uri> = emptyList(),
    val secondaryUri: Uri? = null,
    val params: Map<String, Any> = emptyMap(),
    val textInput: String = ""
)

object ToolPrefillBus {
    private val pending = ConcurrentHashMap<String, ToolPrefillData>()

    fun put(data: ToolPrefillData) {
        pending[data.toolId] = data
    }

    fun consume(toolId: String): ToolPrefillData? = pending.remove(toolId)
}
