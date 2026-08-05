package com.tend.app

import com.tend.app.ai.AiAction
import com.tend.app.data.db.ChatMessage
import com.tend.app.domain.chat.ActionApplier
import com.tend.app.domain.chat.AiExecutionRouter
import com.tend.app.domain.chat.AssistantEngine
import com.tend.app.domain.chat.AssistantRequest
import com.tend.app.domain.chat.AssistantResponse
import com.tend.app.domain.chat.ChatContextPolicy
import com.tend.app.domain.chat.ChatMode
import com.tend.app.domain.chat.EntityRef
import com.tend.app.domain.chat.LocalAssistantEngine
import com.tend.app.domain.chat.ResponseSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guarantees that make one pipeline worth having: what leaves the device is
 * decided in exactly one place, and offline mode is not a lesser citizen.
 */
class ChatPipelineTest {

    private var nextId = 100L

    private fun msg(
        id: Long,
        text: String,
        fromAi: Boolean = false,
        inContext: Boolean = false,
    ) = ChatMessage(
        id = id,
        threadId = 1L,
        fromAi = fromAi,
        text = text,
        createdAt = id,
        source = if (fromAi) ResponseSource.Cloud.stored else ResponseSource.System.stored,
        inContext = inContext,
    )

    private val history = listOf(
        msg(1, "gym every morning"),
        msg(2, "Created the habit.", fromAi = true),
        msg(3, "buy milk", inContext = true),
        msg(4, "Added a task.", fromAi = true),
    )

    /** Records what it was asked to create, and hands back plausible ids. */
    private fun recordingApplier(into: MutableList<AiAction>) = ActionApplier { action ->
        into += action
        when (action) {
            is AiAction.AddTask -> EntityRef(EntityRef.Type.Task, nextId++, action.title)
            is AiAction.AddHabit -> EntityRef(EntityRef.Type.Habit, nextId++, action.name)
            is AiAction.AddPlanBlock -> EntityRef(EntityRef.Type.Plan, nextId++, action.title)
        }
    }

    private fun engineReturning(vararg actions: AiAction) = AssistantEngine {
        AssistantResponse("ok", actions.toList(), ResponseSource.Cloud)
    }

    // ── context policy ──────────────────────────────────────────

    @Test
    fun `offline mode sends no history by default`() {
        val request = AiExecutionRouter.buildRequest(
            threadId = 1, selectedMode = ChatMode.Local, hasKey = true,
            history = history.map { it.copy(inContext = false) },
            userMessage = "add pushups", stateSummary = "…",
        )
        assertEquals(ChatContextPolicy.SelectedOnly, request.policy)
        assertTrue(
            "Nothing was marked, so nothing may travel with the message",
            request.contextMessages.isEmpty(),
        )
    }

    @Test
    fun `offline mode sends exactly the messages marked as context`() {
        val request = AiExecutionRouter.buildRequest(
            threadId = 1, selectedMode = ChatMode.Local, hasKey = true,
            history = history, userMessage = "add that", stateSummary = "…",
        )
        assertEquals(listOf(3L), request.contextMessages.map { it.id })
    }

    @Test
    fun `ai mode sends recent history, capped`() {
        val long = (1..50L).map { msg(it, "message $it") }
        val request = AiExecutionRouter.buildRequest(
            threadId = 1, selectedMode = ChatMode.Ai, hasKey = true,
            history = long, userMessage = "hello", stateSummary = "…",
        )
        assertEquals(ChatContextPolicy.DEFAULT_WINDOW, request.contextMessages.size)
        // The cap keeps the *most recent* window, not the oldest.
        assertEquals(50L, request.contextMessages.last().id)
    }

    @Test
    fun `the message being sent is never duplicated into its own context`() {
        val request = AiExecutionRouter.buildRequest(
            threadId = 1, selectedMode = ChatMode.Ai, hasKey = true,
            history = history, userMessage = "buy milk", stateSummary = "…",
        )
        assertEquals(history.size, request.contextMessages.size)
        assertFalse(request.contextMessages.any { it.id == 0L })
    }

    // ── mode resolution ─────────────────────────────────────────

