package com.tend.app.domain.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * How the assistant should sound.
 *
 * Reaches both engines through [AssistantRequest.personaFragment], so a
 * personality is not an AI-only feature: the offline rules read [tone] too. A
 * setting that only worked with a key would be a dead toggle for anyone
 * running offline.
 */
data class Personality(
    val id: Long,
    val name: String,
    val tone: String = "",
    /** Free-text behaviour notes appended to the system prompt. */
    val notes: String = "",
    /** Short style traits, e.g. "concise", "encouraging". */
    val traits: List<String> = emptyList(),
    /** Extra prompt text, used verbatim. */
    val promptFragment: String = "",
    /** Optional JSON overrides, validated before it is ever stored. */
    val jsonOverrides: String = "",
    val builtIn: Boolean = false,
) {
    /**
     * The text handed to an engine. Empty when the personality says nothing,
     * so a default profile costs no prompt tokens.
     */
    fun fragment(): String = buildString {
        if (tone.isNotBlank()) appendLine("Tone: $tone")
        if (traits.isNotEmpty()) appendLine("Style: ${traits.joinToString(", ")}")
        if (notes.isNotBlank()) appendLine(notes.trim())
        if (promptFragment.isNotBlank()) appendLine(promptFragment.trim())
    }.trim()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("tone", tone)
        .put("notes", notes)
        .put("traits", JSONArray(traits))
        .put("promptFragment", promptFragment)
        .put("jsonOverrides", jsonOverrides)
        .put("builtIn", builtIn)

    companion object {
        /**
         * Always present, never editable, and the fallback whenever a stored
         * profile fails to parse — so the pipeline always has something valid.
         */
        val Default = Personality(
            id = 1L,
            name = "Tend",
            tone = "",
            notes = "",
            builtIn = true,
        )

        val Presets = listOf(
            Default,
            Personality(
                id = 2L,
                name = "Brief",
                tone = "terse and factual",
                traits = listOf("concise", "no filler"),
                notes = "Answer in one sentence wherever possible. Never repeat the request back.",
                builtIn = true,
            ),
            Personality(
                id = 3L,
                name = "Coach",
                tone = "warm and encouraging",
                traits = listOf("supportive", "specific"),
                notes = "Acknowledge progress before suggesting anything. Never scold a missed day.",
                builtIn = true,
            ),
        )

        fun fromJson(obj: JSONObject): Personality? = try {
            val id = obj.optLong("id", 0L)
            val name = obj.optString("name").trim()
            if (id == 0L || name.isEmpty()) null else Personality(
                id = id,
                name = name,
                tone = obj.optString("tone", ""),
                notes = obj.optString("notes", ""),
                traits = obj.optJSONArray("traits")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty(),
                promptFragment = obj.optString("promptFragment", ""),
                jsonOverrides = obj.optString("jsonOverrides", ""),
                builtIn = obj.optBoolean("builtIn", false),
            )
        } catch (e: Exception) {
            null
        }

        /** Decodes a stored list, skipping anything corrupt rather than throwing. */
        fun decodeList(raw: String): List<Personality> = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::fromJson) }
        } catch (e: Exception) {
            emptyList()
        }

        fun encodeList(list: List<Personality>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}
