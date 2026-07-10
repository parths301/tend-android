package com.tend.app.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tend.app.MainActivity
import com.tend.app.R
import com.tend.app.data.db.Habit
import com.tend.app.data.db.TaskItem

/** Channels + builders for every notification Tend posts. */
object Notifications {
    const val CH_HABITS = "habit_reminders"
    const val CH_TASKS = "task_reminders"
    const val CH_CHECKIN = "nightly_checkin"

    const val CHECKIN_ID = 1001
    fun habitNotifId(habitId: Long) = (2000 + habitId).toInt()
    fun taskNotifId(taskId: Long) = (3000 + taskId).toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_HABITS, "Habit reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TASKS, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CHECKIN, "Nightly check-in", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openAppIntent(context: Context, deepLink: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context,
            deepLink?.hashCode() ?: 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                deepLink?.let { putExtra(MainActivity.EXTRA_DEEPLINK, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun actionIntent(context: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (action.hashCode() + id).toInt(),
            Intent(context, ActionReceiver::class.java)
                .setAction(action)
                .putExtra(ActionReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun showHabitReminder(context: Context, habit: Habit, streak: Int) {
        ensureChannels(context)
        val body = if (streak > 0) "Keep the streak alive — day ${streak + 1}." else "A small step today counts."
        val notif = NotificationCompat.Builder(context, CH_HABITS)
            .setSmallIcon(R.drawable.ic_stat_tend)
            .setContentTitle(habit.name)
            .setContentText(body)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .addAction(0, "✓ Done", actionIntent(context, ActionReceiver.ACTION_DONE_HABIT, habit.id))
            .addAction(0, "Snooze 15m", actionIntent(context, ActionReceiver.ACTION_SNOOZE_HABIT, habit.id))
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(habitNotifId(habit.id), notif)
    }

    fun showTaskReminder(context: Context, task: TaskItem) {
        ensureChannels(context)
        val notif = NotificationCompat.Builder(context, CH_TASKS)
            .setSmallIcon(R.drawable.ic_stat_tend)
            .setContentTitle(task.title)
            .setContentText("Due now · ${task.groupName.lowercase().replaceFirstChar { it.uppercaseChar() }}")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .addAction(0, "✓ Done", actionIntent(context, ActionReceiver.ACTION_DONE_TASK, task.id))
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(taskNotifId(task.id), notif)
    }

    fun showCheckin(context: Context) {
        ensureChannels(context)
        val notif = NotificationCompat.Builder(context, CH_CHECKIN)
            .setSmallIcon(R.drawable.ic_stat_tend)
            .setContentTitle("How was today?")
            .setContentText("Anything to add for tomorrow? Tap to plan.")
            .setContentIntent(openAppIntent(context, MainActivity.DEEPLINK_PLAN_TOMORROW))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(CHECKIN_ID, notif)
    }
}
