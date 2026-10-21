package com.smartexpense.domain

import java.math.BigDecimal
import java.util.Locale

sealed interface CalculationOutcome {
    data class Answer(val value: String, val expression: String, val approximate: Boolean = false, val shares: List<Long> = emptyList()) : CalculationOutcome
    data class NeedsInput(val message: String) : CalculationOutcome
}

/** Offline calculation tool; never creates transactions or executes user code. */
object NaturalCalculator {
    fun calculate(input: String): CalculationOutcome = try {
        if (input.length > 1000) problem("Please shorten the calculation to 1,000 characters.")
        var text = input.lowercase(Locale.ROOT).trim().removeSuffix("?")
            .replace("×", "*").replace("÷", "/").replace("−", "-").replace("π", "pi")
            .replace("²", "^2").replace("³", "^3").replace("percent", "%")
            .replace(Regex("^(please |what is |calculate |work out )+"), "")
        if (text.isBlank()) problem("Enter a calculation, such as 120 + 85, or choose a guided calculation.")
        text = spoken(text)
        if (text.startsWith("split ")) {
            val m = Regex("split [₹]?([0-9.,]+) (?:between|among|by) ([0-9]+)(?: people| persons)?").matchEntire(text)
                ?: problem("How much should be split, and between how many people? Try: split 100 between 3 people.")
            val count = m.groupValues[2].toIntOrNull() ?: problem("Enter a whole number of people.")
            if (count !in 1..100) problem("Choose between 1 and 100 people.")
            ExpressionCalculator.evaluate(m.groupValues[1]) // Validate comma grouping before parsing money.
            val total = Money.parse(m.groupValues[1])
            if (total <= 0) problem("Enter a positive bill total with at most two decimal places.")
            val shares = Finance.split(total, (1..count).map(Int::toString), SplitRule.Equal).values.toList()
            CalculationOutcome.Answer(shares.joinToString(" + ", transform = Money::format), "${Money.format(total)} ÷ $count people; remainder distributed one paisa at a time", shares = shares)
        } else {
            conversion(text) ?: run {
                text = text.replace("₹", "").replace(Regex("\\b(?:rs|inr)\\b\\.?"), "").trim()
                val percent = listOf(
                    Regex("(.+?)% of (.+)") to { m: MatchResult -> "(${m.groupValues[1]})/100*(${m.groupValues[2]})" },
                    Regex("add (.+?)% (?:tax )?to (.+)") to { m: MatchResult -> "(${m.groupValues[2]})*(1+(${m.groupValues[1]})/100)" },
                    Regex("take (.+?)% off (.+)") to { m: MatchResult -> "(${m.groupValues[2]})*(1-(${m.groupValues[1]})/100)" },
                    Regex("(increase|decrease) (.+?) by (.+?)%") to { m: MatchResult -> "(${m.groupValues[2]})*(1${if (m.groupValues[1] == "increase") "+" else "-"}(${m.groupValues[3]})/100)" }
                ).firstNotNullOfOrNull { (pattern, render) -> pattern.matchEntire(text)?.let(render) }
                if (percent != null) text = percent
                else if (text.contains('%') && Regex("[+-].*%").containsMatchIn(text)) problem("Do you mean a percentage of the starting amount? Try: increase 100 by 10%.")
                text = Regex("subtract (.+) from (.+)").matchEntire(text)?.let { "(${it.groupValues[2]})-(${it.groupValues[1]})" }
                    ?: Regex("(multiply|divide) (.+) by (.+)").matchEntire(text)?.let { "(${it.groupValues[2]})${if (it.groupValues[1] == "multiply") "*" else "/"}(${it.groupValues[3]})" }
                    ?: if (text.startsWith("add ")) text.removePrefix("add ").replace(" and ", " + ") else text
                if (text.startsWith("total ")) {
                    val parts = text.removePrefix("total ").split(Regex(",\\s+| and "))
                    text = parts.joinToString(" + ") { part ->
                        Regex("(?:[a-z]+\\s+)*([0-9]+(?:\\.[0-9]+)?)").matchEntire(part.trim())?.groupValues?.get(1)
                            ?: problem("List each item with one amount, such as: total lunch 120, coffee 80.")
                    }
                }
                text = text.replace("multiplied by", "*").replace("divided by", "/")
                    .replace("plus", "+").replace("minus", "-").replace("times", "*")
                if (text.contains('=')) CalculationOutcome.Answer(ExpressionCalculator.solve(text.removePrefix("solve ")), text, approximate = true)
                else ExpressionCalculator.evaluate(text).let { CalculationOutcome.Answer(decimalText(it.value), text, it.approximate) }
            }
        }
    } catch (e: CalculationProblem) { CalculationOutcome.NeedsInput(e.message ?: "Please clarify the calculation.") }
      catch (_: ArithmeticException) { CalculationOutcome.NeedsInput("That calculation is outside the supported number range. Try smaller numbers.") }
      catch (_: NumberFormatException) { CalculationOutcome.NeedsInput("Check the numbers and decimal places.") }
      catch (_: IllegalArgumentException) { CalculationOutcome.NeedsInput("Check the amount and use at most two decimal places for a bill split.") }

