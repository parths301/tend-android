package com.tend.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.tend.app.BuildConfig
import com.tend.app.data.SettingsRepository
import com.tend.app.data.db.AppDatabase
import com.tend.app.notif.ReminderScheduler
import com.tend.app.widget.TendWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class BackupException(message: String) : Exception(message)

/** What a completed backup wrote, for the UI to report. */
data class BackupResult(val fileName: String, val rows: Int, val bytes: Int)

/** Turns any backup failure into something worth showing a person. */
fun Exception.readableMessage(): String = when (this) {
    is BackupException -> message ?: "Backup failed."
    is BackupFormatException -> message ?: "That backup couldn't be read."
    else -> message?.take(140) ?: "${this::class.java.simpleName} while writing the backup"
}

/**
 * Reads and writes Tend's on-device backup files.
 *
 * Backups go to a folder the user picks once through the system file picker,
 * so the file lands somewhere they can actually find — Documents, Downloads, a
 * synced Drive folder — and stays there after the app is uninstalled. Tend
 * holds a persisted permission grant for exactly that folder and nothing else.
 */
object BackupManager {

    private val FILE_STAMP: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ENGLISH)

    // ── reading the app's state ─────────────────────────────────

    suspend fun snapshot(context: Context): BackupSnapshot = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        BackupSnapshot(
            createdAtMillis = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            habits = db.habitDao().habitsOnce(),
            logs = db.habitDao().logsOnce(),
            tasks = db.taskDao().tasksOnce(),
            plans = db.planDao().allOnce(),
            notes = db.noteDao().allOnce(),
            settings = SettingsRepository(context).snapshot(),
            threads = db.chatDao().threadsOnce(),
            messages = db.chatDao().allMessagesOnce(),
            links = db.chatDao().allLinksOnce(),
        )
    }

    /**
     * A readable name for a picked folder, derived from the tree URI itself so
     * the UI never has to touch storage to draw a label.
     */
    fun folderLabel(treeUri: String): String {
        if (treeUri.isBlank()) return ""
        val decoded = percentDecode(treeUri.substringAfter("/tree/", "")).ifBlank { return treeUri }
        // Storage-provider ids look like "primary:Documents/Tend"; the Downloads
        // provider just says "downloads".
        if (!decoded.contains(':')) {
            return decoded.replaceFirstChar { it.uppercaseChar() }
        }
        val volume = decoded.substringBefore(':')
        val path = decoded.substringAfter(':').trim('/')
        val volumeLabel = when {
            volume.equals("primary", ignoreCase = true) -> ""
            volume.equals("home", ignoreCase = true) -> "Documents"
            else -> "SD card"
        }
        return listOf(volumeLabel, path)
            .filter { it.isNotBlank() }
            .joinToString("/")
            .ifBlank { "Internal storage" }
    }

    /**
     * Percent-decoding by hand rather than through `Uri.decode`, so labelling a
     * folder stays a pure string operation the JVM tests can exercise. Unlike
     * `URLDecoder` it leaves "+" alone, which is a legal character in a path.
     */
    private fun percentDecode(encoded: String): String {
        if ('%' !in encoded) return encoded
        // Bytes, not chars: a non-ASCII folder name arrives as several escapes
        // that only mean something once decoded together as UTF-8.
        val out = java.io.ByteArrayOutputStream(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            val byte = if (c == '%' && i + 2 < encoded.length) {
                encoded.substring(i + 1, i + 3).toIntOrNull(16)
            } else {
                null
            }
            if (byte != null) {
                out.write(byte)
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun fileNameFor(atMillis: Long): String {
        val stamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault())
        return BackupFormat.FILE_PREFIX + FILE_STAMP.format(stamp) + BackupFormat.FILE_SUFFIX
    }

    // ── writing ─────────────────────────────────────────────────

    /**
     * Writes one backup into the chosen folder and prunes the oldest files
     * beyond [SettingsRepository.backupKeep]. Records the outcome either way,
     * so Settings can always show what happened last.
     */
    suspend fun backupNow(context: Context): BackupResult = withContext(Dispatchers.IO) {
        val settings = SettingsRepository(context)
        val at = System.currentTimeMillis()
        try {
            val folder = settings.backupFolder.first()
            if (folder.isBlank()) {
                throw BackupException("Choose a backup folder first.")
            }
            val dir = resolveFolder(context, folder)
            val snapshot = snapshot(context)
            val json = BackupFormat.encode(snapshot)
            val name = fileNameFor(at)

            // Same-minute reruns would otherwise pile up "(1)" copies.
            dir.findFile(name)?.delete()
            val target = dir.createFile(BackupFormat.MIME, name)
                ?: throw BackupException("Couldn't create a file in the backup folder.")

            val bytes = json.toByteArray()
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(bytes) }
                ?: throw BackupException("Couldn't open the backup file for writing.")

            prune(dir, settings.backupKeep.first())
            settings.recordBackupResult(at, name, "")
            BackupResult(name, snapshot.rowCount, bytes.size)
        } catch (e: Exception) {
            settings.recordBackupResult(at, "", e.readableMessage())
            throw e
        }
    }

    /** Writes a backup to the URI the user picked in a "save as" dialog. */
    suspend fun exportTo(context: Context, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val snapshot = snapshot(context)
        val bytes = BackupFormat.encode(snapshot).toByteArray()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw BackupException("Couldn't write to that location.")
        BackupResult(
            DocumentFile.fromSingleUri(context, uri)?.name ?: fileNameFor(snapshot.createdAtMillis),
            snapshot.rowCount,
            bytes.size,
        )
    }

    private fun resolveFolder(context: Context, treeUri: String): DocumentFile {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: throw BackupException("The backup folder is no longer reachable — choose it again.")
        if (!dir.exists() || !dir.canWrite()) {
            throw BackupException("Tend can't write to the backup folder any more — choose it again.")
        }
        return dir
    }

    /** Keeps the [keep] newest backups; the timestamped name sorts chronologically. */
    private fun prune(dir: DocumentFile, keep: Int) {
        dir.listFiles()
            .filter {
                val n = it.name.orEmpty()
                n.startsWith(BackupFormat.FILE_PREFIX) && n.endsWith(BackupFormat.FILE_SUFFIX)
            }
            .sortedByDescending { it.name.orEmpty() }
            .drop(keep.coerceAtLeast(1))
            .forEach { runCatching { it.delete() } }
    }

    // ── reading a backup back in ────────────────────────────────

    suspend fun read(context: Context, uri: Uri): BackupSnapshot = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw BackupException("Couldn't open that file.")
        if (text.isBlank()) throw BackupException("That file is empty.")
        BackupFormat.decode(text)
    }

    /**
     * Replaces everything in the app with [snapshot]. Destructive by design —
     * a restore that merged would double up every habit log. Runs in a single
     * transaction so a failure part-way leaves the old data intact.
     */
    suspend fun restore(context: Context, snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        val clean = BackupFormat.sanitize(snapshot)
        val db = AppDatabase.get(context)

        db.withTransaction {
            // Attachments and chat first: messages cascade from threads, so the
            // rows must go before the tables they point at are rebuilt.
            db.chatDao().clearAttachments()
            db.chatDao().clearThreads()
            db.noteDao().clearNotes()
            db.planDao().clearPlans()
            db.taskDao().clearTasks()
            db.habitDao().clearLogs()
            db.habitDao().clearHabits()

            db.habitDao().insertHabits(clean.habits)
            db.habitDao().insertLogs(clean.logs)
            db.taskDao().insertAll(clean.tasks)
            db.planDao().insertAll(clean.plans)
            db.noteDao().insertAll(clean.notes)
            db.chatDao().insertThreads(clean.threads)
            db.chatDao().insertMessages(clean.messages)
            db.chatDao().insertLinks(clean.links)
        }

        clean.settings?.let { SettingsRepository(context).applySnapshot(it) }

        // Everything downstream of the data has to catch up.
        TendWidgets.refresh(context)
        ReminderScheduler.reschedule(context)
    }

    // ── one-off share ───────────────────────────────────────────

    /** Writes a backup to app cache and returns a share intent for it. */
    suspend fun shareIntent(context: Context): Intent = withContext(Dispatchers.IO) {
        val snapshot = snapshot(context)
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val file = java.io.File(dir, fileNameFor(snapshot.createdAtMillis))
        file.writeText(BackupFormat.encode(snapshot))
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "com.tend.app.fileprovider", file,
        )
        Intent(Intent.ACTION_SEND).apply {
            type = BackupFormat.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
