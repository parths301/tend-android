package com.tend.app

import com.tend.app.ai.OpenRouterCatalog
import com.tend.app.ai.OpenRouterModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercised against a trimmed capture of the real `/api/v1/models` response
 * (`src/test/resources/openrouter_models.json`). Every entry in that fixture is
 * there for a reason — see the comments on each assertion.
 */
class OpenRouterCatalogTest {

    private val fixture: String =
        javaClass.classLoader!!.getResourceAsStream("openrouter_models.json")!!
            .bufferedReader().use { it.readText() }

    private val parsed: List<OpenRouterModel> = OpenRouterCatalog.parse(fixture)
    private fun ids() = parsed.map { it.id }

    @Test
    fun `excludes models that emit anything other than text`() {
        // google/lyria-3-pro-preview is a *music* model. It is free, and it
        // advertises response_format, so a naive "is text among the outputs"
        // filter puts a music generator at the top of the shortlist.
        assertFalse(
            "Lyria emits ['text','audio'] and must not reach the picker",
            ids().any { it.contains("lyria") },
        )
    }

    @Test
    fun `excludes models that cannot be pinned to JSON`() {
        // Tend's protocol is JSON-only; a model without response_format will
        // fail to parse often enough to be a bad offer.
        assertFalse(ids().any { it.contains("inkling") })
    }

    @Test
    fun `keeps ordinary text models`() {
        assertTrue(ids().contains("openai/gpt-oss-20b"))
        assertTrue(ids().contains("openai/gpt-4o-mini"))
    }

    @Test
    fun `free models rank above paid ones`() {
        val free = parsed.indexOfFirst { it.id == "openai/gpt-oss-20b:free" }
        val paid = parsed.indexOfFirst { it.id == "openai/gpt-oss-20b" }
        assertTrue("free model missing from the catalogue", free >= 0)
        assertTrue(free < paid)
        assertTrue(parsed[free].free)
    }

    @Test
    fun `variable pricing sorts last rather than cheapest`() {
        // "-1" means "depends what it routes to". Taken at face value it is
        // less than zero, which would rank an auto-router above every free
        // model and head the shortlist.
        val variable = parsed.indexOfFirst { it.id == "synthetic/variable-router" }
        assertTrue("synthetic variable-priced model missing", variable >= 0)
        assertEquals(parsed.lastIndex, variable)
        assertTrue(parsed[variable].variablePricing)
        assertFalse(parsed[variable].free)
    }

    @Test
    fun `cheap but obscure models are not recommended`() {
        // These are cheaper than gpt-4o-mini but nobody wants them chosen for
        // them — this is the case that rules out ranking on price alone.
        val obscure = parsed.filter { it.id.contains("lunaris") || it.id.contains("nex-n2") }
        assertTrue("fixture should contain the obscure cheap models", obscure.isNotEmpty())
        assertTrue(obscure.none { it.recommended })
    }

    @Test
    fun `shortlist prefers recommended models and stays short`() {
        val shortlist = OpenRouterCatalog.shortlist(parsed)
        assertTrue(shortlist.isNotEmpty())
        assertTrue(shortlist.all { it.recommended })
        assertTrue(shortlist.size <= OpenRouterCatalog.SHORTLIST_SIZE)
        // Cheapest-first is preserved within the shortlist.
        assertEquals("openai/gpt-oss-20b:free", shortlist.first().id)
    }

    @Test
    fun `shortlist falls back to cheapest when nothing matches the patterns`() {
        // If OpenRouter renames everything, the picker must not go empty.
        val unmatched = parsed.filter { !it.recommended }
        val shortlist = OpenRouterCatalog.shortlist(unmatched)
        assertTrue(shortlist.isNotEmpty())
        assertTrue(shortlist.none { it.variablePricing })
    }

    @Test
    fun `search matches id and label, case-insensitively`() {
        assertTrue(OpenRouterCatalog.search(parsed, "GPT-OSS").isNotEmpty())
        assertTrue(OpenRouterCatalog.search(parsed, "mistral").isNotEmpty())
        assertEquals(parsed.size, OpenRouterCatalog.search(parsed, "  ").size)
        assertTrue(OpenRouterCatalog.search(parsed, "zzzznope").isEmpty())
    }

    @Test
    fun `isRecommended agrees with the parsed flag`() {
        parsed.forEach { assertEquals(it.id, it.recommended, OpenRouterCatalog.isRecommended(it.id)) }
    }

    @Test
    fun `malformed input yields an empty list rather than throwing`() {
        assertTrue(OpenRouterCatalog.parse("").isEmpty())
        assertTrue(OpenRouterCatalog.parse("not json").isEmpty())
        assertTrue(OpenRouterCatalog.parse("{}").isEmpty())
        assertTrue(OpenRouterCatalog.parse("""{"data":[]}""").isEmpty())
        assertTrue(OpenRouterCatalog.parse("""{"data":[{"id":"x"}]}""").isEmpty())
    }

    @Test
    fun `price label reads sensibly`() {
        val free = parsed.first { it.free }
        assertEquals("Free", free.priceLabel)
        val paid = parsed.first { !it.free && !it.variablePricing }
        assertTrue(paid.priceLabel.startsWith("$"))
        assertTrue(paid.priceLabel.endsWith("per 1M"))
        assertEquals("Varies", parsed.first { it.variablePricing }.priceLabel)
    }
}
