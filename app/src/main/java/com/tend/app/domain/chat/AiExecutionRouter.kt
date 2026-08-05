package com.tend.app.domain.chat

import com.tend.app.ai.AiAction
import com.tend.app.ai.AiProtocol
import com.tend.app.data.db.ChatMessage

/**
 * Applies an action to the user's real data and reports what it created.
 *
 * Returning an [EntityRef] is what makes chat-created items clickable: the row
 * ids already come back from the DAOs, they were simply being discarded. A null
 * means the action produced nothing worth linking to.
 */
fun interface ActionApplier {
    suspend fun apply(action: AiAction): EntityRef?
}

/**
 * The only place a chat turn is dispatched.
 *
 * Both engines run through here, so linked entities, response shape and error
 * handling cannot drift apart between AI and offline mode — there is no second
 * code path where they could.
 */


class AiExecutionRouter(
    private val cloud: AssistantEngine,
    private val local: AssistantEngine,
    private val applier: ActionApplier,
) {

    suspend fun run(request: AssistantRequest): AssistantResponse {
        val engine = when (request.mode) {
            ChatMode.Ai -> cloud
            ChatMode.Local -> local
        }
        val response = engine.run(request)

        // Filter out actions containing sensitive identifiers before applying
        val safeActions = response.actions.filterNot { action ->
            when (action) {
                is AiAction.AddTask -> AiProtocol.isSensitive(action.title)
                is AiAction.AddPlanBlock -> AiProtocol.isSensitive(action.title)
                is AiAction.AddHabit -> AiProtocol.isSensitive(action.name)
            }
        }

        // Apply first, then link: an EntityRef is only honest once the row exists.
        val links = safeActions.mapNotNull { applier.apply(it) }
        return response.copy(actions = safeActions, links = links)
    }


    companion object {
        /**
         * Resolves mode, policy and context in one step.
         *
         * Kept together because they are one decision: the mode determines the
         * policy, and the policy determines what leaves the device. Splitting
         * them across call sites is how "what did we actually send?" stops
         * having a single answer.
         */
        fun buildRequest(
            threadId: Long,
            selectedMode: ChatMode,
            hasKey: Boolean,
            history: List<ChatMessage>,
            userMessage: String,
            stateSummary: String,
            personaFragment: String? = null,
            customPrompt: String = "",
            historyWindow: Int = ChatContextPolicy.DEFAULT_WINDOW,
        ): AssistantRequest {
            val mode = ChatMode.effective(selectedMode, hasKey)
            // The window is user-tunable, but only AI mode has history to window;
            // offline's policy ignores it entirely.
            val policy = when (mode) {
                ChatMode.Ai -> ChatContextPolicy.FullHistory(historyWindow)
                ChatMode.Local -> ChatContextPolicy.SelectedOnly
            }
            return AssistantRequest(
                threadId = threadId,
                mode = mode,
                policy = policy,
                userMessage = userMessage,
                contextMessages = policy.selectContext(history),
                // Offline mode never sends anything anywhere, so a state summary
                // would be pointless work; it is also one less thing to leak if
                // a future local model is ever swapped in.
                stateSummary = if (mode == ChatMode.Ai) stateSummary else "",
                personaFragment = personaFragment,
                customPrompt = customPrompt,
            )
        }
    }
}
