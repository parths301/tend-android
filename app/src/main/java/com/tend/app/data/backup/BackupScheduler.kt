package com.tend.app.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tend.app.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Automatic backups run through WorkManager rather than the reminder alarm
 * chain: writing a file is deferrable work, it survives reboots without a boot
 * receiver, and it doesn't spend the app's exact-alarm budget on something the
 * user never sees happen.
 */
object BackupScheduler {

    const val WORK_NAME = "tend-auto-backup"

    /** Brings the scheduled work in line with the saved interval and time. */
    suspend fun sync(context: Context) {
        val settings = SettingsRepository(context)
        val interval = settings.backupInterval.first()
        val wm = WorkManager.getInstance(context.applicationContext)

        val repeatDays = when (interval) {
            SettingsRepository.BACKUP_DAILY -> 1L
            SettingsRepository.BACKUP_WEEKLY -> 7L
            else -> {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
        }

        val delay = initialDelayMillis(
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
            atMin = settings.backupMin.first(),
        )

        val request = PeriodicWorkRequestBuilder<BackupWorker>(repeatDays, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()

        // UPDATE keeps the existing work's identity, so changing the time
        // doesn't reset the schedule to "starting over from now".
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Pure: millis until the next occurrence of [atMin] strictly after [nowMillis]. */
    fun initialDelayMillis(nowMillis: Long, zone: ZoneId, atMin: Int): Long {
        val today: LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli() + atMin * 60_000L
        val todayAt = at(today)
        return (if (todayAt > nowMillis) todayAt else at(today.plusDays(1))) - nowMillis
    }
}

/** One scheduled backup. Failures are recorded by [BackupManager] and surfaced in Settings. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        BackupManager.backupNow(applicationContext)
        Result.success()
    } catch (e: BackupException) {
        // Folder gone or permission revoked — retrying won't help; the user
        // has to pick a folder again, and Settings now says so.
        Result.failure()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
