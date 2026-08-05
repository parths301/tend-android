package com.tend.app.data

import com.tend.app.data.db.AppDatabase
import com.tend.app.data.db.Attachment
import com.tend.app.data.db.ChatMessage
import com.tend.app.data.db.ChatThread
import com.tend.app.data.db.MessageLink
import com.tend.app.domain.chat.ChatMode
import com.tend.app.domain.chat.EntityRef
import com.tend.app.domain.chat.ResponseSource
import kotlinx.coroutines.flow.Flow

/**
 * Everything the chat persists.
 *
 * "New chat" creates a row; "clear chat" empties one and keeps it. Those are
 * genuinely different operations here, not two names for resetting a list in
 * memory, which is what made the old chat lose everything on process death.
 */
class ChatRepository(private val db: AppDatabase) {

    private val dao = db.chatDao()

    // ── threads ──────────────────────────────────────────────────

    fun threads(): Flow<List<ChatThread>> = dao.threads()

    suspend fun threadById(id: Long): ChatThread? = dao.threadById(id)

    /**
     * The thread to show on open: the most recent one, or a fresh one if this is
     * the first launch since the chat gained history.
     */
    suspend fun mostRecentOrNew(mode: ChatMode): Long =
        dao.mostRecentThread()?.id ?: newThread(mode)

    suspend fun newThread(mode: ChatMode, title: String = UNTITLED): Long {
        val now = System.currentTimeMillis()
        return dao.insertThread(
            ChatThread(
                title = title,
                createdAt = now,
                updatedAt = now,
                mode = mode.stored,
            )
        )
    }

    suspend fun renameThread(threadId: Long, title: String) =
        dao.setTitle(threadId, title.trim().ifEmpty { UNTITLED })

    suspend fun setPinned(threadId: Long, pinned: Boolean) = dao.setPinned(threadId, pinned)

    suspend fun setMode(threadId: Long, mode: ChatMode) =
        dao.setMode(threadId, mode.stored, System.currentTimeMillis())

    /** Empties a thread's messages but keeps the thread. */
    suspend fun clearMessages(threadId: Long) {
        val ids = dao.messagesForOnce(threadId).map { it.id }
        dao.deleteAttachmentsFor(OWNER_MESSAGE, ids)
        dao.clearMessagesFor(threadId)   // links cascade
        dao.touchThread(threadId, System.currentTimeMillis())
    }

    /** Removes the thread entirely, with its messages, links and attachments. */
    suspend fun deleteThread(threadId: Long) {
        val ids = dao.messagesForOnce(threadId).map { it.id }
        dao.deleteAttachmentsFor(OWNER_MESSAGE, ids)
        dao.deleteThread(threadId)       // messages and links cascade
    }

    suspend fun saveDraft(threadId: Long, draft: String) = dao.setDraft(threadId, draft)

    /** Thread ids matching a query in their title or any message body. */
    suspend fun searchThreads(query: String): List<Long> {
        val q = query.trim()
        return if (q.isEmpty()) emptyList() else dao.searchThreadIds(q)
    }

    // ── messages ─────────────────────────────────────────────────

    fun messages(threadId: Long): Flow<List<ChatMessage>> = dao.messagesFor(threadId)

    fun links(threadId: Long): Flow<List<MessageLink>> = dao.linksForThread(threadId)

    fun attachments(threadId: Long): Flow<List<Attachment>> = dao.attachmentsForThread(threadId)

    suspend fun historyFor(threadId: Long): List<ChatMessage> = dao.messagesForOnce(threadId)

    /**
     * Appends a message and, if it created anything, its links — then titles the
     * thread from the first user message so the history list is readable without
     * the user naming anything.
     */
    suspend fun append(
        threadId: Long,
        fromAi: Boolean,
        text: String,
        source: ResponseSource,
        modelId: String? = null,
        links: List<EntityRef> = emptyList(),
    ): Long {
        val now = System.currentTimeMillis()
        val isFirst = dao.messageCount(threadId) == 0
        val messageId = dao.insertMessage(
            ChatMessage(
                threadId = threadId,
                fromAi = fromAi,
                text = text,
                createdAt = now,
                source = source.stored,
                modelId = modelId,
            )
        )
        if (links.isNotEmpty()) {
            dao.insertLinks(
                links.map {
                    MessageLink(
                        messageId = messageId,
                        entityType = it.type.stored,
                        entityId = it.id,
                        label = it.label,
                        createdAt = now,
                    )
                }
            )
        }
        dao.touchThread(threadId, now)
        if (isFirst && !fromAi) autoTitle(threadId, text)
        return messageId
    }

    suspend fun setInContext(messageId: Long, inContext: Boolean) =
        dao.setInContext(messageId, inContext)

    suspend fun clearContext(threadId: Long) = dao.clearContextFor(threadId)

    /**
     * Deletes messages only.
     *
     * Anything they created is left alone: a task the user still has on their
     * list must not vanish because they tidied up the conversation. The link
     * rows go with the message, so the chip disappears while the task remains.
     */
    suspend fun deleteMessages(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteAttachmentsFor(OWNER_MESSAGE, ids)
        dao.deleteMessages(ids)
    }

    // ── attachments ──────────────────────────────────────────────

    suspend fun attachTo(messageId: Long, attachments: List<Attachment>) {
        if (attachments.isEmpty()) return
        dao.insertAttachments(attachments.map { it.copy(ownerType = OWNER_MESSAGE, ownerId = messageId) })
    }

    private suspend fun autoTitle(threadId: Long, firstMessage: String) {
        val title = firstMessage.trim().lineSequence().firstOrNull().orEmpty()
            .take(TITLE_MAX)
            .trim()
            .ifEmpty { return }
        dao.setTitle(threadId, if (firstMessage.length > TITLE_MAX) "$title…" else title)
    }

    companion object {
        const val UNTITLED = "New chat"
        const val OWNER_MESSAGE = "message"
        private const val TITLE_MAX = 40
    }
}
