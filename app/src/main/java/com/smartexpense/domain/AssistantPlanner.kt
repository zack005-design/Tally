package com.smartexpense.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Runs Tally's tiny reasoning model safely. The question is anonymised (names and numbers become
 * placeholders), the model writes a step program, and every step is executed here with exact code over
 * local records. Numbers in the answer therefore always come from saved entries or the user's own
 * figures, never from the model. Invalid or low-confidence programs produce no answer.
 */
object AssistantPlanner {
    data class Answer(val text: String, val steps: List<String>, val program: List<String>, val confidence: Float,
                      /** Placeholder values of this turn, carried into a follow-up question. */ val slots: Map<String, String> = emptyMap()) {
        fun display(): String = if (steps.isEmpty()) text else text + "\n\nHow I worked it out:\n" + steps.withIndex().joinToString("\n") { "${it.index + 1}. ${it.value}" }
    }
    data class Data(val records: List<Record>, val categories: List<Category>, val accounts: List<Account>,
                    val budgets: List<Budget>, val today: LocalDate, val zone: ZoneId = ZoneId.systemDefault())

    private val token = Regex("[0-9][0-9,]*(?:\\.[0-9]+)?(?:\\s*(?:k|lakh|lakhs|lac|thousand))?(?![a-z0-9])|[a-z]+(?:'[a-z]+)?|%|\\?|[^\\s]")
    private val drop = setOf("₹", "rs", "inr", "rupees", "rupee", ".", ",", "!", ":", ";", "\"", "'", "(", ")", "-")
    private val numberPattern = Regex("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(k|lakh|lakhs|lac|thousand)?")
    private val cues = Regex("^\\s*(?:how|what|which|where|when|did|do|does|am|is|are|was|were|can|could|should|compare|if|plan|predict|tell|show|count|cutting|budget|my|total|average|daily|top|biggest|largest|kitna|sabse|month|hey|please|i want|to reach|saving)\\b|\\?\\s*$", RegexOption.IGNORE_CASE)

    private val followUp = Regex("^\\s*(?:and|what about|how about|same for|now|try|aur|vs|versus|compare (?:that|it|with)|is that|and compared)\\b", RegexOption.IGNORE_CASE)

    /** Short follow-ups to the previous answer ("and last month?", "what about travel"), never expense commands. */
    fun looksLikeFollowUp(text: String): Boolean = (text.length in 2..80 && followUp.containsMatchIn(text)) || (text.trim().endsWith("?") && text.length < 40)

    /** A cheap check before running the model: questions and planning requests, not expense commands. */
    fun looksLikeQuestion(text: String): Boolean = text.length in 3..200 && cues.containsMatchIn(text)

    fun tokenize(text: String): List<String> = token.findAll(text.lowercase(Locale.ROOT)).map { it.value }.filter { it !in drop }.toList()

    /**
     * Mirrors assistant_data_v2.anonymise: longest names first, a trailing plural "s" allowed, same value same
     * placeholder. [carried] holds the previous turn's placeholders, reused here; new ones continue the numbering.
     */
    fun anonymise(text: String, categories: List<String>, accounts: List<String>, vocabulary: Set<String>,
                  carried: Map<String, String> = emptyMap()): Pair<List<String>, Map<String, String>>? {
        val tokens = tokenize(text)
        val names = (categories.map { it to "CAT" } + accounts.map { it to "ACC" }).sortedByDescending { tokenize(it.first).size }
        val slots = linkedMapOf<String, String>().apply { putAll(carried) }
        val seen = hashMapOf<Pair<String, String>, String>()
        carried.forEach { (label, value) -> seen.putIfAbsent(label.trimEnd { it.isDigit() } to value, label) }
        val counters = hashMapOf("CAT" to 0, "ACC" to 0, "NUM" to 0, "WORD" to 0)
        carried.keys.forEach { label ->
            val kind = label.trimEnd { it.isDigit() }; val n = label.substring(kind.length).toIntOrNull()
            if (kind in counters && n != null) counters[kind] = maxOf(counters.getValue(kind), n)
        }
        fun label(kind: String, value: String): String = seen.getOrPut(kind to value) {
            counters[kind] = counters.getValue(kind) + 1
            "$kind${counters.getValue(kind)}".also { slots[it] = value }
        }
        val out = mutableListOf<String>(); var i = 0
        while (i < tokens.size) {
            val match = names.firstOrNull { (name, _) ->
                val parts = tokenize(name); val window = tokens.subList(i, minOf(tokens.size, i + parts.size))
                parts.isNotEmpty() && window.size == parts.size &&
                    (window == parts || (window.dropLast(1) == parts.dropLast(1) && window.last() == parts.last() + "s"))
            }
            if (match != null) { out += label(match.second, match.first); i += tokenize(match.first).size; continue }
            val t = tokens[i]
            out += when {
                numberPattern.matches(t) -> label("NUM", t)
                t in vocabulary || !t.first().isLetter() -> t
                else -> label("WORD", t)
            }
            i++
        }
        if (counters.values.any { it > 4 } || out.size > 40) return null
        return out to slots
    }

