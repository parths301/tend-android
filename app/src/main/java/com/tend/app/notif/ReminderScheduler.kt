package com.tend.app.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tend.app.data.SettingsRepository
import com.tend.app.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single-alarm chain: at any moment at most ONE alarm is registered — for the
 * next upcoming reminder across habit reminders, task due times, and the
 * nightly check-in. When it fires, `ReminderReceiver` posts what's due and
 * calls [reschedule] for the next one. No polling, no services.
 */
object ReminderScheduler {

    suspend fun reschedule(context: Context) {
        val settings = SettingsRepository(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(context)

        if (!settings.notificationsEnabled.first()) {
            am.cancel(pi)
            return
        }

        val db = AppDatabase.get(context)
        val habitReminders = db.habitDao().habitsOnce().mapNotNull { it.reminderMin }
        val taskDues = db.taskDao().tasksOnce()
            .filter { !it.done && it.dueDay != null && it.dueMin != null }
            .map { it.dueDay!! to it.dueMin!! }
        val checkin = if (settings.checkinEnabled.first()) settings.checkinMin.first() else null

        val next = computeNextMillis(
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
            habitReminderMins = habitReminders,
            taskDues = taskDues,
            checkinMin = checkin,
        )

        am.cancel(pi)
        if (next != null) {
            scheduleAt(am, next, pi)
        }
    }

    /** Exact when permitted, graceful inexact fallback otherwise. */
    fun scheduleAt(am: AlarmManager, atMillis: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun firePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun snoozePendingIntent(context: Context, habitId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (50_000 + habitId).toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_SNOOZE_HABIT_ID, habitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Pure: earliest upcoming occurrence strictly after [nowMillis].
     * Daily entries (habit reminders, check-in) roll to tomorrow when already
     * past; task dues are one-shot and skipped once past.
     */
    fun computeNextMillis(
        nowMillis: Long,
        zone: ZoneId,
        habitReminderMins: List<Int>,
        taskDues: List<Pair<Long, Int>>,
        checkinMin: Int?,
    ): Long? {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        fun at(day: LocalDate, min: Int): Long =
            day.atStartOfDay(zone).toInstant().toEpochMilli() + min * 60_000L

        val candidates = mutableListOf<Long>()
        for (min in habitReminderMins + listOfNotNull(checkinMin)) {
            val todayAt = at(today, min)
            candidates += if (todayAt > nowMillis + GUARD_MS) todayAt else at(today.plusDays(1), min)
        }
        for ((day, min) in taskDues) {
            val t = at(LocalDate.ofEpochDay(day), min)
            if (t > nowMillis + GUARD_MS) candidates += t
        }
        return candidates.minOrNull()
    }

    const val ACTION_FIRE = "com.tend.app.REMINDER_FIRE"
    const val EXTRA_SNOOZE_HABIT_ID = "snooze_habit_id"
    private const val GUARD_MS = 1_000L
}
