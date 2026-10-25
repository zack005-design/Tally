package com.smartexpense.domain

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Bounded English input conveniences; exact money/date code, no generated financial values. */
internal object EnglishCommandInput {
    data class Prepared(val text: String, val date: LocalDate? = null, val error: String? = null, val invalidDate: Boolean = false)
    private val options = RegexOption.IGNORE_CASE
    private const val weekdays = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val months = "january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec"
    private val future = Regex("\\b(?:tomorrow|next\\s+(?:$weekdays|week|month|year)|in\\s+(?:[a-z0-9-]+\\s+){1,6}(?:minutes?|hours?|days?|weeks?|months?|years?)|(?:minutes?|hours?|days?|weeks?|months?|years?)\\s+(?:from\\s+now|later))\\b", options)
    private val relative = Regex("\\b(?:day before yesterday|(?:[0-9]+|one|two|three|four|five|six|seven)\\s+days?\\s+ago|last\\s+(?:$weekdays)|yesterday|today)(?![\\p{L}\\p{N}'’])", options)
    private val iso = Regex("\\b(?:on\\s+)?[0-9]{4}-[0-9]{2}-[0-9]{2}\\b", options)
    private val calendar = Regex("\\bon\\s+(?:[0-9]{1,2}\\s+[a-z]+\\s+[0-9]{4}|[a-z]+\\s+[0-9]{1,2},?\\s+[0-9]{4})\\b", options)
    private val unresolvedDate = Regex("\\bon\\s+(?:[0-9]{1,4}[-/][0-9]+|[0-9]{1,2}\\s+[a-z]+|(?:$months)\\s+[0-9])\\b", options)
    private val unresolvedRelative = Regex("\\b(?:(?:last|next)\\s+[a-z]+|(?:days?|weeks?|months?|years?)\\s+ago|$weekdays|today|yesterday|tomorrow)(?![\\p{L}\\p{N}'’])", options)
    private val unresolvedScale = Regex("(?<![0-9.,])[0-9]+(?:[.,][0-9]+)*\\s*(?:k|thousands?|lakhs?|lacs?|rupees|inr|rs|million|billion|trillion|crores?|hundreds?)\\b", options)
    private val onPrefix = Regex("^on\\s+", options)
    private val spaces = Regex("\\s+")
    private val scale = Regex("(?<![\\p{L}\\p{N}.,])([0-9]+(?:\\.[0-9]{1,2})?)\\s*(k|thousand|lakhs?|lacs?|rupees|inr|rs)\\b", options)
    private val unknownSuffix = Regex("(?<![\\p{L}\\p{N}.,])[0-9]+(?:[.,][0-9]+)*[a-z]+\\b", options)
    private val currencyPrefix = Regex("\\b(?:INR|Rs\\.?)(?=\\s*[0-9])", options)
    private val currencySuffix = Regex("(?<![\\p{L}\\p{N}.,])([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:rupees|inr|rs)\\b", options)
    private val attachedCurrency = Regex("\\b([a-z]{3,4})(?=[_0-9])", options)
    private val foreignCurrencies = java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.toSet() - "INR" + setOf("BTC", "ETH", "USDT", "USDC")
    private val transferKind = Regex("\\btransfer(?:red)?\\b", options)
    private val transferAccounts = Regex("\\bfrom\\s+.+?\\s+to\\s+.+?[.!]?$", options)
    private val dateModifiers = setOf("before", "after", "since", "until", "than", "about", "around", "roughly", "approximately", "between")
    private val wordDays = listOf("one", "two", "three", "four", "five", "six", "seven")
    private val numberModifiers = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty fifty sixty seventy eighty ninety hundred thousand million billion minus negative point half score about around roughly approximately and or to between nearly almost over under least most").split(' ').toSet()
    private val formats = listOf("d MMMM uuuu", "d MMM uuuu", "MMMM d, uuuu", "MMM d, uuuu", "MMMM d uuuu", "MMM d uuuu").map {
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    }

