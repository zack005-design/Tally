package com.smartexpense.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SpendingQuestionsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 24) // a Tuesday
    private val food = Category("food", "Food", 0, "", false)
    private val bills = Category("bills", "Bills", 0, "", false)
    private var next = 0
    private fun record(day: LocalDate, description: String, personal: Long, category: String? = "food",
                       kind: Kind = Kind.EXPENSE, reversed: Boolean = false, amount: Long = personal) =
        Record("r${next++}", kind, description, amount, personal, category, null,
            day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), "", reversed, listOf("wallet"), listOf("self"))
    private val records = listOf(
        record(today, "Chai", 2000), record(today.minusDays(1), "masala chai", 3000),
        record(today.withDayOfMonth(1), "Lunch", 25000), record(today.minusDays(3), "Rent", 1_000_000, "bills"),
        record(today.minusMonths(1), "Chai", 1500), record(today, "Chai refund", 500, kind = Kind.REFUND),
        record(today, "Dinner split", 40000, amount = 120000), record(today, "Deleted chai", 9999, reversed = true),
        record(today, "Salary", 0, null, Kind.INCOME, amount = 5_000_000))
    private fun ask(text: String) = SpendingQuestions.answer(text, records, listOf(food, bills), today, zone)

    @Test fun categoryTotalsUseYourShareAndSubtractRefunds() {
        val answer = ask("How much did I spend on food this month?")!!
        assertEquals(2000L + 3000 + 25000 + 40000 - 500, answer.total)
        assertTrue(answer.text.startsWith("You spent ₹695.00 on Food this month"))
        assertEquals(LocalDate.of(2026, 11, 1), answer.start)
    }

    @Test fun descriptionWordsMatchWholeWordsAndPlurals() {
        // "Chai refund" is a refund of chai, so it is subtracted.
        assertEquals(4500L, ask("how much did i spend on chai this week")!!.total)
        assertEquals(4500L, ask("total spent on chais this week")!!.total)
        assertEquals(1500L, ask("how much did I spend on chai last month")!!.total)
        assertEquals(0L, ask("how much did I spend on samosa")!!.total)
    }

    @Test fun periodsResolveToTheRightDates() {
        assertEquals(2000L + 40000 - 500, ask("how much did I spend today")!!.total)
        assertEquals(3000L, ask("how much did I spend yesterday?")!!.total)
        assertEquals(LocalDate.of(2026, 11, 16) to LocalDate.of(2026, 11, 22), ask("total spent last week")!!.let { it.start to it.end })
        assertEquals(1_000_000L, ask("how much did I spend on bills in november")!!.total)
        assertEquals(LocalDate.of(2025, 12, 1), ask("how much did I spend in december")!!.start)
        assertEquals(today.minusDays(6), ask("how much did i spend in the last 7 days")!!.start)
        assertEquals(today.minusDays(6), ask("how much did i spend last 7 days")!!.start)
    }

    @Test fun incomeQuestionsReportIncome() {
        val answer = ask("how much did I earn this month")!!
        assertEquals(5_000_000L, answer.total)
        assertTrue(answer.text.startsWith("Income this month: ₹50000.00"))
    }

    @Test fun ordinaryCommandsAreNotQuestions() {
        listOf("coffee 45 cash", "spent 45 on coffee using cash", "how are you", "how much is in my wallet?").forEach {
            assertFalse(it, SpendingQuestions.isQuestion(it))
            assertNull(it, ask(it))
        }
    }
}