    /** [previous] is the last answered turn, used for follow-ups such as "and last month?" when the model supports it. */
    fun answer(question: String, model: TinyLanguageModel, data: Data, previous: Answer? = null): Answer? {
        val active = data.categories.filterNot { it.archived }
        val context = previous?.takeIf { model.supportsContext }
        val (source, slots) = anonymise(question, active.map { it.name }, data.accounts.filterNot { it.archived }.map { it.name }, model.words,
            context?.slots.orEmpty()) ?: return null
        val (program, confidence) = model.generate(source, context = context?.program)
        // Answer only a structurally valid plan whose first step clears its development-chosen threshold.
        // "unknown" is a refusal: return null so exact rules or manual entry can take over.
        val head = program.firstOrNull() ?: return null
        if (head == "unknown" || !AssistantValidator.valid(source, program, context?.program.orEmpty()) ||
            confidence < (model.headThresholds[head] ?: return null)) return null
        return runCatching { Executor(data, slots).run(program) }.getOrNull()?.copy(program = program, confidence = confidence, slots = slots)
    }

    /** Runs a step program with exact code; throws on any invalid step. Exposed for tests. */
    internal fun execute(program: List<String>, slots: Map<String, String>, data: Data): Answer = Executor(data, slots).run(program)

    /** Parses a number slot such as "25k", "1.5 lakh" or "50,000". */
    internal fun numberValue(text: String): BigDecimal? {
        val m = numberPattern.matchEntire(text) ?: return null
        val value = m.groupValues[1].replace(",", "").toBigDecimalOrNull() ?: return null
        val scale = when (m.groupValues[2]) { "k", "thousand" -> 1000; "lakh", "lakhs", "lac" -> 100000; else -> 1 }
        return value * BigDecimal(scale)
    }

    private class Executor(val data: Data, val slots: Map<String, String>) {
        private val steps = mutableListOf<String>()
        private val results = mutableListOf<Pair<Long, String>>() // value in paise and its label
        private val months = Month.entries.associateBy { it.name.lowercase(Locale.ROOT) }

        fun run(program: List<String>): Answer {
            require(program.lastOrNull() == "answer" && program.size >= 2)
            val statements = program.dropLast(1).joinToString(" ").split(" ; ").map { it.trim().removeSuffix(";").trim().split(" ") }
            require(statements.isNotEmpty() && statements.size <= 4)
            var final: String? = null
            for (s in statements) final = statement(s)
            return Answer(requireNotNull(final), steps.toList(), emptyList(), 0f)
        }

        private fun statement(s: List<String>): String = when (s.first()) {
            "spend" -> spend(s.drop(1))
            "income" -> income(s.drop(1))
            "count" -> count(s.drop(1))
            "average" -> average(s.drop(1))
            "top" -> top(s.drop(1))
            "largest" -> largest(s.drop(1))
            "compare" -> compare(s)
            "share" -> share(s)
            "budget" -> budget(s.drop(1))
            "forecast" -> forecast(s)
            "saveplan" -> savePlan(s)
            "savetime" -> saveTime(s)
            "whatif" -> whatIf(s)
            "help" -> help(s)
            "unknown" -> "I can answer questions about your own Tally records and plans, such as spending, budgets, comparisons and savings. I can't help with that one."
            else -> error("Unknown step")
        }

        // ---- filters and periods
        private data class Filter(val start: LocalDate, val end: LocalDate, val label: String, val category: Category?, val account: Account?, val text: String?, val n: Int?)

