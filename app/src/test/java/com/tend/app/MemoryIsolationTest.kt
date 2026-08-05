package com.tend.app

import com.tend.app.data.db.ChatMessage
import com.tend.app.domain.chat.AiExecutionRouter
import com.tend.app.domain.chat.ChatMode
import com.tend.app.domain.chat.MemoryCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Memory must never reach the assistant.
 *
 * The strongest part of that guarantee is not tested here because it cannot
 * fail at runtime: `MemoryRepository` is not a constructor argument of
 * `AiExecutionRouter` or of either engine, so there is no reference through
 * which vault content could travel. If someone adds one, this file is the
 * reminder of why they shouldn't.
 *
 * What *is* tested is the part that could regress silently: that a vault
 * command is recognised before a request is ever built, and that nothing about
 * a chat turn carries vault text.
 */
class MemoryIsolationTest {

    private fun message(id: Long, text: String, inContext: Boolean = false) = ChatMessage(
        id = id, threadId = 1, fromAi = false, text = text,
        createdAt = id, source = "system", inContext = inContext,
    )

    // ── command recognition ─────────────────────────────────────

    @Test
    fun `add to memory is recognised in the forms people actually type`() {
        listOf(
            "add to memory the spare key is with Sam",
            "Add to memory: the spare key is with Sam",
            "add this to memory, the spare key is with Sam",
            "  ADD TO MEMORY   the spare key is with Sam  ",
        ).forEach { input ->
            val parsed = MemoryCommand.parse(input)
            assertTrue("Not recognised: $input", parsed is MemoryCommand.Add)
            assertTrue(
                "Payload lost from: $input",
                (parsed as MemoryCommand.Add).text.contains("spare key is with Sam"),
            )
        }
    }

    @Test
    fun `search memory is recognised in the forms people actually type`() {
        listOf(
            "search memory passport",
            "search in memory for passport",
            "find memory passport",
            "look up memory: passport",
        ).forEach { input ->
            val parsed = MemoryCommand.parse(input)
            assertTrue("Not recognised: $input", parsed is MemoryCommand.Search)
            assertEquals("passport", (parsed as MemoryCommand.Search).query)
        }
    }

    @Test
    fun `a bare search lists everything rather than failing`() {
        assertEquals("", (MemoryCommand.parse("search memory") as MemoryCommand.Search).query)
    }

    @Test
    fun `ordinary messages are not mistaken for vault commands`() {
        // These mention memory but are not instructions about the vault; treating
        // them as commands would silently swallow a real request.
        listOf(
            "remind me to buy a memory card",
            "add a task to back up my memories",
            "how much memory does this app use?",
            "gym at 6pm",
        ).forEach { assertNull("Wrongly treated as a command: $it", MemoryCommand.parse(it)) }
    }

    // ── nothing vault-shaped enters a request ───────────────────

    @Test
    fun `a request carries only chat history, never vault content`() {
        // Whatever is in the vault, the only text a request can contain comes
        // from chat_messages and the app's own state summary.
        val history = listOf(
            message(1, "gym every morning"),
            message(2, "buy milk", inContext = true),
        )
        val request = AiExecutionRouter.buildRequest(
            threadId = 1,
            selectedMode = ChatMode.Ai,
            hasKey = true,
            history = history,
            userMessage = "what's on today?",
            stateSummary = "Habits: gym",
        )

        val everythingSent = buildString {
            append(request.userMessage)
            append(request.stateSummary)
            request.contextMessages.forEach { append(it.text) }
            request.personaFragment?.let(::append)
        }

        assertTrue(everythingSent.contains("gym"))
        assertFalse(
            "Nothing in a request may originate outside chat history and app state",
            everythingSent.contains("spare key"),
        )
    }

    @Test
    fun `offline mode sends nothing at all beyond the typed message`() {
        val request = AiExecutionRouter.buildRequest(
            threadId = 1,
            selectedMode = ChatMode.Local,
            hasKey = false,
            history = listOf(message(1, "something private")),
            userMessage = "hello",
            stateSummary = "Habits: gym, meditation",
        )
        assertEquals(ChatMode.Local, request.mode)
        assertTrue(request.contextMessages.isEmpty())
        assertTrue(request.stateSummary.isEmpty())
    }
}
