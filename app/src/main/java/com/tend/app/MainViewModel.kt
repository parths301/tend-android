package com.tend.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tend.app.ai.AiAction
import com.tend.app.ai.AiClient
import com.tend.app.ai.AiProtocol
import com.tend.app.ai.ModelOption
import com.tend.app.ai.OpenRouterCatalog
import com.tend.app.data.CalEvent
import com.tend.app.data.CalendarRepository
import com.tend.app.data.ChatRepository
import com.tend.app.data.SettingsRepository
import com.tend.app.data.TendRepository
import com.tend.app.data.backup.BackupFormat
import com.tend.app.data.backup.BackupManager
import com.tend.app.data.backup.BackupScheduler
import com.tend.app.data.backup.BackupSnapshot
import com.tend.app.data.backup.readableMessage
import com.tend.app.data.vault.MemoryItem
import com.tend.app.data.vault.MemoryRepository
import com.tend.app.data.vault.UnlockResult
import com.tend.app.data.vault.VaultSession
import com.tend.app.data.vault.VaultState
import com.tend.app.data.db.AppDatabase
import com.tend.app.data.db.ChatMessage
import com.tend.app.data.db.ChatThread
import com.tend.app.data.db.Habit
import com.tend.app.data.db.HabitLog
import com.tend.app.data.db.MessageLink
import com.tend.app.data.db.NoteEntry
import com.tend.app.data.db.PlanBlock
import com.tend.app.data.db.TaskItem
import com.tend.app.domain.Streaks
import com.tend.app.domain.chat.ActionApplier
import com.tend.app.domain.chat.AiExecutionRouter
import com.tend.app.domain.chat.ChatMode
import com.tend.app.domain.chat.CloudAssistantEngine
import com.tend.app.domain.chat.CloudCredentials
import com.tend.app.domain.chat.EntityRef
import com.tend.app.domain.chat.LocalAssistantEngine
import com.tend.app.domain.chat.MemoryCommand
import com.tend.app.domain.chat.ResponseSource
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

enum class Tab { Today, Plan, Tasks, Stats, Detail, Settings, Memory }
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

/**
 * A chat message joined with what it created.
 *
 * The links come from `message_links`, not from parsing the reply text, so a
 * chip always knows exactly which row it points at.
 */
data class ChatMsgUi(
    val message: ChatMessage,
    val links: List<EntityRef> = emptyList(),
) {
    val fromAi: Boolean get() = message.fromAi
    val text: String get() = message.text
    val source: ResponseSource get() = ResponseSource.from(message.source)
    val inContext: Boolean get() = message.inContext
}

/** Models available to the saved key, fetched from the provider. */
/**
 * The model picker's state.
 *
 * [loading] is only for a genuinely empty picker; a refresh that has something
 * to show sets [refreshing] instead and leaves [models] in place, so reopening
 * Settings never blanks the list you were looking at.
 */
data class ModelsUi(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val models: List<ModelOption> = emptyList(),
    val error: String? = null,
) {
    val ids: List<String> get() = models.map { it.id }
}

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

/**
 * A line of feedback from a backup action. Like [AutoPlanMessage], it carries
 * its own outcome: the persisted [BackupStatus] only covers `backupNow`, so an
 * export or restore failure would otherwise be indistinguishable from success.
 */
data class BackupMessage(val text: String, val failed: Boolean)

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

/** How much of the screen the chat is using. Same state, same composables. */
enum class ChatSize { Sheet, FullScreen }

