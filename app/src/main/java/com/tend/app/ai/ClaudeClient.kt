package com.tend.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam

/**
 * Thin wrapper around the official Anthropic Java SDK for the BYOK path.
 * A client is cached per API key so multi-turn chats reuse the connection pool.
 */
class ClaudeClient {
    private var cachedKey: String? = null
    private var cachedClient: AnthropicClient? = null

    private fun clientFor(apiKey: String): AnthropicClient {
        val existing = cachedClient
        if (existing != null && cachedKey == apiKey) return existing
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        cachedKey = apiKey
        cachedClient = client
        return client
    }

    /**
     * Sends the conversation and returns Claude's text reply.
     * [history] is (isAssistant, text) pairs in chronological order.
     */
    fun complete(
        apiKey: String,
        model: String,
        system: String,
        history: List<Pair<Boolean, String>>,
    ): String {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(1024L)
            .system(system)
        for ((isAssistant, text) in history) {
            val role = if (isAssistant) MessageParam.Role.ASSISTANT else MessageParam.Role.USER
            builder.addMessage(MessageParam.builder().role(role).content(text).build())
        }
        val response = clientFor(apiKey).messages().create(builder.build())
        val out = StringBuilder()
        for (block in response.content()) {
            block.text().ifPresent { out.append(it.text()) }
        }
        return out.toString()
    }
}
