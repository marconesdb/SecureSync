package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "securesync_prefs")

class SecureDataStore(private val context: Context) {

    companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_FIREBASE_ENABLED = booleanPreferencesKey("firebase_enabled")
        val KEY_DARK_MODE = stringPreferencesKey("dark_mode") // "SYSTEM", "LIGHT", "DARK"
        val KEY_LOCAL_ENCRYPTION = booleanPreferencesKey("local_encryption_enabled")
    }

    val userIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_ID]
    }

    val userEmailFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_EMAIL]
    }

    val userNameFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_NAME]
    }

    val firebaseEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FIREBASE_ENABLED] ?: false // Default to local for standalone reliability
    }

    val darkModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_MODE] ?: "SYSTEM"
    }

    val localEncryptionFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_LOCAL_ENCRYPTION] ?: false
    }

    suspend fun saveSession(userId: String, email: String, name: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = userId
            preferences[KEY_USER_EMAIL] = email
            preferences[KEY_USER_NAME] = name
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_USER_ID)
            preferences.remove(KEY_USER_EMAIL)
            preferences.remove(KEY_USER_NAME)
        }
    }

    suspend fun setFirebaseEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FIREBASE_ENABLED] = enabled
        }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_MODE] = mode
        }
    }

    suspend fun setLocalEncryption(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOCAL_ENCRYPTION] = enabled
        }
    }
}
