package com.tend.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** A read-only calendar event mapped onto one day's plan timeline. */
data class CalEvent(
    val title: String,
    val startMin: Int,
    val endMin: Int,
)

/** Read-only access to the device calendar for the Plan timeline and Auto-plan. */
class CalendarRepository(context: Context) {
    private val appContext = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun eventsFor(day: Long): List<CalEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val date = LocalDate.ofEpochDay(day)
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(dayStart.toString())
            .appendPath(dayEnd.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )

        val events = mutableListOf<CalEvent>()
        try {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                while (cursor.moveToNext()) {
                    if (cursor.getInt(allDayIdx) == 1) continue // skip all-day entries
                    val begin = cursor.getLong(beginIdx)
                    val end = cursor.getLong(endIdx)
                    val startMin = (((begin - dayStart) / 60_000).toInt()).coerceIn(0, 24 * 60 - 1)
                    val endMin = (((end - dayStart) / 60_000).toInt()).coerceIn(startMin + 5, 24 * 60)
                    events += CalEvent(
                        title = cursor.getString(titleIdx)?.ifBlank { null } ?: "Busy",
                        startMin = startMin,
                        endMin = endMin,
                    )
                }
            }
        } catch (_: SecurityException) {
            // permission revoked mid-flight
        }
        events.sortedBy { it.startMin }
    }
}
