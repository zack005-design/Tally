package com.smartexpense.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SmartInsightsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 24)
    private val food = Category("food", "Food", 0, "", false)
    private val bills = Category("bills", "Bills", 0, "", false)
    private var next = 0
    private fun spend(day: LocalDate, description: String, paise: Long, category: String? = "food", kind: Kind = Kind.EXPENSE,
                      people: List<String> = listOf("self"), reversed: Boolean = false) =
        Record("r${next++}", kind, description, paise, paise, category, null, day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            "", reversed, listOf("wallet"), people)
    private fun build(records: List<Record>, budgets: List<Budget> = emptyList()) =
        SmartInsights.build(records, budgets, listOf(food, bills), today, zone)

    @Test fun forecastProjectsTheLast28DaysPaceAndComparesTheBudget() {
        val records = (1..40).map { spend(today.minusDays(it.toLong()), "lunch", 10_000) }
        val forecast = build(records, listOf(Budget("b", "2026-11", null, 250_000))).forecast!!
        assertEquals(10_000L, forecast.dailyPace)
        assertEquals(6, forecast.daysLeft)
        assertEquals(230_000L, forecast.spentSoFar) // 1–23 November
        assertEquals(290_000L, forecast.projected)
        assertEquals(250_000L, forecast.budget)
        assertNull(build(listOf(spend(today, "lunch", 100))).forecast)
    }

    @Test fun categoryAndLargeEntryAlertsNeedHistoryAndAClearGap() {
        val history = (1L..3L).flatMap { back -> listOf(spend(today.minusMonths(back).withDayOfMonth(10), "lunch", 20_000)) }
        val calm = build(history + spend(today.withDayOfMonth(10), "lunch", 25_000)).alerts
        assertTrue(calm.isEmpty())
        val high = build(history + spend(today.withDayOfMonth(10), "party food", 120_000)).alerts
        assertTrue(high.any { it.title == "Food is higher than usual" })
        val peers = (10..14).map { spend(today.minusDays(it.toLong()), "snack", 20_000) }
        val big = build(peers + spend(today.minusDays(2), "Wedding gift dinner", 300_000)).alerts
        assertTrue(big.any { it.title == "Unusually large: Wedding gift dinner" })
    }

    @Test fun recurringPaymentsNeedASteadyIntervalAndAmount() {
        val netflix = (0..3).map { spend(LocalDate.of(2026, 8, 5).plusMonths(it.toLong()), "Netflix", 64_900, "bills") }
        val recurring = build(netflix).recurring.single()
        assertEquals("Netflix", recurring.description)
        assertEquals(64_900L, recurring.typicalAmount)
        assertEquals(31, recurring.everyDays) // median of the 31-, 30- and 31-day gaps
        assertEquals(LocalDate.of(2026, 12, 6), recurring.nextDate)
        assertEquals(63_722L, recurring.monthlyCost) // ₹649 × 30.4375 / 31 = ₹637.22
        val irregular = listOf(5L, 9L, 40L, 41L).map { spend(today.minusDays(it), "Chai", 2_000) }
        assertTrue(build(irregular).recurring.isEmpty())
        val varying = (0..3).map { spend(LocalDate.of(2026, 8, 5).plusMonths(it.toLong()), "Groceries", 100_000L + it * 40_000) }
        assertTrue(build(varying).recurring.isEmpty())
        val shared = (0..3).map { spend(LocalDate.of(2026, 8, 5).plusMonths(it.toLong()), "Rent", 1_000_000, people = listOf("self", "asha")) }
        assertTrue(build(shared).recurring.isEmpty())
    }

    @Test fun budgetSuggestionsUseTheMedianMonthRoundedUpAndSkipBudgetedCategories() {
        val records = listOf(spend(LocalDate.of(2026, 8, 3), "lunch", 300_050), spend(LocalDate.of(2026, 9, 3), "lunch", 410_000),
            spend(LocalDate.of(2026, 10, 3), "lunch", 520_000))
        val suggestion = build(records).budgets.single()
        assertEquals("food", suggestion.categoryId)
        assertEquals(410_000L, suggestion.amount)
        assertTrue(build(records, listOf(Budget("b", "2026-11", "food", 1))).budgets.isEmpty())
        val odd = listOf(spend(LocalDate.of(2026, 9, 3), "lunch", 300_050), spend(LocalDate.of(2026, 10, 3), "lunch", 300_050))
        assertEquals(310_000L, build(odd).budgets.single().amount)
    }

    @Test fun duplicatesAreSameDescriptionAmountAndDay() {
        val records = listOf(spend(today, "Chai", 2_000), spend(today, "chai ", 2_000), spend(today, "Chai", 2_500),
            spend(today.minusDays(1), "Chai", 2_000), spend(today, "Chai", 2_000, reversed = true))
        val duplicate = build(records).duplicates.single()
        assertEquals(2, duplicate.count)
        assertEquals(today, duplicate.date)
        assertNotNull(SmartInsights.possibleDuplicate("CHAI", 2_000, today, records, zone))
        assertNull(SmartInsights.possibleDuplicate("Chai", 3_000, today, records, zone))
        assertNull(SmartInsights.possibleDuplicate("Chai", null, today, records, zone))
    }
}
