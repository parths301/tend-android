package com.tend.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.tend.app.data.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One entry in the model picker, uniform across providers.
 *
 * [detail] and [recommended] only carry information for OpenRouter, where the
 * catalogue is large enough to need ranking; for Gemini and Anthropic the list
 * is short and the id says everything. Keeping one shape means the picker has
 * one code path.
 */
data class ModelOption(
    val id: String,
    val label: String = id,
    val detail: String? = null,
    val recommended: Boolean = false,
)

/**
 * BYOK client for "Ask Tend". Supports three providers:
 *  - Gemini (Google AI, REST API)
 *  - Claude (Anthropic, official Java SDK)
 *  - OpenRouter (one key, ~340 models from 58 vendors, OpenAI-shaped REST)
 *
 * Also lists the models available to a key so Settings can offer a picker
 * instead of asking the user to type a model name.
 */
class AiClient {

    // ── completion ──────────────────────────────────────────────

    /** [history] is (isAssistant, text) pairs in chronological order. */
    fun complete(
        provider: String,
        apiKey: String,
        model: String,
        system: String,
        history: List<Pair<Boolean, String>>,
    ): String {
        // Both Gemini and Anthropic require the conversation to open with a user
        // turn; the app's greeting bubble is assistant-authored, so drop leading
        // assistant messages before sending.
        val turns = history.dropWhile { it.first }
        require(turns.isNotEmpty()) { "no user message to send" }
        // Named branches rather than a trailing `else`: with three providers an
        // else-falls-through-to-Claude would silently send an OpenRouter key to
        // Anthropic if a constant were ever mistyped.
        return when (provider) {
            SettingsRepository.PROVIDER_GEMINI -> geminiComplete(apiKey, model, system, turns)
            SettingsRepository.PROVIDER_OPENROUTER -> openRouterComplete(apiKey, model, system, turns)
            SettingsRepository.PROVIDER_ANTHROPIC -> claudeComplete(apiKey, model, system, turns)
            else -> throw IllegalArgumentException("Unknown AI provider: $provider")
        }
    }

    /** Models the key can use, best default first. */
    fun listModels(provider: String, apiKey: String): List<ModelOption> = when (provider) {
        SettingsRepository.PROVIDER_GEMINI -> geminiModels(apiKey).map { ModelOption(it) }
        SettingsRepository.PROVIDER_OPENROUTER -> openRouterModels(apiKey)
        SettingsRepository.PROVIDER_ANTHROPIC -> anthropicModels(apiKey).map { ModelOption(it) }
        else -> throw IllegalArgumentException("Unknown AI provider: $provider")
    }

    // ── Gemini ──────────────────────────────────────────────────

