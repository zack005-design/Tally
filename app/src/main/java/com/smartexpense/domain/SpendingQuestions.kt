package com.smartexpense.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Answers simple spending questions from the user's own local records, e.g.
 * "how much did I spend on food this month?" or "total spent on chai last week".
 * Deterministic and read-only: it reuses the report rules (personal share, refunds subtracted,
 * reversed entries ignored) and never creates or changes a transaction.
 */
object SpendingQuestions {
    data class Answer(val text: String, val total: Long, val entries: Int, val start: LocalDate, val end: LocalDate)

    private val options = setOf(RegexOption.IGNORE_CASE)
    private val months = Month.entries.joinToString("|") { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT) }
    private const val periods = "today|yesterday|this week|last week|this month|last month|this year|last year|(?:the )?last [0-9]{1,3} days"
    private val spending = Regex(
        "^\\s*(?:how much (?:did|have|do) (?:i|we) (?:spend|spent)|how much (?:i|we) (?:spent|spend)|how much was spent|what did i spend|total (?:spent|spending)|(?:my )?spending)" +
            "(?:\\s+(?:on|for)\\s+(.+?))?(?:\\s+(?:in\\s+)?($periods|$months|in (?:$months)))?\\s*[?.!]?\\s*$", options)
    private val income = Regex("^\\s*(?:how much (?:did|have) (?:i|we) (?:earn|earned|receive|received)|(?:total |my )?income)" +
        "(?:\\s+(?:in\\s+)?($periods|$months|in (?:$months)))?\\s*[?.!]?\\s*$", options)

    /** Cheap check before loading every record. */
    fun isQuestion(text: String): Boolean = spending.matches(text.trim()) || income.matches(text.trim())

    fun answer(text: String, records: List<Record>, categories: List<Category>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Answer? {
        income.matchEntire(text.trim())?.let { m ->
            val (start, end, label) = period(m.groupValues[1], today) ?: return null
            val selected = records.filter { it.kind == Kind.INCOME }
            val report = Reports.period(selected, start, end, zone)
            val count = countIn(selected, start, end, zone)
            return Answer("Income $label: ${Money.format(report.income)} from $count ${plural(count)}.", report.income, count, start, end)
        }
        val m = spending.matchEntire(text.trim()) ?: return null
        val subject = m.groupValues[1].trim().trimEnd('?', '.', '!').trim()
        val (start, end, label) = period(m.groupValues[2], today) ?: return null
        val spendingRecords = records.filter { it.kind == Kind.EXPENSE || it.kind == Kind.REFUND }
        val category = categories.firstOrNull { singular(it.name) == singular(subject) }
        val selected = when {
            subject.isEmpty() -> spendingRecords
            category != null -> spendingRecords.filter { it.categoryId == category.id }
            else -> {
                val words = normalizedDescription(subject).split(' ').map(::singular)
                spendingRecords.filter { record ->
                    val own = normalizedDescription(record.description).split(Regex("[^\\p{L}\\p{N}]+")).map(::singular)
                    own.windowed(words.size).any { it == words }
                }
            }
        }
        val report = Reports.period(selected, start, end, zone)
        val count = countIn(selected, start, end, zone)
        val target = when {
            subject.isEmpty() -> ""
            category != null -> " on ${category.name}"
            else -> " on \"$subject\""
        }
        return Answer("You spent ${Money.format(report.spending)}$target $label (${count} ${plural(count)}, your share only, refunds subtracted).",
            report.spending, count, start, end)
    }

    private fun countIn(records: List<Record>, start: LocalDate, end: LocalDate, zone: ZoneId): Int {
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli(); val until = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return records.count { !it.reversed && it.date in from until until }
    }

    private fun period(raw: String, today: LocalDate): Triple<LocalDate, LocalDate, String>? {
        val value = raw.lowercase(Locale.ROOT).removePrefix("in ").trim().removePrefix("the ")
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when {
            value.isEmpty() || value == "this month" -> Triple(today.withDayOfMonth(1), today, "this month")
            value == "today" -> Triple(today, today, "today")
            value == "yesterday" -> today.minusDays(1).let { Triple(it, it, "yesterday") }
            value == "this week" -> Triple(monday, today, "this week")
            value == "last week" -> Triple(monday.minusWeeks(1), monday.minusDays(1), "last week")
            value == "last month" -> today.minusMonths(1).let { Triple(it.withDayOfMonth(1), it.with(TemporalAdjusters.lastDayOfMonth()), "last month") }
            value == "this year" -> Triple(today.withDayOfYear(1), today, "this year")
            value == "last year" -> today.minusYears(1).let { Triple(it.withDayOfYear(1), it.with(TemporalAdjusters.lastDayOfYear()), "last year") }
            value.startsWith("last ") -> {
                val days = value.removePrefix("last ").substringBefore(' ').toIntOrNull() ?: return null
                if (days !in 1..366) null else Triple(today.minusDays(days - 1L), today, "in the last $days days")
            }
            else -> {
                val month = Month.entries.firstOrNull { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).equals(value, true) } ?: return null
                // The most recent such month that has started: this year's, or last year's if it is still ahead.
                val year = if (month.value <= today.monthValue) today.year else today.year - 1
                val first = LocalDate.of(year, month, 1)
                val last = minOf(first.with(TemporalAdjusters.lastDayOfMonth()), today)
                Triple(first, last, "in ${month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} $year")
            }
        }
    }

    private fun singular(word: String): String {
        val lower = word.trim().lowercase(Locale.ROOT)
        return if (lower.length > 3 && lower.endsWith("s") && !lower.endsWith("ss")) lower.dropLast(1) else lower
    }
    private fun plural(count: Int) = if (count == 1) "entry" else "entries"
}
