package com.tend.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder")
    fun habits(): Flow<List<Habit>>

    @Query("SELECT * FROM habit_logs")
    fun logs(): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay LIMIT 1")
    suspend fun logFor(habitId: Long, epochDay: Long): HabitLog?

    // One-shot reads for widgets and the reminder scheduler
    @Query("SELECT * FROM habits ORDER BY sortOrder")
    suspend fun habitsOnce(): List<Habit>

    @Query("SELECT * FROM habit_logs")
    suspend fun logsOnce(): List<HabitLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: HabitLog)

    @Insert
    suspend fun insertHabits(habits: List<Habit>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<HabitLog>)

    @Insert
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun habitById(id: Long): Habit?

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabit(habitId: Long)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsFor(habitId: Long)

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("DELETE FROM habit_logs")
    suspend fun clearLogs()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY sortOrder, id")
    fun tasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks")
    suspend fun tasksOnce(): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun taskById(id: Long): TaskItem?

    @Update
    suspend fun update(task: TaskItem)

    @Insert
    suspend fun insert(task: TaskItem): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskItem>)

    @Delete
    suspend fun delete(task: TaskItem)

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_blocks WHERE epochDay = :epochDay ORDER BY startMin")
    fun forDay(epochDay: Long): Flow<List<PlanBlock>>

    @Query("SELECT * FROM plan_blocks WHERE epochDay = :epochDay ORDER BY startMin")
    suspend fun forDayOnce(epochDay: Long): List<PlanBlock>

    @Query("SELECT * FROM plan_blocks ORDER BY epochDay, startMin")
    suspend fun allOnce(): List<PlanBlock>

    @Query("SELECT * FROM plan_blocks WHERE id = :id LIMIT 1")
    suspend fun blockById(id: Long): PlanBlock?

    @Update
    suspend fun update(block: PlanBlock)

    @Insert
    suspend fun insert(block: PlanBlock): Long

    @Insert
    suspend fun insertAll(blocks: List<PlanBlock>)

    @Query("SELECT COUNT(*) FROM plan_blocks WHERE epochDay = :epochDay AND title = :title")
    suspend fun countByTitle(epochDay: Long, title: String): Int

    @Delete
    suspend fun delete(block: PlanBlock)

    @Query("DELETE FROM plan_blocks WHERE epochDay = :epochDay AND source = 'auto'")
    suspend fun deleteAutoFor(epochDay: Long)

    @Query("DELETE FROM plan_blocks")
    suspend fun clearPlans()
}

@Dao
interface ChatDao {
    // ── threads ──
    @Query("SELECT * FROM chat_threads ORDER BY pinned DESC, updatedAt DESC")
    fun threads(): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_threads ORDER BY pinned DESC, updatedAt DESC")
    suspend fun threadsOnce(): List<ChatThread>

    @Query("SELECT * FROM chat_threads WHERE id = :id LIMIT 1")
    suspend fun threadById(id: Long): ChatThread?

    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC LIMIT 1")
    suspend fun mostRecentThread(): ChatThread?

    @Insert
    suspend fun insertThread(thread: ChatThread): Long

    @Insert
    suspend fun insertThreads(threads: List<ChatThread>)

    @Update
    suspend fun updateThread(thread: ChatThread)

    @Query("UPDATE chat_threads SET draft = :draft WHERE id = :threadId")
    suspend fun setDraft(threadId: Long, draft: String)

    @Query("UPDATE chat_threads SET title = :title WHERE id = :threadId")
    suspend fun setTitle(threadId: Long, title: String)

    @Query("UPDATE chat_threads SET pinned = :pinned WHERE id = :threadId")
    suspend fun setPinned(threadId: Long, pinned: Boolean)

    @Query("UPDATE chat_threads SET mode = :mode, updatedAt = :at WHERE id = :threadId")
    suspend fun setMode(threadId: Long, mode: String, at: Long)

    @Query("UPDATE chat_threads SET updatedAt = :at WHERE id = :threadId")
    suspend fun touchThread(threadId: Long, at: Long)

