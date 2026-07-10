package com.tend.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tend.app.ai.AiAction
import com.tend.app.ai.AiClient
import com.tend.app.ai.AiProtocol
import com.tend.app.data.SettingsRepository
import com.tend.app.data.TendRepository
import com.tend.app.data.db.AppDatabase
import com.tend.app.data.db.Habit
import com.tend.app.data.db.HabitLog
import com.tend.app.data.db.NoteEntry
import com.tend.app.data.db.PlanBlock
import com.tend.app.data.db.TaskItem
import com.tend.app.domain.Streaks
import com.tend.app.widget.TendWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

data class Shell(
    val tab: Tab = Tab.Today,
    val view: HabitView = HabitView.Grid,
    val filter: String = "All",
    val aiOpen: Boolean = false,
    val aiThinking: Boolean = false,
    val detailHabitId: Long = 1L,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TendRepository(AppDatabase.get(app))
    val settings = SettingsRepository(app)
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
                    rate30 = Streaks.rate(doneDays, today),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tasks: StateFlow<List<TaskItem>> =
        repo.tasks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val plan: StateFlow<List<PlanBlock>> =
        repo.planFor(today).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /** API key for the currently selected provider. */
    val apiKey: StateFlow<String> =
        combine(settings.provider, settings.apiKeys) { p, keys -> keys[p].orEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val modelsState = MutableStateFlow(ModelsUi())
    val models: StateFlow<ModelsUi> = modelsState.asStateFlow()

    init {
        // Early builds seeded demo data; clear it once so the app runs on real data only.
        viewModelScope.launch {
            repo.purgeLegacyDemoData()
            TendWidgets.refresh(getApplication())
        }
    }

    // ── navigation ──────────────────────────────────────────────
    fun selectTab(tab: Tab) = shellState.update { it.copy(tab = tab) }
    fun openDetail(habitId: Long) = shellState.update { it.copy(tab = Tab.Detail, detailHabitId = habitId) }
    fun setView(view: HabitView) = shellState.update { it.copy(view = view) }
    fun setFilter(filter: String) = shellState.update { it.copy(filter = filter) }

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
        val habit = habits.value.firstOrNull { it.habit.id == habitId }?.habit ?: return
        viewModelScope.launch {
            repo.toggleHabitToday(habit, today)
            TendWidgets.refresh(getApplication())
        }
    }

    fun toggleTask(task: TaskItem) = viewModelScope.launch { repo.toggleTask(task) }
    fun togglePlan(block: PlanBlock) = viewModelScope.launch { repo.togglePlan(block) }

    fun addHabit(name: String, category: String, type: String, goal: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.addHabit(trimmed, category, goal.trim().ifEmpty { "Daily" }, type)
            TendWidgets.refresh(getApplication())
        }
    }

    fun deleteHabit(habitId: Long) {
        viewModelScope.launch {
            repo.deleteHabit(habitId)
            shellState.update { it.copy(tab = Tab.Today) }
            TendWidgets.refresh(getApplication())
        }
    }

    fun addTask(title: String, group: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.addTask(trimmed, group) }
    }

    fun deleteTask(task: TaskItem) = viewModelScope.launch { repo.deleteTask(task) }

    fun addPlanBlock(title: String, startMin: Int, durationMin: Int, kind: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.addPlanBlock(today, startMin, startMin + durationMin, trimmed, kind) }
    }

    fun deletePlanBlock(block: PlanBlock) = viewModelScope.launch { repo.deletePlanBlock(block) }

    fun addNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val habitId = shellState.value.detailHabitId
        viewModelScope.launch { repo.addNote(habitId, trimmed) }
    }

    // ── settings ────────────────────────────────────────────────
    fun setHeatmapWeeks(weeks: Int) = viewModelScope.launch { settings.setHeatmapWeeks(weeks) }
    fun setShowAiBar(show: Boolean) = viewModelScope.launch { settings.setShowAiBar(show) }
    fun setModel(model: String) = viewModelScope.launch { settings.setModel(model) }

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
                val result = if (key.isNotBlank()) {
                    runRemote(provider.value, key) ?: run {
                        delay(400)
                        AiProtocol.simulate(input)
                    }
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
                val raw = ai.complete(aiProvider, key, model.value, AiProtocol.systemPrompt(stateSummary()), history)
                AiProtocol.parse(raw) ?: com.tend.app.ai.AiResult(raw.take(500), emptyList())
            } catch (e: Exception) {
                val label = SettingsRepository.providerLabel(aiProvider)
                push(ChatMsg(true, "Couldn't reach $label (${e.message?.take(80) ?: "network error"}) — handling it locally instead."))
                null
            }
        }

    private suspend fun apply(action: AiAction) {
        when (action) {
            is AiAction.AddTask -> repo.addTask(action.title, action.group)
            is AiAction.AddPlanBlock ->
                repo.addPlanBlock(today, action.startMin, action.startMin + action.durationMin, action.title)
            is AiAction.AddHabit -> {
                repo.addHabit(action.name, action.category, action.goal)
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

    // ── suggestion chips (mirror the prototype's canned flows) ──
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
}
