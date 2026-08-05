package com.tend.app

import com.tend.app.data.backup.BackupFormat
import com.tend.app.data.backup.BackupFormatException
import com.tend.app.data.backup.BackupSettings
import com.tend.app.data.backup.BackupSnapshot
import com.tend.app.data.db.Habit
import com.tend.app.data.db.HabitLog
import com.tend.app.data.db.NoteEntry
import com.tend.app.data.db.PlanBlock
import com.tend.app.data.db.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private fun sample(): BackupSnapshot = BackupSnapshot(
        createdAtMillis = 1_754_380_800_000L,
        appVersion = "2.1.0",
        habits = listOf(
            Habit(
                id = 1, name = "Morning run", category = "Fitness", colorHex = 0xFFD96F4E,
                glyph = "MR", type = "check", goal = "Daily · 7:00 AM", sortOrder = 0,
                createdDay = 20_000, reminderMin = 420,
            ),
            Habit(
                id = 2, name = "Deep work", category = "Work", colorHex = 0xFF7D74C9,
                glyph = "DW", type = "time", goal = "2h", sortOrder = 1,
                createdDay = 20_001, reminderMin = null,
            ),
        ),
        logs = listOf(
            HabitLog(id = 10, habitId = 1, epochDay = 20_100, done = true),
            HabitLog(id = 11, habitId = 2, epochDay = 20_100, done = false, minutes = 90),
        ),
        tasks = listOf(
            TaskItem(id = 20, title = "Buy groceries", groupName = "PERSONAL", done = false, sortOrder = 3, dueDay = 20_101, dueMin = 1020),
            TaskItem(id = 21, title = "File taxes", groupName = "WORK", done = true, sortOrder = 4),
        ),
        plans = listOf(
            PlanBlock(id = 30, epochDay = 20_100, startMin = 540, endMin = 660, title = "Focus", kind = "focus", done = false, source = "auto"),
        ),
        notes = listOf(NoteEntry(id = 40, habitId = 1, timestamp = 1_754_000_000_000L, text = "Felt easy today")),
        settings = BackupSettings(
            heatmapWeeks = 12, showAiBar = false, provider = "gemini", model = "gemini-2.5-flash",
            customCategories = listOf("Reading", "Music"), notificationsEnabled = true,
            checkinEnabled = false, checkinMin = 1_200, calendarEnabled = true,
        ),
    )

    @Test
    fun `round trips every table and setting`() {
        val original = sample()
        val decoded = BackupFormat.decode(BackupFormat.encode(original))

        assertEquals(original.createdAtMillis, decoded.createdAtMillis)
        assertEquals(original.appVersion, decoded.appVersion)
        assertEquals(original.habits, decoded.habits)
        assertEquals(original.logs, decoded.logs)
        assertEquals(original.tasks, decoded.tasks)
        assertEquals(original.plans, decoded.plans)
        assertEquals(original.notes, decoded.notes)
        assertEquals(original.settings, decoded.settings)
    }

    @Test
    fun `keeps nulls distinct from zero`() {
        val decoded = BackupFormat.decode(BackupFormat.encode(sample()))
        assertEquals(420, decoded.habits[0].reminderMin)
        assertNull(decoded.habits[1].reminderMin)
        assertEquals(20_101L, decoded.tasks[0].dueDay)
        assertNull(decoded.tasks[1].dueDay)
        assertNull(decoded.tasks[1].dueMin)
    }

    @Test
    fun `counts every row`() {
        assertEquals(2 + 2 + 2 + 1 + 1, sample().rowCount)
    }

    @Test
    fun `rejects files that are not tend backups`() {
        val e = runCatching { BackupFormat.decode("""{"hello":"world"}""") }.exceptionOrNull()
        assertTrue(e is BackupFormatException)
    }

    @Test
    fun `rejects text that is not json`() {
        val e = runCatching { BackupFormat.decode("not json at all") }.exceptionOrNull()
        assertTrue(e is BackupFormatException)
    }

    @Test
    fun `rejects a newer format version`() {
        val bumped = BackupFormat.encode(sample()).replace("\"version\": 1", "\"version\": 99")
        val e = runCatching { BackupFormat.decode(bumped) }.exceptionOrNull()
        assertTrue(e is BackupFormatException)
        assertTrue(e!!.message!!.contains("newer version"))
    }

    @Test
    fun `reads a backup that predates settings`() {
        val without = BackupFormat.encode(sample().copy(settings = null))
        val decoded = BackupFormat.decode(without)
        assertNull(decoded.settings)
        assertEquals(2, decoded.habits.size)
    }

    @Test
    fun `flags rows that point at a missing habit`() {
        val orphaned = sample().let {
            it.copy(logs = it.logs + HabitLog(id = 12, habitId = 999, epochDay = 20_100, done = true))
        }
        val problems = BackupFormat.problems(orphaned)
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("missing habit"))
    }

    @Test
    fun `sanitize drops orphans and clears child ids`() {
        val damaged = sample().let {
            it.copy(
                logs = it.logs + HabitLog(id = 12, habitId = 999, epochDay = 20_100, done = true),
                notes = it.notes + NoteEntry(id = 41, habitId = 999, timestamp = 1L, text = "orphan"),
            )
        }
        val clean = BackupFormat.sanitize(damaged)

        assertEquals(2, clean.logs.size)
        assertEquals(1, clean.notes.size)
        assertTrue(BackupFormat.problems(clean).isEmpty())
        // Habit ids are the join key and must survive; everything else is reassigned by Room.
        assertEquals(listOf(1L, 2L), clean.habits.map { it.id })
        assertTrue(clean.logs.all { it.id == 0L })
        assertTrue(clean.tasks.all { it.id == 0L })
        assertTrue(clean.plans.all { it.id == 0L })
        assertTrue(clean.notes.all { it.id == 0L })
    }

    @Test
    fun `sanitize collapses duplicate check-ins that would break the unique index`() {
        val dupes = sample().let {
            it.copy(logs = it.logs + HabitLog(id = 13, habitId = 1, epochDay = 20_100, done = false))
        }
        val clean = BackupFormat.sanitize(dupes)
        assertEquals(2, clean.logs.size)
        assertEquals(1, clean.logs.count { it.habitId == 1L && it.epochDay == 20_100L })
    }

    @Test
    fun `sanitize drops duplicate habit ids`() {
        val dupes = sample().let { it.copy(habits = it.habits + it.habits[0].copy(name = "Copy")) }
        val clean = BackupFormat.sanitize(dupes)
        assertEquals(2, clean.habits.size)
        assertEquals("Morning run", clean.habits[0].name)
    }

    @Test
    fun `written file is self-describing`() {
        val json = BackupFormat.encode(sample())
        assertTrue(json.contains("\"format\": \"tend-backup\""))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"counts\""))
        // Secrets never reach the file.
        assertTrue(!json.contains("api_key") && !json.contains("apiKey"))
        assertNotNull(BackupFormat.decode(json))
    }

    @Test
    fun `empty app still produces a valid backup`() {
        val empty = BackupSnapshot(
            createdAtMillis = 1L, appVersion = "2.1.0",
            habits = emptyList(), logs = emptyList(), tasks = emptyList(),
            plans = emptyList(), notes = emptyList(), settings = null,
        )
        val decoded = BackupFormat.decode(BackupFormat.encode(empty))
        assertEquals(0, decoded.rowCount)
    }
}