    /** Deletes messages and links too, via ON DELETE CASCADE. */
    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: Long)

    @Query("DELETE FROM chat_threads")
    suspend fun clearThreads()

    // ── messages ──
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt, id")
    fun messagesFor(threadId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt, id")
    suspend fun messagesForOnce(threadId: Long): List<ChatMessage>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt, id")
    suspend fun allMessagesOnce(): List<ChatMessage>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE threadId = :threadId")
    suspend fun messageCount(threadId: Long): Int

    @Insert
    suspend fun insertMessage(message: ChatMessage): Long

    @Insert
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Query("UPDATE chat_messages SET inContext = :inContext WHERE id = :messageId")
    suspend fun setInContext(messageId: Long, inContext: Boolean)

    @Query("UPDATE chat_messages SET inContext = 0 WHERE threadId = :threadId")
    suspend fun clearContextFor(threadId: Long)

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteMessages(ids: List<Long>)

    /** Clears a thread's messages but keeps the thread itself — distinct from delete. */
    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun clearMessagesFor(threadId: Long)

    /** Thread ids whose title or any message text matches. */
    @Query(
        "SELECT DISTINCT t.id FROM chat_threads t " +
            "LEFT JOIN chat_messages m ON m.threadId = t.id " +
            "WHERE t.title LIKE '%' || :q || '%' OR m.text LIKE '%' || :q || '%'"
    )
    suspend fun searchThreadIds(q: String): List<Long>

    // ── links ──
    @Query("SELECT * FROM message_links WHERE messageId IN (:messageIds)")
    fun linksForMessages(messageIds: List<Long>): Flow<List<MessageLink>>

    @Query(
        "SELECT l.* FROM message_links l " +
            "INNER JOIN chat_messages m ON m.id = l.messageId WHERE m.threadId = :threadId"
    )
    fun linksForThread(threadId: Long): Flow<List<MessageLink>>

    @Query("SELECT * FROM message_links")
    suspend fun allLinksOnce(): List<MessageLink>

    @Insert
    suspend fun insertLink(link: MessageLink): Long

    @Insert
    suspend fun insertLinks(links: List<MessageLink>)

    // ── attachments ──
    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun attachmentsFor(ownerType: String, ownerId: Long): List<Attachment>

    @Query(
        "SELECT a.* FROM attachments a " +
            "INNER JOIN chat_messages m ON m.id = a.ownerId " +
            "WHERE a.ownerType = 'message' AND m.threadId = :threadId"
    )
    fun attachmentsForThread(threadId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments")
    suspend fun allAttachmentsOnce(): List<Attachment>

    @Insert
    suspend fun insertAttachment(attachment: Attachment): Long

    @Insert
    suspend fun insertAttachments(attachments: List<Attachment>)

    @Query("DELETE FROM attachments WHERE ownerType = :ownerType AND ownerId IN (:ownerIds)")
    suspend fun deleteAttachmentsFor(ownerType: String, ownerIds: List<Long>)

    @Query("DELETE FROM attachments")
    suspend fun clearAttachments()
}

@Dao
interface MemoryDao {
    /**
     * Newest first. Returns ciphertext — decryption happens above this layer,
     * only when the vault is unlocked, and never inside a SQL query.
     */
    @Query("SELECT * FROM memory_entries ORDER BY createdAt DESC")
    fun entries(): Flow<List<MemoryEntry>>

    @Query("SELECT * FROM memory_entries ORDER BY createdAt DESC")
    suspend fun entriesOnce(): List<MemoryEntry>

    @Query("SELECT * FROM memory_entries WHERE id = :id LIMIT 1")
    suspend fun entryById(id: Long): MemoryEntry?

    @Query("SELECT COUNT(*) FROM memory_entries")
    fun count(): Flow<Int>

    @Insert
    suspend fun insert(entry: MemoryEntry): Long

    /** Restore only — preserves the ids a backup captured, same as every other table. */
    @Insert
    suspend fun insertAll(entries: List<MemoryEntry>)

    @Update
    suspend fun update(entry: MemoryEntry)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memory_entries")
    suspend fun clear()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE habitId = :habitId ORDER BY timestamp DESC")
    fun forHabit(habitId: Long): Flow<List<NoteEntry>>

    @Query("SELECT * FROM notes ORDER BY timestamp")
    suspend fun allOnce(): List<NoteEntry>

    @Insert
    suspend fun insert(note: NoteEntry): Long

    @Insert
    suspend fun insertAll(notes: List<NoteEntry>)

    @Query("DELETE FROM notes WHERE habitId = :habitId")
    suspend fun deleteFor(habitId: Long)

    @Query("DELETE FROM notes")
    suspend fun clearNotes()
}
