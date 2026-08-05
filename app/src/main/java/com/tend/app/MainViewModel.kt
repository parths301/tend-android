package com.tend.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tend.app.ai.AiAction
import com.tend.app.ai.AiClient
import com.tend.app.ai.AiProtocol
import com.tend.app.data.CalEvent
import com.tend.app.data.CalendarRepository
import com.tend.app.data.SettingsRepository
import com.tend.app.data.TendRepository
import com.tend.app.data.backup.BackupFormat
import com.tend.app.data.backup.BackupManager
import com.tend.app.data.backup.BackupScheduler
import com.tend.app.data.backup.BackupSnapshot
import com.tend.app.data.backup.readableMessage
import com.tend.app.data.db.AppDatabase
import com.tend.app.data.db.Habit
import com.tend.app.data.db.HabitLog
import com.tend.app.data.db.NoteEntry
import com.tend.app.data.db.PlanBlock
import com.tend.app.data.db.TaskItem
import com.tend.app.domain.Streaks
import com.tend.app.notif.Notifications
import com.tend.app.notif.ReminderScheduler
import com.tend.app.widget.TendWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

enum class Tab { Today, Plan, Tasks, Stats, Detail, Settings }
enum class HabitView { Grid, Week }

/** A habit joined with everything derived from its log history. */
data class HabitUi(
    val habit: Habit,
    val doneDays: Set<Long>,
    val minutesByDay: Map<Long, Int>,
    val doneToday: Boolean,
    val minutesToday: Int,
    val streak: Int,
    val best: Int,
    val rate30: Int,
)

data class ChatMsg(val fromAi: Boolean, val text: String)

