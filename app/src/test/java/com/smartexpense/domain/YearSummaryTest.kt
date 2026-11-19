package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class YearSummaryTest {
    private val zone = ZoneOffset.UTC
    private var next = 0
    private fun record(date: String, kind: Kind, amount: Long, personal: Long = amount, category: String? = "food", reversed: Boolean = false) =
        Record("r${next++}", kind, "Entry", amount, personal, category, null,
            LocalDate.parse(date).atTime(12, 0).toInstant(zone).toEpochMilli(), "", reversed)

    @Test fun countsYourShareAndRefundsButNotTransfersOrReversedRecords() {
        val records = listOf(
            record("2025-01-10", Kind.EXPENSE, 90000, personal = 30000),
            record("2025-01-20", Kind.REFUND, 5000),
            record("2025-02-05", Kind.TRANSFER, 100000, personal = 0, category = null),
            record("2025-03-01", Kind.INCOME, 250000, category = null),
            record("2025-03-02", Kind.EXPENSE, 40000, category = "travel"),
            record("2025-03-03", Kind.EXPENSE, 99999, reversed = true),
        )
        val summary = YearSummaries.build(records, 2025, today = LocalDate.parse("2026-06-01"), zone = zone)
        assertEquals(65000L, summary.spending)
        assertEquals(250000L, summary.income)
        assertEquals(listOf(25000L, 0L, 40000L) + List(9) { 0L }, summary.months)
        assertEquals(mapOf<String?, Long>("food" to 25000L, "travel" to 40000L), summary.categories)
        assertEquals(LocalDate.parse("2025-12-31"), summary.through)
        assertEquals(5, summary.recordCount)
        assertEquals(3, summary.busiestMonth)
    }

    @Test fun yearInProgressIsComparedWithTheSameDatesLastYear() {
        val records = listOf(
            record("2025-03-10", Kind.EXPENSE, 20000),
            record("2025-04-01", Kind.EXPENSE, 70000),
            record("2026-02-01", Kind.EXPENSE, 30000),
            record("2026-03-20", Kind.EXPENSE, 50000),
        )
        val summary = YearSummaries.build(records, 2026, today = LocalDate.parse("2026-03-15"), zone = zone)
        assertEquals(30000L, summary.spending)
        assertEquals(20000L, summary.previousSpending)
        assertEquals(50, summary.changePercent)
        assertEquals(0L, summary.months[3])
        assertEquals(LocalDate.parse("2026-03-15"), summary.through)
    }

    @Test fun noBaselineMeansNoPercentageAndEmptyYearsHaveNoBusiestMonth() {
        val summary = YearSummaries.build(listOf(record("2024-05-05", Kind.EXPENSE, 1000)), 2024, today = LocalDate.parse("2026-01-01"), zone = zone)
        assertNull(summary.changePercent)
        val empty = YearSummaries.build(emptyList(), 2023, today = LocalDate.parse("2026-01-01"), zone = zone)
        assertFalse(empty.hasRecords)
        assertNull(empty.busiestMonth)
        assertEquals(0L, empty.spending)
    }

    @Test fun tagTotalsUseYourShareAndCountRecordsUnderEveryTag() {
        val records = listOf(
            record("2025-02-01", Kind.EXPENSE, 90000, personal = 30000).copy(tags = listOf("Goa trip", "Friends")),
            record("2025-02-02", Kind.EXPENSE, 20000).copy(tags = listOf("goa trip")),
            record("2025-02-03", Kind.REFUND, 5000).copy(tags = listOf("Goa trip")),
            record("2025-02-04", Kind.TRANSFER, 70000, personal = 0, category = null).copy(tags = listOf("Moving")),
            record("2025-02-05", Kind.EXPENSE, 8000, reversed = true).copy(tags = listOf("Goa trip")),
        )
        val summary = YearSummaries.build(records, 2025, today = LocalDate.parse("2026-01-01"), zone = zone)
        assertEquals(mapOf("Friends" to 30000L, "Goa trip" to 45000L), summary.tags)
    }

    @Test fun leapDayInProgressComparesWithTheEndOfFebruary() {
        val records = listOf(record("2027-02-28", Kind.EXPENSE, 1000), record("2028-02-29", Kind.EXPENSE, 3000))
        val summary = YearSummaries.build(records, 2028, today = LocalDate.parse("2028-02-29"), zone = zone)
        assertEquals(1000L, summary.previousSpending)
        assertEquals(200, summary.changePercent)
    }
}
