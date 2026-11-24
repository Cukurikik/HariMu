package com.harimu.app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.harimu.app.core.design.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "harimu_settings")

@Serializable
data class AppSettings(
    val themeMode: String = ThemeMode.DARK.name,
    val dynamicColor: Boolean = false,
    val isEnglish: Boolean = false,
    val outputQuality: Int = 90,
    val autoCleanTemp: Boolean = true,
    val outputFolderUri: String = "",
    val outputFolderLabel: String = "",
    val cloudEndpointUrl: String = "",
    val cloudBucketName: String = "",
    val aiGatewayUrl: String = "",
    val aiUploadConsent: Boolean = false
) {
    fun resolvedThemeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == themeMode } ?: ThemeMode.DARK
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val IS_ENGLISH = booleanPreferencesKey("is_english")
        val OUTPUT_QUALITY = stringPreferencesKey("output_quality")
        val AUTO_CLEAN_TEMP = booleanPreferencesKey("auto_clean_temp")
        val OUTPUT_FOLDER_URI = stringPreferencesKey("output_folder_uri")
        val OUTPUT_FOLDER_LABEL = stringPreferencesKey("output_folder_label")
        val CLOUD_ENDPOINT = stringPreferencesKey("cloud_endpoint")
        val CLOUD_BUCKET = stringPreferencesKey("cloud_bucket")
        val AI_GATEWAY = stringPreferencesKey("ai_gateway_url")
        val AI_INSTALL_ID = stringPreferencesKey("ai_install_id")
        val AI_TOKEN = stringPreferencesKey("ai_install_token")
        val AI_TOKEN_EXPIRES = stringPreferencesKey("ai_install_token_expires")
        val AI_UPLOAD_CONSENT = booleanPreferencesKey("ai_upload_consent")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            isEnglish = prefs[Keys.IS_ENGLISH] ?: false,
            outputQuality = prefs[Keys.OUTPUT_QUALITY]?.toIntOrNull() ?: 90,
            autoCleanTemp = prefs[Keys.AUTO_CLEAN_TEMP] ?: true,
            outputFolderUri = prefs[Keys.OUTPUT_FOLDER_URI] ?: "",
            outputFolderLabel = prefs[Keys.OUTPUT_FOLDER_LABEL] ?: "",
            cloudEndpointUrl = prefs[Keys.CLOUD_ENDPOINT] ?: "",
            cloudBucketName = prefs[Keys.CLOUD_BUCKET] ?: "",
            aiGatewayUrl = prefs[Keys.AI_GATEWAY] ?: "",
            aiUploadConsent = prefs[Keys.AI_UPLOAD_CONSENT] ?: false
        )
    }

    suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setEnglish(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_ENGLISH] = enabled }
    }

    suspend fun setOutputQuality(quality: Int) {
        context.dataStore.edit { it[Keys.OUTPUT_QUALITY] = quality.coerceIn(30, 100).toString() }
    }

    suspend fun setAutoCleanTemp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLEAN_TEMP] = enabled }
    }

    suspend fun setOutputFolder(uri: String, label: String) {
        context.dataStore.edit {
            it[Keys.OUTPUT_FOLDER_URI] = uri.trim()
            it[Keys.OUTPUT_FOLDER_LABEL] = label.trim()
        }
    }

    suspend fun setCloudConnection(endpoint: String, bucket: String) {
        context.dataStore.edit {
            it[Keys.CLOUD_ENDPOINT] = endpoint.trim()
            it[Keys.CLOUD_BUCKET] = bucket.trim()
        }
    }

    suspend fun setAiGatewayUrl(url: String) {
        context.dataStore.edit { it[Keys.AI_GATEWAY] = url.trim().trimEnd('/') }
    }

    /**
     * Token instal AI. Disimpan di DataStore biasa karena token ini bukan kunci provider — ia hanya
     * identitas perangkat yang bisa dicabut dari sisi Worker dengan mengganti INSTALL_TOKEN_SECRET.
     */
    suspend fun aiInstallId(): String {
        val existing = context.dataStore.data.first()[Keys.AI_INSTALL_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = java.util.UUID.randomUUID().toString().replace("-", "")
        context.dataStore.edit { it[Keys.AI_INSTALL_ID] = generated }
        return generated
    }

    suspend fun aiToken(): Pair<String, Long> {
        val prefs = context.dataStore.data.first()
        val token = prefs[Keys.AI_TOKEN] ?: ""
        val expires = prefs[Keys.AI_TOKEN_EXPIRES]?.toLongOrNull() ?: 0L
        return token to expires
    }

    suspend fun saveAiToken(token: String, expiresAtEpochSec: Long) {
        context.dataStore.edit {
            it[Keys.AI_TOKEN] = token
            it[Keys.AI_TOKEN_EXPIRES] = expiresAtEpochSec.toString()
        }
    }

    suspend fun clearAiToken() {
        context.dataStore.edit {
            it.remove(Keys.AI_TOKEN)
            it.remove(Keys.AI_TOKEN_EXPIRES)
        }
    }

    suspend fun aiUploadConsent(): Boolean =
        context.dataStore.data.first()[Keys.AI_UPLOAD_CONSENT] ?: false

    suspend fun setAiUploadConsent(granted: Boolean) {
        context.dataStore.edit { it[Keys.AI_UPLOAD_CONSENT] = granted }
    }

    suspend fun applyImported(imported: AppSettings) {
        context.dataStore.edit {
            it[Keys.THEME_MODE] = imported.themeMode
            it[Keys.DYNAMIC_COLOR] = imported.dynamicColor
            it[Keys.IS_ENGLISH] = imported.isEnglish
            it[Keys.OUTPUT_QUALITY] = imported.outputQuality.toString()
            it[Keys.AUTO_CLEAN_TEMP] = imported.autoCleanTemp
            it[Keys.OUTPUT_FOLDER_URI] = imported.outputFolderUri
            it[Keys.OUTPUT_FOLDER_LABEL] = imported.outputFolderLabel
            it[Keys.CLOUD_ENDPOINT] = imported.cloudEndpointUrl
            it[Keys.CLOUD_BUCKET] = imported.cloudBucketName
        }
    }
}
