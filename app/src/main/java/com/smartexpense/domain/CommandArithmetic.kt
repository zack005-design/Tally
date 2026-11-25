package com.smartexpense.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Exact arithmetic for one amount inside an expense command, before rules or models read it:
 * `40 + 25`, `3 x 15`, `2 chai at 15 each`, `450 + 5% gst`, `2000 - 10%`.
 * Results use BigDecimal and are rounded half-up to whole paise only when needed; the draft notes
 * the calculation so the user can check it. Signs, ranges (`200-250`) and division are never guessed.
 */
internal object CommandArithmetic {
    data class Result(val text: String, val note: String? = null, val error: String? = null)

    private const val operand = "(?:₹\\s*)?([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)"
    private const val before = "(?<![\\p{L}\\p{N}.,/+\\-−*×%])"
    private const val after = "(?![\\p{L}\\p{N}.,%/])"
    private val options = setOf(RegexOption.IGNORE_CASE)
    private val chain = Regex("$before$operand(?:\\s*(?:[+*×]|(?<=[0-9])x(?=\\s*[₹0-9])|\\s(?:x|plus|times)\\s)\\s*$operand)+$after", options)
    private val operator = Regex("\\s*(?:[+*×]|(?<=[0-9])x(?=\\s*[₹0-9])|\\s(?:x|plus|times)\\s)\\s*", options)
    private val percent = Regex("$before$operand\\s*([+\\-−]|\\splus\\s|\\sminus\\s|\\sless\\s)\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*%(?:\\s*(?:gst|tax|vat|tip|service charge|discount|off))?(?![\\p{L}\\p{N}])", options)
    private val each = Regex("(?<![\\p{L}\\p{N}.,])([1-9][0-9]?)\\s+(\\p{L}[\\p{L}'’ -]{0,40}?)\\s+(?:at|@)\\s*$operand\\s*each(?![\\p{L}\\p{N}])", options)
    private val amountPattern = Regex("(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+|[1-9][0-9]?(?:,[0-9]{2})*,[0-9]{3})(?:\\.[0-9]{1,2})?")
    private val hundred = BigDecimal(100)
    private val maximum = BigDecimal("100000000") // ₹10 crore; larger results need manual entry.
    private const val invalid = "Check the calculation. Use positive INR amounts such as 40 + 25, 3 x 15, 2 chai at 15 each or 450 + 18% gst."

    fun apply(command: String): Result {
        val matches = listOf(each, percent, chain).sumOf { it.findAll(command).count() }
        if (matches == 0) return Result(command)
        if (matches > 1) return Result(command, error = "Use one calculation per expense. Enter separate expenses separately.")
        each.find(command)?.let { m ->
            val count = BigDecimal(m.groupValues[1])
            val price = amount(m.groupValues[3]) ?: return Result(command, error = invalid)
            val total = finish(count * price) ?: return Result(command, error = invalid)
            val item = m.groupValues[2].trim()
            return Result(command.replaceRange(m.range, "$item ${Money.input(total.first)}"),
                note("${m.groupValues[1]} × ${plain(price)}", total))
        }
        percent.find(command)?.let { m ->
            val base = amount(m.groupValues[1]) ?: return Result(command, error = invalid)
            val rate = BigDecimal(m.groupValues[3])
            if (rate.signum() <= 0 || rate > hundred) return Result(command, error = "Use a percentage from 0.01% to 100%.")
            val adding = m.groupValues[2].trim().lowercase(Locale.ROOT) in setOf("+", "plus")
            // Exact: shifting the decimal point never rounds (BigDecimal "/" would keep the dividend scale).
            val change = (base * rate).movePointLeft(2)
            val total = finish(if (adding) base + change else base - change) ?: return Result(command, error = invalid)
            return Result(command.replaceRange(m.range, Money.input(total.first)),
                note("${plain(base)} ${if (adding) "+" else "−"} ${plain(rate)}%", total))
        }
        val m = chain.find(command)!!
        val terms = m.value.split(Regex("\\s*\\+\\s*|\\s+plus\\s+", options))
        var total = BigDecimal.ZERO
        var count = 0
        val shown = mutableListOf<String>()
        for (term in terms) {
            val factors = term.split(operator).filter(String::isNotBlank)
            if (factors.isEmpty()) return Result(command, error = invalid)
            count += factors.size
            if (count > 10) return Result(command, error = "Use at most 10 numbers in one calculation.")
            var product = BigDecimal.ONE
            for (factor in factors) product *= amount(factor.trim().removePrefix("₹").trim()) ?: return Result(command, error = invalid)
            total += product
            shown += factors.joinToString(" × ") { plain(BigDecimal(it.trim().removePrefix("₹").trim().replace(",", ""))) }
        }
        val result = finish(total) ?: return Result(command, error = invalid)
        return Result(command.replaceRange(m.range, Money.input(result.first)), note(shown.joinToString(" + "), result))
    }

    private fun amount(text: String): BigDecimal? =
        if (amountPattern.matches(text)) BigDecimal(text.replace(",", "")).takeIf { it.signum() > 0 } else null

    /** Paise and whether rounding was needed; null when not a positive amount within range. */
    private fun finish(value: BigDecimal): Pair<Long, Boolean>? {
        if (value.signum() <= 0 || value > maximum) return null
        val rounded = value.setScale(2, RoundingMode.HALF_UP)
        return rounded.movePointRight(2).longValueExact() to (rounded.compareTo(value) != 0)
    }

    private fun note(expression: String, total: Pair<Long, Boolean>) =
        "Calculated amount: $expression = ${Money.format(total.first)}${if (total.second) " (rounded to the nearest paisa)" else ""}. Check it before saving."

    private fun plain(value: BigDecimal) = value.stripTrailingZeros().toPlainString()
}
