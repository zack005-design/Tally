package com.smartexpense.domain

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ExtendedCommandModelTest {
    private fun baseline() = File("src/localAi/assets/tally-command.bin").inputStream().use(ScratchCommandModel::load)
    private fun extension() = File("src/localAi/assets/tally-command-extended.bin").inputStream().use { ScratchCommandModel.load(it, true) }

    @Test fun compositeRuntimeMatchesFrozenPythonFixtures() {
        val old = baseline(); val extended = extension()
        val lines = requireNotNull(javaClass.getResourceAsStream("/command-extended-parity.tsv")).bufferedReader().use { it.readLines() }
        assertEquals(386, lines.size)
        val decoder = java.util.Base64.getDecoder()
        lines.forEach { line ->
            val f = line.split('\t'); val text = String(decoder.decode(f[0]), Charsets.UTF_8)
            val oldResult = old.suggest(text)
            val result = oldResult ?: extended.suggest(text)
            if (f[1] == "-") assertNull(text, result) else {
                assertNotNull(text, result); result!!
                assertEquals(text, f[1].toLong(), result.amount)
                assertEquals(text, f[2], result.account)
                assertEquals(text, String(decoder.decode(f[3]), Charsets.UTF_8), result.description)
                assertEquals(text, f[4].toDouble(), result.confidence.toDouble(), .00005)
                assertEquals(text, if (oldResult == null) "extension" else "baseline", f[5])
            }
        }
    }

    @Test fun newPhrasesBecomeReviewableDraftsAndUnsafeCommandsStayIncomplete() {
        val parser = CommandExpenseTextParser(baseline(), extension = extension())
        listOf("Paid 49 for coffee using gpy" to "coffee", "maine chai ke liye cash se 30 diye" to "chai").forEach { (text, expected) ->
            val result = parser.parse(text, emptyList())
            assertEquals(expected, result.description)
            assertNotNull(result.amount)
            assertNotNull(result.fromAccount)
            assertTrue(result.warnings.any { it.contains("Review") })
        }
        listOf("maine chai pe 25 cash se nahi diye", "coffee 45 wallett gpy", "chai 25 aur taxi 100 cash").forEach {
            assertNull(it, parser.parse(it, emptyList()).amount)
        }
    }
}
