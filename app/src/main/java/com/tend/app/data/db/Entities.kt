package com.tend.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val colorHex: Long,          // e.g. 0xFFD96F4E
    val glyph: String,           // two-letter badge, e.g. "MS"
    val type: String,            // "check" | "time" | "avoid"
    val goal: String,            // e.g. "Daily · 8:00 AM"
    val sortOrder: Int = 0,
    val createdDay: Long = 0,    // epoch day the habit was created; stats start here
    val reminderMin: Int? = null, // minutes since midnight; null = no reminder
)

@Entity(
    tableName = "habit_logs",
    indices = [Index(value = ["habitId", "epochDay"], unique = true)],
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val done: Boolean,
    val minutes: Int = 0,        // logged time for "time" habits
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val groupName: String,       // "PERSONAL" | "WORK" | "HEALTH" | ...
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val dueDay: Long? = null,    // epoch day the task is due; null = no due date
    val dueMin: Int? = null,     // minutes since midnight; null = no due time
)

@Entity(tableName = "plan_blocks")
data class PlanBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startMin: Int,           // minutes since midnight
    val endMin: Int,
    val title: String,
    val kind: String,            // "habit" | "focus" | "event"
    val done: Boolean = false,
    val source: String = "manual", // "manual" | "habit" | "auto"
)

@Entity(tableName = "notes")
data class NoteEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val timestamp: Long,         // epoch millis
    val text: String,
)

// ── chat ────────────────────────────────────────────────────────────────
// Ask Tend used to be a list in memory. These four tables give it threads that
// survive process death, a record of what each message created, and somewhere
// for attachments to hang.

/** One conversation. `draft` is the unsent composer text, kept per thread. */
@Entity(tableName = "chat_threads")
data class ChatThread(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,          // epoch millis
    val updatedAt: Long,          // bumped on every message; drives recency order
    val mode: String,             // ChatMode.stored — the mode this thread last ran in
    val pinned: Boolean = false,
    val draft: String = "",
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThread::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["threadId"])],
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val fromAi: Boolean,
    val text: String,
    val createdAt: Long,
    val source: String,           // ResponseSource.stored — "cloud" | "local" | "system"
    val modelId: String? = null,  // what actually answered, for the "AI" tag
    /** Manually marked "add to AI context" — only meaningful for Local Mode. */
    val inContext: Boolean = false,
    val personalityId: Long? = null,
)

/**
 * Something a message created — a task, habit, plan block.
 *
 * Deliberately **not** a foreign key onto habits/tasks/plans. If the user later
 * deletes the task, the reference must survive so the chat can say "this task
 * was deleted" instead of the chip silently disappearing. Resolution happens at
 * render time against the live tables.
 */
@Entity(
    tableName = "message_links",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["messageId"])],
)
data class MessageLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val entityType: String,       // EntityRef.Type.stored — "task" | "habit" | "plan"
    val entityId: Long,
    val label: String,            // snapshot of the title at creation, for dead links
    val createdAt: Long,
)

// ── memory vault ────────────────────────────────────────────────────────

/**
 * One vault entry. **Every user-supplied field here is ciphertext.**
 *
 * Only structural metadata is left readable — when it was added, what kind of
 * thing it is, how big the payload is — because those are needed to list and
 * sort the vault while it is locked, and none of them reveal content.
 *
 * Attachment bytes are not in this row: they live in `filesDir/vault`, sealed
 * under the vault's data key the same way the text fields above are, and
 * [blobPath] names the file.
 */
@Entity(tableName = "memory_entries")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val kind: String,              // "text" | "image" | "file"
    val sizeBytes: Long = 0,
    /** Ciphertext. A short label the user sees in the list once unlocked. */
    val sealedTitle: String,
    /** Ciphertext. The note body, or a caption for an image/file. */
    val sealedBody: String,
    /** Ciphertext. Original filename, for entries that came from a file. */
    val sealedFileName: String = "",
    /** Ciphertext. Text extracted by OCR, when the user has enabled it. */
    val sealedOcrText: String = "",
    /** Filename under filesDir/vault; empty for pure text entries. */
    val blobPath: String = "",
    val mime: String = "",
)

/**
 * A file or image attached to something. `ownerType`/`ownerId` rather than a
 * foreign key so chat messages, habits, tasks and plan blocks can all share
 * one table and one rendering primitive. (Memory has its own storage — see
 * `MemoryEntry.blobPath` — because its bytes are sealed under the vault's
 * data key rather than left as an ordinary SAF reference.)
 */
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["ownerType", "ownerId"])],
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: String,        // "message" | "habit" | "task" | "plan"
    val ownerId: Long,
    val uri: String,              // SAF content:// URI, persisted permission taken
    val mime: String,
    val displayName: String,
    val sizeBytes: Long,
    val createdAt: Long,
)
