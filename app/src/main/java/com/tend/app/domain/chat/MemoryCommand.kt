package com.tend.app.domain.chat

/**
 * A vault instruction typed into the chat box.
 *
 * Parsing lives here, in pure domain code, but **execution deliberately does
 * not**. A recognised command never becomes an [AssistantRequest] at all: the
 * caller handles it and returns before the pipeline is entered, so vault text
 * cannot reach an engine even by mistake.
 *
 * That is a stronger guarantee than intercepting inside the router would give,
 * because it means neither engine — nor anything the router touches — ever
 * holds a reference to the vault or sees the words the user typed.
 */
sealed interface MemoryCommand {

    /** `add to memory <text>` */
    data class Add(val text: String) : MemoryCommand

    /** `search memory <query>` — a blank query lists everything. */
    data class Search(val query: String) : MemoryCommand

    companion object {
        private val addRegex = Regex("""(?i)^\s*add\s+(?:this\s+)?to\s+memory[:,]?\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
        private val searchRegex = Regex("""(?i)^\s*(?:search|find|look\s+up)\s+(?:in\s+)?memory(?:\s+for)?[:,]?\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)

        /** Null when the message is ordinary chat and should go to an engine. */
        fun parse(input: String): MemoryCommand? {
            addRegex.find(input)?.let { return Add(it.groupValues[1].trim()) }
            searchRegex.find(input)?.let { return Search(it.groupValues[1].trim()) }
            return null
        }
    }
}