    private fun geminiComplete(
        apiKey: String,
        model: String,
        system: String,
        history: List<Pair<Boolean, String>>,
    ): String {
        val contents = JSONArray()
        for ((isAssistant, text) in history) {
            contents.put(
                JSONObject()
                    .put("role", if (isAssistant) "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            // The app's protocol is JSON-only; force structured output so the
            // model can't wrap it in prose or markdown fences.
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

        val raw = http(
            "POST", "$GEMINI_BASE/models/$model:generateContent",
            mapOf("x-goog-api-key" to apiKey), body.toString(),
        )
        val parts = JSONObject(raw)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val out = StringBuilder()
        for (i in 0 until parts.length()) out.append(parts.getJSONObject(i).optString("text"))
        return out.toString()
    }

    private fun geminiModels(apiKey: String): List<String> {
        val raw = http("GET", "$GEMINI_BASE/models?pageSize=200", mapOf("x-goog-api-key" to apiKey))
        val models = JSONObject(raw).optJSONArray("models") ?: return emptyList()
        val stable = mutableListOf<String>()
        val all = mutableListOf<String>()
        for (i in 0 until models.length()) {
            val m = models.getJSONObject(i)
            val name = m.optString("name").removePrefix("models/")
            val methods = m.optJSONArray("supportedGenerationMethods")
            var chat = false
            if (methods != null) {
                for (j in 0 until methods.length()) if (methods.optString(j) == "generateContent") chat = true
            }
            if (chat && name.startsWith("gemini-") && !NON_TEXT.containsMatchIn(name)) {
                all.add(name)
                // Preview/experimental variants get retired without notice and often
                // ship with near-zero free-tier quota (429s), so keep them out of the
                // picker unless a key has nothing else.
                if (!UNSTABLE.containsMatchIn(name)) stable.add(name)
            }
        }
        // Newest families first; the plain "flash" of the newest generation is the
        // sensible default, so surface it at the top.
        return (stable.ifEmpty { all }).sortedWith(
            compareByDescending<String> { it }.thenBy { it.length }
        ).sortedByDescending { generationOf(it) * 10 + if (it.endsWith("flash")) 1 else 0 }
    }

    private fun generationOf(model: String): Int {
        val match = Regex("gemini-(\\d+)\\.(\\d+)").find(model) ?: return 0
        return match.groupValues[1].toInt() * 10 + match.groupValues[2].toInt()
    }

    // ── OpenRouter ──────────────────────────────────────────────

    private fun openRouterComplete(
        apiKey: String,
        model: String,
        system: String,
        history: List<Pair<Boolean, String>>,
    ): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        for ((isAssistant, text) in history) {
            messages.put(
                JSONObject()
                    .put("role", if (isAssistant) "assistant" else "user")
                    .put("content", text)
            )
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            // The picker only offers models advertising response_format, so this
            // is always honoured — the app's protocol is JSON-only.
            .put("response_format", JSONObject().put("type", "json_object"))

        val raw = http("POST", "$OPENROUTER_BASE/chat/completions", authHeaders(apiKey), body.toString())
        val choices = JSONObject(raw).optJSONArray("choices")
            ?: throw RuntimeException("OpenRouter returned no choices")
        if (choices.length() == 0) throw RuntimeException("OpenRouter returned no choices")
        return choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty()
    }

    private fun openRouterModels(apiKey: String): List<ModelOption> {
        // With a key, /models/user respects the provider preferences set on the
        // user's OpenRouter account; the public catalogue ignores them. Fall
        // back to the public list, which needs no auth, so the picker still
        // populates before a key is saved.
        val raw = if (apiKey.isBlank()) {
            http("GET", "$OPENROUTER_BASE/models", emptyMap())
        } else {
            try {
                http("GET", "$OPENROUTER_BASE/models/user", authHeaders(apiKey))
            } catch (e: Exception) {
                http("GET", "$OPENROUTER_BASE/models", emptyMap())
            }
        }
        return OpenRouterCatalog.parse(raw).map {
            ModelOption(
                id = it.id,
                label = it.label,
                detail = it.priceLabel,
                recommended = it.recommended,
            )
        }
    }

    private fun authHeaders(apiKey: String) = mapOf(
        "Authorization" to "Bearer $apiKey",
        // Attribution for openrouter.ai's app rankings. Neither is required and
        // neither identifies the user.
        "HTTP-Referer" to APP_URL,
        "X-OpenRouter-Title" to APP_NAME,
    )

    // ── Anthropic ───────────────────────────────────────────────

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

    private fun claudeComplete(
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

    private fun anthropicModels(apiKey: String): List<String> {
        val raw = http(
            "GET", "https://api.anthropic.com/v1/models?limit=100",
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
        )
        val data = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        val names = mutableListOf<String>()
        for (i in 0 until data.length()) names.add(data.getJSONObject(i).optString("id"))
        return names.filter { it.isNotBlank() }
    }

    // ── plumbing ────────────────────────────────────────────────

    private fun http(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String? = null,
    ): String {
        // Retry once on a transient connection drop (common on mobile networks:
        // "Software caused connection abort", "Connection reset"). HTTP error
        // responses are not retried — they're surfaced to the user as-is.
        return try {
            httpOnce(method, url, headers, body)
        } catch (e: java.io.IOException) {
            httpOnce(method, url, headers, body)
        }
    }

    private fun httpOnce(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String? = null,
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val message = try {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                throw RuntimeException(message?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"
        const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"
        const val APP_URL = "https://github.com/parths301/tend-android"
        const val APP_NAME = "Tend"

        /** Non-text model ids to hide from the picker (chat protocol is text-only). */
        val NON_TEXT = Regex("embed|image|imagen|audio|tts|live|veo|aqa|vision|robotics|computer-use")

        /** Preview/experimental/special variants — unreliable, hidden from the picker. */
        val UNSTABLE = Regex("preview|exp|thinking|tuning|customtools")
    }
}
