package com.example.healthcare.TokenManager

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.urlDataStore by preferencesDataStore(name = "url_prefs")

class UrlPreferences(private val context: Context) {

    companion object {
        private val API_BASE_URL_KEY = stringPreferencesKey("api_base_url")
        private val WEBSOCKET_URL_KEY = stringPreferencesKey("websocket_url")

        // ✅ Fixed: correct domain + trailing slash
        const val DEFAULT_API_URL = "https://disproportionable-unantagonistic-alvin.ngrok-free.app/"
        const val DEFAULT_WS_URL = "wss://yourself-keen-pine-inner.trycloudflare.com/ws?token="

        // ✅ Helper to normalize URLs
        private fun normalizeApiUrl(url: String): String {
            val trimmed = url.trim()
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }

        fun isValidUrl(url: String): Boolean {
            return url.trim().startsWith("http://") || url.trim().startsWith("https://")
        }
    }

    suspend fun saveUrls(apiUrl: String, wsUrl: String) {
        // ✅ Validate before saving — never store a bad URL
        val safeApiUrl = if (isValidUrl(apiUrl)) normalizeApiUrl(apiUrl) else DEFAULT_API_URL
        val safeWsUrl = if (wsUrl.startsWith("ws")) wsUrl.trim() else DEFAULT_WS_URL

        context.urlDataStore.edit { prefs ->
            prefs[API_BASE_URL_KEY] = safeApiUrl
            prefs[WEBSOCKET_URL_KEY] = safeWsUrl
        }
    }

    fun getApiUrl(): Flow<String> {
        return context.urlDataStore.data.map { prefs ->
            val saved = prefs[API_BASE_URL_KEY] ?: DEFAULT_API_URL
            // ✅ Fallback to default if somehow corrupted data is stored
            if (isValidUrl(saved)) normalizeApiUrl(saved) else DEFAULT_API_URL
        }
    }

    fun getWsUrl(): Flow<String> {
        return context.urlDataStore.data.map { prefs ->
            prefs[WEBSOCKET_URL_KEY] ?: DEFAULT_WS_URL
        }
    }

    suspend fun getApiUrlOnce(): String = getApiUrl().first()

    suspend fun getWsUrlOnce(): String = getWsUrl().first()

    // ✅ Call this to wipe corrupted saved URLs and reset to defaults
    suspend fun resetToDefaults() {
        context.urlDataStore.edit { prefs ->
            prefs[API_BASE_URL_KEY] = DEFAULT_API_URL
            prefs[WEBSOCKET_URL_KEY] = DEFAULT_WS_URL
        }
    }
}
