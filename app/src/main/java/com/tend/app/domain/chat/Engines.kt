package com.tend.app.domain.chat

import com.tend.app.ai.AiClient
import com.tend.app.ai.AiProtocol
import com.tend.app.data.SettingsRepository

/**
 * The offline engine: deterministic rules, no model, no network.
 *
 * Structured as an [AssistantEngine] rather than an `if` branch so a real
 * on-device model can replace it later by implementing the same interface —
 * nothing upstream or downstream would change.
 */
class LocalAssistantEngine : AssistantEngine {

    override suspend fun run(request: AssistantRequest): AssistantResponse {
        val result = AiProtocol.simulate(resolveInput(request))
        return AssistantResponse(
            reply = result.reply,
            actions = result.actions,
            source = ResponseSource.Local,
        )
    }

    /**
     * What the rules actually read.
     *
     * Normally the message just typed. But if that message is purely
     * referential — "add that", "same again" — and the user has marked earlier
     * messages as context, the most recent marked message is what they meant.
     * This is the one place offline mode uses the selection, and it is why
     * "add to AI context" is not a no-op when there is no AI.
     */
    private fun resolveInput(request: AssistantRequest): String {
        if (!referentialRegex.matches(request.userMessage.trim())) return request.userMessage
        val referent = request.contextMessages.lastOrNull { !it.fromAi } ?: return request.userMessage
        return referent.text
    }

    private companion object {
        val referentialRegex =
            Regex("""(?i)^\s*(add|do|make|schedule)?\s*(that|it|this|the same|same)\s*(one|again)?\s*[.!]?\s*$""")
    }
}

/**
 * The cloud engine: whichever BYOK provider is configured.
 *
 * Credentials arrive through a supplier rather than being read here, because
 * resolving them involves settings *and* the healing step that swaps a retired
 * model for a working one. That belongs with the code that owns the model list.
 */
class CloudAssistantEngine(
    private val ai: AiClient,
    private val credentials: suspend () -> CloudCredentials?,
) : AssistantEngine {

    override suspend fun run(request: AssistantRequest): AssistantResponse {
        val creds = credentials() ?: return AssistantResponse(
            reply = "No API key is configured, so this went to offline mode instead.",
            source = ResponseSource.System,
            error = "missing credentials",
        )

        // Context first, then the message being sent. The engine appends it so
        // the policy never has to reason about the turn it is part of.
        val history = request.contextMessages.map { it.fromAi to it.text } +
            (false to request.userMessage)

        val system = buildString {
            append(AiProtocol.systemPrompt(request.stateSummary))
            request.personaFragment?.takeIf { it.isNotBlank() }?.let {
                append("\n\nAdditional style guidance:\n")
                append(it)
            }
        }

        return try {
            val raw = ai.complete(creds.provider, creds.apiKey, creds.model, system, history)
            // A model that ignored the JSON contract still said something useful;
            // show it rather than failing the turn.
            val parsed = AiProtocol.parse(raw)
            AssistantResponse(
                reply = parsed?.reply ?: raw.take(500),
                actions = parsed?.actions.orEmpty(),
                source = ResponseSource.Cloud,
                modelId = creds.model,
            )
        } catch (e: Exception) {
            val label = SettingsRepository.providerLabel(creds.provider)
            AssistantResponse(
                reply = "$label request failed: ${e.message?.take(120) ?: "network error"}. " +
                    "Check your key and model in Settings, then try again.",
                source = ResponseSource.System,
                modelId = creds.model,
                error = e.message ?: "network error",
            )
        }
    }
}
