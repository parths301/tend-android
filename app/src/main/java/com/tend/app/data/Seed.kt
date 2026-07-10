package com.tend.app.data

import com.tend.app.data.db.AppDatabase
import com.tend.app.data.db.Habit
import com.tend.app.data.db.HabitLog
import com.tend.app.data.db.NoteEntry
import com.tend.app.data.db.PlanBlock
import com.tend.app.data.db.TaskItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Seeds the database with the demo state from the design prototype so the app
 * opens looking exactly like the mockup: four habits with realistic history,
 * a planned day, grouped tasks, and habit notes.
 */
object Seed {
    const val STRETCH_ID = 1L
    const val DEEP_WORK_ID = 2L
    const val READ_ID = 3L
    const val NO_SUGAR_ID = 4L

    // Same LCG the prototype used, so history "texture" matches.
    private class Lcg(seed: Long) {
        private var n = seed
        fun next(): Double {
            n = (n * 1664525 + 1013904223) % 4294967296L
            return n.toDouble() / 4294967296.0
        }
    }

    suspend fun seedIfEmpty(db: AppDatabase, today: Long) {
        if (db.habitDao().count() > 0) return

        db.habitDao().insertHabits(
            listOf(
                Habit(STRETCH_ID, "Morning Stretch", "Fitness", 0xFFD96F4E, "MS", "check", "Daily · 8:00 AM", 0),
                Habit(DEEP_WORK_ID, "Deep Work", "Work", 0xFF7D74C9, "DW", "time", "2h · weekdays", 1),
                Habit(READ_ID, "Read 20 pages", "Mind", 0xFF2F9C82, "RE", "check", "Daily · evening", 2),
                Habit(NO_SUGAR_ID, "No sugar", "Health", 0xFFC9931F, "NS", "avoid", "Resist · daily", 3),
            )
        )

        val logs = mutableListOf<HabitLog>()
        // (habitId, rngSeed, density, currentStreak, doneToday)
        logs += history(STRETCH_ID, 7, 0.74, streak = 12, doneToday = false, today = today)
        logs += history(DEEP_WORK_ID, 21, 0.60, streak = 5, doneToday = false, today = today, timed = true)
        logs += history(READ_ID, 33, 0.90, streak = 34, doneToday = true, today = today)
        logs += history(NO_SUGAR_ID, 45, 0.71, streak = 4, doneToday = false, today = today)
        db.habitDao().insertLogs(logs)

        db.taskDao().insertAll(
            listOf(
                TaskItem(title = "Call mom", groupName = "PERSONAL", sortOrder = 0),
                TaskItem(title = "Water the plants", groupName = "PERSONAL", sortOrder = 1),
                TaskItem(title = "Prepare client presentation", groupName = "WORK", sortOrder = 2),
                TaskItem(title = "Review PR feedback", groupName = "WORK", done = true, sortOrder = 3),
                TaskItem(title = "Book dentist appointment", groupName = "HEALTH", sortOrder = 4),
            )
        )

        db.planDao().insertAll(
            listOf(
                PlanBlock(epochDay = today, startMin = 450, endMin = 465, title = "Wake up + water", kind = "habit", done = true),
                PlanBlock(epochDay = today, startMin = 480, endMin = 500, title = "Morning Stretch", kind = "habit", done = true),
                PlanBlock(epochDay = today, startMin = 510, endMin = 600, title = "Deep Work — thesis draft", kind = "focus"),
                PlanBlock(epochDay = today, startMin = 615, endMin = 640, title = "Team standup", kind = "event"),
                PlanBlock(epochDay = today, startMin = 750, endMin = 795, title = "Lunch + walk", kind = "habit"),
                PlanBlock(epochDay = today, startMin = 1080, endMin = 1140, title = "Gym session", kind = "habit"),
                PlanBlock(epochDay = today, startMin = 1290, endMin = 1320, title = "Read 20 pages", kind = "habit"),
            )
        )

        val zone = ZoneId.systemDefault()
        fun at(daysAgo: Long, hour: Int, minute: Int): Long =
            LocalDateTime.of(LocalDate.ofEpochDay(today - daysAgo), java.time.LocalTime.of(hour, minute))
                .atZone(zone).toInstant().toEpochMilli()

        db.noteDao().insertAll(
            listOf(
                NoteEntry(habitId = STRETCH_ID, timestamp = at(2, 8, 24), text = "Hips feel way looser this week. Adding 2 min of ankle work."),
                NoteEntry(habitId = STRETCH_ID, timestamp = at(5, 8, 31), text = "Skipped the neck rolls — slept badly, neck sore."),
                NoteEntry(habitId = DEEP_WORK_ID, timestamp = at(2, 10, 2), text = "Finished the results section draft."),
                NoteEntry(habitId = DEEP_WORK_ID, timestamp = at(3, 9, 58), text = "90 minutes, zero distractions. New record."),
                NoteEntry(habitId = READ_ID, timestamp = at(1, 7, 40), text = "Started \"The Overstory\" — chapter 3 tonight."),
                NoteEntry(habitId = NO_SUGAR_ID, timestamp = at(4, 15, 40), text = "Craving hit after lunch. Tea helped."),
            )
        )
    }

    private fun history(
        habitId: Long,
        rngSeed: Long,
        density: Double,
        streak: Int,
        doneToday: Boolean,
        today: Long,
        timed: Boolean = false,
    ): List<HabitLog> {
        val rng = Lcg(rngSeed)
        val out = mutableListOf<HabitLog>()
        // The current streak runs up to yesterday (or today when doneToday).
        val streakEnd = if (doneToday) today else today - 1
        val streakStart = streakEnd - streak + 1
        for (day in (today - 139)..today) {
            val roll = rng.next()
            val done = when {
                day == today -> doneToday
                day in streakStart..streakEnd -> true
                day == streakStart - 1 -> false // pin the streak length exactly
                else -> roll < density
            }
            if (day == today && !doneToday) continue // no log yet today
            val minutes = if (timed && done) 60 + ((roll * 6).toInt() * 15) else 0
            out += HabitLog(habitId = habitId, epochDay = day, done = done, minutes = minutes)
        }
        return out
    }
}
