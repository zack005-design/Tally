package com.smartexpense.domain

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CommandUnicodeInputTest {
    private fun parser(ai: Boolean): CommandExpenseTextParser = if (!ai) CommandExpenseTextParser(null) else
        CommandExpenseTextParser(File("src/localAi/assets/tally-command.bin").inputStream().use(ScratchCommandModel::load),
            extension = File("src/localAi/assets/tally-command-extended.bin").inputStream().use { ScratchCommandModel.load(it, true) })

    @Test fun decimalDigitsAndPastedSpacesMatchAsciiDrafts() {
        for (ai in listOf(false, true)) {
            val p = parser(ai)
            val expected = p.parse("coffee 45.50 cash", emptyList())
            listOf("coffee ४५.५० cash", "coffee ٤٥.٥٠ cash", "coffee ٤٥٫٥٠ cash", "coffee ４５．５０ cash",
                "coffee\u00a045.50\u202fcash").forEach { text ->
                val actual = p.parse(text, emptyList())
                assertEquals(text, expected.amount, actual.amount)
                assertEquals(text, expected.fromAccount, actual.fromAccount)
                assertEquals(text, expected.description, actual.description)
            }
        }
    }

    @Test fun learnedHinglishAcceptsIndianDigitsWithoutChangingWeights() {
        val result = parser(true).parse("maine chai ke liye cash se ३० diye", emptyList())
        assertEquals(3000L, result.amount)
        assertEquals("chai", result.description)
        assertEquals("wallet", result.fromAccount)
    }

    @Test fun groupedUnicodeAmountsKeepExactPaise() {
        assertEquals(123450L, parser(false).parse("coffee １，２３４．５０ cash", emptyList()).amount)
    }

    @Test fun hiddenControlsNeverProduceAFilledDraft() {
        listOf("coffee 4\u200b5 cash", "coffee 45 cash\u202e", "I did n\u200bot pay 45 for coffee using cash").forEach { text ->
            val result = parser(false).parse(text, emptyList())
            assertNull(text, result.amount)
            assertNull(text, result.fromAccount)
            assertTrue(text, result.warnings.isNotEmpty())
        }
    }

    @Test fun normalizedSignsForeignCurrenciesAndMultipleAmountsRequireReview() {
        listOf("coffee －４５ cash", "coffee ﹣४५ cash", "coffee ＋45 cash", "coffee ￥45 cash",
            "coffee ₩45 cash", "coffee ₿45 cash", "coffee ٤٥ and taxi ٦٠ cash").forEach { text ->
            val result = parser(false).parse(text, emptyList())
            assertNull(text, result.amount)
            assertTrue(text, result.warnings.isNotEmpty())
        }
    }

    @Test fun supplementaryDigitsCannotBeSilentlyDropped() {
        val four = String(Character.toChars(0x1D7DC))
        assertEquals(4500L, parser(false).parse("coffee ${four}5 cash", emptyList()).amount)
        val rod = String(Character.toChars(0x1D360))
        assertNull(parser(false).parse("coffee ${rod}5 cash", emptyList()).amount)
    }

    @Test fun foreignSymbolsAreReviewedBeforeSharedAndOtherTransactionRouting() {
        listOf("dinner ￥45 cash split equally", "Transferred ￥45 from bank to cash",
            "Salary ￥45 gpay", "Refund ￥45 cash").forEach { text ->
            val result = parser(false).parse(text, emptyList())
            assertNull(text, result.amount)
            assertTrue(text, result.warnings.any { it.contains("currency") })
        }
    }

    @Test fun invalidSignedAmountsAreReviewedBeforeSharedAndOtherRouting() {
        val p = parser(false)
        listOf("dinner －４５ cash split equally", "Transferred －４５ from bank to cash",
            "Salary ＋４５ gpay", "Refund ﹣४५ cash").forEach { text ->
            val result = p.parse(text, emptyList())
            assertNull(text, result.amount)
            assertTrue(text, result.warnings.any { it.contains("Invalid amount") })
        }
        val people = listOf(PersonRef("rahul", "Rahul"))
        val valid = "Food for 900. I paid 900, Rahul paid 0. Split equally."
        assertEquals(RuleBasedTextParser().parse(valid, people), p.parse(valid, people))
    }

    @Test fun nonDecimalNumberCharactersAreNotRewrittenAsAmounts() {
        val result = parser(false).parse("coffee ⁴⁵ cash", emptyList())
        assertNull(result.amount)
        assertNull(parser(false).parse("coffee 4⁵ cash", emptyList()).amount)
    }
}
