package com.tend.app.data.vault

import android.content.Context
import android.net.Uri
import com.tend.app.data.db.AppDatabase
import com.tend.app.data.db.MemoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

/** A decrypted entry. Only ever exists in memory, only while unlocked. */
data class MemoryItem(
    val id: Long,
    val createdAt: Long,
    val kind: String,
    val title: String,
    val body: String,
    val fileName: String,
    val ocrText: String,
    val mime: String,
    val sizeBytes: Long,
    val hasBlob: Boolean,
) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

/**
 * The vault's storage.
 *
 * ## What is encrypted, and where
 *
 * | | |
 * |---|---|
 * | Titles, bodies, captions, filenames, OCR text | AES-GCM under the vault DEK, in `memory_entries` |
 * | Image and file bytes | AES-GCM under the same vault DEK, as raw sealed files in `filesDir/vault/` |
 * | Timestamps, kind, size, MIME | plaintext, so a locked vault can still be listed |
 *
 * Blobs use [VaultCrypto] directly rather than Android's `EncryptedFile` on
 * purpose: `EncryptedFile`'s key lives in the Keystore, which does not survive
 * a reinstall, so a blob sealed under it could never be included in a backup.
 * Sealing with the same password-derived DEK the text fields already use makes
 * a blob exactly as portable as a title — copy the bytes, and whatever unlocks
 * the vault elsewhere unlocks the file too.
 *
 * ## Search, and the tradeoff it makes
 *
 * Encrypted-at-rest and SQL `LIKE` are mutually exclusive: the database cannot
 * match text it cannot read. The two honest options were a blind index (HMAC
 * each token and match on the digests) or decrypting and scanning in memory.
 *
 * **Decrypt-and-scan is used here.** A blind index leaks token equality — an
 * attacker with the database learns which entries share words, and can confirm
 * guesses about content without ever unlocking the vault. Scanning leaks
 * nothing, needs the vault open, and at realistic sizes (hundreds of entries,
 * a few KB of text each) costs single-digit milliseconds off the main thread.
 * If a vault ever grew to the point where that stopped being true, the fix
 * would be an encrypted inverted index, not a blind one.
 *
 * Search covers titles, bodies, filenames and — when the user has enabled it —
 * text OCR'd out of images. It does not search image pixels.
 *
 * ## Isolation from the assistant
 *
 * This class is deliberately **not** a dependency of `AiExecutionRouter` or
 * either `AssistantEngine`. Memory cannot reach the context builder because
 * there is no reference by which it could — the exclusion is a property of the
 * object graph, not a rule someone has to remember.
 */
class MemoryRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val session: VaultSession,
) {

    private val dao = db.memoryDao()

    private val vaultDir: File by lazy { vaultDir(context) }

    /** Ciphertext rows — safe to observe while locked, for a count or a list length. */
    fun rawEntries(): Flow<List<MemoryEntry>> = dao.entries()

    fun count(): Flow<Int> = dao.count()

    // ── writing ─────────────────────────────────────────────────

    suspend fun addText(title: String, body: String): Long? = withContext(Dispatchers.IO) {
        val key = session.requireKey() ?: return@withContext null
        dao.insert(
            MemoryEntry(
                createdAt = System.currentTimeMillis(),
                kind = KIND_TEXT,
                sealedTitle = VaultCrypto.sealText(key, title),
                sealedBody = VaultCrypto.sealText(key, body),
            )
        )
    }

    /**
     * Copies a picked file **into** the vault, encrypted, and forgets the
     * original URI.
     *
     * Keeping a `content://` reference would mean the vault's contents were
     * really sitting unencrypted in the gallery, protected by nothing. Import
     * costs a copy; that is the price of the file actually being in a vault.
     */
    suspend fun addFile(
        uri: Uri,
        displayName: String,
        mime: String,
        caption: String,
        ocrText: String = "",
    ): Long? = withContext(Dispatchers.IO) {
        val key = session.requireKey() ?: return@withContext null
        val blobName = "${UUID.randomUUID()}.bin"
        val target = File(vaultDir, blobName)

        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }
        try {
            target.writeBytes(VaultCrypto.seal(key, bytes))
        } catch (e: Exception) {
            target.delete()
            return@withContext null
        }

        dao.insert(
            MemoryEntry(
                createdAt = System.currentTimeMillis(),
                kind = if (mime.startsWith("image/")) KIND_IMAGE else KIND_FILE,
                sizeBytes = bytes.size.toLong(),
                sealedTitle = VaultCrypto.sealText(key, displayName),
                sealedBody = VaultCrypto.sealText(key, caption),
                sealedFileName = VaultCrypto.sealText(key, displayName),
                sealedOcrText = if (ocrText.isBlank()) "" else VaultCrypto.sealText(key, ocrText),
                blobPath = blobName,
                mime = mime,
            )
        )
    }

    /** Adds OCR text to an existing entry, e.g. after the model is downloaded. */
    suspend fun attachOcrText(id: Long, text: String): Boolean = withContext(Dispatchers.IO) {
        val key = session.requireKey() ?: return@withContext false
        val entry = dao.entryById(id) ?: return@withContext false
        dao.update(entry.copy(sealedOcrText = VaultCrypto.sealText(key, text)))
        true
    }

    // ── reading ─────────────────────────────────────────────────

    suspend fun items(): List<MemoryItem> = withContext(Dispatchers.IO) {
        val key = session.requireKey() ?: return@withContext emptyList()
        dao.entriesOnce().mapNotNull { decrypt(it, key) }
    }

    /**
     * Matches across every decrypted text field. Returns everything when the
     * query is blank, so the same call powers both browsing and searching.
     */
    suspend fun search(query: String): List<MemoryItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val all = items()
        if (q.isEmpty()) all else all.filter { item ->
            item.title.contains(q, true) ||
                item.body.contains(q, true) ||
                item.fileName.contains(q, true) ||
                item.ocrText.contains(q, true) ||
                item.mime.contains(q, true)
        }
    }

    /** Decrypted bytes of an entry's blob, for display. Null when locked. */
    suspend fun readBlob(id: Long): ByteArray? = withContext(Dispatchers.IO) {
        val key = session.requireKey() ?: return@withContext null
        val entry = dao.entryById(id) ?: return@withContext null
        if (entry.blobPath.isEmpty()) return@withContext null
        val file = File(vaultDir, entry.blobPath)
        if (!file.exists()) return@withContext null
        try {
            VaultCrypto.open(key, file.readBytes())
        } catch (e: Exception) {
            null
        }
    }

    // ── deleting ────────────────────────────────────────────────

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.entryById(id)?.let { entry ->
            if (entry.blobPath.isNotEmpty()) File(vaultDir, entry.blobPath).delete()
        }
        dao.delete(id)
    }

    /**
     * Wipes the vault: rows, blobs, then the key material.
     *
     * Order matters only for tidiness — once [VaultSession.destroy] drops the
     * wrapped keys, anything left behind is undecryptable by anyone.
     */
    suspend fun destroyEverything() = withContext(Dispatchers.IO) {
        dao.clear()
        vaultDir.listFiles()?.forEach { it.delete() }
        session.destroy()
    }

    // ── backup / restore ────────────────────────────────────────
    //
    // Both sides of a backup move ciphertext only — rows and blob bytes are
    // already sealed under the vault DEK on disk, so neither export nor
    // import needs the vault unlocked. What makes the copy usable again is
    // [VaultSession]'s key material travelling in the same backup.

    /** Every row, ciphertext and all — the same shape a backup embeds. */
    suspend fun entriesForBackup(): List<MemoryEntry> = withContext(Dispatchers.IO) { dao.entriesOnce() }

    /** The sealed bytes of a blob exactly as stored, for [entriesForBackup] rows that have one. */
    suspend fun rawBlob(blobPath: String): ByteArray? = withContext(Dispatchers.IO) {
        if (blobPath.isEmpty()) return@withContext null
        val file = File(vaultDir, blobPath)
        if (!file.exists()) return@withContext null
        try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    /** Replaces every entry and blob with what a backup captured. */
    suspend fun restoreFromBackup(entries: List<MemoryEntry>, blobs: Map<String, ByteArray>) =
        withContext(Dispatchers.IO) {
            dao.clear()
            vaultDir.listFiles()?.forEach { it.delete() }
            if (entries.isNotEmpty()) dao.insertAll(entries)
            blobs.forEach { (path, bytes) -> File(vaultDir, path).writeBytes(bytes) }
        }

    private fun decrypt(entry: MemoryEntry, key: SecretKey): MemoryItem? {
        // A row that will not decrypt is corrupt or from a destroyed vault;
        // skipping it is better than showing the user mojibake.
        val title = VaultCrypto.openText(key, entry.sealedTitle) ?: return null
        return MemoryItem(
            id = entry.id,
            createdAt = entry.createdAt,
            kind = entry.kind,
            title = title,
            body = entry.sealedBody.takeIf { it.isNotEmpty() }
                ?.let { VaultCrypto.openText(key, it) }.orEmpty(),
            fileName = entry.sealedFileName.takeIf { it.isNotEmpty() }
                ?.let { VaultCrypto.openText(key, it) }.orEmpty(),
            ocrText = entry.sealedOcrText.takeIf { it.isNotEmpty() }
                ?.let { VaultCrypto.openText(key, it) }.orEmpty(),
            mime = entry.mime,
            sizeBytes = entry.sizeBytes,
            hasBlob = entry.blobPath.isNotEmpty(),
        )
    }

    companion object {
        const val KIND_TEXT = "text"
        const val KIND_IMAGE = "image"
        const val KIND_FILE = "file"

        /** Where blobs live, exposed so a caller with no repository instance (backup) can find them. */
        fun vaultDir(context: Context): File = File(context.filesDir, "vault").apply { mkdirs() }
    }
}
