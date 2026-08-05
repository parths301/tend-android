package com.tend.app.domain.chat

import com.tend.app.ai.AiAction
import com.tend.app.data.db.ChatMessage

/**
 * The contracts every chat turn flows through, whatever answered it.
 *
 * The point of this file is that "AI mode" and "offline mode" differ in exactly
 * two places — which [AssistantEngine] runs, and which [ChatContextPolicy] picks
 * the context — and nowhere else. Everything downstream (persistence, linked
 * entities, the UI) sees one shape.
 */

/** Which engine the user has chosen. Persisted per thread. */
enum class ChatMode(val stored: String, val label: String) {
    Ai("ai", "AI"),
    Local("local", "Offline"),
    ;

    /**
     * Context each mode gathers by default.
     *
     * Offline is [ChatContextPolicy.SelectedOnly] rather than
     * [ChatContextPolicy.LatestOnly] because the two coincide when the user has
     * marked nothing: "latest message only" is what an empty selection means.
     * One policy covers both halves of the requirement with no branching.
     */
    val defaultPolicy: ChatContextPolicy
        get() = when (this) {
            Ai -> ChatContextPolicy.FullHistory()
            Local -> ChatContextPolicy.SelectedOnly
        }

    companion object {
        fun from(stored: String?): ChatMode =
            entries.firstOrNull { it.stored == stored } ?: Ai

        /**
         * AI mode without a key is not AI mode. Resolving this centrally keeps
         * the fallback out of the UI, so the toggle can never claim a state the
         * pipeline isn't actually in.
         */
        fun effective(selected: ChatMode, hasKey: Boolean): ChatMode =
            if (selected == Ai && !hasKey) Local else selected
    }
}

/** Where an answer came from. Drives the "AI" tag and the message tint. */
enum class ResponseSource(val stored: String) {
    Cloud("cloud"),
    Local("local"),
    System("system"),
    ;

    companion object {
        fun from(stored: String?): ResponseSource =
            entries.firstOrNull { it.stored == stored } ?: System
    }
}

/**
 * A pointer to something the app owns, as stored in `message_links`.
 *
 * Deliberately not a reference to the row itself: the entity may be edited or
 * deleted after the message that created it, and the chat has to cope with both.
 */
data class EntityRef(val type: Type, val id: Long, val label: String) {
    enum class Type(val stored: String, val display: String) {
        Task("task", "Task"),
        Habit("habit", "Habit"),
        Plan("plan", "Plan"),
        ;

        companion object {
            fun from(stored: String?): Type? = entries.firstOrNull { it.stored == stored }
        }
    }
}

/**
 * Which earlier messages accompany the one being sent.
 *
 * Explicit and testable rather than decided inline at the call site, so
 * "what did we actually send?" has a single answer per mode. Selects *prior*
 * context only — the message being sent is always included by the engine.
 */
sealed interface ChatContextPolicy {

    fun selectContext(history: List<ChatMessage>): List<ChatMessage>

    /** Everything recent, capped so a long thread can't blow up the request. */
    data class FullHistory(val maxMessages: Int = DEFAULT_WINDOW) : ChatContextPolicy {
        override fun selectContext(history: List<ChatMessage>): List<ChatMessage> =
            history.takeLast(maxMessages)
    }

    /** Nothing. The turn carries only the message the user just typed. */
    data object LatestOnly : ChatContextPolicy {
        override fun selectContext(history: List<ChatMessage>): List<ChatMessage> = emptyList()
    }

    /** Only messages the user explicitly marked "add to AI context". */
    data object SelectedOnly : ChatContextPolicy {
        override fun selectContext(history: List<ChatMessage>): List<ChatMessage> =
            history.filter { it.inContext }
    }

    companion object {
        const val DEFAULT_WINDOW = 20
    }
}

/** One turn's input, already resolved — engines do not reach back for more. */
data class AssistantRequest(
    val threadId: Long,
    val mode: ChatMode,
    val policy: ChatContextPolicy,
    val userMessage: String,
    /** Prior messages chosen by [policy]; never includes [userMessage]. */
    val contextMessages: List<ChatMessage>,
    val stateSummary: String,
    /**
     * Extra system-prompt text from the active personality. Null when the
     * default personality says nothing; **both** engines honour it, so a
     * personality is not an AI-only feature.
     */
    val personaFragment: String? = null,
    /**
     * The user's raw system-prompt override, empty when they haven't set one.
     * Applied by the cloud engine; the offline rules have no prompt to override.
     */
    val customPrompt: String = "",
)

/**
 * One turn's result, normalized so a cloud reply and a rule-engine reply are
 * indistinguishable to everything downstream.
 *
 * [links] is filled in by [AiExecutionRouter] *after* the actions have been
 * applied and real row ids exist — engines cannot know them.
 */
data class AssistantResponse(
    val reply: String,
    val actions: List<AiAction> = emptyList(),
    val source: ResponseSource,
    val modelId: String? = null,
    val links: List<EntityRef> = emptyList(),
    /** Set when the turn failed; [reply] then holds the user-facing explanation. */
    val error: String? = null,
)

/** The one thing a cloud call needs, resolved by the caller that owns settings. */
data class CloudCredentials(val provider: String, val apiKey: String, val model: String)

/** Anything that can answer a turn. Two implementations; no third path exists. */
fun interface AssistantEngine {
    suspend fun run(request: AssistantRequest): AssistantResponse
}
