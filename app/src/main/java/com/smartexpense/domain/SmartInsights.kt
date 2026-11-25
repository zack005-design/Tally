package com.smartexpense.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * On-device insights computed from the user's own records with transparent statistics; no model
 * weights and no network. Every number can be traced to saved entries, and nothing is saved or changed.
 */
object SmartInsights {
    data class Forecast(val spentSoFar: Long, val projected: Long, val dailyPace: Long, val daysLeft: Int, val budget: Long?)
    data class Alert(val title: String, val detail: String)
    data class Recurring(val description: String, val typicalAmount: Long, val everyDays: Int, val nextDate: LocalDate,
                         val monthlyCost: Long, val occurrences: Int)
    data class BudgetSuggestion(val categoryId: String, val categoryName: String, val amount: Long, val basedOnMonths: Int)
    data class Duplicate(val description: String, val amount: Long, val date: LocalDate, val count: Int)
    data class Report(val forecast: Forecast?, val alerts: List<Alert>, val recurring: List<Recurring>,
                      val budgets: List<BudgetSuggestion>, val duplicates: List<Duplicate>)

    fun build(records: List<Record>, budgets: List<Budget>, categories: List<Category>, today: LocalDate,
              zone: ZoneId = ZoneId.systemDefault()): Report {
        val personal = records.filter { !it.reversed && (it.kind == Kind.EXPENSE || it.kind == Kind.REFUND) }
        fun day(record: Record) = Instant.ofEpochMilli(record.date).atZone(zone).toLocalDate()
        fun spend(record: Record) = if (record.kind == Kind.REFUND) -record.personalAmount else record.personalAmount
        fun total(from: LocalDate, to: LocalDate, filter: (Record) -> Boolean = { true }) =
            Finance.total(personal.filter { day(it) in from..to && filter(it) }.map(::spend))
        val names = categories.associate { it.id to it.name }
        return Report(forecast(personal, budgets, today, ::day, ::total),
            alerts(personal, names, today, ::day, ::total),
            recurring(records.filter { !it.reversed && it.kind == Kind.EXPENSE && it.personIds.all { p -> p == "self" } }, today, ::day),
            budgetSuggestions(personal, budgets, categories, today, ::total),
            duplicates(records.filter { !it.reversed && it.kind == Kind.EXPENSE }, today, ::day))
    }

    /** Month-end projection: spent so far plus the last 28 days' average daily spend for the days left. */
    private fun forecast(personal: List<Record>, budgets: List<Budget>, today: LocalDate, day: (Record) -> LocalDate,
                         total: (LocalDate, LocalDate, (Record) -> Boolean) -> Long): Forecast? {
        val start = today.withDayOfMonth(1)
        val history = personal.filter { day(it) < today }
        if (history.none { day(it) <= today.minusDays(14) }) return null // too little history to project honestly
        val pace = total(today.minusDays(28), today.minusDays(1)) { true } / 28
        val daysLeft = today.lengthOfMonth() - today.dayOfMonth
        val soFar = total(start, today) { true }
        val month = YearMonth.from(today).toString()
        val budget = budgets.filter { it.month == month && it.categoryId == null }.maxOfOrNull { it.amount }
        return Forecast(soFar, Math.addExact(soFar, Math.multiplyExact(maxOf(pace, 0L), daysLeft.toLong())), maxOf(pace, 0L), daysLeft, budget)
    }