    fun prepare(input: String, clock: Clock): Prepared {
        val text = spaces.replace(input, " ")
        fun invalidDate() = Prepared(text, error = "Use one valid past date: today, yesterday, days ago, last Monday, or a date such as 21 October 2026. Future or conflicting dates need manual correction.", invalidDate = true)
        if (future.containsMatchIn(text)) return invalidDate()
        val today = LocalDate.now(clock)
        val matches = (iso.findAll(text) + calendar.findAll(text) + relative.findAll(text)).toList()
        if (matches.size > 1) return invalidDate()
        val match = matches.singleOrNull()
        if (match != null) {
            val prefix = text.substring(0, match.range.first).trimEnd()
            val preceding = prefix.substringAfterLast(' ').trim(',', ';', ':', '.').lowercase(Locale.ROOT)
            val offset = match.value.endsWith(" ago", ignoreCase = true)
            if ((offset || onPrefix.replace(match.value, "").firstOrNull()?.isDigit() == true) && prefix.lastOrNull() in listOf('-', '−', '+', '.', '/')) return invalidDate()
            if (preceding in dateModifiers) return invalidDate()
            if (offset && preceding.isNotEmpty() && (preceding.all(Char::isDigit) || preceding.split('-').any { it in numberModifiers })) return invalidDate()
        }
        val date = if (match == null) null else resolveDate(onPrefix.replace(match.value, ""), today) ?: return invalidDate()
        if (date != null && date.isAfter(today)) return invalidDate()
        var command = if (match == null) text else spaces.replace(text.replaceRange(match.range, " "), " ").trim().trimEnd(',', ';', '.', ':').trimEnd()
        if (unresolvedDate.containsMatchIn(command) || unresolvedRelative.containsMatchIn(command)) return invalidDate()
        // Account identifiers are text, not money tokens; preserve them during amount expansion.
        val accountRange = if (transferKind.containsMatchIn(command)) transferAccounts.find(command) else null
        val accountText = accountRange?.value.orEmpty()
        if (accountRange != null) command = command.substring(0, accountRange.range.first)
        command = currencyPrefix.replace(command, "₹ ")
        var amountError = false
        fun exactAmount(amount: MatchResult, multiplier: Long): String =
            try { Money.input(Math.multiplyExact(Money.parse(amount.groupValues[1]), multiplier)) }
            catch (_: IllegalArgumentException) { amountError = true; amount.value }
            catch (_: ArithmeticException) { amountError = true; amount.value }
        command = scale.replace(command) { amount ->
            val multiplier = when (amount.groupValues[2].lowercase(Locale.ROOT)) {
                "k", "thousand" -> 1000L
                "lakh", "lakhs", "lac", "lacs" -> 100000L
                else -> 1L
            }
            exactAmount(amount, multiplier)
        }
        command = currencySuffix.replace(command) { exactAmount(it, 1L) }
        if (amountError || unknownSuffix.containsMatchIn(command) || unresolvedScale.containsMatchIn(command) || attachedCurrency.findAll(command).any { it.groupValues[1].uppercase(Locale.ROOT) in foreignCurrencies })
            return Prepared(text, date = date, error = "Check the amount. Use plain INR digits or shorthand such as 1.5k or 1.25 lakh, with at most two decimal places before the suffix.")
        return Prepared(command + accountText, date)
    }

    private fun resolveDate(value: String, today: LocalDate): LocalDate? {
        val lower = value.lowercase(Locale.ROOT)
        return when {
            lower == "today" -> today
            lower == "yesterday" -> today.minusDays(1)
            lower == "day before yesterday" -> today.minusDays(2)
            lower.startsWith("last ") -> runCatching {
                today.with(TemporalAdjusters.previous(DayOfWeek.valueOf(lower.substringAfterLast(' ').uppercase(Locale.ROOT))))
            }.getOrNull()
            lower.endsWith(" ago") -> {
                val count = lower.substringBefore(' ').let { it.toIntOrNull() ?: (wordDays.indexOf(it) + 1) }
                if (count in 1..365) today.minusDays(count.toLong()) else null
            }
            else -> runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                ?: formats.firstNotNullOfOrNull { runCatching { LocalDate.parse(value, it) }.getOrNull() }
        }
    }
}
