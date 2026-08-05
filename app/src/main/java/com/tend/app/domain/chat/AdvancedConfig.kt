package com.tend.app.domain.chat

import com.tend.app.ai.AiProtocol
import org.json.JSONObject

/**
 * User overrides for the prompt and for tunable numbers.
 *
 * The rule this file exists to enforce: **a bad override can never break the
 * assistant.** Every read goes through a validator, and anything that fails
 * falls back to the shipped default rather than propagating. Saving is where
 * errors are reported; loading is where they are survived.
 */
object AdvancedConfig {

    /**
     * What the JSON config may contain. Anything else is ignored rather than
     * rejected, so a newer app version's key in an older build is harmless.
     */
    data class Values(
        val historyWindow: Int = ChatContextPolicy.DEFAULT_WINDOW,
        val localThinkingMs: Long = 550L,
        val temperature: Double = 0.7,
    ) {
        fun toPrettyJson(): String = JSONObject()
            .put("historyWindow", historyWindow)
            .put("localThinkingMs", localThinkingMs)
            .put("temperature", temperature)
            .toString(2)
    }

    val DEFAULTS = Values()

    fun defaultPrompt(): String = AiProtocol.systemPrompt(STATE_PLACEHOLDER)

    /** Where the live state summary is spliced into a custom prompt. */
    const val STATE_PLACEHOLDER = "{{state}}"

    sealed interface Validation {
        data class Ok(val values: Values) : Validation
        data class Invalid(val message: String) : Validation
    }

    /**
     * Checks a JSON config before it is stored.
     *
     * Ranges are enforced here, not just types: a `historyWindow` of 100000
     * parses fine and would then build a request large enough to fail every
     * call, which is exactly the "silently corrupt runtime behaviour" this is
     * meant to prevent.
     */
    fun validate(raw: String): Validation {
        val text = raw.trim()
        if (text.isEmpty()) return Validation.Ok(DEFAULTS)

        val obj = try {
            JSONObject(text)
        } catch (e: Exception) {
            return Validation.Invalid("Not valid JSON: ${e.message?.take(90) ?: "unparseable"}")
        }

        val window = obj.optInt("historyWindow", DEFAULTS.historyWindow)
        if (window !in 1..200) {
            return Validation.Invalid("historyWindow must be between 1 and 200 (got $window).")
        }

        val think = obj.optLong("localThinkingMs", DEFAULTS.localThinkingMs)
        if (think !in 0..5_000) {
            return Validation.Invalid("localThinkingMs must be between 0 and 5000 (got $think).")
        }

        val temperature = obj.optDouble("temperature", DEFAULTS.temperature)
        if (temperature.isNaN() || temperature !in 0.0..2.0) {
            return Validation.Invalid("temperature must be between 0 and 2 (got $temperature).")
        }

        return Validation.Ok(Values(window, think, temperature))
    }

    /**
     * Reads stored config, falling back to defaults without complaint.
     *
     * Deliberately silent: this runs on every message, and a config that was
     * valid when saved can only be invalid now through corruption or a
     * downgrade. Neither is worth failing a user's message over.
     */
    fun load(raw: String): Values = when (val result = validate(raw)) {
        is Validation.Ok -> result.values
        is Validation.Invalid -> DEFAULTS
    }

    /**
     * Builds the system prompt, honouring a custom template if there is one.
     *
     * A custom prompt that forgot the placeholder still gets the state appended,
     * because a prompt with no state is technically valid and practically
     * useless — the model would answer with no idea what the user is tracking.
     */
    fun systemPrompt(customPrompt: String, stateSummary: String): String {
        val template = customPrompt.trim()
        if (template.isEmpty()) return AiProtocol.systemPrompt(stateSummary)
        return if (template.contains(STATE_PLACEHOLDER)) {
            template.replace(STATE_PLACEHOLDER, stateSummary)
        } else {
            "$template\n\nCurrent state:\n$stateSummary"
        }
    }
}
