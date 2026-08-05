package com.tend.app.ai

import org.json.JSONObject

/** One OpenRouter model, reduced to what the picker needs. */
data class OpenRouterModel(
    val id: String,
    val label: String,
    /** USD per million tokens. Meaningless when [variablePricing]. */
    val promptPerMillion: Double,
    val completionPerMillion: Double,
    val free: Boolean,
    /** OpenRouter's auto-routers price per underlying model, so there is no fixed rate. */
    val variablePricing: Boolean,
    /** In the hand-picked shortlist of known-good cheap workhorses. */
    val recommended: Boolean,
) {
    /** "Free", "Varies", or "$0.03 / $0.13 per 1M". */
    val priceLabel: String
        get() = when {
            free -> "Free"
            variablePricing -> "Varies"
            else -> "$%s / $%s per 1M".format(trim(promptPerMillion), trim(completionPerMillion))
        }

    private fun trim(value: Double): String =
        if (value >= 10) "%.0f".format(value) else "%.2f".format(value)
}

/**
 * Turns OpenRouter's `/models` response into a picker-ready list.
 *
 * Kept apart from [AiClient] and free of Android types so the filtering and
 * ranking — the parts with actual judgement in them — are unit-testable against
 * a captured response.
 */
object OpenRouterCatalog {

    /**
     * Known-good, cheap, widely-used models, matched as patterns rather than
     * exact ids.
     *
     * A hard-coded list of ids rots: models are retired and point releases are
     * renamed constantly. Intersecting patterns with the live catalogue means a
     * retired model disappears on its own and a new `-flash` shows up without a
     * release. It also avoids the alternative failure, which is real: ranking
     * purely on price puts obscure 8B models above `gpt-4o-mini`.
     */
    private val CURATED = Regex(
        listOf(
            "gpt-oss",
            "gpt-[\\d.]+-(nano|mini)",
            "gpt-4o-mini",
            "deepseek[^/]*-flash",
            "gemini[^/]*-flash",
            "claude[^/]*-haiku",
            "mistral-small",
            "llama-3\\.\\d+[^/]*instruct",
            "qwen[\\d.]*-flash",
            "gemma-\\d",
        ).joinToString("|")
    )

    /** How many recommended models the picker shows before "show all". */
    const val SHORTLIST_SIZE = 12

    /** Whether an id is in the shortlist, derivable without the full catalogue. */
    fun isRecommended(id: String): Boolean = CURATED.containsMatchIn(id)

    /**
     * Filtered, ranked model list. Cheapest first, free at the top, and
     * variably-priced routers last. Returns empty rather than throwing on
     * anything malformed — a broken catalogue must not take out Settings.
     */
    fun parse(rawJson: String): List<OpenRouterModel> {
        val data = try {
            JSONObject(rawJson).optJSONArray("data")
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val models = mutableListOf<OpenRouterModel>()
        for (i in 0 until data.length()) {
            val entry = data.optJSONObject(i) ?: continue
            models += toModel(entry) ?: continue
        }

        // Cheapest first. Variable pricing sorts last: "-1" means "depends on
        // what it routes to", and taken literally it would rank as cheaper than
        // free and head the shortlist.
        return models.sortedWith(
            compareBy<OpenRouterModel> { it.variablePricing }
                .thenBy { it.promptPerMillion + it.completionPerMillion }
                .thenBy { it.id }
        )
    }

    /** The shortlist shown before the user asks for everything. */
    fun shortlist(models: List<OpenRouterModel>): List<OpenRouterModel> {
        val recommended = models.filter { it.recommended }
        // If OpenRouter renames everything out from under the patterns, fall
        // back to the cheapest real models so the picker is never empty.
        return (recommended.ifEmpty { models.filter { !it.variablePricing } })
            .take(SHORTLIST_SIZE)
    }

    /** Case-insensitive match over both the id and the human label. */
    fun search(models: List<OpenRouterModel>, query: String): List<OpenRouterModel> {
        val q = query.trim()
        if (q.isEmpty()) return models
        return models.filter { it.id.contains(q, true) || it.label.contains(q, true) }
    }

    private fun toModel(entry: JSONObject): OpenRouterModel? {
        val id = entry.optString("id").takeIf { it.isNotBlank() } ?: return null

        // The chat protocol is JSON-only, so a model that can't be pinned to
        // structured output is a liability rather than a choice.
        val params = entry.optJSONArray("supported_parameters") ?: return null
        var structured = false
        for (i in 0 until params.length()) {
            if (params.optString(i) == "response_format") structured = true
        }
        if (!structured) return null

        // Output must be text and *only* text. Checking "text is among the
        // outputs" is not enough: Google's Lyria music models emit
        // ["text","audio"], advertise response_format, and are free — so a
        // looser filter puts a music generator at the top of the shortlist.
        val architecture = entry.optJSONObject("architecture") ?: return null
        val outputs = architecture.optJSONArray("output_modalities") ?: return null
        if (outputs.length() != 1 || outputs.optString(0) != "text") return null
        val inputs = architecture.optJSONArray("input_modalities") ?: return null
        var textIn = false
        for (i in 0 until inputs.length()) if (inputs.optString(i) == "text") textIn = true
        if (!textIn) return null

        val pricing = entry.optJSONObject("pricing") ?: return null
        val prompt = pricing.optString("prompt").toDoubleOrNull() ?: return null
        val completion = pricing.optString("completion").toDoubleOrNull() ?: return null
        val variable = prompt < 0 || completion < 0

        return OpenRouterModel(
            id = id,
            label = entry.optString("name").takeIf { it.isNotBlank() } ?: id,
            promptPerMillion = if (variable) 0.0 else prompt * 1_000_000,
            completionPerMillion = if (variable) 0.0 else completion * 1_000_000,
            free = !variable && prompt == 0.0 && completion == 0.0,
            variablePricing = variable,
            recommended = CURATED.containsMatchIn(id),
        )
    }
}