    /** Category spending well above its usual level for the same point in the month, and unusually large entries. */
    private fun alerts(personal: List<Record>, names: Map<String, String>, today: LocalDate, day: (Record) -> LocalDate,
                       total: (LocalDate, LocalDate, (Record) -> Boolean) -> Long): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val elapsed = today.dayOfMonth.toLong()
        for ((categoryId, name) in names) {
            val now = total(today.withDayOfMonth(1), today) { it.categoryId == categoryId }
            val previous = (1L..3L).map { back ->
                val month = YearMonth.from(today).minusMonths(back)
                total(month.atDay(1), month.atDay(minOf(elapsed.toInt(), month.lengthOfMonth()))) { it.categoryId == categoryId }
            }
            if (previous.count { it > 0 } < 2) continue
            val usual = previous.sum() / 3
            if (now >= 50_000 + usual && now * 2 >= usual * 3)
                alerts += Alert("$name is higher than usual", "${editorial(now)} so far this month, against about ${editorial(usual)} by this date in the last 3 months.")
        }
        val recent = personal.filter { it.kind == Kind.EXPENSE && day(it) >= today.minusDays(6) }
        for (record in recent) {
            val peers = personal.filter { it.kind == Kind.EXPENSE && it.categoryId == record.categoryId && it.id != record.id &&
                day(it) in today.minusDays(90)..today }.map { it.personalAmount }.sorted()
            if (peers.size < 5) continue
            val median = peers[peers.size / 2]
            if (record.personalAmount >= 100_000 && median > 0 && record.personalAmount >= median * 3)
                alerts += Alert("Unusually large: ${record.description}", "${editorial(record.personalAmount)} on ${day(record)}, about ${record.personalAmount / median}× your typical ${names[record.categoryId] ?: "similar"} entry of ${editorial(median)}.")
        }
        return alerts
    }

    /** Same description at a steady weekly to yearly interval with a stable amount. */
    private fun recurring(expenses: List<Record>, today: LocalDate, day: (Record) -> LocalDate): List<Recurring> =
        expenses.groupBy { normalizedDescription(it.description) }.filterKeys { it.isNotBlank() }.mapNotNull { (_, group) ->
            val sorted = group.sortedBy { it.date }
            if (sorted.size < 3) return@mapNotNull null
            val dates = sorted.map(day)
            val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.sorted()
            val gap = gaps[gaps.size / 2]
            if (gap !in 6..370 || gaps.any { kotlin.math.abs(it - gap) > maxOf(3, gap / 7) }) return@mapNotNull null
            val amounts = sorted.map { it.personalAmount }.sorted()
            val typical = amounts[amounts.size / 2]
            if (typical <= 0 || amounts.any { kotlin.math.abs(it - typical) * 10 > typical }) return@mapNotNull null
            var next = dates.last().plusDays(gap.toLong())
            if (next.isBefore(today.minusDays(gap.toLong()))) return@mapNotNull null // stopped long ago
            while (next.isBefore(today)) next = next.plusDays(gap.toLong())
            val monthly = BigDecimal.valueOf(typical).multiply(BigDecimal("30.4375")).divide(BigDecimal(gap), 0, RoundingMode.HALF_UP).toLong()
            Recurring(sorted.last().description, typical, gap, next, monthly, sorted.size)
        }.sortedBy { it.nextDate }

    /** For categories without a budget this month: the median of the last 3 full months, rounded up to ₹100. */
    private fun budgetSuggestions(personal: List<Record>, budgets: List<Budget>, categories: List<Category>, today: LocalDate,
                                  total: (LocalDate, LocalDate, (Record) -> Boolean) -> Long): List<BudgetSuggestion> {
        val month = YearMonth.from(today).toString()
        val budgeted = budgets.filter { it.month == month }.mapNotNull { it.categoryId }.toSet()
        return categories.filter { !it.archived && it.id !in budgeted }.mapNotNull { category ->
            val spent = (1L..3L).map { back -> YearMonth.from(today).minusMonths(back).let { m -> total(m.atDay(1), m.atEndOfMonth()) { it.categoryId == category.id } } }
            if (spent.count { it > 0 } < 2) return@mapNotNull null
            val median = spent.sorted()[1]
            if (median <= 0) return@mapNotNull null
            BudgetSuggestion(category.id, category.name, (median + 9_999) / 10_000 * 10_000, spent.count { it > 0 })
        }.sortedByDescending { it.amount }
    }

    /** Same description and amount saved more than once on the same day in the last 30 days. */
    private fun duplicates(expenses: List<Record>, today: LocalDate, day: (Record) -> LocalDate): List<Duplicate> =
        expenses.filter { day(it) >= today.minusDays(30) }
            .groupBy { Triple(normalizedDescription(it.description), it.amount, day(it)) }
            .filter { it.value.size > 1 && it.key.first.isNotBlank() }
            .map { (key, group) -> Duplicate(group.first().description, key.second, key.third, group.size) }
            .sortedByDescending { it.date }

    /** A just-interpreted draft that matches an entry already saved today. */
    fun possibleDuplicate(description: String, amount: Long?, date: LocalDate, records: List<Record>, zone: ZoneId = ZoneId.systemDefault()): Record? {
        if (amount == null) return null
        val key = normalizedDescription(description)
        return records.firstOrNull { !it.reversed && it.kind == Kind.EXPENSE && it.amount == amount &&
            normalizedDescription(it.description) == key && Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() == date }
    }

    private fun editorial(paise: Long) = Money.format(paise)
}
