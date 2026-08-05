package com.tend.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tend.app.data.backup.BackupSettings
import com.tend.app.domain.chat.ChatMode
import com.tend.app.domain.chat.Personality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.settingsStore

    private val heatmapWeeksKey = intPreferencesKey("heatmap_weeks")
    private val showAiBarKey = booleanPreferencesKey("show_ai_bar")
    private val chatModeKey = stringPreferencesKey("chat_mode")
    private val ocrEnabledKey = booleanPreferencesKey("memory_ocr_enabled")
    private val customPromptKey = stringPreferencesKey("custom_system_prompt")
    private val advancedJsonKey = stringPreferencesKey("advanced_json")
    private val personalitiesKey = stringPreferencesKey("personalities")
    private val activePersonalityKey = longPreferencesKey("active_personality")
    private val providerKey = stringPreferencesKey("ai_provider")
    private val customCategoriesKey = stringSetPreferencesKey("custom_categories")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val checkinEnabledKey = booleanPreferencesKey("checkin_enabled")
    private val checkinMinKey = intPreferencesKey("checkin_min")
    private val calendarEnabledKey = booleanPreferencesKey("calendar_enabled")

    // Backup
    private val backupFolderKey = stringPreferencesKey("backup_folder_uri")
    private val backupIntervalKey = stringPreferencesKey("backup_interval")
    private val backupMinKey = intPreferencesKey("backup_min")
    private val backupKeepKey = intPreferencesKey("backup_keep")
    private val backupLastAtKey = longPreferencesKey("backup_last_at")
    private val backupLastFileKey = stringPreferencesKey("backup_last_file")
    private val backupLastErrorKey = stringPreferencesKey("backup_last_error")

    private fun modelKey(provider: String) = stringPreferencesKey("model_$provider")

    // Last successfully fetched model list per provider. Persisting it means a
    // cold start can show the picker immediately instead of an empty list while
    // the network answers.
    private fun modelCacheKey(provider: String) = stringPreferencesKey("model_cache_$provider")
    private fun modelCacheAtKey(provider: String) = longPreferencesKey("model_cache_at_$provider")

    val heatmapWeeks: Flow<Int> = store.data.map { it[heatmapWeeksKey] ?: 17 }
    val showAiBar: Flow<Boolean> = store.data.map { it[showAiBarKey] ?: true }
    /** The mode the user selected. What actually runs also depends on a key existing. */
    val chatMode: Flow<ChatMode> = store.data.map { ChatMode.from(it[chatModeKey]) }

    /** Off by default: turning it on triggers a model download. */
    val ocrEnabled: Flow<Boolean> = store.data.map { it[ocrEnabledKey] ?: false }

    /** Empty means "use the shipped prompt" — not "send no prompt". */
    val customPrompt: Flow<String> = store.data.map { it[customPromptKey].orEmpty() }

    val advancedJson: Flow<String> = store.data.map { it[advancedJsonKey].orEmpty() }

    /**
     * Presets plus anything the user made. Stored profiles that fail to parse
     * are dropped rather than throwing, so a corrupt entry costs one profile
     * instead of the whole screen.
     */
    val personalities: Flow<List<Personality>> = store.data.map { prefs ->
        val stored = prefs[personalitiesKey]?.let(Personality::decodeList).orEmpty()
        Personality.Presets + stored.filterNot { saved -> Personality.Presets.any { it.id == saved.id } }
    }

    val activePersonality: Flow<Personality> = store.data.map { prefs ->
        val id = prefs[activePersonalityKey] ?: Personality.Default.id
        val stored = prefs[personalitiesKey]?.let(Personality::decodeList).orEmpty()
        (Personality.Presets + stored).firstOrNull { it.id == id } ?: Personality.Default
    }
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

    suspend fun setChatMode(mode: ChatMode) {
        store.edit { it[chatModeKey] = mode.stored }
    }

    suspend fun setOcrEnabled(enabled: Boolean) {
        store.edit { it[ocrEnabledKey] = enabled }
    }

    suspend fun setCustomPrompt(prompt: String) {
        store.edit { it[customPromptKey] = prompt }
    }

    /** Callers validate first; this only stores. */
    suspend fun setAdvancedJson(json: String) {
        store.edit { it[advancedJsonKey] = json }
    }

    suspend fun saveUserPersonalities(list: List<Personality>) {
        store.edit { it[personalitiesKey] = Personality.encodeList(list.filterNot(Personality::builtIn)) }
    }

    suspend fun setActivePersonality(id: Long) {
        store.edit { it[activePersonalityKey] = id }
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

    /** Cached model ids for [provider], newest-known order, or empty. */
    suspend fun cachedModels(provider: String): List<String> {
        val raw = store.data.first()[modelCacheKey(provider)].orEmpty()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    /** Epoch millis of the last successful fetch for [provider]; 0 if never. */
    suspend fun modelsFetchedAt(provider: String): Long =
        store.data.first()[modelCacheAtKey(provider)] ?: 0L

    suspend fun cacheModels(provider: String, ids: List<String>, atMillis: Long) {
        // A newline-joined string rather than a preference Set: DataStore's Set
        // has no defined order, and the ranking is the whole point here.
        store.edit {
            it[modelCacheKey(provider)] = ids.joinToString("\n")
            it[modelCacheAtKey(provider)] = atMillis
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

    // ── backup ──────────────────────────────────────────────────

    /** Persisted SAF tree URI of the folder backups are written to; empty = not chosen yet. */
    val backupFolder: Flow<String> = store.data.map { it[backupFolderKey] ?: "" }
    val backupInterval: Flow<String> = store.data.map { it[backupIntervalKey] ?: BACKUP_OFF }
    val backupMin: Flow<Int> = store.data.map { it[backupMinKey] ?: DEFAULT_BACKUP_MIN }
    val backupKeep: Flow<Int> = store.data.map { it[backupKeepKey] ?: DEFAULT_BACKUP_KEEP }
    val backupLastAt: Flow<Long> = store.data.map { it[backupLastAtKey] ?: 0L }
    val backupLastFile: Flow<String> = store.data.map { it[backupLastFileKey] ?: "" }
    val backupLastError: Flow<String> = store.data.map { it[backupLastErrorKey] ?: "" }

    suspend fun setBackupFolder(uri: String) {
        store.edit { it[backupFolderKey] = uri }
    }

    suspend fun setBackupInterval(interval: String) {
        store.edit { it[backupIntervalKey] = interval }
    }

    suspend fun setBackupMin(min: Int) {
        store.edit { it[backupMinKey] = min.coerceIn(0, 24 * 60 - 1) }
    }

    suspend fun setBackupKeep(keep: Int) {
        store.edit { it[backupKeepKey] = keep.coerceIn(1, 60) }
    }

    suspend fun recordBackupResult(atMillis: Long, fileName: String, error: String) {
        store.edit {
            it[backupLastAtKey] = atMillis
            it[backupLastFileKey] = fileName
            it[backupLastErrorKey] = error
        }
    }

    /** Everything a backup carries, minus the API keys. */
    suspend fun snapshot(): BackupSettings = BackupSettings(
        heatmapWeeks = heatmapWeeks.first(),
        showAiBar = showAiBar.first(),
        provider = provider.first(),
        model = model.first(),
        customCategories = customCategories.first(),
        notificationsEnabled = notificationsEnabled.first(),
        checkinEnabled = checkinEnabled.first(),
        checkinMin = checkinMin.first(),
        calendarEnabled = calendarEnabled.first(),
    )

    /**
     * Restores a backed-up settings block. Backup destination and schedule are
     * deliberately left alone — those describe *this* device, not the one the
     * backup came from.
     */
    suspend fun applySnapshot(s: BackupSettings) {
        store.edit { prefs ->
            prefs[heatmapWeeksKey] = s.heatmapWeeks.coerceIn(8, 17)
            prefs[showAiBarKey] = s.showAiBar
            prefs[providerKey] = s.provider
            prefs[customCategoriesKey] = s.customCategories.toSet()
            prefs[notificationsEnabledKey] = s.notificationsEnabled
            prefs[checkinEnabledKey] = s.checkinEnabled
            prefs[checkinMinKey] = s.checkinMin.coerceIn(0, 24 * 60 - 1)
            prefs[calendarEnabledKey] = s.calendarEnabled
            if (s.model.isNotBlank()) prefs[modelKey(s.provider)] = s.model
        }
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

    private fun loadKeys(): Map<String, String> = PROVIDERS.associateWith {
        securePrefs.getString("${it}_api_key", "") ?: ""
    }

    private val keysState = MutableStateFlow(loadKeys())
    val apiKeys: StateFlow<Map<String, String>> = keysState.asStateFlow()

    fun setApiKey(provider: String, key: String) {
        securePrefs.edit().putString("${provider}_api_key", key.trim()).apply()
        keysState.value = loadKeys()
    }

    companion object {
        const val PROVIDER_ANTHROPIC = "anthropic"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENROUTER = "openrouter"

        /** Every provider, in the order the segmented control shows them. */
        val PROVIDERS = listOf(PROVIDER_GEMINI, PROVIDER_ANTHROPIC, PROVIDER_OPENROUTER)
        const val DEFAULT_CHECKIN_MIN = 21 * 60 + 30 // 9:30 PM nightly check-in
        val PRESET_CATEGORIES = listOf("Fitness", "Mind", "Work", "Health")

        const val BACKUP_OFF = "off"
        const val BACKUP_DAILY = "daily"
        const val BACKUP_WEEKLY = "weekly"
        const val DEFAULT_BACKUP_MIN = 2 * 60 // 2:00 AM, when the phone is idle
        const val DEFAULT_BACKUP_KEEP = 7

        fun intervalLabel(interval: String): String = when (interval) {
            BACKUP_DAILY -> "Daily"
            BACKUP_WEEKLY -> "Weekly"
            else -> "Off"
        }

        fun defaultModel(provider: String): String = when (provider) {
            PROVIDER_GEMINI -> "gemini-2.5-flash"
            PROVIDER_OPENROUTER -> "openai/gpt-oss-20b"
            else -> "claude-opus-4-8"
        }

        fun providerLabel(provider: String): String = when (provider) {
            PROVIDER_GEMINI -> "Gemini"
            PROVIDER_OPENROUTER -> "OpenRouter"
            else -> "Claude"
        }

        fun keyPlaceholder(provider: String): String = when (provider) {
            PROVIDER_GEMINI -> "AIza…"
            PROVIDER_OPENROUTER -> "sk-or-v1-…"
            else -> "sk-ant-…"
        }
    }
}