        private fun filter(args: List<String>): Filter {
            var i = 0
            val (start, end, label) = when (val p = args[i++]) {
                "month" -> {
                    val month = requireNotNull(months[args[i++]])
                    val year = if (month.value <= data.today.monthValue) data.today.year else data.today.year - 1
                    val first = LocalDate.of(year, month, 1)
                    Triple(first, minOf(first.with(TemporalAdjusters.lastDayOfMonth()), data.today), "in ${month.name.lowercase().replaceFirstChar { it.titlecase() }} $year")
                }
                "last_days" -> {
                    val days = requireNotNull(numberValue(slot(args[i++]))).intValueExact()
                    require(days in 1..366)
                    Triple(data.today.minusDays(days - 1L), data.today, "in the last $days days")
                }
                else -> period(p)
            }
            var category: Category? = null; var account: Account? = null; var text: String? = null; var n: Int? = null
            while (i < args.size) {
                when (args[i++]) {
                    "cat" -> category = data.categories.first { it.name.equals(slot(args[i]), true) }.also { i++ }
                    "acc" -> account = data.accounts.first { it.name.equals(slot(args[i]), true) }.also { i++ }
                    "text" -> { val words = mutableListOf<String>(); while (i < args.size && args[i].startsWith("WORD")) words += slot(args[i++]); require(words.isNotEmpty()); text = words.joinToString(" ") }
                    "n" -> n = requireNotNull(numberValue(slot(args[i++]))).intValueExact().also { require(it in 1..10) }
                    else -> error("Bad filter")
                }
            }
            return Filter(start, end, label, category, account, text, n)
        }

        private fun period(p: String): Triple<LocalDate, LocalDate, String> {
            val today = data.today; val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return when (p) {
                "today" -> Triple(today, today, "today")
                "yesterday" -> today.minusDays(1).let { Triple(it, it, "yesterday") }
                "this_week" -> Triple(monday, today, "this week")
                "last_week" -> Triple(monday.minusWeeks(1), monday.minusDays(1), "last week")
                "this_month" -> Triple(today.withDayOfMonth(1), today, "this month")
                "last_month" -> today.minusMonths(1).let { Triple(it.withDayOfMonth(1), it.with(TemporalAdjusters.lastDayOfMonth()), "last month") }
                "this_year" -> Triple(today.withDayOfYear(1), today, "this year")
                "last_year" -> today.minusYears(1).let { Triple(it.withDayOfYear(1), it.with(TemporalAdjusters.lastDayOfYear()), "last year") }
                else -> error("Bad period")
            }
        }

        private fun slot(name: String) = requireNotNull(slots[name]) { "Missing $name" }
        private fun day(r: Record) = Instant.ofEpochMilli(r.date).atZone(data.zone).toLocalDate()
        private fun matches(r: Record, f: Filter): Boolean = !r.reversed && day(r) in f.start..f.end &&
            (f.category == null || r.categoryId == f.category.id) && (f.account == null || f.account.id in r.accountIds) &&
            (f.text == null || normalizedDescription(r.description).split(Regex("[^\\p{L}\\p{N}]+")).windowed(f.text.split(" ").size).any { it == f.text.split(" ") })
        private fun describe(f: Filter) = listOfNotNull(f.category?.name, f.account?.let { "from ${it.name}" }, f.text?.let { "\"$it\"" }).joinToString(" ").ifEmpty { "everything" }
        private fun spending(f: Filter) = Finance.total(data.records.filter { matches(it, f) }.map {
            when (it.kind) { Kind.EXPENSE -> it.personalAmount; Kind.REFUND -> -it.personalAmount; else -> 0L } })

        private fun spend(args: List<String>): String {
            val f = filter(args); val value = spending(f)
            val label = "${describe(f).replaceFirstChar { it.titlecase() }} ${f.label}"
            results += value to label
            steps += "Added up your share of ${describe(f)} expenses ${f.label}, minus refunds: ${Money.format(value)}."
            return "You spent ${Money.format(value)} on ${describe(f)} ${f.label}."
        }

        private fun income(args: List<String>): String {
            val f = filter(args)
            val value = Finance.total(data.records.filter { it.kind == Kind.INCOME && matches(it, f) }.map { it.amount })
            results += value to "Income ${f.label}"
            steps += "Added up income entries ${f.label}: ${Money.format(value)}."
            return "Your income ${f.label} was ${Money.format(value)}."
        }

        private fun count(args: List<String>): String {
            val f = filter(args); val n = data.records.count { it.kind == Kind.EXPENSE && matches(it, f) }
            steps += "Counted saved expenses for ${describe(f)} ${f.label}: $n."
            return "You recorded $n ${if (n == 1) "expense" else "expenses"} for ${describe(f)} ${f.label}."
        }

        private fun average(args: List<String>): String {
            val f = filter(args); val total = spending(f)
            val days = ChronoUnit.DAYS.between(f.start, minOf(f.end, data.today)) + 1
            val perDay = BigDecimal.valueOf(total).divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP).toLong()
            steps += "Spending on ${describe(f)} ${f.label}: ${Money.format(total)}."
            steps += "Divided by $days ${if (days == 1L) "day" else "days"}: ${Money.format(perDay)} a day."
            return "You spent about ${Money.format(perDay)} a day on ${describe(f)} ${f.label}."
        }

