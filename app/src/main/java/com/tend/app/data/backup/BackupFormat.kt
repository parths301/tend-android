package com.tend.app.data.backup

import com.tend.app.data.db.ChatMessage
import com.tend.app.data.db.ChatThread
import com.tend.app.data.db.Habit
import com.tend.app.data.db.HabitLog
import com.tend.app.data.db.MessageLink
import com.tend.app.data.db.NoteEntry
import com.tend.app.data.db.PlanBlock
import com.tend.app.data.db.TaskItem
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything a Tend backup carries, minus secrets. */
data class BackupSettings(
    val heatmapWeeks: Int,
    val showAiBar: Boolean,
    val provider: String,
    val model: String,
    val customCategories: List<String>,
    val notificationsEnabled: Boolean,
    val checkinEnabled: Boolean,
    val checkinMin: Int,
    val calendarEnabled: Boolean,
)

data class BackupSnapshot(
    val createdAtMillis: Long,
    val appVersion: String,
    val habits: List<Habit>,
    val logs: List<HabitLog>,
    val tasks: List<TaskItem>,
    val plans: List<PlanBlock>,
    val notes: List<NoteEntry>,
    val settings: BackupSettings?,
    val threads: List<ChatThread> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val links: List<MessageLink> = emptyList(),
) {
    val rowCount: Int
        get() = habits.size + logs.size + tasks.size + plans.size + notes.size +
            threads.size + messages.size
}

class BackupFormatException(message: String) : Exception(message)

/**
 * The on-device backup file: a single self-describing JSON document.
 *
 * Plain JSON on purpose — it restores into Tend, but it also opens in any text
 * editor, so a backup stays readable even if this app is gone. API keys are
 * deliberately **not** included: the file lands in ordinary user storage, and a
 * key belongs in the device keystore, not in a document you might sync or mail.
 */
object BackupFormat {

    const val FORMAT = "tend-backup"

    /**
     * v2 adds chat threads, messages and entity links.
     *
     * Readers still accept v1 — the chat arrays are simply absent and default to
     * empty — so a backup taken before this release restores unchanged.
     */
    const val VERSION = 2
    const val MIME = "application/json"
    const val FILE_PREFIX = "tend-backup-"
    const val FILE_SUFFIX = ".json"

    fun encode(snapshot: BackupSnapshot): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("appVersion", snapshot.appVersion)
        root.put("createdAt", snapshot.createdAtMillis)

        root.put(
            "counts",
            JSONObject()
                .put("habits", snapshot.habits.size)
                .put("habitLogs", snapshot.logs.size)
                .put("tasks", snapshot.tasks.size)
                .put("planBlocks", snapshot.plans.size)
                .put("notes", snapshot.notes.size)
                .put("chatThreads", snapshot.threads.size)
                .put("chatMessages", snapshot.messages.size),
        )

        root.put(
            "habits",
            snapshot.habits.jsonArray {
                put("id", it.id)
                put("name", it.name)
                put("category", it.category)
                put("colorHex", it.colorHex)
                put("glyph", it.glyph)
                put("type", it.type)
                put("goal", it.goal)
                put("sortOrder", it.sortOrder)
                put("createdDay", it.createdDay)
                putNullableInt("reminderMin", it.reminderMin)
            },
        )

        root.put(
            "habitLogs",
            snapshot.logs.jsonArray {
                put("id", it.id)
                put("habitId", it.habitId)
                put("epochDay", it.epochDay)
                put("done", it.done)
                put("minutes", it.minutes)
            },
        )

        root.put(
            "tasks",
            snapshot.tasks.jsonArray {
                put("id", it.id)
                put("title", it.title)
                put("groupName", it.groupName)
                put("done", it.done)
                put("sortOrder", it.sortOrder)
                putNullableLong("dueDay", it.dueDay)
                putNullableInt("dueMin", it.dueMin)
            },
        )

        root.put(
            "planBlocks",
            snapshot.plans.jsonArray {
                put("id", it.id)
                put("epochDay", it.epochDay)
                put("startMin", it.startMin)
                put("endMin", it.endMin)
                put("title", it.title)
                put("kind", it.kind)
                put("done", it.done)
                put("source", it.source)
            },
        )

        root.put(
            "notes",
            snapshot.notes.jsonArray {
                put("id", it.id)
                put("habitId", it.habitId)
                put("timestamp", it.timestamp)
                put("text", it.text)
            },
        )

        // Chat. Attachments are referenced by content:// URI and live outside the
        // app, so they are deliberately not inlined here — a backup stays a
        // readable text file rather than a container of encoded blobs.
        root.put(
            "chatThreads",
            snapshot.threads.jsonArray {
                put("id", it.id)
                put("title", it.title)
                put("createdAt", it.createdAt)
                put("updatedAt", it.updatedAt)
                put("mode", it.mode)
                put("pinned", it.pinned)
                put("draft", it.draft)
            },
        )