data class Shell(
    val tab: Tab = Tab.Today,
    val view: HabitView = HabitView.Grid,
    val filter: String = "All",
    val aiOpen: Boolean = false,
    val aiThinking: Boolean = false,
    val chatSize: ChatSize = ChatSize.Sheet,
    val detailHabitId: Long = 1L,
    val planDay: Long = LocalDate.now().toEpochDay(),
    /**
     * An item a chat chip asked to open. The destination screen consumes this
     * and pops its own editor, so a link lands on the real edit surface rather
     * than just the right tab — and no second detail screen has to exist.
     */
    val focusRef: EntityRef? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TendRepository(AppDatabase.get(app))
    private val chatRepo = ChatRepository(AppDatabase.get(app))

    // The vault is reachable from the ViewModel and from nowhere near the chat
    // pipeline — see MemoryCommand for why that separation is structural.
    val vaultSession = VaultSession(app)
    private val memoryRepo = MemoryRepository(app, AppDatabase.get(app), vaultSession)

    val settings = SettingsRepository(app)
    private val calendar = CalendarRepository(app)
    private val ai = AiClient()

    /**
     * The one dispatcher for chat turns.
     *
     * Both engines and the action applier are wired once, here, so nothing
     * downstream can accidentally take a different route: `sendAi` has no branch
     * on mode at all.
     */
    private val router = AiExecutionRouter(
        cloud = CloudAssistantEngine(ai) { resolveCredentials() },
        local = LocalAssistantEngine(),
        applier = ActionApplier { applyAction(it) },
    )

    val todayDate: LocalDate = LocalDate.now()
    val today: Long = todayDate.toEpochDay()

    private val shellState = MutableStateFlow(Shell())
    val shell: StateFlow<Shell> = shellState.asStateFlow()

    // ── chat ────────────────────────────────────────────────────
    // The active thread is an id, and everything else is derived from Room. The
    // list is never held in memory as the source of truth, which is what made
    // the old chat vanish on process death.

    private val activeThreadState = MutableStateFlow(0L)
    val activeThread: StateFlow<Long> = activeThreadState.asStateFlow()

    val threads: StateFlow<List<ChatThread>> =
        chatRepo.threads().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val messagesFlow: StateFlow<List<ChatMessage>> =
        activeThreadState
            .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else chatRepo.messages(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val linksFlow: StateFlow<List<MessageLink>> =
        activeThreadState
            .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else chatRepo.links(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val chat: StateFlow<List<ChatMsgUi>> =
        combine(messagesFlow, linksFlow) { messages, links ->
            val byMessage = links.groupBy { it.messageId }
            messages.map { message ->
                ChatMsgUi(
                    message = message,
                    links = byMessage[message.id].orEmpty().mapNotNull { link ->
                        EntityRef.Type.from(link.entityType)?.let {
                            EntityRef(it, link.entityId, link.label)
                        }
                    },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Composer text, held here rather than in the composable because
     * `key(shell.tab)` in TendApp destroys screen state on every tab change —
     * expanding the sheet to full screen would otherwise wipe what you typed.
     */
    private val draftState = MutableStateFlow("")
    val draft: StateFlow<String> = draftState.asStateFlow()

    /** Messages ticked for a bulk action. Transient by design; not persisted. */
    private val selectionState = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = selectionState.asStateFlow()


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
    private val backupMessageState = MutableStateFlow<BackupMessage?>(null)
    val backupMessage: StateFlow<BackupMessage?> = backupMessageState.asStateFlow()
    private val pendingRestoreState = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore: StateFlow<PendingRestore?> = pendingRestoreState.asStateFlow()

    /** API key for the currently selected provider. */
    val apiKey: StateFlow<String> =
        combine(settings.provider, settings.apiKeys) { p, keys -> keys[p].orEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val modelsState = MutableStateFlow(ModelsUi())
    val models: StateFlow<ModelsUi> = modelsState.asStateFlow()

    /** The mode the user picked. Declared after [apiKey] because it reads it. */
    val chatMode: StateFlow<ChatMode> =
        settings.chatMode.stateIn(viewModelScope, SharingStarted.Eagerly, ChatMode.Ai)

    /**
     * What the pipeline will actually do. AI mode without a key is offline mode,
     * and the UI shows this rather than the raw selection so the toggle can never
     * claim a state the pipeline isn't in.
     */
    val effectiveChatMode: StateFlow<ChatMode> =
        combine(chatMode, apiKey) { selected, key -> ChatMode.effective(selected, key.isNotBlank()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ChatMode.Local)

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
                backupMessageState.value =
                    BackupMessage("Couldn't keep access to that folder — try another one.", failed = true)
                return@launch
            }
            settings.setBackupFolder(uri.toString())
            BackupScheduler.sync(getApplication(), force = true)
            backupMessageState.value = BackupMessage(
                "Backup folder set to ${BackupManager.folderLabel(uri.toString())}.",
                failed = false,
            )
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
                    BackupMessage("Backed up ${result.rows} entries to ${result.fileName}.", failed = false)
            } catch (e: Exception) {
                backupMessageState.value = BackupMessage(e.readableMessage(), failed = true)
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
                backupMessageState.value =
                    BackupMessage("Saved ${result.rows} entries to ${result.fileName}.", failed = false)
            } catch (e: Exception) {
                backupMessageState.value = BackupMessage(e.readableMessage(), failed = true)
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
                backupMessageState.value = BackupMessage(e.readableMessage(), failed = true)
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
                // Re-resolve rather than wipe: a restore replaces the chat tables
                // too, and the old id may not exist in the restored data.
                activeThreadState.value = 0L
                draftState.value = ""
                selectionState.value = emptySet()
                backupMessageState.value =
                    BackupMessage("Restored ${pending.snapshot.rowCount} entries.", failed = false)
            } catch (e: Exception) {
                backupMessageState.value =
                    BackupMessage("Restore failed: ${e.readableMessage()}", failed = true)
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
                backupMessageState.value = BackupMessage(e.readableMessage(), failed = true)
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

    /** User-initiated refresh — always hits the network. */
    fun refreshModels() = refreshModelsFor(provider.value, force = true)

    /**
     * Called when Settings comes into view. Cheap: it serves the cache and only
     * goes to the network if the list is older than [MODELS_TTL_MS].
     */
    fun refreshModelsIfStale() = refreshModelsFor(provider.value, force = false)

    /**
     * Loads the model list for [p], showing whatever is already known first.
     *
     * OpenRouter's catalogue is public, so its picker populates before a key is
     * saved — the other two need one.
     */
    private fun refreshModelsFor(p: String, force: Boolean = true) {
        val key = settings.apiKeys.value[p].orEmpty()
        val needsKey = p != SettingsRepository.PROVIDER_OPENROUTER
        if (key.isBlank() && needsKey) {
            modelsState.value = ModelsUi()
            return
        }
        viewModelScope.launch {
            // Serve the last known list immediately so the picker is usable
            // while the network catches up.
            if (modelsState.value.models.isEmpty()) {
                val cached = settings.cachedModels(p)
                if (cached.isNotEmpty()) {
                    modelsState.value = ModelsUi(models = cached.map { restoreOption(p, it) })
                }
            }

            val age = System.currentTimeMillis() - settings.modelsFetchedAt(p)
            if (!force && modelsState.value.models.isNotEmpty() && age < MODELS_TTL_MS) return@launch

            val hasSomething = modelsState.value.models.isNotEmpty()
            modelsState.update { it.copy(loading = !hasSomething, refreshing = hasSomething, error = null) }
            try {
                val list = withContext(Dispatchers.IO) { ai.listModels(p, key) }
                modelsState.value = ModelsUi(models = list)
                settings.cacheModels(p, list.map { it.id }, System.currentTimeMillis())
                val ids = list.map { it.id }
                if (ids.isNotEmpty() && model.value !in ids) {
                    val preferred = SettingsRepository.defaultModel(p)
                    settings.setModel(if (preferred in ids) preferred else ids.first())
                }
            } catch (e: Exception) {
                // A failed refresh must not throw away a list that still works.
                // Offline, the cached models are exactly what the user needs.
                modelsState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message?.take(120) ?: "Couldn't fetch models",
                    )
                }
            }
        }
    }

    /**
     * Rebuilds a picker entry from a cached id. Price detail is lost until the
     * refresh lands — it isn't cached — but "recommended" is derivable from the
     * id, so the shortlist survives a cold offline start.
     */
    private fun restoreOption(provider: String, id: String): ModelOption =
        if (provider == SettingsRepository.PROVIDER_OPENROUTER) {
            ModelOption(id = id, recommended = OpenRouterCatalog.isRecommended(id))
        } else {
            ModelOption(id)
        }

    // ── Ask Tend ────────────────────────────────────────────────

    fun openAi() {
        viewModelScope.launch {
            val threadId = ensureThread()
            if (chatRepo.historyFor(threadId).isEmpty()) greet(threadId)
            shellState.update { it.copy(aiOpen = true) }
        }
    }

    fun closeAi() {
        persistDraft()
        shellState.update { it.copy(aiOpen = false, chatSize = ChatSize.Sheet) }
    }

    fun expandChat() = shellState.update { it.copy(chatSize = ChatSize.FullScreen) }

    fun collapseChat() = shellState.update { it.copy(chatSize = ChatSize.Sheet) }

    /** Resolves the thread to work in, creating one on first use. */
    private suspend fun ensureThread(): Long {
        activeThreadState.value.takeIf { it != 0L }?.let { return it }
        val id = chatRepo.mostRecentOrNew(chatMode.value)
        activeThreadState.value = id
        draftState.value = chatRepo.threadById(id)?.draft.orEmpty()
        return id
    }

    private suspend fun greet(threadId: Long) {
        val done = habits.value.count { it.doneToday }
        val total = habits.value.size
        val open = tasks.value.count { !it.done }
        val star = habits.value.maxByOrNull { it.streak }
        val starText = star?.let { " — ${it.habit.name} is at a ${it.streak}-day streak" } ?: ""
        chatRepo.append(
            threadId = threadId,
            fromAi = true,
            text = "Good morning. ${total - done} habits and $open open tasks left today$starText. What should I set up?",
            source = ResponseSource.System,
        )
    }

    fun sendAi(text: String) {
        val input = text.trim()
        if (input.isEmpty() || shellState.value.aiThinking) return
        shellState.update { it.copy(aiThinking = true) }
        viewModelScope.launch {
            try {
                val threadId = ensureThread()
                // History is read before the new message is stored, so the policy
                // never has to exclude the turn it belongs to.
                val history = chatRepo.historyFor(threadId)
                draftState.value = ""
                chatRepo.saveDraft(threadId, "")
                chatRepo.append(threadId, fromAi = false, text = input, source = ResponseSource.System)

                // Vault commands are answered here and return. Nothing is built,
                // no engine runs, and in AI mode nothing is sent — the text never
                // enters the pipeline at all.
                MemoryCommand.parse(input)?.let { command ->
                    chatRepo.append(
                        threadId = threadId,
                        fromAi = true,
                        text = runMemoryCommand(command),
                        source = ResponseSource.System,
                    )
                    return@launch
                }

                val request = AiExecutionRouter.buildRequest(
                    threadId = threadId,
                    selectedMode = chatMode.value,
                    hasKey = apiKey.value.isNotBlank(),
                    history = history,
                    userMessage = input,
                    stateSummary = stateSummary(),
                )
                chatRepo.setMode(threadId, request.mode)

                // Offline used to fake latency so the reply didn't appear
                // instantly; the rules are still instant, so keep that beat.
                if (request.mode == ChatMode.Local) delay(LOCAL_THINK_MS)

                val response = withContext(Dispatchers.IO) { router.run(request) }
                chatRepo.append(
                    threadId = threadId,
                    fromAi = true,
                    text = response.reply,
                    source = response.source,
                    modelId = response.modelId,
                    links = response.links,
                )
            } finally {
                shellState.update { it.copy(aiThinking = false) }
            }
        }
    }

    /**
     * Credentials for a cloud turn, including the healing step: if the saved
     * model is no longer in the fetched list (retired, or filtered out), swap in
     * a working one for this call and persist the correction.
     */
    private suspend fun resolveCredentials(): CloudCredentials? {
        val key = apiKey.value
        if (key.isBlank()) return null
        val aiProvider = provider.value
        val loaded = modelsState.value.ids
        val effectiveModel = when {
            loaded.isEmpty() || model.value in loaded -> model.value
            else -> (loaded.firstOrNull { it == SettingsRepository.defaultModel(aiProvider) }
                ?: loaded.first()).also { settings.setModel(it) }
        }
        return CloudCredentials(aiProvider, key, effectiveModel)
    }

    /**
     * Creates what the assistant asked for and reports back what it made.
     *
     * The row ids were always available — the DAOs return them — they were just
     * being dropped. Returning an [EntityRef] is the whole of what makes a chat
     * message's chips clickable, and it happens on one path, so offline mode
     * links entities exactly as cloud mode does.
     */
    private suspend fun applyAction(action: AiAction): EntityRef? = when (action) {
        is AiAction.AddTask -> {
            val id = repo.addTask(action.title, action.group)
            ReminderScheduler.reschedule(getApplication())
            EntityRef(EntityRef.Type.Task, id, action.title)
        }
        is AiAction.AddPlanBlock -> {
            val id = repo.addPlanBlock(
                today, action.startMin, action.startMin + action.durationMin, action.title, action.kind,
            )
            EntityRef(EntityRef.Type.Plan, id, action.title)
        }
        is AiAction.AddHabit -> {
            val id = repo.addHabit(action.name, action.category, action.goal, createdDay = today)
            TendWidgets.refresh(getApplication())
            EntityRef(EntityRef.Type.Habit, id, action.name)
        }
    }

    // ── memory vault ────────────────────────────────────────────

    val vaultState: StateFlow<VaultState> =
        vaultSession.state.stateIn(viewModelScope, SharingStarted.Eagerly, VaultState.NotSetUp)

    val memoryCount: StateFlow<Int> =
        memoryRepo.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val memoryResultsState = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memoryResults: StateFlow<List<MemoryItem>> = memoryResultsState.asStateFlow()

    private val recoveryCodeState = MutableStateFlow<String?>(null)

    /** Shown exactly once, at setup. Cleared as soon as the user dismisses it. */
    val recoveryCode: StateFlow<String?> = recoveryCodeState.asStateFlow()

    private val vaultErrorState = MutableStateFlow<String?>(null)
    val vaultError: StateFlow<String?> = vaultErrorState.asStateFlow()

    fun createVault(password: String) {
        if (password.length < MIN_PASSWORD) {
            vaultErrorState.value = "Use at least $MIN_PASSWORD characters."
            return
        }
        viewModelScope.launch {
            vaultErrorState.value = null
            recoveryCodeState.value = withContext(Dispatchers.Default) {
                vaultSession.setUp(password.toCharArray())
            }
            refreshMemory("")
        }
    }

    fun dismissRecoveryCode() {
        recoveryCodeState.value = null
    }

    fun unlockVault(password: String) {
        viewModelScope.launch {
            // PBKDF2 at 210k iterations is deliberately slow; keep it off the
            // main thread or the unlock button freezes the UI for a third of a
            // second on every attempt.
            val result = withContext(Dispatchers.Default) {
                vaultSession.unlock(password.toCharArray())
            }
            applyUnlock(result, "That password doesn't open this vault.")
        }
    }

    fun unlockVaultWithRecoveryCode(code: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                vaultSession.unlockWithRecoveryCode(code)
            }
            applyUnlock(result, "That recovery code doesn't match.")
        }
    }

    private suspend fun applyUnlock(result: UnlockResult, failureMessage: String) {
        when (result) {
            UnlockResult.Success -> {
                vaultErrorState.value = null
                refreshMemory("")
            }
            UnlockResult.WrongSecret -> vaultErrorState.value = failureMessage
            UnlockResult.NotSetUp -> vaultErrorState.value = "No vault has been created yet."
        }
    }

    fun changeVaultPassword(current: String, next: String) {
        if (next.length < MIN_PASSWORD) {
            vaultErrorState.value = "Use at least $MIN_PASSWORD characters."
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                vaultSession.changePassword(current.toCharArray(), next.toCharArray())
            }
            vaultErrorState.value = if (ok) null else "That current password isn't right."
        }
    }

    fun lockVault() {
        memoryResultsState.value = emptyList()
        vaultSession.lock()
    }

    fun destroyVault() {
        viewModelScope.launch {
            memoryRepo.destroyEverything()
            memoryResultsState.value = emptyList()
            vaultErrorState.value = null
        }
    }

    fun refreshMemory(query: String) {
        viewModelScope.launch {
            memoryResultsState.value = memoryRepo.search(query)
        }
    }

    fun addMemoryNote(title: String, body: String) {
        viewModelScope.launch {
            memoryRepo.addText(title.ifBlank { body.take(40) }, body)
            refreshMemory("")
        }
    }

    fun addMemoryFile(uri: Uri, displayName: String, mime: String, caption: String) {
        viewModelScope.launch {
            memoryRepo.addFile(uri, displayName, mime, caption)
            refreshMemory("")
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepo.delete(id)
            refreshMemory("")
        }
    }

    suspend fun memoryBlob(id: Long): ByteArray? = memoryRepo.readBlob(id)

    /**
     * Runs a vault command typed into the chat and returns the reply to show.
     *
     * The results are summarised rather than pasted into the conversation:
     * chat messages are stored unencrypted, so spilling vault contents into a
     * thread would quietly undo the encryption the user asked for.
     */
    private suspend fun runMemoryCommand(command: MemoryCommand): String {
        if (vaultSession.state.value != VaultState.Unlocked) {
            return if (vaultSession.isSetUp()) {
                "Memory is locked. Open it from Settings → Memory to unlock, then try again."
            } else {
                "There's no Memory vault yet. Create one in Settings → Memory — it's encrypted and " +
                    "kept out of everything the assistant sees."
            }
        }
        return when (command) {
            is MemoryCommand.Add -> {
                if (command.text.isBlank()) {
                    "Tell me what to remember, like \"add to memory: spare key is with Sam\"."
                } else {
                    memoryRepo.addText(command.text.take(60), command.text)
                    refreshMemory("")
                    "Saved to Memory, encrypted. It stays out of every AI request."
                }
            }

            is MemoryCommand.Search -> {
                val hits = memoryRepo.search(command.query)
                memoryResultsState.value = hits
                when {
                    hits.isEmpty() && command.query.isBlank() -> "Memory is empty."
                    hits.isEmpty() -> "Nothing in Memory matches \"${command.query}\"."
                    else -> "${hits.size} match${if (hits.size == 1) "" else "es"} in Memory: " +
                        hits.take(5).joinToString(", ") { it.title } +
                        (if (hits.size > 5) ", …" else "") +
                        ". Open Settings → Memory to view them."
                }
            }
        }
    }

    // ── chat management ─────────────────────────────────────────

    fun setChatMode(mode: ChatMode) {
        viewModelScope.launch { settings.setChatMode(mode) }
    }

    fun setDraft(text: String) {
        draftState.value = text
    }

    /** Drafts are per thread, so they survive switching away and back. */
    private fun persistDraft() {
        val threadId = activeThreadState.value
        if (threadId == 0L) return
        val text = draftState.value
        viewModelScope.launch { chatRepo.saveDraft(threadId, text) }
    }

    fun newChat() {
        persistDraft()
        viewModelScope.launch {
            selectionState.value = emptySet()
            draftState.value = ""
            val id = chatRepo.newThread(chatMode.value)
            activeThreadState.value = id
            greet(id)
        }
    }

    fun openThread(threadId: Long) {
        if (threadId == activeThreadState.value) return
        persistDraft()
        viewModelScope.launch {
            selectionState.value = emptySet()
            activeThreadState.value = threadId
            draftState.value = chatRepo.threadById(threadId)?.draft.orEmpty()
        }
    }

    /** Empties the current thread but keeps it — distinct from [deleteThread]. */
    fun clearChat() {
        val threadId = activeThreadState.value.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            selectionState.value = emptySet()
            chatRepo.clearMessages(threadId)
            greet(threadId)
        }
    }

    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            chatRepo.deleteThread(threadId)
            if (threadId == activeThreadState.value) {
                selectionState.value = emptySet()
                draftState.value = ""
                // Fall back to whatever is left rather than showing an empty shell.
                activeThreadState.value = 0L
                val id = ensureThread()
                if (chatRepo.historyFor(id).isEmpty()) greet(id)
            }
        }
    }

    fun renameThread(threadId: Long, title: String) {
        viewModelScope.launch { chatRepo.renameThread(threadId, title) }
    }

    fun setThreadPinned(threadId: Long, pinned: Boolean) {
        viewModelScope.launch { chatRepo.setPinned(threadId, pinned) }
    }

    // ── message actions ─────────────────────────────────────────

    fun toggleSelected(messageId: Long) = selectionState.update { current ->
        if (messageId in current) current - messageId else current + messageId
    }

    fun clearSelection() {
        selectionState.value = emptySet()
    }

    /**
     * Deletes messages only. Anything they created stays: a task still on the
     * user's list must not disappear because they tidied the conversation.
     */
    fun deleteSelectedMessages() {
        val ids = selectionState.value.toList()
        if (ids.isEmpty()) return
        selectionState.value = emptySet()
        viewModelScope.launch { chatRepo.deleteMessages(ids) }
    }

    fun deleteMessage(messageId: Long) {
        selectionState.update { it - messageId }
        viewModelScope.launch { chatRepo.deleteMessages(listOf(messageId)) }
    }

    /** Marks a message as context for offline mode. */
    fun toggleInContext(messageId: Long) {
        val current = messagesFlow.value.firstOrNull { it.id == messageId } ?: return
        viewModelScope.launch { chatRepo.setInContext(messageId, !current.inContext) }
    }

    fun clearChatContext() {
        val threadId = activeThreadState.value.takeIf { it != 0L } ?: return
        viewModelScope.launch { chatRepo.clearContext(threadId) }
    }

    /**
     * Opens whatever a chat chip points at.
     *
     * Returns false when the row is gone, so the caller can say so rather than
     * navigating to a blank screen.
     */
    suspend fun resolveLink(ref: EntityRef): Boolean = when (ref.type) {
        EntityRef.Type.Habit -> repo.habitById(ref.id)?.let {
            shellState.update { s ->
                s.copy(tab = Tab.Detail, detailHabitId = ref.id, aiOpen = false, focusRef = null)
            }
            true
        } ?: false

        EntityRef.Type.Task -> repo.taskById(ref.id)?.let {
            shellState.update { s -> s.copy(tab = Tab.Tasks, aiOpen = false, focusRef = ref) }
            true
        } ?: false

        EntityRef.Type.Plan -> repo.planBlockById(ref.id)?.let { block ->
            shellState.update { s ->
                s.copy(tab = Tab.Plan, planDay = block.epochDay, aiOpen = false, focusRef = ref)
            }
            true
        } ?: false
    }

    /** Called by a destination once it has opened the editor for [Shell.focusRef]. */
    fun consumeFocusRef() = shellState.update { it.copy(focusRef = null) }

    private fun stateSummary(): String {
        val habitLines = habits.value.joinToString("\n") {
            "- ${it.habit.name} (${it.habit.category}): ${it.streak}-day streak, ${if (it.doneToday) "done" else "not done"} today"
        }
        val openTasks = tasks.value.filter { !it.done }.joinToString(", ") { it.title }.ifEmpty { "none" }
        val planLines = plan.value.joinToString("; ") { "${it.title} at ${it.startMin / 60}:${"%02d".format(it.startMin % 60)}" }
        return "Habits:\n$habitLines\nOpen tasks: $openTasks\nToday's plan: $planLines"
    }

    // ── suggestion chips (canned offline flows) ─────────────────
    // These answer locally without consulting an engine, so they are stamped
    // System rather than Local: nothing inferred them from the user's words.

    private fun chipTurn(prompt: String, work: suspend (Long) -> String) {
        if (shellState.value.aiThinking) return
        shellState.update { it.copy(aiThinking = true) }
        viewModelScope.launch {
            try {
                val threadId = ensureThread()
                chatRepo.append(threadId, fromAi = false, text = prompt, source = ResponseSource.System)
                val reply = work(threadId)
                chatRepo.append(threadId, fromAi = true, text = reply, source = ResponseSource.System)
            } finally {
                shellState.update { it.copy(aiThinking = false) }
            }
        }
    }

    fun chipPlanMorning() = chipTurn("Plan my morning") {
        delay(LOCAL_THINK_MS)
        val title = "Inbox zero + admin"
        if (repo.hasPlanBlock(today, title)) {
            "\"$title\" is already on today's plan. Anything else to slot in?"
        } else {
            repo.addPlanBlock(today, 11 * 60, 11 * 60 + 45, title, kind = "focus")
            "Done — I blocked \"$title\" at 11:00 for 45 minutes. Check the Plan tab."
        }
    }

    fun chipAddTask() = chipTurn("Add a task") {
        delay(450)
        "Sure — type it below. Try \"Add buy groceries at 5pm\" and I'll file it under the right group."
    }

    fun chipWeekSummary() = chipTurn("How's my week?") {
        delay(LOCAL_THINK_MS)
        val habitList = habits.value
        val total = habitList.size
        val weekDays = (today - 6)..today
        val possible = total * 7
        val done = habitList.sumOf { h -> weekDays.count { it in h.doneDays } }
        val pct = if (possible > 0) done * 100 / possible else 0
        val star = habitList.maxByOrNull { it.streak }
        val doneToday = habitList.count { it.doneToday }
        val openTasks = tasks.value.count { !it.done }
        "Solid week: $done of $possible check-ins ($pct%). " +
            (star?.let { "${it.habit.name} is on a ${it.streak}-day streak — personal best is ${it.best}. " } ?: "") +
            "Today you're at $doneToday of $total habits with $openTasks tasks left."
    }

    private companion object {
        /** Streak lengths worth interrupting the screen for. */
        val STREAK_MILESTONES = setOf(3, 7, 14, 30, 50, 100, 150, 200, 365)

        /** How long a fetched model list stays fresh before a background refresh. */
        const val MODELS_TTL_MS = 5 * 60 * 1000L

        /** How long to wait for a check-off to surface through Room before giving up. */
        const val WRITE_SETTLE_MS = 1_500L

        /**
         * Offline replies are instant. A short pause keeps the thinking bubble
         * legible instead of the answer appearing before the question lands.
         */
        const val LOCAL_THINK_MS = 550L

        /** Short enough not to be a nuisance, long enough to be worth PBKDF2. */
        const val MIN_PASSWORD = 8
    }
}
