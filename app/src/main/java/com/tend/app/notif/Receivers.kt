package com.tend.app.notif

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tend.app.data.SettingsRepository
import com.tend.app.data.TendRepository
import com.tend.app.data.db.AppDatabase
import com.tend.app.domain.Streaks
import com.tend.app.widget.TendWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires for the single chained alarm (or a snooze) and posts whatever is due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val snoozeHabitId = intent.getLongExtra(ReminderScheduler.EXTRA_SNOOZE_HABIT_ID, -1L)
        async {
            if (snoozeHabitId >= 0) {
                fireSnoozed(context, snoozeHabitId)
            } else {
                fireDue(context)
                ReminderScheduler.reschedule(context)
            }
        }
    }

    private suspend fun fireSnoozed(context: Context, habitId: Long) {
        val db = AppDatabase.get(context)
        val habit = db.habitDao().habitById(habitId) ?: return
        val today = LocalDate.now().toEpochDay()
        if (db.habitDao().logFor(habitId, today)?.done == true) return // done meanwhile
        Notifications.showHabitReminder(context, habit, streakFor(db, habitId, today))
    }

    /** Posts everything scheduled within the last few minutes (alarm batching guard). */
    private suspend fun fireDue(context: Context) {
        val settings = SettingsRepository(context)
        if (!settings.notificationsEnabled.first()) return

        val db = AppDatabase.get(context)
        val today = LocalDate.now().toEpochDay()
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        fun due(min: Int?) = min != null && nowMin - min in 0..WINDOW_MIN

        db.habitDao().habitsOnce()
            .filter { due(it.reminderMin) }
            .forEach { habit ->
                if (db.habitDao().logFor(habit.id, today)?.done != true) {
                    Notifications.showHabitReminder(context, habit, streakFor(db, habit.id, today))
                }
            }

        db.taskDao().tasksOnce()
            .filter { !it.done && it.dueDay == today && due(it.dueMin) }
            .forEach { Notifications.showTaskReminder(context, it) }

        if (settings.checkinEnabled.first() && due(settings.checkinMin.first())) {
            Notifications.showCheckin(context)
        }
    }

    private suspend fun streakFor(db: AppDatabase, habitId: Long, today: Long): Int {
        val done = db.habitDao().logsOnce()
            .filter { it.habitId == habitId && it.done }
            .map { it.epochDay }
            .toSet()
        return Streaks.current(done, today)
    }

    private companion object {
        const val WINDOW_MIN = 6
    }
}

/** Re-arms the alarm chain after reboots and clock/timezone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                async { ReminderScheduler.reschedule(context) }
        }
    }
}

/** Handles the Done / Snooze buttons on reminder notifications. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val nm = context.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            ACTION_DONE_HABIT -> async {
                val db = AppDatabase.get(context)
                val today = LocalDate.now().toEpochDay()
                val habit = db.habitDao().habitById(id) ?: return@async
                if (db.habitDao().logFor(id, today)?.done != true) {
                    TendRepository(db).toggleHabitToday(habit, today)
                }
                TendWidgets.refresh(context)
                nm.cancel(Notifications.habitNotifId(id))
            }
            ACTION_SNOOZE_HABIT -> {
                val am = context.getSystemService(AlarmManager::class.java)
                ReminderScheduler.scheduleAt(
                    am,
                    System.currentTimeMillis() + 15 * 60_000L,
                    ReminderScheduler.snoozePendingIntent(context, id),
                )
                nm.cancel(Notifications.habitNotifId(id))
            }
            ACTION_DONE_TASK -> async {
                val db = AppDatabase.get(context)
                val task = db.taskDao().tasksOnce().firstOrNull { it.id == id } ?: return@async
                if (!task.done) db.taskDao().update(task.copy(done = true))
                nm.cancel(Notifications.taskNotifId(id))
            }
        }
    }

    companion object {
        const val ACTION_DONE_HABIT = "com.tend.app.action.DONE_HABIT"
        const val ACTION_SNOOZE_HABIT = "com.tend.app.action.SNOOZE_HABIT"
        const val ACTION_DONE_TASK = "com.tend.app.action.DONE_TASK"
        const val EXTRA_ID = "id"
    }
}
