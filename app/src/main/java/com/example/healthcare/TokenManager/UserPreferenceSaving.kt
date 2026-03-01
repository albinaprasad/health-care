package com.example.healthcare.TokenManager

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


object PrefManager {
    fun get(context: Context) = UserPreferenceSaving(context.applicationContext)
}

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferenceSaving(val context: Context) {


    companion object {
        private val TOKEN_KEY   = stringPreferencesKey("auth_token")
        private val ELDER_ID_KEY = intPreferencesKey("elder_id")
        private val PRESCRIPTION_HASH_KEY = stringPreferencesKey("prescription_hash")
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

     fun getToken() : Flow<String?>{
       return context.dataStore.data.map{ preferences ->
          preferences[TOKEN_KEY]
        }
    }

    suspend fun saveElderId(id: Int) {
        context.dataStore.edit { preferences ->
            preferences[ELDER_ID_KEY] = id
        }
    }

    suspend fun getElderIdOnce(): Int {
        return context.dataStore.data.map { it[ELDER_ID_KEY] ?: -1 }.first()
    }

    suspend fun savePrescriptionHash(hash: String) {
        context.dataStore.edit { preferences ->
            preferences[PRESCRIPTION_HASH_KEY] = hash
        }
    }

    suspend fun getPrescriptionHashOnce(): String {
        return context.dataStore.data.map { it[PRESCRIPTION_HASH_KEY] ?: "" }.first()
    }

    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(ELDER_ID_KEY)
        }
    }
}