package com.tend.app.ai

import org.json.JSONObject

/** Something the assistant wants to do to the user's data. */
sealed class AiAction {
    data class AddTask(val title: String, val group: String) : AiAction()
    data class AddPlanBlock(
        val title: String,
        val startMin: Int,
        val durationMin: Int,
        val kind: String = "event",
    ) : AiAction()
    data class AddHabit(val name: String, val category: String, val goal: String) : AiAction()
}

data class AiResult(val reply: String, val actions: List<AiAction>)

object AiProtocol {

    fun systemPrompt(stateSummary: String): String = """
        You are Tend, the assistant inside a personal companion app. The app has exactly
        three kinds of data, and you act on the user's request by emitting actions:

        - HABIT: a recurring behaviour to build or quit (exercise, read daily, no sugar,
          quit drinking, meditate). Tracked every day with streaks.
        - PLAN BLOCK: a time-boxed entry on TODAY's timeline (a meeting, a focus session,
          an errand at a specific time).
        - TASK: a one-off to-do with no fixed time.

        Current state:
        $stateSummary

        The app also has a separate encrypted Memory vault you cannot see or write to —
        it exists specifically so identifiers and secrets never pass through you or get
        stored as plain text. If the user shares or asks to save something like an ID
        number, card number, PIN, password, or similar secret, do NOT put it in a task,
        plan, or habit, and do NOT repeat it back in your reply. Instead emit an empty
        actions array and, in "reply", tell them to save it in Memory (Settings → Memory,
        or by typing e.g. "remember ..." in this chat) instead.

        Respond ONLY with a single valid JSON object — no prose before or after, no
        markdown fences — in this exact shape:
        {"reply": "<1-2 friendly sentences saying exactly what you did>",
         "actions": [
           {"type": "add_habit", "name": "...", "category": "Fitness|Mind|Work|Health", "goal": "e.g. Daily · 8:00 AM"},
           {"type": "add_task", "title": "...", "group": "PERSONAL|WORK|HEALTH"},
           {"type": "add_plan", "title": "...", "start": "17:00", "durationMin": 30}
         ]}

        Routing rules — follow them strictly:
        - Anything recurring, or phrased as "habit", "every day", "daily", "stop X",
          "quit X", "start doing X" → add_habit. NEVER add these as tasks.
        - "Plan my day/morning/afternoon" → emit SEVERAL add_plan actions (3-6 sensible
          blocks, e.g. 09:00-18:00, 24h "HH:MM" start times, no overlaps with the
          existing plan above). Weave in the user's habits and open tasks where sensible.
        - A one-off with a time ("buy groceries at 5pm") → add_plan.
        - A one-off without a time → add_task.
        - A question or summary request → empty actions array, answer in "reply".
        - Multiple requests in one message → multiple actions.
        - "start" must be 24-hour "HH:MM". Keep names/titles short (2-5 words), not the
          user's whole sentence.
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
                            actions += AiAction.AddPlanBlock(
                                title = a.optString("title"),
                                startMin = parseClock(a.optString("start", "12:00")),
                                durationMin = a.optInt("durationMin", 30).coerceIn(5, 8 * 60),
                                kind = a.optString("kind", "event").lowercase()
                                    .takeIf { it in setOf("focus", "event", "habit") } ?: "event",
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

    private fun parseClock(raw: String): Int {
        val parts = raw.trim().split(":")
        val minutes = (parts.getOrNull(0)?.toIntOrNull() ?: 12) * 60 +
            (parts.getOrNull(1)?.take(2)?.toIntOrNull() ?: 0)
        return minutes.coerceIn(0, 24 * 60 - 15)
    }

    // ── AI auto-plan ────────────────────────────────────────────

    fun autoPlanPrompt(
        dateLabel: String,
        fixed: List<String>,
        openTasks: List<String>,
        habits: List<String>,
    ): String = """
        You are Tend's day planner. Build a realistic plan for TODAY ($dateLabel) as
        time blocks between 08:00 and 21:00.

        FIXED items that already occupy the timeline — you MUST NOT overlap any of
        these, and you MUST NOT re-emit them:
        ${fixed.joinToString("\n") { "- $it" }.ifEmpty { "- (none)" }}