        private fun top(args: List<String>): String {
            val f = filter(args); val n = f.n ?: 3
            val names = data.categories.associate { it.id to it.name }
            val totals = data.records.filter { matches(it, f) && (it.kind == Kind.EXPENSE || it.kind == Kind.REFUND) }
                .groupBy { it.categoryId }.mapValues { (_, rs) -> Finance.total(rs.map { if (it.kind == Kind.REFUND) -it.personalAmount else it.personalAmount }) }
                .filterValues { it > 0 }.entries.sortedByDescending { it.value }.take(n)
            steps += "Grouped your expenses ${f.label} by category and sorted by total."
            if (totals.isEmpty()) return "No spending recorded ${f.label}."
            return "Your top categories ${f.label}: " + totals.joinToString("; ") { "${names[it.key] ?: "Uncategorised"} ${Money.format(it.value)}" } + "."
        }

        private fun largest(args: List<String>): String {
            val f = filter(args)
            val biggest = data.records.filter { it.kind == Kind.EXPENSE && matches(it, f) }.maxByOrNull { it.personalAmount }
            steps += "Looked through ${describe(f)} expenses ${f.label} for the highest share."
            return biggest?.let { "Your biggest ${describe(f)} expense ${f.label} was ${it.description}: ${Money.format(it.personalAmount)} on ${day(it)}." }
                ?: "No ${describe(f)} expenses recorded ${f.label}."
        }

        private fun pair(s: List<String>): Pair<Pair<Long, String>, Pair<Long, String>> {
            require(s.size == 3 && s[1] == "$1" && s[2] == "$2" && results.size == 2)
            return results[0] to results[1]
        }

        private fun compare(s: List<String>): String {
            val (a, b) = pair(s); val difference = a.first - b.first
            val percent = if (b.first > 0) BigDecimal.valueOf(kotlin.math.abs(difference) * 100).divide(BigDecimal.valueOf(b.first), 0, RoundingMode.HALF_UP).toLong() else null
            steps += "Compared ${a.second} (${Money.format(a.first)}) with ${b.second} (${Money.format(b.first)}): difference ${Money.format(kotlin.math.abs(difference))}${percent?.let { ", $it%" } ?: ""}."
            return when {
                difference > 0 -> "${a.second} is higher: ${Money.format(a.first)} against ${Money.format(b.first)}, ${Money.format(difference)} more${percent?.let { " ($it% more)" } ?: ""}."
                difference < 0 -> "${a.second} is lower: ${Money.format(a.first)} against ${Money.format(b.first)}, ${Money.format(-difference)} less${percent?.let { " ($it% less)" } ?: ""}."
                else -> "They are the same: ${Money.format(a.first)} each."
            }
        }

        private fun share(s: List<String>): String {
            val (part, whole) = pair(s)
            require(whole.first > 0) { "No total" }
            val percent = BigDecimal.valueOf(part.first * 100).divide(BigDecimal.valueOf(whole.first), 1, RoundingMode.HALF_UP)
            steps += "Divided ${Money.format(part.first)} by your total ${Money.format(whole.first)}: ${percent.toPlainString()}%."
            return "${part.second} was ${percent.toPlainString()}% of your spending (${Money.format(part.first)} of ${Money.format(whole.first)})."
        }

        private fun budget(args: List<String>): String {
            val category = if (args.firstOrNull() == "cat") data.categories.first { it.name.equals(slot(args[1]), true) } else null
            require(args.isEmpty() || (args.size == 2 && category != null))
            val month = YearMonth.from(data.today)
            val budget = data.budgets.firstOrNull { it.month == month.toString() && it.categoryId == category?.id }
            val spent = spending(Filter(month.atDay(1), data.today, "this month", category, null, null, null))
            val name = category?.name ?: "overall"
            steps += "Spent on ${category?.name ?: "everything"} this month: ${Money.format(spent)}."
            if (budget == null) return "You have not set a $name budget for this month. You have spent ${Money.format(spent)} so far."
            val left = budget.amount - spent
            steps += "Your $name budget is ${Money.format(budget.amount)}; ${Money.format(budget.amount)} − ${Money.format(spent)} = ${Money.format(left)}."
            val days = month.lengthOfMonth() - data.today.dayOfMonth + 1
            return if (left >= 0) "You have ${Money.format(left)} left in your $name budget, about ${Money.format(left / days)} a day for the remaining $days days."
            else "You are ${Money.format(-left)} over your $name budget this month."
        }

