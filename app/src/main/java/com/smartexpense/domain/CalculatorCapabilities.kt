package com.smartexpense.domain

import java.math.BigDecimal
import java.math.BigInteger

/** Small, explicit offline tools. Patterns are compiled once; no ledger access or model inference. */
internal object CalculatorCapabilities {
    private const val number = "([+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+))"
    private val decimal = Regex(number)
    private val statistics = Regex("(sum|average|mean|median|minimum|maximum|population variance|population standard deviation|sample variance|sample standard deviation) (.+)")
    private val separators = Regex("\\s*;\\s*|,\\s+")
    private val ratio = Regex("simplify ratio ([0-9]+(?:\\s*:\\s*[0-9]+)+)")
    private val ratioSeparator = Regex("\\s*:\\s*")
    private val change = Regex("percentage change from $number to $number")
    private val tip = Regex("tip $number% on ₹?$number")
    private val reverseTax = Regex("remove $number% (?:tax|gst|vat) from ₹?$number")
    private val emi = Regex("(?:loan )?emi (?:on|for) (?:a )?(?:loan of )?₹?$number at $number% per (?:year|annum) for $number (years?|months?)")
    private val simpleInterest = Regex("simple interest on ₹?$number at $number% per year for $number years?")
    private val compoundInterest = Regex("compound interest on ₹?$number at $number% per year for $number years? compounded (yearly|quarterly|monthly)")
    private val hundred = BigDecimal(100)

