package com.tend.app.ai

import org.json.JSONObject

/** Something the assistant wants to do to the user's data. */
sealed class AiAction {
    data class AddTask(val title: String, val group: String) : AiAction()
    data class AddPlanBlock(val title: String, val startMin: Int, val durationMin: Int) : AiAction()
    data class AddHabit(val name: String, val category: String, val goal: String) : AiAction()
}

data class AiResult(val reply: String, val actions: List<AiAction>)

object AiProtocol {

    fun systemPrompt(stateSummary: String): String = """
        You are Tend, the assistant inside a personal companion app that tracks habits,
        a daily plan (timeline of blocks), and to-do tasks.

        Current state:
        $stateSummary

        Respond ONLY with a single JSON object, no markdown fences, in this shape:
        {"reply": "<short friendly message to show the user>",
         "actions": [
           {"type": "add_task", "title": "...", "group": "PERSONAL|WORK|HEALTH"},
           {"type": "add_plan", "title": "...", "start": "17:00", "durationMin": 30},
           {"type": "add_habit", "name": "...", "category": "Fitness|Mind|Work|Health", "goal": "..."}
         ]}
        The "actions" array may be empty when the user is just asking a question.
        Keep replies to one or two sentences.
    """.trimIndent()

    /** Parses the model's JSON reply; returns null if it isn't valid JSON. */
    fun parse(raw: String): AiResult? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val obj = JSONObject(cleaned.substring(start, end + 1))
            val reply = obj.optString("reply", "")
            val actions = mutableListOf<AiAction>()
            val arr = obj.optJSONArray("actions")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    when (a.optString("type")) {
                        "add_task" -> actions += AiAction.AddTask(
                            title = a.optString("title"),
                            group = a.optString("group", "PERSONAL").uppercase(),
                        )
                        "add_plan" -> {
                            val start24 = a.optString("start", "12:00").split(":")
                            val minutes = (start24.getOrNull(0)?.toIntOrNull() ?: 12) * 60 +
                                (start24.getOrNull(1)?.toIntOrNull() ?: 0)
                            actions += AiAction.AddPlanBlock(
                                title = a.optString("title"),
                                startMin = minutes.coerceIn(0, 24 * 60 - 15),
                                durationMin = a.optInt("durationMin", 30).coerceIn(5, 8 * 60),
                            )
                        }
                        "add_habit" -> actions += AiAction.AddHabit(
                            name = a.optString("name"),
                            category = a.optString("category", "Mind"),
                            goal = a.optString("goal", "Daily"),
                        )
                    }
                }
            }
            if (reply.isBlank() && actions.isEmpty()) null else AiResult(reply, actions)
        } catch (e: Exception) {
            null
        }
    }

    private val timeRegex = Regex("(?i)\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
    private val prefixRegex = Regex("(?i)^(add|remind me to|create|schedule)\\s+")

    /**
     * Offline fallback that mirrors the prototype's behavior: "add buy groceries
     * at 5pm" becomes a plan block; anything else becomes a Personal task.
     */
    fun simulate(input: String): AiResult {
        val match = timeRegex.find(input)
        val cleanedTitle = input
            .replace(timeRegex, "")
            .replace(prefixRegex, "")
            .trim()
            .replaceFirstChar { it.uppercaseChar() }
            .ifEmpty { "New item" }

        return if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: 12
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val meridiem = match.groupValues[3].lowercase()
            when {
                meridiem == "pm" && hour < 12 -> hour += 12
                meridiem == "am" && hour == 12 -> hour = 0
                meridiem.isEmpty() && hour in 1..7 -> hour += 12 // "at 5" usually means 5pm
            }
            val startMin = (hour * 60 + minute).coerceIn(0, 24 * 60 - 30)
            AiResult(
                reply = "Done — added \"$cleanedTitle\" at ${fmt(startMin)} to today's plan. Check the Plan tab.",
                actions = listOf(AiAction.AddPlanBlock(cleanedTitle, startMin, 30)),
            )
        } else {
            AiResult(
                reply = "Done — added \"$cleanedTitle\" to your tasks for today. It's in the Personal group; swipe to the Tasks tab to see it.",
                actions = listOf(AiAction.AddTask(cleanedTitle, "PERSONAL")),
            )
        }
    }

    private fun fmt(min: Int): String {
        val h24 = min / 60
        val suffix = if (h24 >= 12) "PM" else "AM"
        val h = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return "%d:%02d %s".format(h, min % 60, suffix)
    }
}
