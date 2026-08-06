package com.tend.app

import com.tend.app.data.backup.BackupFormat
import com.tend.app.data.backup.BackupFormatException
import com.tend.app.data.backup.BackupMemoryEntry
import com.tend.app.data.backup.BackupSettings
import com.tend.app.data.backup.BackupSnapshot
import com.tend.app.data.backup.BackupVaultKeyMaterial
import com.tend.app.data.db.ChatMessage
import com.tend.app.data.db.ChatThread
import com.tend.app.data.db.Habit
import com.tend.app.data.db.HabitLog
import com.tend.app.data.db.MessageLink
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

    private fun withChat(): BackupSnapshot = sample().copy(
        threads = listOf(
            ChatThread(id = 50, title = "Groceries", createdAt = 1L, updatedAt = 2L, mode = "ai", draft = "half typed"),
        ),
        messages = listOf(
            ChatMessage(id = 60, threadId = 50, fromAi = false, text = "buy milk", createdAt = 1L, source = "system"),
            ChatMessage(
                id = 61, threadId = 50, fromAi = true, text = "Added it.", createdAt = 2L,
                source = "cloud", modelId = "claude-opus-4-8", inContext = true,
            ),
        ),
        links = listOf(
            MessageLink(id = 70, messageId = 61, entityType = "task", entityId = 20, label = "Buy groceries", createdAt = 2L),
        ),
    )

    private fun withMemory(): BackupSnapshot = sample().copy(
        memoryEntries = listOf(
            BackupMemoryEntry(
                id = 80, createdAt = 1L, kind = "text", sizeBytes = 0,
                sealedTitle = "cipher-title", sealedBody = "cipher-body",
                sealedFileName = "", sealedOcrText = "", blobPath = "", mime = "",
                sealedBlobBase64 = "",
            ),
            BackupMemoryEntry(
                id = 81, createdAt = 2L, kind = "image", sizeBytes = 4,
                sealedTitle = "cipher-title-2", sealedBody = "cipher-caption",
                sealedFileName = "cipher-name", sealedOcrText = "cipher-ocr",
                blobPath = "abc.bin", mime = "image/jpeg",
                sealedBlobBase64 = "c2VhbGVkLWJ5dGVz",
            ),
        ),
        vaultKeyMaterial = BackupVaultKeyMaterial(
            saltPassword = "salt-p", saltRecovery = "salt-r", iterations = 210_000,
            wrappedPassword = "wrapped-p", wrappedRecovery = "wrapped-r", verifier = "verifier",
        ),
    )

    @Test
    fun `round trips memory entries and vault key material`() {
        val decoded = BackupFormat.decode(BackupFormat.encode(withMemory()))

        assertEquals(2, decoded.memoryEntries.size)
        assertEquals("cipher-title", decoded.memoryEntries[0].sealedTitle)
        assertEquals("abc.bin", decoded.memoryEntries[1].blobPath)
        assertEquals("c2VhbGVkLWJ5dGVz", decoded.memoryEntries[1].sealedBlobBase64)
        assertNotNull(decoded.vaultKeyMaterial)
        assertEquals("wrapped-p", decoded.vaultKeyMaterial!!.wrappedPassword)
        assertEquals(BackupFormat.VERSION, decoded.formatVersion)
    }

    @Test
    fun `a pre-v3 file has no opinion about memory, not an empty one`() {
        // A backup written before Memory existed must decode as "unknown", so
        // restore knows not to wipe whatever vault the device already has —
        // very different from a v3 backup whose vault was genuinely empty.
        val v2 = BackupFormat.encode(sample())
            .replace("\"version\": ${BackupFormat.VERSION}", "\"version\": 2")
        val decoded = BackupFormat.decode(v2)
        assertEquals(2, decoded.formatVersion)
        assertTrue(decoded.memoryEntries.isEmpty())
        assertNull(decoded.vaultKeyMaterial)
    }

    @Test
    fun `flags memory entries with no key material to unlock them`() {
        val orphaned = withMemory().copy(vaultKeyMaterial = null)
        val problems = BackupFormat.problems(orphaned)
        assertTrue(problems.any { it.contains("key material") })
    }

    @Test
    fun `sanitize drops duplicate memory entry ids`() {
        val dupes = withMemory().let { it.copy(memoryEntries = it.memoryEntries + it.memoryEntries[0]) }
        val clean = BackupFormat.sanitize(dupes)
        assertEquals(2, clean.memoryEntries.size)
    }

    @Test
    fun `round trips chat threads, messages and links`() {
        val decoded = BackupFormat.decode(BackupFormat.encode(withChat()))

        assertEquals(1, decoded.threads.size)
        assertEquals("half typed", decoded.threads[0].draft)
        assertEquals(2, decoded.messages.size)
        assertEquals("claude-opus-4-8", decoded.messages[1].modelId)
        assertTrue(decoded.messages[1].inContext)
        // A message with no model must come back as null, not the string "null".
        assertNull(decoded.messages[0].modelId)
        assertEquals(1, decoded.links.size)
        assertEquals(20L, decoded.links[0].entityId)
    }

    @Test
    fun `a restored link still points at the task it created`() {
        // The whole reason task ids are preserved: after a round trip through
        // sanitize, the link's entityId must still match a task that exists.
        val clean = BackupFormat.sanitize(withChat())
        val linkTarget = clean.links.single().entityId
        assertTrue(
            "Restored link points at a task that no longer exists",
            clean.tasks.any { it.id == linkTarget },
        )
    }

    @Test
    fun `sanitize drops messages whose thread is gone and links whose message is gone`() {
        val damaged = withChat().let {
            it.copy(
                messages = it.messages + ChatMessage(
                    id = 62, threadId = 999, fromAi = false, text = "orphan",
                    createdAt = 3L, source = "system",
                ),
                links = it.links + MessageLink(
                    id = 71, messageId = 999, entityType = "task", entityId = 20,
                    label = "orphan", createdAt = 3L,
                ),
            )
        }
        val clean = BackupFormat.sanitize(damaged)
        assertEquals(2, clean.messages.size)
        assertEquals(1, clean.links.size)
    }

    @Test
    fun `a link to a deleted entity is kept on purpose`() {
        // Requirement F's graceful-degradation case: the chip has to survive so
        // the UI can say the task was deleted, rather than silently vanishing.
        val danglingTarget = withChat().copy(tasks = emptyList())
        val clean = BackupFormat.sanitize(danglingTarget)
        assertEquals(1, clean.links.size)
    }

    @Test
    fun `a version 1 file without any chat still restores`() {
        // Backups written before chat existed have no chat arrays at all.
        val v1 = BackupFormat.encode(sample())
            .replace("\"version\": ${BackupFormat.VERSION}", "\"version\": 1")
        val decoded = BackupFormat.decode(v1)
        assertEquals(2, decoded.habits.size)
        assertTrue(decoded.threads.isEmpty())
        assertTrue(decoded.messages.isEmpty())
        assertTrue(decoded.links.isEmpty())
    }

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
        val bumped = BackupFormat.encode(sample())
            .replace("\"version\": ${BackupFormat.VERSION}", "\"version\": 99")
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
    fun `sanitize drops orphans and keeps the keys links depend on`() {
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

        // Habit ids are the join key for logs and notes. Task and plan ids are
        // kept for a second reason: message_links records the id of whatever a
        // chat message created, so reassigning them would restore the data and
        // silently break every chip pointing at it. Restore clears the tables
        // first, so there is nothing for the preserved ids to collide with.
        assertEquals(listOf(1L, 2L), clean.habits.map { it.id })
        assertEquals(sample().tasks.map { it.id }, clean.tasks.map { it.id })
        assertEquals(sample().plans.map { it.id }, clean.plans.map { it.id })

        // Logs and notes carry no inbound references, so they are still free to
        // be reassigned by Room.
        assertTrue(clean.logs.all { it.id == 0L })
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
        assertTrue(json.contains("\"version\": ${BackupFormat.VERSION}"))
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
