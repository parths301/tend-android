package com.tend.app

import com.tend.app.data.backup.BackupManager
import com.tend.app.data.backup.BackupScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BackupScheduleTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `waits until later today when the time has not passed`() {
        val now = millis(2026, 8, 5, 0, 30)
        val delay = BackupScheduler.initialDelayMillis(now, zone, atMin = 2 * 60)
        assertEquals(90 * 60_000L, delay)
    }

    @Test
    fun `rolls to tomorrow when the time has already passed`() {
        val now = millis(2026, 8, 5, 9, 0)
        val delay = BackupScheduler.initialDelayMillis(now, zone, atMin = 2 * 60)
        assertEquals(17 * 60 * 60_000L, delay)
    }

    @Test
    fun `never schedules in the past`() {
        val now = millis(2026, 8, 5, 2, 0)
        val delay = BackupScheduler.initialDelayMillis(now, zone, atMin = 2 * 60)
        assertTrue(delay > 0)
        assertEquals(24 * 60 * 60_000L, delay)
    }

    @Test
    fun `survives a spring-forward day`() {
        // 2026-03-08 in New York loses the 2 AM hour entirely.
        val now = millis(2026, 3, 7, 23, 0)
        val delay = BackupScheduler.initialDelayMillis(now, zone, atMin = 2 * 60)
        assertTrue("delay must stay positive across a DST gap", delay > 0)
        assertTrue("delay must stay under a day", delay < 24 * 60 * 60_000L)
    }
}

class BackupFileNamingTest {

    @Test
    fun `file names sort chronologically so pruning keeps the newest`() {
        val zone = ZoneId.systemDefault()
        fun at(h: Int, d: Int) =
            LocalDateTime.of(2026, 8, d, h, 0).atZone(zone).toInstant().toEpochMilli()

        // Chronological order in, lexicographic order out — that equivalence is
        // what lets pruning keep the newest files by sorting on name alone.
        val names = listOf(at(2, 3), at(14, 4), at(2, 5)).map { BackupManager.fileNameFor(it) }
        assertEquals(names.sorted(), names)
        assertTrue(names.all { it.startsWith("tend-backup-") && it.endsWith(".json") })
    }

    @Test
    fun `folder labels read like a path`() {
        assertEquals(
            "Documents/Tend",
            BackupManager.folderLabel(
                "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FTend"
            ),
        )
        assertEquals(
            "SD card/Backups",
            BackupManager.folderLabel(
                "content://com.android.externalstorage.documents/tree/1234-5678%3ABackups"
            ),
        )
        assertEquals("", BackupManager.folderLabel(""))
    }
}
