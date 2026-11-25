package com.smartexpense.domain

import kotlin.math.absoluteValue

/** Amount matching for the Transactions search box. Text matching stays in the screen. */
object TransactionSearch {
    private val amountPattern = Regex("₹?\\s*[0-9][0-9,]*(\\.[0-9]{1,2})?")

    /** Paise for a query such as "80", "₹1,250" or "20.50"; null when the query is not an amount. */
    fun amount(query: String): Long? {
        val text = query.trim()
        if (!amountPattern.matches(text)) return null
        return runCatching { Money.parse(text.replace(" ", "")) }.getOrNull()?.takeIf { it > 0 }
    }

    /** True when the record's total or your share equals [paise], ignoring sign. */
    fun matchesAmount(record: Record, paise: Long): Boolean =
        record.amount.absoluteValue == paise || record.personalAmount.absoluteValue == paise
}

/**
 * Understands a typed search such as "coffee over 100 last month", "food in october by gpay" or
 * "refunds under 500". Recognised phrases become filters; the remaining words are matched as text.
 * Deterministic and local; an unrecognised query behaves exactly like the plain text search.
 */
object SmartSearch {
    data class Query(
        val text: String, val minimum: Long? = null, val maximum: Long? = null,
        val start: java.time.LocalDate? = null, val end: java.time.LocalDate? = null,
        val categoryId: String? = null, val accountId: String? = null, val kind: Kind? = null, val labels: List<String> = emptyList()
    ) { val structured: Boolean get() = labels.isNotEmpty() }

    private val options = setOf(RegexOption.IGNORE_CASE)
    private const val money = "₹?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    private val between = Regex("\\bbetween\\s+$money\\s+and\\s+$money", options)
    private val over = Regex("\\b(?:over|above|more than|greater than|at least|>=?)\\s*$money", options)
    private val under = Regex("\\b(?:under|below|less than|at most|<=?)\\s*$money", options)
    private val months = java.time.Month.entries.joinToString("|") { it.name.lowercase(java.util.Locale.ROOT) }
    private val period = Regex("\\b(?:(today|yesterday|this week|last week|this month|last month|this year|last year)|(?:in the |the )?last ([0-9]{1,3}) days|(?:in )?($months))\\b", options)
    private val kinds = mapOf("expenses" to Kind.EXPENSE, "expense" to Kind.EXPENSE, "income" to Kind.INCOME, "refunds" to Kind.REFUND,
        "refund" to Kind.REFUND, "transfers" to Kind.TRANSFER, "transfer" to Kind.TRANSFER, "repayments" to Kind.REPAYMENT, "repayment" to Kind.REPAYMENT)
    private val connectors = Regex("\\b(?:show|find|me|my|all|spent|spending|on|for|in|from|with|by|via|using|paid|entries|transactions)\\b", options)

    fun understand(query: String, categories: List<Category>, accounts: List<Account>, today: java.time.LocalDate): Query {
        var rest = " ${query.trim()} "
        val labels = mutableListOf<String>()
        var minimum: Long? = null; var maximum: Long? = null
        fun amount(text: String) = runCatching { Money.parse(text.replace(",", "")) }.getOrNull()?.takeIf { it >= 0 }
        between.find(rest)?.let { m ->
            minimum = amount(m.groupValues[1]); maximum = amount(m.groupValues[2])
            if (minimum != null && maximum != null) { rest = rest.replaceRange(m.range, " "); labels += "${Money.format(minimum!!)}–${Money.format(maximum!!)}" }
            else { minimum = null; maximum = null }
        }
        if (minimum == null) over.find(rest)?.let { m -> amount(m.groupValues[1])?.let { minimum = it; rest = rest.replaceRange(m.range, " "); labels += "over ${Money.format(it)}" } }
        if (maximum == null) under.find(rest)?.let { m -> amount(m.groupValues[1])?.let { maximum = it; rest = rest.replaceRange(m.range, " "); labels += "under ${Money.format(it)}" } }
        var start: java.time.LocalDate? = null; var end: java.time.LocalDate? = null
        period.find(rest)?.let { m ->
            val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val range = when (m.groupValues[1].lowercase(java.util.Locale.ROOT)) {
                "today" -> today to today
                "yesterday" -> today.minusDays(1) to today.minusDays(1)
                "this week" -> monday to today
                "last week" -> monday.minusWeeks(1) to monday.minusDays(1)
                "this month" -> today.withDayOfMonth(1) to today
                "last month" -> today.minusMonths(1).withDayOfMonth(1) to today.withDayOfMonth(1).minusDays(1)
                "this year" -> today.withDayOfYear(1) to today
                "last year" -> today.minusYears(1).withDayOfYear(1) to today.withDayOfYear(1).minusDays(1)
                else -> m.groupValues[2].toIntOrNull()?.takeIf { it in 1..366 }?.let { today.minusDays(it - 1L) to today }
                    ?: m.groupValues[3].takeIf { it.isNotEmpty() }?.let { name ->
                        val month = java.time.Month.valueOf(name.uppercase(java.util.Locale.ROOT))
                        val year = if (month.value <= today.monthValue) today.year else today.year - 1
                        java.time.YearMonth.of(year, month).let { it.atDay(1) to it.atEndOfMonth() }
                    }
            }
            if (range != null) { start = range.first; end = range.second; rest = rest.replaceRange(m.range, " "); labels += m.value.trim().lowercase(java.util.Locale.ROOT) }
        }
        fun takeName(names: List<Pair<String, String>>): Pair<String, String>? = names.sortedByDescending { it.second.length }.firstOrNull { (_, name) ->
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}s?(?![\\p{L}\\p{N}])", options).containsMatchIn(rest)
        }?.also { (_, name) -> rest = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}s?(?![\\p{L}\\p{N}])", options).replaceFirst(rest, " ") }
        val category = takeName(categories.filterNot { it.archived }.map { it.id to it.name })?.also { labels += it.second }
        val account = takeName(accounts.filterNot { it.archived }.map { it.id to it.name })?.also { labels += "via ${it.second}" }
        var kind: Kind? = null
        kinds.entries.firstOrNull { Regex("\\b${it.key}\\b", options).containsMatchIn(rest) }?.let { (word, value) ->
            kind = value; rest = Regex("\\b$word\\b", options).replaceFirst(rest, " "); labels += value.name.lowercase(java.util.Locale.ROOT)
        }
        if (labels.isEmpty()) return Query(query.trim())
        val text = connectors.replace(rest, " ").replace(Regex("\\s+"), " ").trim()
        return Query(text, minimum, maximum, start, end, category?.first, account?.first, kind, labels)
    }
}