        Open tasks to schedule (fit the important-sounding ones, ~30-45 min each;
        it's fine to leave some out if the day is full):
        ${openTasks.joinToString("\n") { "- $it" }.ifEmpty { "- (none)" }}

        Habits for context (already tracked; only schedule ones that clearly need a
        session and are not already in the fixed list):
        ${habits.joinToString("\n") { "- $it" }.ifEmpty { "- (none)" }}

        Guidelines: group focus work in the morning where possible, add a lunch
        break around 13:00 if free, leave 10-15 min gaps between blocks, nothing
        after 21:00.

        Respond ONLY with a single valid JSON object, no markdown fences:
        {"reply": "<1-2 sentences summarising the plan>",
         "blocks": [
           {"title": "...", "start": "09:00", "durationMin": 45, "kind": "focus|event|habit"}
         ]}
        "start" must be 24-hour "HH:MM". Titles short (2-5 words).
    """.trimIndent()

    /** Parses the auto-plan response into plan-block actions. */
    fun parseAutoPlan(raw: String): Pair<String, List<AiAction.AddPlanBlock>>? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val obj = JSONObject(cleaned.substring(start, end + 1))
            val reply = obj.optString("reply", "Here's your day.")
            val blocks = mutableListOf<AiAction.AddPlanBlock>()
            val arr = obj.optJSONArray("blocks") ?: return null
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                val title = b.optString("title").trim()
                if (title.isEmpty()) continue
                blocks += AiAction.AddPlanBlock(
                    title = title,
                    startMin = parseClock(b.optString("start", "09:00")),
                    durationMin = b.optInt("durationMin", 30).coerceIn(10, 4 * 60),
                    kind = b.optString("kind", "focus").lowercase()
                        .takeIf { it in setOf("focus", "event", "habit") } ?: "focus",
                )
            }
            reply to blocks
        } catch (e: Exception) {
            null
        }
    }

    /** Drops blocks that overlap any fixed (busy) interval. */
    fun filterOverlaps(
        blocks: List<AiAction.AddPlanBlock>,
        busy: List<Pair<Int, Int>>,
    ): List<AiAction.AddPlanBlock> {
        val placed = mutableListOf<Pair<Int, Int>>()
        return blocks.filter { block ->
            val range = block.startMin to (block.startMin + block.durationMin)
            val clash = (busy + placed).any { (s, e) -> range.first < e && s < range.second }
            if (!clash) placed += range
            !clash
        }
    }

    private val timeRegex = Regex("(?i)\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
    private val prefixRegex = Regex("(?i)^(add|remind me to|create|schedule)\\s+")

    private val habitRegex = Regex(
        "(?i)\\bhabits?\\b|\\bevery ?day\\b|\\bdaily\\b|^\\s*(quit|stop)\\s+"
    )

    private val sensitiveKeywordRegex = Regex(
        "(?i)\\b(?:aadhaar|ssn|social security|credit card|cvv|pin|password|passport|secret)\\b"
    )

    /**
     * A run of exactly 12 or 16 digits — an Aadhaar or card number — however it
     * was typed. `[\d\s-]` covers the formatting people actually use ("4111
     * 1111 1111 1111", "1234-5678-9012"), and the not-preceded/followed-by-\w
     * checks stand in for `\b`, which can't be used here: `\b` only fires at a
     * transition into or out of a word character, and every character this
     * pattern matches (digits, and the spaces/dashes between them) already is
     * or borders one, so a literal `\b` would never anchor where intended. The
     * checks keep the same guarantee `\b` gave the old digits-only pattern: a
     * number embedded in an alphanumeric id like "order123456789012" still
     * doesn't match.
     */
    private val sensitiveDigitRunRegex = Regex("""(?<!\w)\d[\d\s-]{9,25}\d(?!\w)""")

    fun isSensitive(text: String): Boolean {
        if (sensitiveKeywordRegex.containsMatchIn(text)) return true
        return sensitiveDigitRunRegex.findAll(text).any { match ->
            val digitCount = match.value.count(Char::isDigit)
            digitCount == 12 || digitCount == 16
        }
    }

    /**
     * Offline fallback — simple rules, no AI model involved:
     * habit-sounding requests become habits, "at 5pm" requests become plan
     * blocks, day-planning needs a real key, anything else becomes a task.
     */
    fun simulate(input: String): AiResult {
        if (isSensitive(input)) {
            return AiResult(
                reply = "This message contains sensitive information (ID, card, or credentials). " +
                    "To protect your privacy, Tend will not create a plaintext task out of it. " +
                    "Use the encrypted Memory Vault to store sensitive items safely.",
                actions = emptyList(),
            )
        }

        // Habit intent
        if (habitRegex.containsMatchIn(input)) {

            val name = input
                .replace(prefixRegex, "")
                .replace(Regex("(?i)\\ba\\s+habit\\s+(to|of|for)\\s+"), "")
                .replace(Regex("(?i)\\bas a habit\\b|\\bhabits?\\b|\\bevery ?day\\b|\\bdaily\\b"), "")
                .trim().trim('.', ',', '!').trim()
                .replaceFirstChar { it.uppercaseChar() }
                .ifEmpty { "New habit" }
            return AiResult(
                reply = "Done — created the habit \"$name\". It's on your Today tab; tap it each day to build the streak.",
                actions = listOf(AiAction.AddHabit(name, "Health", "Daily")),
            )
        }

        // Day planning needs a real model
        if (Regex("(?i)\\bplan\\s+(my|the|out)\\b").containsMatchIn(input)) {
            return AiResult(
                reply = "Planning a whole day needs the full assistant — add a Gemini, Claude or OpenRouter API key in Settings and I'll lay out your day. Offline I can still do quick adds like \"gym at 6pm\".",
                actions = emptyList(),
            )
        }

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
