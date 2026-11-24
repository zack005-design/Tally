package com.smartexpense.domain

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CommandArithmeticTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-25T08:00:00Z"), ZoneId.of("Asia/Kolkata"))
    private fun parser(ai: Boolean) = CommandExpenseTextParser(
        if (ai) File("src/localAi/assets/tally-command.bin").inputStream().use(ScratchCommandModel::load) else null,
        RuleBasedTextParser(clock),
        if (ai) File("src/localAi/assets/tally-command-extended.bin").inputStream().use { ScratchCommandModel.load(it, true) } else null,
        clock)

    @Test fun exactCalculationsBecomeOneReviewableAmountInBothEditions() {
        val cases = mapOf(
            "coffee 40+25 cash" to 6500L, "coffee 40 + 25 + 10 cash" to 7500L, "chai 3x15 cash" to 4500L,
            "chai 3 x 15 cash" to 4500L, "chai 3 * 15 cash" to 4500L, "chai 3 × 15 cash" to 4500L,
            "snacks 2 x 15 + 20 cash" to 5000L, "coffee 40 plus 25 cash" to 6500L, "coffee ₹40 + ₹25 cash" to 6500L,
            "groceries 1.5k + 250 cash" to 175000L, "Rs. 40 + Rs. 25 for coffee using cash" to 6500L,
            "lunch 450 + 5% gst gpay" to 47250L, "lunch 450 + 18% cash" to 53100L, "shoes 2000 - 10% cash" to 180000L,
            "shoes 2000 minus 10% discount cash" to 180000L, "fruit 2.5 x 3.33 cash" to 833L,
            "lunch 333 + 18% cash" to 39294L, "lunch 99.99 + 18% gst cash" to 11799L)
        for (ai in listOf(false, true)) for ((text, expected) in cases) {
            val result = parser(ai).parse(text, emptyList())
            assertEquals("$text (ai=$ai)", expected, result.amount)
            assertTrue(text, result.warnings.any { it.startsWith("Calculated amount:") })
        }
        assertTrue(parser(false).parse("fruit 2.5 x 3.33 cash", emptyList()).warnings.any { "rounded to the nearest paisa" in it })
    }

    @Test fun eachPricesKeepTheItemAsTheDescription() {
        for (ai in listOf(false, true)) {
            val result = parser(ai).parse("2 chai at 15 each cash", emptyList())
            assertEquals(3000L, result.amount)
            assertEquals("chai", result.description)
            assertEquals(Kind.EXPENSE, result.kind)
        }
        assertEquals(15000L, parser(false).parse("3 movie tickets @ 50 each gpay", emptyList()).amount)
    }

    @Test fun unsafeOrAmbiguousArithmeticIsNeverGuessed() {
        listOf("taxi 200-250 cash", "coffee 80/2 cash", "coffee -40 + 25 cash", "coffee 40 + 25 cash and tea 10 + 5 cash",
            "coffee 0 x 25 cash", "coffee 40 + 150% cash", "coffee 40 + 0% cash", "coffee 40 + 25.555 cash",
            "coffee 1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10 + 11 cash", "coffee 99999999 x 99 cash").forEach { text ->
            val result = parser(false).parse(text, emptyList())
            assertNull(text, result.amount)
            assertTrue(text, result.warnings.isNotEmpty())
        }
    }

    @Test fun ordinaryCommandsAreUnchanged() {
        listOf("coffee 45 cash", "I paid 1.5k for groceries using cash yesterday", "Salary 2000 gpay").forEach { text ->
            assertFalse(text, parser(true).parse(text, emptyList()).warnings.any { it.startsWith("Calculated amount:") })
        }
        assertEquals(CommandArithmetic.Result("coffee 45 cash"), CommandArithmetic.apply("coffee 45 cash"))
    }

    @Test fun conflictingUpiAppAccountsNeedManualEntry() {
        listOf("biryani 280 upi or cash", "chai 20 paytm phonepe").forEach { text ->
            assertNull(text, parser(true).parse(text, emptyList()).amount)
        }
    }
}
