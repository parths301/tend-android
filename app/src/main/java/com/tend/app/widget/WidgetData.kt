package com.tend.app.widget

import android.content.Context
import com.tend.app.data.db.AppDatabase
import com.tend.app.domain.Streaks
import java.time.LocalDate

/** Snapshot of one habit for rendering inside a home-screen widget. */
data class WidgetHabit(
    val id: Long,
    val name: String,
    val colorHex: Long,
    val glyph: String,
    val streak: Int,
    val doneToday: Boolean,
    /** Mon..Sun of the current week; true = checked. */
    val week: List<Boolean>,
)

object WidgetData {
    suspend fun load(context: Context): List<WidgetHabit> {
        val db = AppDatabase.get(context)
        val today = LocalDate.now().toEpochDay()
        val monday = today - (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
        val logsByHabit = db.habitDao().logsOnce().groupBy { it.habitId }
        return db.habitDao().habitsOnce().map { habit ->
            val doneDays = logsByHabit[habit.id].orEmpty().filter { it.done }.map { it.epochDay }.toSet()
            WidgetHabit(
                id = habit.id,
                name = habit.name,
                colorHex = habit.colorHex,
                glyph = habit.glyph,
                streak = Streaks.current(doneDays, today),
                doneToday = today in doneDays,
                week = (0..6).map { offset ->
                    val day = monday + offset
                    day <= today && day in doneDays
                },
            )
        }
    }
}
