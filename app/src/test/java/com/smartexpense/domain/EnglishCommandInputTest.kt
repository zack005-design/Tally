package com.smartexpense.domain

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.*
import org.junit.Test

class EnglishCommandInputTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-25T08:00:00Z"), ZoneId.of("Asia/Kolkata"))
    private fun parser(ai: Boolean) = CommandExpenseTextParser(
        if (ai) File("src/localAi/assets/tally-command.bin").inputStream().use(ScratchCommandModel::load) else null,
        RuleBasedTextParser(clock),
        if (ai) File("src/localAi/assets/tally-command-extended.bin").inputStream().use { ScratchCommandModel.load(it, true) } else null,
        clock
    )

    @Test fun datesDoNotPreventLearnedDescriptionExtraction() {
        val result = parser(true).parse("I paid 45 for coffee using cash on 2026-10-21", emptyList())
        assertEquals(4500L, result.amount)
        assertEquals("coffee", result.description)
        assertEquals(LocalDate.of(2026,10,21), result.date)
    }

    @Test fun englishCalendarDatesWorkInBothEditions() {
        for (ai in listOf(false,true)) {
            val p=parser(ai)
            listOf("coffee 45 cash on 21 October 2026", "coffee 45 cash on October 21, 2026",
                "coffee 45 cash on 21 Oct 2026").forEach { text ->
                val result=p.parse(text,emptyList())
                assertEquals(text,4500L,result.amount)
                assertEquals(text,"coffee",result.description)
                assertEquals(text,LocalDate.of(2026,10,21),result.date)
            }
        }
    }

    @Test fun relativeDatesUseTheLoggingClock() {
        val today=LocalDate.now(clock)
        val cases=mapOf("two days ago" to today.minusDays(2), "2 days ago" to today.minusDays(2),
            "day before yesterday" to today.minusDays(2), "last Monday" to today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY)))
        for ((phrase,expected) in cases) {
            val result=parser(false).parse("coffee 45 cash $phrase",emptyList())
            assertEquals(phrase,4500L,result.amount)
            assertEquals(phrase,expected,result.date)
        }
    }

    @Test fun shorthandKeepsExactAmountsInBothEditions() {
        for (ai in listOf(false,true)) {
            val p=parser(ai)
            mapOf("1.25k" to 125000L,"2K" to 200000L,"2 thousand" to 200000L,"1.25 lakh" to 12500000L).forEach { (amount,expected) ->
                val result=p.parse("coffee $amount cash",emptyList())
                assertEquals(amount,expected,result.amount)
                assertEquals(amount,"coffee",result.description)
                assertTrue(amount,result.fromAccount in setOf("cash","wallet"))
            }
        }
    }

    @Test fun englishSentenceCombinesShorthandAndPastDate() {
        val result=parser(true).parse("I paid 1.5k for groceries using cash yesterday",emptyList())
        assertEquals(150000L,result.amount)
        assertEquals("groceries",result.description)
        assertEquals(LocalDate.of(2026,10,24),result.date)
    }

    @Test fun ambiguousAndInvalidDateInputsRequireCorrection() {
        listOf("coffee 45 cash on 31 February 2026", "coffee 45 cash on 2026-02-30",
            "coffee 45 cash yesterday today", "coffee 45 cash next Monday",
            "coffee 45 cash on 31 October 2026", "coffee 45 cash on 10/11/2026").forEach { text ->
            val result=parser(false).parse(text,emptyList())
            assertNull(text,result.amount)
            assertTrue(text,result.warnings.isNotEmpty())
        }
    }

    @Test fun amountShorthandNeverHidesSignsCurrencyOrInvalidPrecision() {
        listOf("coffee -1.5k cash","coffee 1.234k cash","coffee 1.5kk cash","coffee 2k USD cash",
            "coffee 1.5k and taxi 2k cash","coffee 999999999999999999999k cash").forEach { text ->
            val result=parser(false).parse(text,emptyList())
            assertNull(text,result.amount)
            assertTrue(text,result.warnings.isNotEmpty())
        }
    }

    @Test fun dateSpacingAndSignedOffsetsCannotChangeTheMeaning() {
        val p=parser(false)
        assertEquals(LocalDate.of(2026,10,23), p.parse("coffee 45 cash day   before yesterday",emptyList()).date)
        listOf("coffee 45 cash -2 days ago", "coffee 45 cash 1.2 days ago", "coffee 45 cash last night",
            "coffee 45 cash two weeks ago", "coffee 45 cash Monday").forEach { text ->
            val result=p.parse(text,emptyList())
            assertNull(text,result.amount)
            assertNull(text,result.date)
            assertTrue(text,"date" in result.missing)
        }
    }

    @Test fun unsupportedSpacedScaleWordsAreNotSilentlyIgnored() {
        listOf("coffee 1.5 million cash", "coffee 1,000 thousand cash", "coffee 1,000 lakh cash", "coffee 1k thousand cash").forEach {
            assertNull(it,parser(false).parse(it,emptyList()).amount)
        }
    }

    @Test fun wordedFutureOffsetsRequireDateCorrection() {
        listOf("coffee 45 cash in two days", "coffee 45 cash in ten days", "coffee 45 cash two days from now").forEach { text ->
            val result=parser(false).parse(text,emptyList())
            assertNull(text,result.amount)
            assertNull(text,result.date)
            assertTrue(text,"date" in result.missing)
        }
    }

    @Test fun compoundWordOffsetsAreNotReadAsTheirLastWord() {
        listOf("coffee 45 cash twenty two days ago", "coffee 45 cash one hundred days ago", "coffee 45 cash about two days ago").forEach { text ->
            val result=parser(false).parse(text,emptyList())
            assertNull(text,result.amount)
            assertNull(text,result.date)
            assertTrue(text,"date" in result.missing)
        }
    }

    @Test fun calendarResolutionUsesTheInjectedTimezoneAndLeapDay() {
        val midnight=Clock.fixed(Instant.parse("2024-02-29T20:00:00Z"),ZoneId.of("Asia/Kolkata"))
        val p=CommandExpenseTextParser(null,RuleBasedTextParser(midnight),null,midnight)
        assertEquals(LocalDate.of(2024,3,1),p.parse("coffee 45 cash today",emptyList()).date)
        assertEquals(LocalDate.of(2024,2,29),p.parse("coffee 45 cash yesterday",emptyList()).date)
        assertEquals(LocalDate.of(2024,2,23),p.parse("coffee 45 cash last Friday",emptyList()).date)
    }

    @Test fun currencyLabelsAndShorthandWorkTogether() {
        mapOf("INR1.5k" to 150000L, "Rs1k" to 100000L, "Rs.1k" to 100000L,
            "1.5k rupees" to 150000L, "1 thousand INR" to 100000L).forEach { (amount,expected) ->
            assertEquals(amount,expected,parser(false).parse("coffee $amount cash",emptyList()).amount)
        }
    }

    @Test fun attachedUnknownCurrencyOrWordsCannotHideAnAmountSuffix() {
        listOf("coffee USD1k cash","coffee USD45 cash","coffee ABC1.5k cash").forEach { text ->
            assertNull(text,parser(false).parse(text,emptyList()).amount)
        }
    }

    @Test fun relativeDateSubstringsCannotHideAnUnresolvedOffset() {
        listOf("coffee 45 cash two days before yesterday","coffee 45 cash since yesterday").forEach { text ->
            val result=parser(false).parse(text,emptyList())
            assertNull(text,result.amount)
            assertNull(text,result.date)
            assertTrue(text,"date" in result.missing)
        }
    }

    @Test fun sharedAndOtherTransactionRoutesKeepExactValues() {
        val p=parser(false)
        val people=listOf(PersonRef("rahul","Rahul"))
        val shared=p.parse("Food for 1.5k. I paid 1k, Rahul paid 500. Split equally yesterday",people)
        assertEquals(150000L,shared.amount)
        assertEquals(mapOf("self" to 100000L,"rahul" to 50000L),shared.contributions)
        assertEquals(SplitRule.Equal,shared.splitRule)
        assertEquals(LocalDate.of(2026,10,24),shared.date)
        val income=p.parse("Salary 2k gpay yesterday",emptyList())
        assertEquals(Kind.INCOME,income.kind)
        assertEquals(200000L,income.amount)
        val transfer=p.parse("Transferred 2k from bank to cash yesterday",emptyList())
        assertEquals(Kind.TRANSFER,transfer.kind)
        assertEquals(200000L,transfer.amount)
        listOf("HDFC123","INR123","Vault2k").forEach { account ->
            val text="transfer 500 from $account to wallet"
            assertEquals(text,RuleBasedTextParser(clock).parse(text,emptyList()),p.parse(text,emptyList()))
        }
    }

    @Test fun correctingAnInvalidAmountDoesNotLoseAValidPastDate() {
        val result=parser(false).parse("coffee 1.234k cash two days ago",emptyList())
        assertNull(result.amount)
        assertEquals(LocalDate.of(2026,10,23),result.date)
        assertFalse("date" in result.missing)
    }

    @Test fun futureAndNegatedSentencesRemainIncompleteAfterDateRemoval() {
        listOf("I will pay 2k for coffee using cash today","I did not pay 2k for coffee using cash yesterday").forEach { text ->
            assertNull(text,parser(false).parse(text,emptyList()).amount)
        }
    }
}