    @Test
    fun `ai mode without a key resolves to offline`() {
        assertEquals(ChatMode.Local, ChatMode.effective(ChatMode.Ai, hasKey = false))
        assertEquals(ChatMode.Ai, ChatMode.effective(ChatMode.Ai, hasKey = true))
        // Choosing offline is a choice; a key does not override it.
        assertEquals(ChatMode.Local, ChatMode.effective(ChatMode.Local, hasKey = true))
    }

    @Test
    fun `offline turns carry no state summary`() {
        val request = AiExecutionRouter.buildRequest(
            threadId = 1, selectedMode = ChatMode.Ai, hasKey = false,
            history = history, userMessage = "hi", stateSummary = "habits: gym, reading",
        )
        assertEquals(ChatMode.Local, request.mode)
        assertTrue(
            "Nothing leaves the device offline, so the summary should not be built in",
            request.stateSummary.isEmpty(),
        )
    }

    // ── linked entities ─────────────────────────────────────────

    @Test
    fun `router links every entity an engine creates`() = runBlocking {
        val applied = mutableListOf<AiAction>()
        val router = AiExecutionRouter(
            cloud = engineReturning(
                AiAction.AddTask("Buy milk", "PERSONAL"),
                AiAction.AddHabit("Pushups", "Fitness", "Daily"),
            ),
            local = engineReturning(),
            applier = recordingApplier(applied),
        )
        val response = router.run(
            AiExecutionRouter.buildRequest(
                threadId = 1, selectedMode = ChatMode.Ai, hasKey = true,
                history = emptyList(), userMessage = "x", stateSummary = "",
            )
        )
        assertEquals(2, applied.size)
        assertEquals(2, response.links.size)
        assertEquals(
            listOf(EntityRef.Type.Task, EntityRef.Type.Habit),
            response.links.map { it.type },
        )
        assertTrue("Links must carry real ids", response.links.all { it.id > 0 })
    }

    @Test
    fun `offline mode links entities exactly as cloud mode does`() = runBlocking {
        // The requirement is parity, so assert on the shapes rather than trusting
        // that both paths happen to call the same applier today.
        val cloudApplied = mutableListOf<AiAction>()
        val localApplied = mutableListOf<AiAction>()

        suspend fun linksFor(mode: ChatMode, applied: MutableList<AiAction>) = AiExecutionRouter(
            cloud = LocalAssistantEngine(),
            local = LocalAssistantEngine(),
            applier = recordingApplier(applied),
        ).run(
            AiExecutionRouter.buildRequest(
                threadId = 1, selectedMode = mode, hasKey = true,
                history = emptyList(), userMessage = "buy groceries", stateSummary = "",
            )
        ).links

        nextId = 100L
        val cloudLinks = linksFor(ChatMode.Ai, cloudApplied)
        nextId = 100L
        val localLinks = linksFor(ChatMode.Local, localApplied)

        assertEquals(cloudApplied, localApplied)
        assertEquals(cloudLinks, localLinks)
        assertTrue(localLinks.isNotEmpty())
    }

    // ── the offline engine ──────────────────────────────────────

    @Test
    fun `offline engine creates a task for a plain request`() = runBlocking {
        val response = LocalAssistantEngine().run(
            AssistantRequest(1, ChatMode.Local, ChatContextPolicy.SelectedOnly, "buy groceries", emptyList(), "")
        )
        assertEquals(ResponseSource.Local, response.source)
        assertNotNull(response.actions.filterIsInstance<AiAction.AddTask>().firstOrNull())
    }

    @Test
    fun `a referential message resolves against the marked context`() = runBlocking {
        // "add that" alone would become a task literally titled "That". With a
        // marked habit-shaped message it becomes the habit the user meant — this
        // is what stops "add to AI context" being a no-op with no AI present.
        val engine = LocalAssistantEngine()
        val marked = listOf(msg(7, "meditate every day", inContext = true))

        val withContext = engine.run(
            AssistantRequest(1, ChatMode.Local, ChatContextPolicy.SelectedOnly, "add that", marked, "")
        )
        assertTrue(
            "Expected the marked message to drive the action",
            withContext.actions.any { it is AiAction.AddHabit },
        )

        val without = engine.run(
            AssistantRequest(1, ChatMode.Local, ChatContextPolicy.SelectedOnly, "add that", emptyList(), "")
        )
        assertFalse(without.actions.any { it is AiAction.AddHabit })
    }
}
