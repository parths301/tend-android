package com.tend.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    private val providerKey = stringPreferencesKey("ai_provider")
    private val customCategoriesKey = stringSetPreferencesKey("custom_categories")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val checkinEnabledKey = booleanPreferencesKey("checkin_enabled")
    private val checkinMinKey = intPreferencesKey("checkin_min")
    private val calendarEnabledKey = booleanPreferencesKey("calendar_enabled")

    private fun modelKey(provider: String) = stringPreferencesKey("model_$provider")

    val heatmapWeeks: Flow<Int> = store.data.map { it[heatmapWeeksKey] ?: 17 }
    val showAiBar: Flow<Boolean> = store.data.map { it[showAiBarKey] ?: true }
    val provider: Flow<String> = store.data.map { it[providerKey] ?: PROVIDER_GEMINI }
    val customCategories: Flow<List<String>> =
        store.data.map { (it[customCategoriesKey] ?: emptySet()).sorted() }
    val notificationsEnabled: Flow<Boolean> = store.data.map { it[notificationsEnabledKey] ?: true }
    val checkinEnabled: Flow<Boolean> = store.data.map { it[checkinEnabledKey] ?: true }
    val checkinMin: Flow<Int> = store.data.map { it[checkinMinKey] ?: DEFAULT_CHECKIN_MIN }
    val calendarEnabled: Flow<Boolean> = store.data.map { it[calendarEnabledKey] ?: false }

    /** Selected model for the currently selected provider. */
    val model: Flow<String> = store.data.map { prefs ->
        val p = prefs[providerKey] ?: PROVIDER_GEMINI
        prefs[modelKey(p)] ?: defaultModel(p)
    }

    suspend fun setHeatmapWeeks(weeks: Int) {
        store.edit { it[heatmapWeeksKey] = weeks.coerceIn(8, 17) }
    }

    suspend fun setShowAiBar(show: Boolean) {
        store.edit { it[showAiBarKey] = show }
    }

    suspend fun setProvider(provider: String) {
        store.edit { it[providerKey] = provider }
    }

    suspend fun setModel(model: String) {
        store.edit { prefs ->
            val p = prefs[providerKey] ?: PROVIDER_GEMINI
            prefs[modelKey(p)] = model.trim().ifEmpty { defaultModel(p) }
        }
    }

    suspend fun addCustomCategory(name: String) {
        val trimmed = name.trim().replaceFirstChar { it.uppercaseChar() }
        if (trimmed.isEmpty() || trimmed in PRESET_CATEGORIES) return
        store.edit { it[customCategoriesKey] = (it[customCategoriesKey] ?: emptySet()) + trimmed }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.edit { it[notificationsEnabledKey] = enabled }
    }

    suspend fun setCheckinEnabled(enabled: Boolean) {
        store.edit { it[checkinEnabledKey] = enabled }
    }

    suspend fun setCheckinMin(min: Int) {
        store.edit { it[checkinMinKey] = min.coerceIn(0, 24 * 60 - 1) }
    }

    suspend fun setCalendarEnabled(enabled: Boolean) {
        store.edit { it[calendarEnabledKey] = enabled }
    }

    // BYOK API keys live in EncryptedSharedPreferences, never in plain storage.
    // One key per provider, so switching providers doesn't lose the other key.
    private val securePrefs = EncryptedSharedPreferences.create(
        appContext,
        "tend_secure",
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun loadKeys(): Map<String, String> = mapOf(
        PROVIDER_ANTHROPIC to (securePrefs.getString("anthropic_api_key", "") ?: ""),
        PROVIDER_GEMINI to (securePrefs.getString("gemini_api_key", "") ?: ""),
    )

    private val keysState = MutableStateFlow(loadKeys())
    val apiKeys: StateFlow<Map<String, String>> = keysState.asStateFlow()

    fun setApiKey(provider: String, key: String) {
        securePrefs.edit().putString("${provider}_api_key", key.trim()).apply()
        keysState.value = loadKeys()
    }

    companion object {
        const val PROVIDER_ANTHROPIC = "anthropic"
        const val PROVIDER_GEMINI = "gemini"
        const val DEFAULT_CHECKIN_MIN = 21 * 60 + 30 // 9:30 PM nightly check-in
        val PRESET_CATEGORIES = listOf("Fitness", "Mind", "Work", "Health")

        fun defaultModel(provider: String): String = when (provider) {
            PROVIDER_GEMINI -> "gemini-2.5-flash"
            else -> "claude-opus-4-8"
        }

        fun providerLabel(provider: String): String = when (provider) {
            PROVIDER_GEMINI -> "Gemini"
            else -> "Claude"
        }

        fun keyPlaceholder(provider: String): String = when (provider) {
            PROVIDER_GEMINI -> "AIza…"
            else -> "sk-ant-…"
        }
    }
}
