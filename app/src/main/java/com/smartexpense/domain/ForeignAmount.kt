package com.smartexpense.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Converts one foreign-currency amount in a command with a rate the user typed:
 * "lunch 12 usd at 83.5 cash", "coffee $4.50 @ ₹83.25 cash". There are no downloaded rates; without
 * "at <rate>" the command still needs manual conversion. The original amount and rate go into notes.
 */
internal object ForeignAmount {
    data class Result(val text: String, val note: String? = null, val notes: String? = null, val error: String? = null)

    // A curated list: every ISO code would misread words such as "cup" (Cuban peso) or "all" (Albanian lek).
    private val codes = setOf("USD", "EUR", "GBP", "AED", "SAR", "QAR", "KWD", "OMR", "BHD", "SGD", "MYR", "THB",
        "JPY", "CNY", "HKD", "KRW", "AUD", "NZD", "CAD", "CHF", "SEK", "NOK", "DKK", "LKR", "NPR", "BDT", "ZAR", "IDR", "VND")
    private val symbols = mapOf("$" to "USD", "€" to "EUR", "£" to "GBP")
    private const val amount = "([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"
    private const val rate = "\\s*(?:at|@)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]{1,6})?)(?:\\s*(?:inr|rupees|rs))?(?:\\s*(?:per\\s+[a-z]{3}|/[a-z]{3}))?"
    private val options = setOf(RegexOption.IGNORE_CASE)
    private val after = Regex("(?<![\\p{L}\\p{N}.,])$amount\\s*([a-z]{3}|[\$€£])$rate(?![\\p{L}\\p{N}])", options)
    private val before = Regex("(?<![\\p{L}\\p{N}.,])([a-z]{3}|[\$€£])\\s*$amount$rate(?![\\p{L}\\p{N}])", options)
    private val maximum = BigDecimal("100000000")

    fun apply(command: String): Result {
        val found = (after.findAll(command).map { Triple(it, it.groupValues[2], it.groupValues[1]) } +
            before.findAll(command).map { Triple(it, it.groupValues[1], it.groupValues[2]) })
            .filter { code(it.second) != null }.toList()
        if (found.isEmpty()) return Result(command)
        if (found.size > 1) return Result(command, error = "Convert one foreign amount per expense.")
        val (match, rawCode, rawAmount) = found.single()
        val code = code(rawCode)!!
        val value = BigDecimal(rawAmount.replace(",", ""))
        val perUnit = BigDecimal(match.groupValues[3])
        if (value.signum() <= 0 || perUnit.signum() <= 0) return Result(command, error = "Use a positive amount and a positive INR rate.")
        val exact = value.multiply(perUnit)
        val inr = exact.setScale(2, RoundingMode.HALF_UP)
        if (inr.signum() <= 0 || inr > maximum) return Result(command, error = "The converted amount is outside the supported range.")
        val paise = inr.movePointRight(2).longValueExact()
        val rounded = inr.compareTo(exact) != 0
        val shownAmount = value.stripTrailingZeros().toPlainString(); val shownRate = perUnit.stripTrailingZeros().toPlainString()
        return Result(command.replaceRange(match.range, Money.input(paise)),
            note = "Converted $code $shownAmount × ₹$shownRate = ${Money.format(paise)}${if (rounded) " (rounded to the nearest paisa)" else ""} using your rate. Check it before saving.",
            notes = "Original: $code $shownAmount · INR per unit $shownRate")
    }

    private fun code(raw: String): String? = symbols[raw] ?: raw.uppercase(Locale.ROOT).takeIf { it in codes }
}
