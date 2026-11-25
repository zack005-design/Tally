package com.smartexpense.domain

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ForeignAmountTest {
    private fun parser(ai: Boolean) = CommandExpenseTextParser(
        if (ai) File("src/localAi/assets/tally-command.bin").inputStream().use(ScratchCommandModel::load) else null,
        extension = if (ai) File("src/localAi/assets/tally-command-extended.bin").inputStream().use { ScratchCommandModel.load(it, true) } else null)

    @Test fun foreignAmountsConvertExactlyWithTheTypedRate() {
        val cases = mapOf("lunch 12 usd at 83.5 cash" to 100200L, "lunch 12 USD @ ₹83.5 cash" to 100200L,
            "coffee $4.50 at 83.25 cash" to 37463L, "dinner €20 at rs 90 gpay" to 180000L,
            "taxi 35 aed at 22.7345 per aed cash" to 79571L)
        for (ai in listOf(false, true)) for ((text, expected) in cases) {
            val result = parser(ai).parse(text, emptyList())
            assertEquals("$text (ai=$ai)", expected, result.amount)
            assertTrue(text, result.notes!!.startsWith("Original: "))
            assertTrue(text, result.warnings.any { it.startsWith("Converted ") })
        }
        assertEquals("Original: USD 4.5 · INR per unit 83.25", parser(false).parse("coffee $4.50 at 83.25 cash", emptyList()).notes)
        assertTrue(parser(false).parse("coffee $4.50 at 83.25 cash", emptyList()).warnings.any { "rounded to the nearest paisa" in it })
    }

    @Test fun foreignAmountsWithoutARateOrWithTwoCurrenciesStayIncomplete() {
        listOf("lunch 12 usd cash", "coffee $4.50 cash", "lunch 12 usd at 83 and 5 eur at 90 cash", "lunch 12 usd at 0 cash").forEach { text ->
            val result = parser(false).parse(text, emptyList())
            assertNull(text, result.amount)
            assertNull(text, result.notes)
        }
    }

    @Test fun ordinaryWordsAreNotCurrencies() {
        listOf("chai 2 cup at 30 cash", "coffee 45 cash").forEach { text ->
            assertEquals(text, ForeignAmount.Result(text), ForeignAmount.apply(text))
        }
    }
}