        private fun forecast(s: List<String>): String {
            require(s.size == 1)
            val f = SmartInsights.build(data.records, data.budgets, data.categories, data.today, data.zone).forecast
                ?: return "I need about two weeks of records before I can forecast this month."
            steps += "Spent so far this month: ${Money.format(f.spentSoFar)}."
            steps += "Your average over the last 28 days is ${Money.format(f.dailyPace)} a day; × ${f.daysLeft} days left = ${Money.format(f.projected - f.spentSoFar)}."
            val budgetNote = f.budget?.let { b -> if (f.projected > b) " That is ${Money.format(f.projected - b)} over your ${Money.format(b)} budget." else " That is within your ${Money.format(b)} budget." } ?: ""
            return "At this pace you will spend about ${Money.format(f.projected)} this month.$budgetNote"
        }

        private fun amount(name: String): Long {
            val value = requireNotNull(numberValue(slot(name)))
            require(value.signum() > 0 && value <= BigDecimal("1000000000"))
            return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }

        private fun savePlan(s: List<String>): String {
            require(s.size == 4 && s[2] == "months")
            val target = amount(s[1]); val months = requireNotNull(numberValue(slot(s[3]))).intValueExact()
            require(months in 1..600)
            val perMonth = BigDecimal.valueOf(target).divide(BigDecimal.valueOf(months.toLong()), 0, RoundingMode.CEILING).toLong()
            steps += "${Money.format(target)} ÷ $months months = ${Money.format(perMonth)} a month (rounded up to the paisa)."
            return "Save ${Money.format(perMonth)} a month to reach ${Money.format(target)} in $months months. This ignores interest and price changes."
        }

        private fun saveTime(s: List<String>): String {
            require(s.size == 4 && s[2] == "per")
            val target = amount(s[1]); val perMonth = amount(s[3])
            val months = (target + perMonth - 1) / perMonth
            steps += "${Money.format(target)} ÷ ${Money.format(perMonth)} a month = ${BigDecimal.valueOf(target).divide(BigDecimal.valueOf(perMonth), 2, RoundingMode.HALF_UP).toPlainString()}, so $months months."
            return "Saving ${Money.format(perMonth)} a month, you would reach ${Money.format(target)} in $months ${if (months == 1L) "month" else "months"} (about ${months / 12} years ${months % 12} months)."
        }

        private fun whatIf(s: List<String>): String {
            require(s.size == 5 && s[1] == "cat" && s[3] == "cut")
            val category = data.categories.first { it.name.equals(slot(s[2]), true) }
            val percent = if (s[4] == "all") BigDecimal(100) else requireNotNull(numberValue(slot(s[4])))
            require(percent.signum() > 0 && percent <= BigDecimal(100))
            val last = YearMonth.from(data.today).minusMonths(1)
            val spent = spending(Filter(last.atDay(1), last.atEndOfMonth(), "last month", category, null, null, null))
            val saved = BigDecimal.valueOf(spent).multiply(percent).movePointLeft(2).setScale(0, RoundingMode.HALF_UP).toLong()
            steps += "${category.name} last month: ${Money.format(spent)}."
            steps += "${percent.stripTrailingZeros().toPlainString()}% of that is ${Money.format(saved)} a month; × 12 = ${Money.format(saved * 12)} a year."
            return if (spent <= 0) "You spent nothing on ${category.name} last month, so cutting it would not save anything based on that month."
            else "Cutting ${category.name} by ${percent.stripTrailingZeros().toPlainString()}% would save about ${Money.format(saved)} a month, or ${Money.format(saved * 12)} a year, if next months look like last month."
        }

        private fun help(s: List<String>): String {
            require(s.size == 2)
            return when (s[1]) {
                "add" -> "Tap Add on Summary, or type a command such as \"coffee 80 cash\" and use the interpret action. Check the draft and tap Save."
                "split" -> "Start an expense, choose Split, pick friends and a split method (equal, exact, percentages, shares or items), then save. Shared balances shows who owes whom."
                "backup" -> "Open More → Settings & backup → Backup & restore. Create a password-encrypted backup file and keep it somewhere safe; restore it on any phone."
                "budget" -> "Open More → Money & planning → Budgets, or Smart insights for suggested budgets you can apply with one tap."
                "export" -> "Open More → History & reports → Export monthly report to save a CSV or PDF report."
                "lock" -> "Open More → Settings & backup → App lock to protect Tally with your fingerprint or device PIN."
                "receipt" -> "Open More → Bills & categories → Photo / PDF bill. Tally reads the bill on your phone and lets you review every line."
                "privacy" -> "Tally has no internet permission and no account. Your records, and all of these answers, stay on this phone."
                else -> error("Unknown topic")
            }
        }
    }
}