/** Models available to the saved key, fetched from the provider. */
data class ModelsUi(
    val loading: Boolean = false,
    val models: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * Auto-plan's reply to the user. Carries whether it worked, so the banner can
 * express the outcome — string-matching the text to guess at failure would
 * break the first time the wording changed.
 */
data class AutoPlanMessage(val text: String, val failed: Boolean)

/**
 * A moment worth celebrating on screen. Kept in the ViewModel rather than in the
 * UI because "did this check-off finish the day" is a question about state, not
 * about a button — the same rule has to hold however the habit got checked.
 */
data class Celebration(
    val kind: Kind,
    val headline: String,
    val detail: String,
) {
    enum class Kind { DayComplete, StreakMilestone }
}

/** Outcome of the most recent backup attempt, scheduled or manual. */
data class BackupStatus(
    val atMillis: Long = 0L,
    val fileName: String = "",
    val error: String = "",
) {
    val ran: Boolean get() = atMillis > 0L
    val failed: Boolean get() = error.isNotEmpty()
}

/** A backup file the user picked, held for confirmation before it overwrites anything. */
data class PendingRestore(
    val snapshot: BackupSnapshot,
    val warnings: List<String>,
)

data class Shell(
    val tab: Tab = Tab.Today,
    val view: HabitView = HabitView.Grid,
    val filter: String = "All",
    val aiOpen: Boolean = false,
    val aiThinking: Boolean = false,
    val detailHabitId: Long = 1L,
    val planDay: Long = LocalDate.now().toEpochDay(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TendRepository(AppDatabase.get(app))
    val settings = SettingsRepository(app)
    private val calendar = CalendarRepository(app)
    private val ai = AiClient()

    val todayDate: LocalDate = LocalDate.now()
    val today: Long = todayDate.toEpochDay()

    private val shellState = MutableStateFlow(Shell())
    val shell: StateFlow<Shell> = shellState.asStateFlow()

    private val chatState = MutableStateFlow<List<ChatMsg>>(emptyList())
    val chat: StateFlow<List<ChatMsg>> = chatState.asStateFlow()

    val habits: StateFlow<List<HabitUi>> =
        combine(repo.habits(), repo.logs()) { habitList, allLogs ->
            val byHabit = allLogs.groupBy(HabitLog::habitId)
            habitList.map { habit ->
                val logs = byHabit[habit.id].orEmpty()
                val doneDays = logs.filter { it.done }.map { it.epochDay }.toSet()
                val minutes = logs.associate { it.epochDay to it.minutes }
                HabitUi(
                    habit = habit,
                    doneDays = doneDays,
                    minutesByDay = minutes,
                    doneToday = today in doneDays,
                    minutesToday = minutes[today] ?: 0,
                    streak = Streaks.current(doneDays, today),
                    best = Streaks.best(doneDays),
                    rate30 = Streaks.rate(doneDays, today, sinceDay = habit.createdDay),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tasks: StateFlow<List<TaskItem>> =
        repo.tasks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val plan: StateFlow<List<PlanBlock>> = shellState
        .map { it.planDay }
        .distinctUntilChanged()
        .flatMapLatest { repo.planFor(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val calendarRefresh = MutableStateFlow(0)
    val calendarEvents: StateFlow<List<CalEvent>> =
        combine(
            shellState.map { it.planDay }.distinctUntilChanged(),
            settings.calendarEnabled,
            calendarRefresh,
        ) { day, enabled, _ -> day to enabled }
            .flatMapLatest { (day, enabled) ->
                flow { emit(if (enabled) calendar.eventsFor(day) else emptyList()) }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notes: StateFlow<List<NoteEntry>> = shellState
        .map { it.detailHabitId }
        .distinctUntilChanged()
        .flatMapLatest { repo.notesFor(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val heatmapWeeks: StateFlow<Int> =
        settings.heatmapWeeks.stateIn(viewModelScope, SharingStarted.Eagerly, 17)
    val showAiBar: StateFlow<Boolean> =
        settings.showAiBar.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val provider: StateFlow<String> =
        settings.provider.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.PROVIDER_GEMINI)
    val model: StateFlow<String> =
        settings.model.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            SettingsRepository.defaultModel(SettingsRepository.PROVIDER_GEMINI),
        )
    val customCategories: StateFlow<List<String>> =
        settings.customCategories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notificationsEnabled: StateFlow<Boolean> =
        settings.notificationsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val checkinEnabled: StateFlow<Boolean> =
        settings.checkinEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val checkinMin: StateFlow<Int> =
        settings.checkinMin.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_CHECKIN_MIN)
    val calendarEnabled: StateFlow<Boolean> =
        settings.calendarEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── backup ──────────────────────────────────────────────────
    val backupFolder: StateFlow<String> =
        settings.backupFolder.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val backupFolderLabel: StateFlow<String> =
        settings.backupFolder.map { BackupManager.folderLabel(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val backupInterval: StateFlow<String> =
        settings.backupInterval.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.BACKUP_OFF)
    val backupMin: StateFlow<Int> =
        settings.backupMin.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_BACKUP_MIN)
    val backupKeep: StateFlow<Int> =
        settings.backupKeep.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_BACKUP_KEEP)
    val backupStatus: StateFlow<BackupStatus> =
        combine(
            settings.backupLastAt,
            settings.backupLastFile,
            settings.backupLastError,
        ) { at, file, error -> BackupStatus(at, file, error) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, BackupStatus())

    private val backupBusyState = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = backupBusyState.asStateFlow()
    private val backupMessageState = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = backupMessageState.asStateFlow()
    private val pendingRestoreState = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore: StateFlow<PendingRestore?> = pendingRestoreState.asStateFlow()

    /** API key for the currently selected provider. */
    val apiKey: StateFlow<String> =
        combine(settings.provider, settings.apiKeys) { p, keys -> keys[p].orEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val modelsState = MutableStateFlow(ModelsUi())
    val models: StateFlow<ModelsUi> = modelsState.asStateFlow()

    /**
     * One-shot celebration events. A SharedFlow with replay 0, so rotating the
     * device or returning to the screen never replays a party the user already
     * saw; [celebratedDay] keeps the day-complete moment to once per day.
     */
    private val celebrationEvents = MutableSharedFlow<Celebration>(extraBufferCapacity = 1)
    val celebrations: SharedFlow<Celebration> = celebrationEvents.asSharedFlow()
    private var celebratedDay: Long? = null

    private val autoPlanningState = MutableStateFlow(false)
    val autoPlanning: StateFlow<Boolean> = autoPlanningState.asStateFlow()
    private val autoPlanMessageState = MutableStateFlow<AutoPlanMessage?>(null)
    val autoPlanMessage: StateFlow<AutoPlanMessage?> = autoPlanMessageState.asStateFlow()

    init {
        Notifications.ensureChannels(app)
        viewModelScope.launch {
            // Early builds seeded demo data; clear it once so the app runs on real data only.
            repo.purgeLegacyDemoData()
            repo.materializeHabitBlocks(today)
            TendWidgets.refresh(getApplication())
            ReminderScheduler.reschedule(getApplication())
            // Re-arms scheduled backups if the OS ever dropped the work.
            BackupScheduler.sync(getApplication())
        }
        viewModelScope.launch {
            // Once a key is present, load the model list so a saved model that has
            // since been retired or is quota-locked (e.g. a "-preview") heals to a
            // working default without the user having to open Settings.
            apiKey.first { it.isNotBlank() }
            refreshModels()
        }
    }

    // ── navigation ──────────────────────────────────────────────
    fun selectTab(tab: Tab) = shellState.update { it.copy(tab = tab) }
    fun openDetail(habitId: Long) = shellState.update { it.copy(tab = Tab.Detail, detailHabitId = habitId) }
    fun setView(view: HabitView) = shellState.update { it.copy(view = view) }
    fun setFilter(filter: String) = shellState.update { it.copy(filter = filter) }

    fun shiftPlanDay(delta: Long) = shellState.update { it.copy(planDay = it.planDay + delta) }
    fun planToday() = shellState.update { it.copy(planDay = today) }

    /** Handles notification deep links; called from the shell on resume. */
    fun consumeDeepLink() {
        when (DeepLinks.pending) {
            MainActivity.DEEPLINK_PLAN_TOMORROW ->
                shellState.update { it.copy(tab = Tab.Plan, planDay = today + 1, aiOpen = false) }
        }
        DeepLinks.pending = null
    }

    private val swipeTabs = listOf(Tab.Today, Tab.Plan, Tab.Tasks, Tab.Stats)
    fun swipe(direction: Int) {
        val current = shellState.value.tab
        val index = swipeTabs.indexOf(current)
        if (index < 0) return
        val next = (index + direction).coerceIn(0, swipeTabs.lastIndex)
        shellState.update { it.copy(tab = swipeTabs[next]) }
    }

    // ── data actions ────────────────────────────────────────────
    fun toggleHabit(habitId: Long) {
        val existing = habits.value.firstOrNull { it.habit.id == habitId } ?: return
        val wasDone = existing.doneToday
        viewModelScope.launch {
            repo.toggleHabitToday(existing.habit, today)
            TendWidgets.refresh(getApplication())
            // Only a check-*on* can be a celebration; un-checking never is.
            if (!wasDone) maybeCelebrate(habitId)
        }
    }

    /**
     * Decides whether the check-off that just happened deserves a celebration.
     *
     * Room's flow is the source of truth for "is it done now", so this waits for
     * the write to surface rather than predicting it — but with a timeout, since
     * a timed habit needs several taps before it flips and must not leave a
     * coroutine parked forever.
     */
    private suspend fun maybeCelebrate(habitId: Long) {
        val settled = withTimeoutOrNull(WRITE_SETTLE_MS) {
            habits.first { list -> list.firstOrNull { it.habit.id == habitId }?.doneToday == true }
        } ?: return

        val justChecked = settled.firstOrNull { it.habit.id == habitId } ?: return

        // Finishing every habit for the day outranks any single streak.
        if (settled.isNotEmpty() && settled.all { it.doneToday }) {
            if (celebratedDay != today) {
                celebratedDay = today
                celebrationEvents.tryEmit(
                    Celebration(
                        kind = Celebration.Kind.DayComplete,
                        headline = "Day complete",
                        detail = "All ${settled.size} habits done. " +
                            "That's what tomorrow's streak is built on.",
                    )
                )
            }
            return
        }

        if (justChecked.streak in STREAK_MILESTONES) {
            celebrationEvents.tryEmit(
                Celebration(
                    kind = Celebration.Kind.StreakMilestone,
                    headline = "${justChecked.streak}-day streak",
                    detail = "${justChecked.habit.name} — your best is ${justChecked.best}.",
                )
            )
        }
    }

    fun toggleTask(task: TaskItem) = viewModelScope.launch { repo.toggleTask(task) }
    fun togglePlan(block: PlanBlock) = viewModelScope.launch { repo.togglePlan(block) }

    fun addHabit(name: String, category: String, type: String, goal: String, reminderMin: Int? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.addHabit(
                trimmed, category,
                goal.trim().ifEmpty { "Daily" },
                type,
                createdDay = today,
                reminderMin = reminderMin,
            )
            repo.materializeHabitBlocks(today)
            TendWidgets.refresh(getApplication())
            ReminderScheduler.reschedule(getApplication())
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            repo.updateHabit(habit)
            repo.materializeHabitBlocks(today)
            TendWidgets.refresh(getApplication())
            ReminderScheduler.reschedule(getApplication())
        }
    }

    fun deleteHabit(habitId: Long) {
        viewModelScope.launch {
            repo.deleteHabit(habitId)
            shellState.update { it.copy(tab = Tab.Today) }
            TendWidgets.refresh(getApplication())
            ReminderScheduler.reschedule(getApplication())
        }
    }

    fun addTask(title: String, group: String, dueDay: Long? = null, dueMin: Int? = null) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.addTask(trimmed, group, dueDay, dueMin)
            ReminderScheduler.reschedule(getApplication())
        }
    }

    fun updateTask(task: TaskItem) {
        viewModelScope.launch {
            repo.updateTask(task)
            ReminderScheduler.reschedule(getApplication())
        }
    }

    fun deleteTask(task: TaskItem) = viewModelScope.launch {
        repo.deleteTask(task)
        ReminderScheduler.reschedule(getApplication())
    }

    fun addPlanBlock(title: String, startMin: Int, durationMin: Int, kind: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val day = shellState.value.planDay
        viewModelScope.launch { repo.addPlanBlock(day, startMin, startMin + durationMin, trimmed, kind) }
    }

    fun updatePlanBlock(block: PlanBlock) = viewModelScope.launch { repo.updatePlanBlock(block) }

    fun deletePlanBlock(block: PlanBlock) = viewModelScope.launch { repo.deletePlanBlock(block) }

    fun addNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val habitId = shellState.value.detailHabitId
        viewModelScope.launch { repo.addNote(habitId, trimmed) }
    }

    fun addCustomCategory(name: String) = viewModelScope.launch { settings.addCustomCategory(name) }

    // ── auto-plan (AI) ──────────────────────────────────────────
    fun clearAutoPlanMessage() {
        autoPlanMessageState.value = null
    }

    fun autoPlan() {
        val day = shellState.value.planDay
        if (day != today || autoPlanningState.value) return
        val key = apiKey.value
        if (key.isBlank()) {
            autoPlanMessageState.value = AutoPlanMessage(
                "Auto-plan needs an AI key — add your ${SettingsRepository.providerLabel(provider.value)} key in Settings.",
                failed = true,
            )
            return
        }
        autoPlanningState.value = true
        viewModelScope.launch {
            try {
                fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
                val fixedBlocks = repo.planForOnce(day).filter { it.source != "auto" }
                val calEvents = if (settings.calendarEnabled.first()) calendar.eventsFor(day) else emptyList()
                val busy = fixedBlocks.map { it.startMin to it.endMin } +
                    calEvents.map { it.startMin to it.endMin }
                val fixedDesc =
                    fixedBlocks.map { "${fmt(it.startMin)}-${fmt(it.endMin)} ${it.title}" } +
                        calEvents.map { "${fmt(it.startMin)}-${fmt(it.endMin)} ${it.title} (calendar)" }
                val taskDesc = tasks.value.filter { !it.done }.map { it.title }
                val habitDesc = habits.value.map {
                    "${it.habit.name} — ${it.habit.type}, ${it.streak}-day streak" +
                        if (it.doneToday) ", already done today" else ""
                }

                val prompt = AiProtocol.autoPlanPrompt(todayDate.toString(), fixedDesc, taskDesc, habitDesc)
                val raw = withContext(Dispatchers.IO) {
                    ai.complete(provider.value, key, model.value, prompt, listOf(false to "Plan my day."))
                }
                val parsed = AiProtocol.parseAutoPlan(raw)
                if (parsed == null) {
                    autoPlanMessageState.value =
                        AutoPlanMessage("Auto-plan returned something unexpected — try again.", failed = true)
                    return@launch
                }
                val (reply, blocks) = parsed
                val safe = AiProtocol.filterOverlaps(blocks, busy)
                repo.replaceAutoPlan(
                    day,
                    safe.map {
                        PlanBlock(
                            epochDay = day,
                            startMin = it.startMin,
                            endMin = (it.startMin + it.durationMin).coerceAtMost(24 * 60),
                            title = it.title,
                            kind = it.kind,
                            source = "auto",
                        )
                    },
                )
                autoPlanMessageState.value = AutoPlanMessage(reply, failed = false)
            } catch (e: Exception) {
                autoPlanMessageState.value =
                    AutoPlanMessage("Auto-plan failed: ${e.message?.take(100) ?: "network error"}", failed = true)
            } finally {
                autoPlanningState.value = false
            }
        }
    }

    // ── settings ────────────────────────────────────────────────
    fun setHeatmapWeeks(weeks: Int) = viewModelScope.launch { settings.setHeatmapWeeks(weeks) }
    fun setShowAiBar(show: Boolean) = viewModelScope.launch { settings.setShowAiBar(show) }
    fun setModel(model: String) = viewModelScope.launch { settings.setModel(model) }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setNotificationsEnabled(enabled)
        ReminderScheduler.reschedule(getApplication())
    }

    fun setCheckinEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setCheckinEnabled(enabled)
        ReminderScheduler.reschedule(getApplication())
    }

    fun setCheckinMin(min: Int) = viewModelScope.launch {
        settings.setCheckinMin(min)
        ReminderScheduler.reschedule(getApplication())
    }

    fun setCalendarEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setCalendarEnabled(enabled)
    }

    // ── backup & restore ────────────────────────────────────────

    fun clearBackupMessage() {
        backupMessageState.value = null
    }

    /** Stores the folder the user picked and holds on to write access across reboots. */
    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                backupMessageState.value = "Couldn't keep access to that folder — try another one."
                return@launch
            }
            settings.setBackupFolder(uri.toString())
            BackupScheduler.sync(getApplication(), force = true)
            backupMessageState.value = "Backup folder set to ${BackupManager.folderLabel(uri.toString())}."
        }
    }

    fun setBackupInterval(interval: String) = viewModelScope.launch {
        settings.setBackupInterval(interval)
        BackupScheduler.sync(getApplication(), force = true)
    }

    fun setBackupMin(min: Int) = viewModelScope.launch {
        settings.setBackupMin(min)
        BackupScheduler.sync(getApplication(), force = true)
    }

    fun setBackupKeep(keep: Int) = viewModelScope.launch { settings.setBackupKeep(keep) }

    fun backupNow() {
        if (backupBusyState.value) return
        backupBusyState.value = true
        viewModelScope.launch {
            try {
                val result = BackupManager.backupNow(getApplication())
                backupMessageState.value =
                    "Backed up ${result.rows} entries to ${result.fileName}."
            } catch (e: Exception) {
                backupMessageState.value = e.readableMessage()
            } finally {
                backupBusyState.value = false
            }
        }
    }

    /** One-off "save a copy" to a location picked in the system dialog. */
    fun exportTo(uri: Uri) {
        if (backupBusyState.value) return
        backupBusyState.value = true
        viewModelScope.launch {
            try {
                val result = BackupManager.exportTo(getApplication(), uri)
                backupMessageState.value = "Saved ${result.rows} entries to ${result.fileName}."
            } catch (e: Exception) {
                backupMessageState.value = e.readableMessage()
            } finally {
                backupBusyState.value = false
            }
        }
    }

    /** Reads a picked file and parks it for confirmation — nothing is replaced yet. */
    fun previewRestore(uri: Uri) {
        if (backupBusyState.value) return
        backupBusyState.value = true
        viewModelScope.launch {
            try {
                val snapshot = BackupManager.read(getApplication(), uri)
                pendingRestoreState.value = PendingRestore(snapshot, BackupFormat.problems(snapshot))
            } catch (e: Exception) {
                backupMessageState.value = e.readableMessage()
            } finally {
                backupBusyState.value = false
            }
        }
    }

    fun cancelRestore() {
        pendingRestoreState.value = null
    }

    fun confirmRestore() {
        val pending = pendingRestoreState.value ?: return
        pendingRestoreState.value = null
        backupBusyState.value = true
        viewModelScope.launch {
            try {
                BackupManager.restore(getApplication(), pending.snapshot)
                repo.materializeHabitBlocks(today)
                shellState.update { it.copy(tab = Tab.Today, detailHabitId = 1L) }
                chatState.value = emptyList()
                backupMessageState.value = "Restored ${pending.snapshot.rowCount} entries."
            } catch (e: Exception) {
                backupMessageState.value = "Restore failed: ${e.readableMessage()}"
            } finally {
                backupBusyState.value = false
            }
        }
    }

    /** Builds a backup file in cache and hands it to the system share sheet. */
    fun shareBackup(onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            try {
                onIntent(BackupManager.shareIntent(getApplication()))
            } catch (e: Exception) {
                backupMessageState.value = e.readableMessage()
            }
        }
    }

    fun refreshCalendar() {
        calendarRefresh.update { it + 1 }
    }

    fun setProvider(newProvider: String) {
        viewModelScope.launch {
            settings.setProvider(newProvider)
            refreshModelsFor(newProvider)
        }
    }

    fun setApiKey(key: String) {
        val p = provider.value
        settings.setApiKey(p, key)
        refreshModelsFor(p)
    }

    /** Fetch the model list for the current provider's saved key. */
    fun refreshModels() = refreshModelsFor(provider.value)

    private fun refreshModelsFor(p: String) {
        val key = settings.apiKeys.value[p].orEmpty()
        if (key.isBlank()) {
            modelsState.value = ModelsUi()
            return
        }
        modelsState.value = ModelsUi(loading = true)
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { ai.listModels(p, key) }
                modelsState.value = ModelsUi(models = list)
                if (list.isNotEmpty() && model.value !in list) {
                    val preferred = SettingsRepository.defaultModel(p)
                    settings.setModel(if (preferred in list) preferred else list.first())
                }
            } catch (e: Exception) {
                modelsState.value = ModelsUi(error = e.message?.take(120) ?: "Couldn't fetch models")
            }
        }
    }

    // ── Ask Tend ────────────────────────────────────────────────
    fun openAi() {
        if (chatState.value.isEmpty()) {
            val done = habits.value.count { it.doneToday }
            val total = habits.value.size
            val open = tasks.value.count { !it.done }
            val star = habits.value.maxByOrNull { it.streak }
            val starText = star?.let { " — ${it.habit.name} is at a ${it.streak}-day streak" } ?: ""
            chatState.value = listOf(
                ChatMsg(true, "Good morning. ${total - done} habits and $open open tasks left today$starText. What should I set up?")
            )
        }
        shellState.update { it.copy(aiOpen = true) }
    }

    fun closeAi() = shellState.update { it.copy(aiOpen = false) }

    private fun push(msg: ChatMsg) = chatState.update { it + msg }

    fun sendAi(text: String) {
        val input = text.trim()
        if (input.isEmpty() || shellState.value.aiThinking) return
        push(ChatMsg(false, input))
        shellState.update { it.copy(aiThinking = true) }
        viewModelScope.launch {
            try {
                val key = apiKey.value
                // With a key: use the real model, and surface failures instead of
                // silently degrading to the offline rules (which can misfile things).
                val result = if (key.isNotBlank()) {
                    runRemote(provider.value, key) ?: return@launch
                } else {
                    delay(550)
                    AiProtocol.simulate(input)
                }
                result.actions.forEach { apply(it) }
                push(ChatMsg(true, result.reply))
            } finally {
                shellState.update { it.copy(aiThinking = false) }
            }
        }
    }

    private suspend fun runRemote(aiProvider: String, key: String): com.tend.app.ai.AiResult? =
        withContext(Dispatchers.IO) {
            try {
                val history = chatState.value.map { it.fromAi to it.text }
                // If the model list is loaded and the saved model isn't in it (retired
                // or filtered out as unstable), fall back to a working one for this call
                // and persist the correction.
                val loaded = modelsState.value.models
                val effectiveModel = when {
                    loaded.isEmpty() || model.value in loaded -> model.value
                    else -> (loaded.firstOrNull { it == SettingsRepository.defaultModel(aiProvider) }
                        ?: loaded.first()).also { settings.setModel(it) }
                }
                val raw = ai.complete(aiProvider, key, effectiveModel, AiProtocol.systemPrompt(stateSummary()), history)
                AiProtocol.parse(raw) ?: com.tend.app.ai.AiResult(raw.take(500), emptyList())
            } catch (e: Exception) {
                val label = SettingsRepository.providerLabel(aiProvider)
                push(
                    ChatMsg(
                        true,
                        "$label request failed: ${e.message?.take(120) ?: "network error"}. " +
                            "Check your key and model in Settings, then try again."
                    )
                )
                null
            }
        }

    private suspend fun apply(action: AiAction) {
        when (action) {
            is AiAction.AddTask -> {
                repo.addTask(action.title, action.group)
                ReminderScheduler.reschedule(getApplication())
            }
            is AiAction.AddPlanBlock ->
                repo.addPlanBlock(today, action.startMin, action.startMin + action.durationMin, action.title, action.kind)
            is AiAction.AddHabit -> {
                repo.addHabit(action.name, action.category, action.goal, createdDay = today)
                TendWidgets.refresh(getApplication())
            }
        }
    }

    private fun stateSummary(): String {
        val habitLines = habits.value.joinToString("\n") {
            "- ${it.habit.name} (${it.habit.category}): ${it.streak}-day streak, ${if (it.doneToday) "done" else "not done"} today"
        }
        val openTasks = tasks.value.filter { !it.done }.joinToString(", ") { it.title }.ifEmpty { "none" }
        val planLines = plan.value.joinToString("; ") { "${it.title} at ${it.startMin / 60}:${"%02d".format(it.startMin % 60)}" }
        return "Habits:\n$habitLines\nOpen tasks: $openTasks\nToday's plan: $planLines"
    }

    // ── suggestion chips (canned offline flows) ─────────────────
    fun chipPlanMorning() {
        if (shellState.value.aiThinking) return
        push(ChatMsg(false, "Plan my morning"))
        shellState.update { it.copy(aiThinking = true) }
        viewModelScope.launch {
            try {
                delay(550)
                val title = "Inbox zero + admin"
                val added = if (!repo.hasPlanBlock(today, title)) {
                    repo.addPlanBlock(today, 11 * 60, 11 * 60 + 45, title, kind = "focus")
                    true
                } else {
                    false
                }
                push(
                    ChatMsg(
                        true,
                        if (added) "Done — I blocked \"$title\" at 11:00 for 45 minutes. Check the Plan tab."
                        else "\"$title\" is already on today's plan. Anything else to slot in?"
                    )
                )
            } finally {
                shellState.update { it.copy(aiThinking = false) }
            }
        }
    }

    fun chipAddTask() {
        if (shellState.value.aiThinking) return
        push(ChatMsg(false, "Add a task"))
        viewModelScope.launch {
            delay(450)
            push(ChatMsg(true, "Sure — type it below. Try \"Add buy groceries at 5pm\" and I'll file it under the right group."))
        }
    }

    fun chipWeekSummary() {
        if (shellState.value.aiThinking) return
        push(ChatMsg(false, "How's my week?"))
        viewModelScope.launch {
            delay(550)
            val habitList = habits.value
            val total = habitList.size
            val weekDays = (today - 6)..today
            val possible = total * 7
            val done = habitList.sumOf { h -> weekDays.count { it in h.doneDays } }
            val pct = if (possible > 0) done * 100 / possible else 0
            val star = habitList.maxByOrNull { it.streak }
            val doneToday = habitList.count { it.doneToday }
            val openTasks = tasks.value.count { !it.done }
            push(
                ChatMsg(
                    true,
                    "Solid week: $done of $possible check-ins ($pct%). " +
                        (star?.let { "${it.habit.name} is on a ${it.streak}-day streak — personal best is ${it.best}. " } ?: "") +
                        "Today you're at $doneToday of $total habits with $openTasks tasks left."
                )
            )
        }
    }

    private companion object {
        /** Streak lengths worth interrupting the screen for. */
        val STREAK_MILESTONES = setOf(3, 7, 14, 30, 50, 100, 150, 200, 365)

        /** How long to wait for a check-off to surface through Room before giving up. */
        const val WRITE_SETTLE_MS = 1_500L
    }
}