    fun calculate(text: String): CalculationOutcome.Answer? {
        statistics.matchEntire(text)?.let { return statistics(it.groupValues[1], it.groupValues[2]) }
        ratio.matchEntire(text)?.let {
            val parts = it.groupValues[1].split(ratioSeparator)
            if (parts.size > 20 || parts.any { part -> part.length > 100 }) problem("Use at most 20 nonnegative integer ratio terms, each at most 100 digits.")
            val values = parts.map(::BigInteger)
            val divisor = values.reduce(BigInteger::gcd)
            if (divisor.signum() == 0) problem("At least one ratio term must be greater than zero.")
            return CalculationOutcome.Answer(values.joinToString(":") { value -> value.divide(divisor).toString() }, "${it.groupValues[1]}; divide each term by $divisor")
        }
        change.matchEntire(text)?.let {
            val original = value(it.groupValues[1]); val updated = value(it.groupValues[2])
            if (original.signum() <= 0) problem("Percentage change needs a positive starting value. Use a plain difference for a zero or negative start.")
            val result = divideNumbers(CalcNumber(bounded((updated - original) * hundred)), CalcNumber(original))
            return CalculationOutcome.Answer("${decimalText(result.value)}%", "($updated - $original) / $original × 100", result.approximate)
        }
        tip.matchEntire(text)?.let {
            val percent = percentage(it.groupValues[1]); val bill = nonnegative(it.groupValues[2], "bill")
            val gratuity = bounded((bill * percent).movePointLeft(2))
            return CalculationOutcome.Answer(decimalText(bounded(bill + gratuity)), "bill ${decimalText(bill)} + tip ${decimalText(gratuity)} (${decimalText(percent)}%); unrounded total")
        }
        reverseTax.matchEntire(text)?.let {
            val percent = percentage(it.groupValues[1]); val total = nonnegative(it.groupValues[2], "tax-inclusive total")
            val base = divideNumbers(CalcNumber(total), CalcNumber(BigDecimal.ONE + percent.movePointLeft(2)))
            return CalculationOutcome.Answer(decimalText(base.value), "tax-inclusive total ${decimalText(total)} / (1 + ${decimalText(percent)}/100); tax ${decimalText(total - base.value)}", base.approximate)
        }
        emi.matchEntire(text)?.let { return emi(it) }
        simpleInterest.matchEntire(text)?.let {
            val principal = nonnegative(it.groupValues[1], "principal"); val rate = percentage(it.groupValues[2]); val years = years(it.groupValues[3])
            val interest = bounded((principal * rate * years).movePointLeft(2))
            return CalculationOutcome.Answer(decimalText(interest), "simple interest = principal × annual rate/100 × years; final amount ${decimalText(bounded(principal + interest))}")
        }
        compoundInterest.matchEntire(text)?.let {
            val principal = nonnegative(it.groupValues[1], "principal"); val rate = percentage(it.groupValues[2]); val years = years(it.groupValues[3])
            val frequency = when (it.groupValues[4]) { "monthly" -> 12; "quarterly" -> 4; else -> 1 }
            val periods = try { (years * frequency.toBigDecimal()).intValueExact() } catch (_: ArithmeticException) { problem("Use a duration containing a whole number of compounding periods.") }
            if (periods !in 0..1200) problem("Use at most 1,200 compounding periods.")
            if (periods == 0 || rate.signum() == 0 || principal.signum() == 0)
                return CalculationOutcome.Answer("0", "zero compound interest; final amount ${decimalText(principal)}; fixed rate, no fees or payments")
            val periodic = divideNumbers(CalcNumber(rate), CalcNumber(hundred * frequency.toBigDecimal()))
            val factor = BigDecimal.ONE + periodic.value
            // Compose growth increments directly, avoiding cancellation in (1+r)^n - 1.
            // Exponentiation by squaring takes logarithmic steps even for 1,200 periods.
            val rounded = periodic.approximate || factor.precision().toLong() * periods > 512
            fun combine(a: BigDecimal, b: BigDecimal): BigDecimal = bounded(
                if (rounded) a.add(b, calcContext).add(a.multiply(b, calcContext), calcContext)
                else a + b + a * b
            )
            var remaining = periods
            var increment = periodic.value
            var gain = BigDecimal.ZERO
            while (remaining > 0) {
                if (remaining % 2 == 1) gain = combine(gain, increment)
                remaining /= 2
                if (remaining > 0) increment = combine(increment, increment)
            }
            val interest = bounded(if (rounded) principal.multiply(gain, calcContext) else principal * gain)
            val final = bounded(principal + interest)
            return CalculationOutcome.Answer(decimalText(interest), "compound interest = P × (1 + annual rate/(100 × $frequency))^$periods − P; final amount ${decimalText(final)}; fixed rate, no fees or payments", rounded)
        }
        when {
            text.startsWith("simple interest") -> problem("Include principal, annual rate and duration: simple interest on 1000 at 10% per year for 2 years.")
            text.startsWith("compound interest") -> problem("Include the compounding schedule: compound interest on 1000 at 10% per year for 2 years compounded yearly.")
            text.startsWith("simplify ratio") -> problem("Use nonnegative whole-number terms, for example simplify ratio 12:18:24.")
            text.startsWith("tip") -> problem("Specify the percentage and bill: tip 10% on 500.")
            text.startsWith("remove") && text.contains("tax") -> problem("Specify the tax rate and tax-inclusive total: remove 18% tax from 1180.")
            text.startsWith("percentage change") -> problem("Specify both values: percentage change from 80 to 100.")
            text.startsWith("variance") || text.startsWith("standard deviation") -> problem("Choose population or sample: population variance 2, 4, 6 or sample standard deviation 2, 4, 6.")
            text.startsWith("emi") || text.startsWith("loan emi") -> problem("Include the loan amount, annual rate and term: emi on 500000 at 9% per year for 5 years.")
        }
        return null
    }

