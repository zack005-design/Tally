package com.smartexpense.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Spending for one calendar year, built only from records the user entered.
 * Uses the same rules as monthly reports: only your share of a shared bill counts,
 * refunds reduce spending, and transfers and repayments are not spending.
 *
 * For the year in progress, [previousSpending] covers the same dates last year so
 * the comparison is like for like.
 */
data class YearSummary(
    val year: Int,
    val through: LocalDate,
    val spending: Long,
    val income: Long,
    val months: List<Long>,
    val categories: Map<String?, Long>,
    val previousSpending: Long,
    val recordCount: Int,
    /** First year with an active record, so the screen does not offer empty years before it. */
    val earliestYear: Int? = null,
    /** Your spending on records carrying each tag. A record with two tags counts towards both. */
    val tags: Map<String, Long> = emptyMap(),
) {
    val hasRecords: Boolean get() = recordCount > 0

    /** Whole-percent change against the same period last year, or null without a baseline. */
    val changePercent: Int?
        get() = if (previousSpending <= 0) null
        else Math.round((spending - previousSpending) * 100.0 / previousSpending).toInt()

    /** Month number (1–12) with the highest spending, or null when nothing was spent. */
    val busiestMonth: Int?
        get() = months.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.index?.plus(1)
}

object YearSummaries {
    fun build(records: List<Record>, year: Int, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): YearSummary {
        val start = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        val inProgress = today.year == year
        val through = if (inProgress) today else yearEnd
        val previousThrough = if (inProgress) today.minusYears(1) else yearEnd.minusYears(1)
        val current = Reports.period(records, start, through, zone)
        val previous = Reports.period(records, start.minusYears(1), previousThrough, zone)
        val months = (1..12).map { month ->
            val period = YearMonth.of(year, month)
            if (period.atDay(1).isAfter(through)) 0L
            else Reports.period(records, period.atDay(1), minOf(period.atEndOfMonth(), through), zone).spending
        }
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = through.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inYear = records.filter { !it.reversed && it.date >= from && it.date < end }
        val tags = Tags.all(inYear.filter { it.kind == Kind.EXPENSE || it.kind == Kind.REFUND })
            .associateWith { tag -> Reports.period(inYear.filter { Tags.has(it, tag) }, start, through, zone).spending }
            .filterValues { it != 0L }
        return YearSummary(
            year = year,
            through = through,
            spending = current.spending,
            income = current.income,
            months = months,
            categories = current.categories.filterValues { it != 0L },
            previousSpending = previous.spending,
            recordCount = inYear.size,
            tags = tags,
        )
    }
}
