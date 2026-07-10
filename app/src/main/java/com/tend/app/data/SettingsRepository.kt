package com.tend.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.settingsStore

    private val heatmapWeeksKey = intPreferencesKey("heatmap_weeks")
    private val showAiBarKey = booleanPreferencesKey("show_ai_bar")
    private val modelKey = stringPreferencesKey("claude_model")

    val heatmapWeeks: Flow<Int> = store.data.map { it[heatmapWeeksKey] ?: 17 }
    val showAiBar: Flow<Boolean> = store.data.map { it[showAiBarKey] ?: true }
    val model: Flow<String> = store.data.map { it[modelKey] ?: DEFAULT_MODEL }

    suspend fun setHeatmapWeeks(weeks: Int) {
        store.edit { it[heatmapWeeksKey] = weeks.coerceIn(8, 17) }
    }

    suspend fun setShowAiBar(show: Boolean) {
        store.edit { it[showAiBarKey] = show }
    }

    suspend fun setModel(model: String) {
        store.edit { it[modelKey] = model.trim().ifEmpty { DEFAULT_MODEL } }
    }

    // BYOK Anthropic API key lives in EncryptedSharedPreferences, never in plain storage.
    private val securePrefs = EncryptedSharedPreferences.create(
        appContext,
        "tend_secure",
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val apiKeyState = MutableStateFlow(securePrefs.getString(API_KEY_PREF, "") ?: "")
    val apiKey: StateFlow<String> = apiKeyState.asStateFlow()

    fun setApiKey(key: String) {
        securePrefs.edit().putString(API_KEY_PREF, key.trim()).apply()
        apiKeyState.value = key.trim()
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-4-8"
        private const val API_KEY_PREF = "anthropic_api_key"
    }
}