    private fun statistics(operation: String, source: String): CalculationOutcome.Answer {
        val fields = source.split(separators)
        if (fields.size !in 1..100 || fields.any { !decimal.matches(it.trim()) })
            problem("Use up to 100 plain numbers separated by comma and space, or semicolons. Do not use thousands commas: average 10, 20, 30.")
        val values = fields.map { value(it.trim()) }
        val n = values.size.toBigDecimal()
        var total = BigDecimal.ZERO
        var squares = BigDecimal.ZERO
        val variance = operation.startsWith("population") || operation.startsWith("sample")
        if (operation.startsWith("sample") && values.size < 2) problem("Sample statistics need at least two values.")
        values.forEach { value ->
            total = bounded(total + value)
            if (variance) squares = bounded(squares + bounded(value * value))
        }
        val result = when (operation) {
            "sum" -> CalcNumber(total)
            "average", "mean" -> divideNumbers(CalcNumber(total), CalcNumber(n))
            "minimum" -> CalcNumber(values.minOrNull()!!)
            "maximum" -> CalcNumber(values.maxOrNull()!!)
            "median" -> {
                val sorted = values.sorted(); val middle = values.size / 2
                if (values.size % 2 == 1) CalcNumber(sorted[middle])
                else divideNumbers(CalcNumber(bounded(sorted[middle - 1] + sorted[middle])), CalcNumber(BigDecimal(2)))
            }
            else -> {
                // Exact numerator avoids subtracting rounded means for large-offset samples.
                // Population divides by n²; sample (Bessel's correction) divides by n(n − 1).
                val denominator = if (operation.startsWith("sample")) n * (n - BigDecimal.ONE) else n * n
                val v = divideNumbers(CalcNumber(bounded(n * squares - bounded(total * total))), CalcNumber(denominator))
                if (operation.endsWith("variance")) v
                else ExpressionCalculator.evaluate("sqrt(${decimalText(v.value)})").let { it.copy(approximate = it.approximate || v.approximate) }
            }
        }
        return CalculationOutcome.Answer(decimalText(result.value), "$operation of ${values.size} values: ${values.joinToString(", ", transform = ::decimalText)}", result.approximate)
    }

    /** Fixed-rate loan instalment: P·r·(1+r)^n / ((1+r)^n − 1) with r the monthly rate. */
    private fun emi(m: MatchResult): CalculationOutcome.Answer {
        val principal = nonnegative(m.groupValues[1], "loan amount"); val rate = percentage(m.groupValues[2])
        val length = nonnegative(m.groupValues[3], "term")
        val months = try { (if (m.groupValues[4].startsWith("year")) length * BigDecimal(12) else length).intValueExact() }
            catch (_: ArithmeticException) { problem("Use a term containing a whole number of months.") }
        if (months !in 1..1200) problem("Use a loan term from 1 to 1,200 months.")
        if (principal.signum() == 0) return CalculationOutcome.Answer("0", "no loan amount, so no instalment")
        val instalment = if (rate.signum() == 0) divideNumbers(CalcNumber(principal), CalcNumber(months.toBigDecimal()))
        else {
            val monthly = rate.divide(BigDecimal(1200), calcContext)
            val growth = (BigDecimal.ONE + monthly).pow(months, calcContext)
            CalcNumber(bounded(principal.multiply(monthly, calcContext).multiply(growth, calcContext)
                .divide(growth - BigDecimal.ONE, calcContext)), true)
        }
        val paid = bounded(instalment.value.multiply(months.toBigDecimal(), calcContext))
        val interest = bounded(paid - principal)
        fun rupees(value: BigDecimal) = decimalText(value.setScale(2, java.math.RoundingMode.HALF_UP))
        return CalculationOutcome.Answer(decimalText(instalment.value),
            "monthly instalment ≈ ₹${rupees(instalment.value)} for $months months at ${decimalText(rate)}% a year; total paid ≈ ₹${rupees(paid)}, interest ≈ ₹${rupees(interest)}; fixed rate, no fees; lenders round differently",
            instalment.approximate)
    }

    private fun value(text: String) = bounded(text.toBigDecimal())
    private fun nonnegative(text: String, label: String): BigDecimal = value(text).also {
        if (it.signum() < 0) problem("Use a nonnegative $label.")
    }
    private fun percentage(text: String): BigDecimal = value(text).also {
        if (it < BigDecimal.ZERO || it > hundred) problem("This tool supports rates from 0% to 100%.")
    }
    private fun years(text: String): BigDecimal = nonnegative(text, "duration").also {
        if (it > hundred) problem("Use a forecast duration of at most 100 years.")
    }
}
