package com.smartexpense.domain

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ScratchCommandModelTest {
    private fun model(): ScratchCommandModel = File("src/localAi/assets/tally-command.bin").inputStream().use(ScratchCommandModel::load)

    @Test fun exactAmountsAreCopiedInsteadOfGenerated() {
        val model = model()
        listOf("Coffee 45 cash" to 4500L, "Lunch 180 GPay" to 18000L,
            "Paid 99.50 for snacks using google pay" to 9950L,
            "office lunch 0.01 wallet" to 1L).forEach { (text, amount) ->
            val draft = requireNotNull(model.suggest(text)) { text }
            assertEquals(amount, draft.amount)
        }
    }

    @Test fun uncertainCommandsDoNotBecomeNeuralExpenseDrafts() {
        val model = model()
        listOf("coffee 45", "coffee 45 cash gpay", "coffee 45 cash google pay", "coffee 45 g pay wallet", "coffee 45 and 80 cash",
            "coffee -45 cash", "coffee 45.001 cash", "split lunch 900 gpay",
            "refund 45 coffee cash", "I did not spend 45 on coffee using cash",
            "transfer 400 from wallet to gpay", "coffee 45 dollars cash", "x".repeat(241)).forEach {
            assertNull(it, model.suggest(it))
        }
    }

    @Test fun malformedModelIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ScratchCommandModel.load("bad".byteInputStream()) }
    }

    @Test fun portableRuntimeMatchesPythonForAllFixtureFields() {
        val model = model()
        val lines = requireNotNull(javaClass.getResourceAsStream("/command-parity.tsv")).bufferedReader().use { it.readLines() }
        assertTrue(lines.size >= 100)
        val decoder = java.util.Base64.getDecoder()
        lines.forEach { line ->
            val fields = line.split('\t')
            val text = String(decoder.decode(fields[0]), Charsets.UTF_8)
            val draft = model.suggest(text)
            if (fields[1] == "-") assertNull(text, draft) else {
                requireNotNull(draft) { text }
                assertEquals(text, fields[1].toLong(), draft.amount)
                assertEquals(text, fields[2], draft.account)
                assertEquals(text, String(decoder.decode(fields[3]), Charsets.UTF_8), draft.description)
                assertEquals(text, fields[4].toDouble(), draft.confidence.toDouble(), 0.00002)
            }
        }
    }

    @Test fun missingModelKeepsExistingParserBehavior() {
        val text = "Transferred 1000 from bank to cash"
        assertEquals(RuleBasedTextParser().parse(text, emptyList()), CommandExpenseTextParser(null).parse(text, emptyList()))
    }
}
