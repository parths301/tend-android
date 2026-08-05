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
        private val ALL = RegexOption.DOT_MATCHES_ALL

        private val searchRegex = Regex(
            """(?i)^\s*(?:search|find|look\s+up|show|list)\s+(?:in\s+|my\s+)?memory(?:\s+for)?[:,;]?\s*(.*)$""",
            ALL,
        )
        private val whatsInMemoryRegex = Regex("""(?i)^\s*what'?s?\s+(?:is\s+)?in\s+(?:my\s+)?memory\s*\??\s*$""")

        private val addPrefixRegex = Regex("""(?i)^\s*add\s+(?:this\s+)?to\s+(?:my\s+)?memory[:,;]?\s*(.*)$""", ALL)
        private val addObjectRegex = Regex("""(?i)^\s*add\s+(.+?)\s+to\s+(?:my\s+)?memory[.!?]?\s*$""", ALL)
        private val saveObjectRegex = Regex("""(?i)^\s*save\s+(.+?)\s+(?:to|in)\s+(?:my\s+)?memory[.!?]?\s*$""", ALL)
        private val savePrefixRegex = Regex("""(?i)^\s*save\s+(?:this\s+)?(?:to|in)\s+(?:my\s+)?memory[:,;]?\s*(.*)$""", ALL)
        private val keepStoreObjectRegex =
            Regex("""(?i)^\s*(?:keep|store)\s+(.+?)\s+in\s+(?:my\s+)?memory[.!?]?\s*$""", ALL)
        private val keepStorePrefixRegex =
            Regex("""(?i)^\s*(?:keep|store)\s+(?:this\s+)?(?:in|to)\s+(?:my\s+)?memory[:,;]?\s*(.*)$""", ALL)


        private val rememberRegex = Regex("""(?i)^\s*(?:please\s+)?remember\s+(?:that\s+)?(.+)$""", ALL)

        private val bareRegex = Regex("""(?i)^\s*memory[:,;]?\s*(.*)$""", ALL)

        private fun cleanText(text: String): String = text.trim().trimStart(',', ':', ';', '.', '-').trim()

        /** Null when the message is ordinary chat and should go to an engine. */
        fun parse(input: String): MemoryCommand? {
            searchRegex.find(input)?.let { return Search(cleanText(it.groupValues[1])) }
            if (whatsInMemoryRegex.matches(input.trim())) return Search("")
            rememberRegex.find(input)?.let { return Add(cleanText(it.groupValues[1])) }
            addObjectRegex.find(input)?.let { return Add(cleanText(it.groupValues[1])) }
            addPrefixRegex.find(input)?.let { return Add(cleanText(it.groupValues[1])) }
            saveObjectRegex.find(input)?.let { return Add(cleanText(it.groupValues[1])) }
            savePrefixRegex.find(input)?.let { return Add(cleanText(it.groupValues[1])) }
            keepStoreObjectRegex.find(input)?.let { return Add(cleanText(it.groupValues[1])) }
            keepStorePrefixRegex.find(input)?.let { return Add(cleanText(it.groupValues[1])) }
            bareRegex.find(input)?.let {
                val text = cleanText(it.groupValues[1])
                return if (text.isBlank()) Search("") else Add(text)
            }
            return null
        }
    }
}


