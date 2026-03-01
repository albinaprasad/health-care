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

        const val DEFAULT_API_URL = "https://disproportionable-unantagonistic-alvin.ngrok-free.dev"
        const val DEFAULT_WS_URL = "wss://yourself-keen-pine-inner.trycloudflare.com/ws?token="
    }

    suspend fun saveUrls(apiUrl: String, wsUrl: String) {
        context.urlDataStore.edit { prefs ->
            prefs[API_BASE_URL_KEY] = apiUrl
            prefs[WEBSOCKET_URL_KEY] = wsUrl
        }
    }

    fun getApiUrl(): Flow<String> {
        return context.urlDataStore.data.map { prefs ->
            prefs[API_BASE_URL_KEY] ?: DEFAULT_API_URL
        }
    }

    fun getWsUrl(): Flow<String> {
        return context.urlDataStore.data.map { prefs ->
            prefs[WEBSOCKET_URL_KEY] ?: DEFAULT_WS_URL
        }
    }

    suspend fun getApiUrlOnce(): String {
        return getApiUrl().first()
    }

    suspend fun getWsUrlOnce(): String {
        return getWsUrl().first()
    }
}
