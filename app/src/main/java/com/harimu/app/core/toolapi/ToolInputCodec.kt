package com.harimu.app.core.toolapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.booleanOrNull
import android.net.Uri

/**
 * Serialisasi [ToolInput] ke JSON untuk diteruskan ke WorkManager (`Data` hanya mendukung tipe
 * primitif). Dipakai oleh `ToolJobWorker` supaya tool berat berjalan di luar `viewModelScope`.
 *
 * Batas: `content://` URI harus masih memiliki izin baca saat worker berjalan; pemanggil
 * sebaiknya memakai `takePersistableUriPermission` untuk berkas yang dipilih pengguna.
 */
object ToolInputCodec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(input: ToolInput): String = buildJsonObject {
        putJsonArray("uris") { input.uris.forEach { add(it.toString()) } }
        put("secondary", input.secondaryUri?.toString() ?: "")
        put("text", input.textInput)
        putJsonObject("params") {
            input.params.forEach { (key, value) ->
                when (value) {
                    is Boolean -> put(key, value)
                    is Int -> put(key, value)
                    is Long -> put(key, value)
                    is Float -> put(key, value)
                    is Double -> put(key, value)
                    is String -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
    }.toString()

    fun decode(raw: String): ToolInput {
        if (raw.isBlank()) return ToolInput()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return ToolInput()
        val uris = (root["uris"] as? JsonArray)?.mapNotNull { element ->
            element.jsonPrimitive.content.takeIf { it.isNotBlank() }?.let(Uri::parse)
        } ?: emptyList()
        val secondary = (root["secondary"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val text = (root["text"] as? JsonPrimitive)?.content ?: ""
        val params = mutableMapOf<String, Any>()
        (root["params"] as? JsonObject)?.forEach { (key, element) ->
            val primitive = element as? JsonPrimitive ?: return@forEach
            when {
                primitive.isString -> params[key] = primitive.content
                else -> {
                    params[key] = primitive.booleanOrNull
                        ?: primitive.intOrNull
                        ?: primitive.floatOrNull
                        ?: primitive.content
                }
            }
        }
        return ToolInput(uris = uris, secondaryUri = secondary, textInput = text, params = params)
    }
}

/** Output yang dikembalikan worker; cukup JSON karena `Data` dibatasi 10 KB. */
object ToolOutputCodec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    data class Entry(val path: String, val mimeType: String, val displayName: String)

    fun encode(entries: List<Entry>): String = buildJsonObject {
        putJsonArray("outputs") {
            entries.forEach { entry ->
                add(
                    buildJsonObject {
                        put("path", entry.path)
                        put("mime", entry.mimeType)
                        put("name", entry.displayName)
                    }
                )
            }
        }
    }.toString()

    fun decode(raw: String): List<Entry> {
        if (raw.isBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        return (root["outputs"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            Entry(
                path = (obj["path"] as? JsonPrimitive)?.content.orEmpty(),
                mimeType = (obj["mime"] as? JsonPrimitive)?.content ?: "application/octet-stream",
                displayName = (obj["name"] as? JsonPrimitive)?.content ?: "output"
            )
        } ?: emptyList()
    }
}