    private fun spoken(source: String): String {
        val small = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen".split(' ').withIndex().associate { it.value to it.index } + mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90))
        val scale = mapOf("thousand" to 1000L, "lakh" to 100000L, "crore" to 10000000L, "million" to 1000000L)
        val words = (small.keys + scale.keys + "hundred" + "point").sortedByDescending(String::length).joinToString("|")
        val expanded = Regex("([0-9]+(?:\\.[0-9]+)?)\\s+(thousand|lakh|crore|million)\\b").replace(source) { "(${it.groupValues[1]}*${scale.getValue(it.groupValues[2])})" }
        val hyphens = Regex("\\b(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)-(one|two|three|four|five|six|seven|eight|nine)\\b").replace(expanded, "$1 $2")
        val result = Regex("\\b(?:$words)(?: +(?:$words))*\\b").replace(hyphens) { match ->
            var sum = 0L; var current = 0L; var previousScale = Long.MAX_VALUE
            val halves = match.value.split(" point ", limit = 2)
            val wholeWords = halves[0].split(Regex("[ -]+"))
            wholeWords.zipWithNext().forEach { (a, b) ->
                if (a in small && b in small && !(small.getValue(a) >= 20 && small.getValue(a) % 10 == 0 && small.getValue(b) in 1..9))
                    problem("Please separate those numbers with an operation, or write the number using digits.")
            }
            wholeWords.forEach { word ->
                when { word in small -> current = Math.addExact(current, small.getValue(word).toLong())
                    word == "hundred" -> { if (current !in 1..9) problem("Please write that number using digits."); current = Math.multiplyExact(current, 100) }
                    word in scale -> {
                        val magnitude = scale.getValue(word)
                        if (current == 0L || magnitude >= previousScale) problem("Please write that number using digits.")
                        sum = Math.addExact(sum, Math.multiplyExact(current, magnitude)); current = 0; previousScale = magnitude
                    }
                    else -> problem("Please write that number using digits.") }
            }
            val decimal = if (halves.size == 2) "." + halves[1].split(' ').joinToString("") { (small[it]?.takeIf { n -> n < 10 } ?: problem("Use individual digits after point.")).toString() } else ""
            "${Math.addExact(sum, current)}$decimal"
        }
        return result
    }

    private data class Unit(val family: String, val factor: String, val label: String)
    private fun conversion(text: String): CalculationOutcome.Answer? {
        val m = Regex("(?:convert )?([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*([a-z]+) (?:to|in) ([a-z]+)(?: at ([0-9]+(?:\\.[0-9]+)?))?").matchEntire(text) ?: return null
        val amount = m.groupValues[1]; val from = m.groupValues[2]; val to = m.groupValues[3]; val rate = m.groupValues[4]
        val currencies = setOf("usd", "inr", "eur", "gbp", "jpy", "aud", "cad")
        if (from in currencies && to in currencies) {
            if (rate.isBlank()) problem("What exchange rate should I use? Add: at 83.5 (target currency per source unit).")
            if (rate.toBigDecimal().signum() <= 0) problem("Use a positive exchange rate.")
            val n = ExpressionCalculator.evaluate("$amount*$rate")
            return CalculationOutcome.Answer("${decimalText(n.value)} ${to.uppercase(Locale.ROOT)}", "$amount $from × $rate $to per $from", n.approximate)
        }
        if (rate.isNotEmpty()) problem("An exchange rate is only supported for currency conversion.")
        val temperatures = mapOf("celsius" to "c", "c" to "c", "fahrenheit" to "f", "f" to "f", "kelvin" to "k", "k" to "k")
        if (from in temperatures && to in temperatures) {
            val c = when (temperatures[from]) { "f" -> "($amount-32)*5/9"; "k" -> "($amount-273.15)"; else -> amount }
            if (ExpressionCalculator.evaluate(c).value < BigDecimal("-273.15")) problem("Temperature cannot be below absolute zero.")
            val expression = when (temperatures[to]) { "f" -> "($c)*9/5+32"; "k" -> "($c)+273.15"; else -> c }
            val n = ExpressionCalculator.evaluate(expression)
            return CalculationOutcome.Answer("${decimalText(n.value)} ${when (temperatures[to]) { "f" -> "°F"; "k" -> "K"; else -> "°C" }}", expression, n.approximate)
        }
        val units = mutableMapOf<String, Unit>()
        fun unit(names: String, family: String, factor: String, label: String) { names.split(' ').forEach { units[it] = Unit(family, factor, label) } }
        unit("m meter meters metre metres", "length", "1", "m"); unit("km kilometer kilometers", "length", "1000", "km")
        unit("cm centimeters", "length", "0.01", "cm"); unit("mm", "length", "0.001", "mm")
        unit("kg kilogram kilograms", "mass", "1000", "kg"); unit("g gram grams", "mass", "1", "g")
        unit("hour hours hr", "time", "3600", "h"); unit("minute minutes min", "time", "60", "min"); unit("second seconds sec s", "time", "1", "s")
        unit("l liter liters litre litres", "volume", "1", "L"); unit("ml", "volume", "0.001", "mL")
        val a = units[from] ?: problem("Which supported unit do you mean? Try km, m, kg, g, hours, minutes, L or mL.")
        val b = units[to] ?: problem("Which target unit do you mean?")
        if (a.family != b.family) problem("Those units measure different quantities. Choose compatible units.")
        val expression = "$amount*${a.factor}/${b.factor}"
        val n = ExpressionCalculator.evaluate(expression)
        return CalculationOutcome.Answer("${decimalText(n.value)} ${b.label}", expression, n.approximate)
    }
}
