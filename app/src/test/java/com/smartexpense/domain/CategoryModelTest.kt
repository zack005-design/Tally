package com.smartexpense.domain

import java.io.ByteArrayInputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class CategoryModelTest {
    private val bundle = File("src/localAi/assets/tally-category.bin").readBytes()
    private val model = CategoryModel.load(ByteArrayInputStream(bundle))
    private val clock = Clock.fixed(Instant.parse("2026-10-25T08:00:00Z"), ZoneId.of("Asia/Kolkata"))

    @Test fun matchesEveryPythonParityCase() {
        val lines = File("src/test/resources/category-parity.tsv").readLines().filter(String::isNotBlank)
        assertTrue(lines.size > 1000)
        for (line in lines) {
            val (encoded, expected, confidence) = line.split('\t')
            val text = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
            val score = model.score(text)
            if (confidence.toDouble() == 0.0) { assertNull(text, score); continue }
            assertEquals(text, confidence.toDouble(), score!!.confidence.toDouble(), 1e-4)
            assertEquals(text, if (expected == "-") null else expected, model.suggest(text)?.category)
        }
    }

    @Test fun abstainsOnUnfamiliarOrEmptyDescriptions() {
        listOf("", "1234", "qwerty", "misc").forEach { assertNull(it, model.suggest(it)) }
    }

    @Test fun rejectsCorruptOrTruncatedBundles() {
        assertThrows(IllegalArgumentException::class.java) { CategoryModel.load(ByteArrayInputStream(bundle.copyOf(bundle.size - 4))) }
        assertThrows(IllegalArgumentException::class.java) { CategoryModel.load(ByteArrayInputStream(bundle + byteArrayOf(0))) }
        val wrongMagic = bundle.copyOf().also { it[0] = 0 }
        assertThrows(IllegalArgumentException::class.java) { CategoryModel.load(ByteArrayInputStream(wrongMagic)) }
    }

    @Test fun fillsOnlyMissingCategoriesOfPersonalExpenses() {
        val parser = CommandExpenseTextParser(null, RuleBasedTextParser(clock), null, clock, model)
        val netflix = parser.parse("netflix 649 gpay", emptyList())
        assertEquals("Subscriptions", netflix.category)
        assertEquals(64900L, netflix.amount)
        assertTrue(netflix.warnings.any { "Category suggested" in it })
        // Rules categories are never replaced.
        assertEquals("Food", parser.parse("lunch 120 cash", emptyList()).category)
        // Non-expense and shared drafts are left to the existing flows.
        assertNull(parser.parse("Salary 2000 gpay", emptyList()).category)
        assertNull(parser.parse("electricity 900 wallet but split with rahul", listOf(PersonRef("rahul", "Rahul"))).category)
        // Without the model, behaviour is unchanged.
        assertNull(CommandExpenseTextParser(null, RuleBasedTextParser(clock), null, clock).parse("netflix 649 gpay", emptyList()).category)
    }
}
