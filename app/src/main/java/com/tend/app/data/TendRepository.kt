package com.tend.app.data

import com.tend.app.data.db.AppDatabase
import com.tend.app.data.db.Habit
import com.tend.app.data.db.HabitLog
import com.tend.app.data.db.NoteEntry
import com.tend.app.data.db.PlanBlock
import com.tend.app.data.db.TaskItem
import kotlinx.coroutines.flow.Flow

class TendRepository(private val db: AppDatabase) {

    fun habits(): Flow<List<Habit>> = db.habitDao().habits()
    fun logs(): Flow<List<HabitLog>> = db.habitDao().logs()
    fun tasks(): Flow<List<TaskItem>> = db.taskDao().tasks()
    fun planFor(day: Long): Flow<List<PlanBlock>> = db.planDao().forDay(day)
    fun notesFor(habitId: Long): Flow<List<NoteEntry>> = db.noteDao().forHabit(habitId)

    /**
     * Early builds shipped with seeded demo data. If this database still
     * carries that demo state, wipe it once so the app starts clean.
     */
    suspend fun purgeLegacyDemoData() {
        val legacy = db.habitDao().habitById(1L)?.name == "Morning Stretch"
        if (!legacy) return
        db.noteDao().clearNotes()
        db.planDao().clearPlans()
        db.taskDao().clearTasks()
        db.habitDao().clearLogs()
        db.habitDao().clearHabits()
    }

    /**
     * Check habits toggle on/off. Time habits accumulate 30-minute chunks and
     * flip to done at 2h; tapping a done time habit resets it.
     */
    suspend fun toggleHabitToday(habit: Habit, today: Long) {
        val existing = db.habitDao().logFor(habit.id, today)
        val log = if (habit.type == "time") {
            if (existing?.done == true) {
                existing.copy(done = false, minutes = 0)
            } else {
                val minutes = (existing?.minutes ?: 0) + 30
                HabitLog(
                    id = existing?.id ?: 0,
                    habitId = habit.id,
                    epochDay = today,
                    done = minutes >= 120,
                    minutes = minutes,
                )
            }
        } else {
            HabitLog(
                id = existing?.id ?: 0,
                habitId = habit.id,
                epochDay = today,
                done = !(existing?.done ?: false),
            )
        }
        db.habitDao().upsertLog(log)
    }

    suspend fun toggleTask(task: TaskItem) = db.taskDao().update(task.copy(done = !task.done))

    suspend fun togglePlan(block: PlanBlock) = db.planDao().update(block.copy(done = !block.done))

    suspend fun deleteTask(task: TaskItem) = db.taskDao().delete(task)

    suspend fun deletePlanBlock(block: PlanBlock) = db.planDao().delete(block)

    suspend fun deleteHabit(habitId: Long) {
        db.noteDao().deleteFor(habitId)
        db.habitDao().deleteLogsFor(habitId)
        db.habitDao().deleteHabit(habitId)
    }

    suspend fun addTask(title: String, group: String = "PERSONAL") =
        db.taskDao().insert(TaskItem(title = title, groupName = group, sortOrder = 999))

    suspend fun addPlanBlock(day: Long, startMin: Int, endMin: Int, title: String, kind: String = "event") =
        db.planDao().insert(PlanBlock(epochDay = day, startMin = startMin, endMin = endMin, title = title, kind = kind))

    suspend fun hasPlanBlock(day: Long, title: String): Boolean =
        db.planDao().countByTitle(day, title) > 0

    suspend fun addNote(habitId: Long, text: String) =
        db.noteDao().insert(NoteEntry(habitId = habitId, timestamp = System.currentTimeMillis(), text = text))

    suspend fun addHabit(name: String, category: String, goal: String, type: String = "check") {
        val palette = listOf(0xFFD96F4E, 0xFF7D74C9, 0xFF2F9C82, 0xFFC9931F)
        val glyph = name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("").ifEmpty { "HB" }
        db.habitDao().insertHabit(
            Habit(
                name = name,
                category = category,
                colorHex = palette[(name.hashCode() and 0x7FFFFFFF) % palette.size],
                glyph = glyph.take(2),
                type = type,
                goal = goal,
                sortOrder = 99,
            )
        )
    }
}