        root.put(
            "chatMessages",
            snapshot.messages.jsonArray {
                put("id", it.id)
                put("threadId", it.threadId)
                put("fromAi", it.fromAi)
                put("text", it.text)
                put("createdAt", it.createdAt)
                put("source", it.source)
                put("modelId", it.modelId ?: JSONObject.NULL)
                put("inContext", it.inContext)
            },
        )

        root.put(
            "messageLinks",
            snapshot.links.jsonArray {
                put("id", it.id)
                put("messageId", it.messageId)
                put("entityType", it.entityType)
                put("entityId", it.entityId)
                put("label", it.label)
                put("createdAt", it.createdAt)
            },
        )

        snapshot.settings?.let { s ->
            root.put(
                "settings",
                JSONObject()
                    .put("heatmapWeeks", s.heatmapWeeks)
                    .put("showAiBar", s.showAiBar)
                    .put("provider", s.provider)
                    .put("model", s.model)
                    .put("customCategories", JSONArray(s.customCategories))
                    .put("notificationsEnabled", s.notificationsEnabled)
                    .put("checkinEnabled", s.checkinEnabled)
                    .put("checkinMin", s.checkinMin)
                    .put("calendarEnabled", s.calendarEnabled),
            )
        }

        return root.toString(2)
    }

    fun decode(text: String): BackupSnapshot {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException("That file isn't a Tend backup — it isn't valid JSON.")
        }

        val format = root.optString("format")
        if (format != FORMAT) {
            throw BackupFormatException("That file isn't a Tend backup.")
        }
        val version = root.optInt("version", -1)
        if (version < 1) {
            throw BackupFormatException("This backup is missing a version and can't be read.")
        }
        if (version > VERSION) {
            throw BackupFormatException(
                "This backup was written by a newer version of Tend (format v$version). Update the app, then restore."
            )
        }

        return BackupSnapshot(
            createdAtMillis = root.optLong("createdAt", 0L),
            appVersion = root.optString("appVersion", "unknown"),
            habits = root.rows("habits") {
                Habit(
                    id = it.getLong("id"),
                    name = it.getString("name"),
                    category = it.optString("category", "Mind"),
                    colorHex = it.optLong("colorHex", 0xFFD96F4E),
                    glyph = it.optString("glyph", "HB"),
                    type = it.optString("type", "check"),
                    goal = it.optString("goal", "Daily"),
                    sortOrder = it.optInt("sortOrder", 0),
                    createdDay = it.optLong("createdDay", 0L),
                    reminderMin = it.optIntOrNull("reminderMin"),
                )
            },
            logs = root.rows("habitLogs") {
                HabitLog(
                    id = it.getLong("id"),
                    habitId = it.getLong("habitId"),
                    epochDay = it.getLong("epochDay"),
                    done = it.optBoolean("done", false),
                    minutes = it.optInt("minutes", 0),
                )
            },
            tasks = root.rows("tasks") {
                TaskItem(
                    id = it.getLong("id"),
                    title = it.getString("title"),
                    groupName = it.optString("groupName", "PERSONAL"),
                    done = it.optBoolean("done", false),
                    sortOrder = it.optInt("sortOrder", 0),
                    dueDay = it.optLongOrNull("dueDay"),
                    dueMin = it.optIntOrNull("dueMin"),
                )
            },
            plans = root.rows("planBlocks") {
                PlanBlock(
                    id = it.getLong("id"),
                    epochDay = it.getLong("epochDay"),
                    startMin = it.getInt("startMin"),
                    endMin = it.getInt("endMin"),
                    title = it.getString("title"),
                    kind = it.optString("kind", "event"),
                    done = it.optBoolean("done", false),
                    source = it.optString("source", "manual"),
                )
            },
            notes = root.rows("notes") {
                NoteEntry(
                    id = it.getLong("id"),
                    habitId = it.getLong("habitId"),
                    timestamp = it.optLong("timestamp", 0L),
                    text = it.optString("text", ""),
                )
            },
            // Absent in v1 files; `rows` yields an empty list, which is correct.
            threads = root.rows("chatThreads") {
                ChatThread(
                    id = it.getLong("id"),
                    title = it.optString("title", "Chat"),
                    createdAt = it.optLong("createdAt", 0L),
                    updatedAt = it.optLong("updatedAt", 0L),
                    mode = it.optString("mode", "ai"),
                    pinned = it.optBoolean("pinned", false),
                    draft = it.optString("draft", ""),
                )
            },
            messages = root.rows("chatMessages") {
                ChatMessage(
                    id = it.getLong("id"),
                    threadId = it.getLong("threadId"),
                    fromAi = it.optBoolean("fromAi", false),
                    text = it.optString("text", ""),
                    createdAt = it.optLong("createdAt", 0L),
                    source = it.optString("source", "system"),
                    modelId = it.optStringOrNull("modelId"),
                    inContext = it.optBoolean("inContext", false),
                )
            },
            links = root.rows("messageLinks") {
                MessageLink(
                    id = it.getLong("id"),
                    messageId = it.getLong("messageId"),
                    entityType = it.optString("entityType", ""),
                    entityId = it.getLong("entityId"),
                    label = it.optString("label", ""),
                    createdAt = it.optLong("createdAt", 0L),
                )
            },
            settings = root.optJSONObject("settings")?.let { s ->
                BackupSettings(
                    heatmapWeeks = s.optInt("heatmapWeeks", 17),
                    showAiBar = s.optBoolean("showAiBar", true),
                    provider = s.optString("provider", "gemini"),
                    model = s.optString("model", ""),
                    customCategories = s.optJSONArray("customCategories")
                        ?.let { arr -> (0 until arr.length()).map { i -> arr.getString(i) } }
                        .orEmpty(),
                    notificationsEnabled = s.optBoolean("notificationsEnabled", true),
                    checkinEnabled = s.optBoolean("checkinEnabled", true),
                    checkinMin = s.optInt("checkinMin", 21 * 60 + 30),
                    calendarEnabled = s.optBoolean("calendarEnabled", false),
                )
            },
        )
    }

    /**
     * Cross-table sanity check. A structurally valid file can still be
     * incoherent — a log pointing at a habit that isn't in the file would
     * restore into an orphan row that no screen can ever show.
     */
    fun problems(snapshot: BackupSnapshot): List<String> {
        val problems = mutableListOf<String>()
        val habitIds = snapshot.habits.map { it.id }.toSet()
        val orphanLogs = snapshot.logs.count { it.habitId !in habitIds }
        val orphanNotes = snapshot.notes.count { it.habitId !in habitIds }
        if (orphanLogs > 0) problems += "$orphanLogs check-in${plural(orphanLogs)} reference a missing habit"
        if (orphanNotes > 0) problems += "$orphanNotes note${plural(orphanNotes)} reference a missing habit"
        if (snapshot.habits.size != habitIds.size) problems += "duplicate habit ids"
        return problems
    }

    /**
     * Makes a snapshot safe to insert: drops the rows [problems] flags, and
     * zeroes every child row's primary key so Room assigns fresh ones. Only
     * habit ids survive as-is, because logs and notes point at them.
     */
    /**
     * Drops rows that point at something absent, and keeps primary keys.
     *
     * Keys are preserved rather than regenerated because restore clears the
     * tables first, so there is nothing to collide with — and because
     * `message_links` records the id of the task or plan a chat message created.
     * Regenerating those ids (as this did before chat existed) would silently
     * turn every restored chip into a dead link.
     */
    fun sanitize(snapshot: BackupSnapshot): BackupSnapshot {
        val habits = snapshot.habits.distinctBy { it.id }
        val habitIds = habits.map { it.id }.toSet()

        val threads = snapshot.threads.distinctBy { it.id }
        val threadIds = threads.map { it.id }.toSet()
        val messages = snapshot.messages
            .distinctBy { it.id }
            .filter { it.threadId in threadIds }
        val messageIds = messages.map { it.id }.toSet()

        return snapshot.copy(
            habits = habits,
            logs = snapshot.logs
                .filter { it.habitId in habitIds }
                .distinctBy { it.habitId to it.epochDay }
                .map { it.copy(id = 0) },
            tasks = snapshot.tasks.distinctBy { it.id },
            plans = snapshot.plans.distinctBy { it.id },
            notes = snapshot.notes.filter { it.habitId in habitIds }.map { it.copy(id = 0) },
            threads = threads,
            messages = messages,
            // A link whose message is gone has nothing to render against. A link
            // whose *entity* is gone is kept on purpose — that is the deleted-item
            // case the chat is designed to report.
            links = snapshot.links.filter { it.messageId in messageIds },
        )
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    private fun <T> List<T>.jsonArray(fill: JSONObject.(T) -> Unit): JSONArray =
        JSONArray().also { arr -> forEach { row -> arr.put(JSONObject().apply { fill(row) }) } }

    private fun <T> JSONObject.rows(key: String, read: (JSONObject) -> T): List<T> {
        val arr = optJSONArray(key) ?: return emptyList()
        return try {
            (0 until arr.length()).map { read(arr.getJSONObject(it)) }
        } catch (e: JSONException) {
            throw BackupFormatException("The \"$key\" section of this backup is damaged: ${e.message}")
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    // Named to avoid org.json's own putOpt(String, Object), which drops nulls
    // silently — here a null is written explicitly so the field stays visible
    // to anyone reading the file.
    private fun JSONObject.putNullableInt(key: String, value: Int?): JSONObject =
        if (value == null) put(key, JSONObject.NULL) else put(key, value)

    private fun JSONObject.putNullableLong(key: String, value: Long?): JSONObject =
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
}
