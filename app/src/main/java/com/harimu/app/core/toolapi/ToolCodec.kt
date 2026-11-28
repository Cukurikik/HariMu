package com.harimu.app.core.toolapi

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull

object ToolInputCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class EncodedParam(
        val type: String,
        val value: String
    )

    @Serializable
    private data class Payload(
        val uris: List<String> = emptyList(),
        val secondaryUri: String? = null,
        val textInput: String = "",
        val params: Map<String, EncodedParam> = emptyMap()
    )

    fun encode(input: ToolInput): String {
        val encodedParams = input.params.mapValues { (_, v) ->
            when (v) {
                is Boolean -> EncodedParam("bool", v.toString())
                is Int -> EncodedParam("int", v.toString())
                is Long -> EncodedParam("long", v.toString())
                is Float -> EncodedParam("float", v.toString())
                is Double -> EncodedParam("double", v.toString())
                else -> EncodedParam("str", v.toString())
            }
        }
        return json.encodeToString(
            Payload(
                uris = input.uris.map { it.toString() },
                secondaryUri = input.secondaryUri?.toString(),
                textInput = input.textInput,
                params = encodedParams
            )
        )
    }

    fun decode(raw: String): ToolInput {
        if (raw.isBlank()) return ToolInput()
        val payload = runCatching { json.decodeFromString<Payload>(raw) }.getOrNull() ?: return ToolInput()
        val decodedParams = LinkedHashMap<String, Any>()
        payload.params.forEach { (k, ep) ->
            val v: Any = when (ep.type) {
                "bool" -> ep.value.toBooleanStrictOrNull() ?: false
                "int" -> ep.value.toIntOrNull() ?: 0
                "long" -> ep.value.toLongOrNull() ?: 0L
                "float" -> ep.value.toFloatOrNull() ?: 0f
                "double" -> ep.value.toDoubleOrNull() ?: 0.0
                else -> ep.value
            }
            decodedParams[k] = v
        }
        return ToolInput(
            uris = payload.uris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() },
            secondaryUri = payload.secondaryUri?.let { runCatching { Uri.parse(it) }.getOrNull() },
            textInput = payload.textInput,
            params = decodedParams
        )
    }
}

object ToolOutputCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Entry(
        val path: String,
        val mimeType: String,
        val displayName: String
    )

    fun encode(entries: List<Entry>): String = json.encodeToString(entries)

    fun decode(raw: String): List<Entry> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Entry>>(raw) }.getOrDefault(emptyList())
    }
}
