package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class NaturalCalculatorTest {
    private fun answer(text: String): CalculationOutcome.Answer =
        NaturalCalculator.calculate(text).let { it as? CalculationOutcome.Answer ?: error("$text -> $it") }

    @Test fun arithmeticIsExactWithConventionalPrecedence() {
        mapOf("0.1 + 0.2" to "0.3", "2 + 3 * 4" to "14", "2(3+4)" to "14",
            "2^3^2" to "512", "-2^2" to "-4", "2^-2" to "0.25", "5!" to "120",
            "10 mod 3" to "1", "sqrt(144)" to "12", "abs(-5)" to "5").forEach { (input, expected) ->
            assertEquals(input, expected, answer(input).value)
        }
        assertFalse(answer("0.1 + 0.2").approximate)
        assertTrue(answer("1/3").approximate)
    }

    @Test fun commonLanguageAndIndianMagnitudesWork() {
        mapOf("what is 120 plus 85 plus 40" to "245", "add 120 and 85" to "205",
            "subtract 20 from 100" to "80", "multiply 12 by 8" to "96",
            "divide 900 by 3" to "300", "one hundred twenty plus thirty" to "150",
            "1.5 lakh + 25 thousand" to "175000", "two point five times four" to "10",
            "total lunch 120, coffee 80, taxi 45" to "245").forEach { (input, expected) ->
            assertEquals(input, expected, answer(input).value)
        }
    }

    @Test fun percentagesShowTheIntendedCalculation() {
        mapOf("15% of 200" to "30", "what is 15 percent of 200" to "30",
            "add 5% tax to 450" to "472.5", "take 10% off 500" to "450",
            "increase 200 by 10%" to "220", "decrease 200 by 10%" to "180").forEach { (input, expected) ->
            assertEquals(input, expected, answer(input).value)
        }
        assertTrue(NaturalCalculator.calculate("100 + 10%") is CalculationOutcome.NeedsInput)
    }

    @Test fun billSplitsConserveEveryPaisa() {
        val result = answer("split ₹100 between 3 people")
        assertEquals(listOf(3334L, 3333L, 3333L), result.shares)
        assertEquals(10000L, result.shares.sum())
        assertTrue(NaturalCalculator.calculate("split 100") is CalculationOutcome.NeedsInput)
    }

    @Test fun conversionsRequireCompatibleUnitsOrAnExplicitCurrencyRate() {
        mapOf("convert 2.5 km to m" to "2500 m", "500 g in kg" to "0.5 kg",
            "2 hours in minutes" to "120 min", "32 fahrenheit to celsius" to "0 °C",
            "convert 10 usd to inr at 83.5" to "835 INR").forEach { (input, expected) ->
            assertEquals(input, expected, answer(input).value)
        }
        assertTrue(NaturalCalculator.calculate("convert 10 usd to inr") is CalculationOutcome.NeedsInput)
        assertTrue(NaturalCalculator.calculate("2 kg to km") is CalculationOutcome.NeedsInput)
    }

    @Test fun simpleEquationsAndExplicitAngleUnitsWork() {
        assertEquals("x = 4", answer("2*x + 3 = 11").value)
        assertEquals("x = 2 or x = 3", answer("x^2 - 5*x + 6 = 0").value)
        assertEquals("0.5", answer("sin_deg(30)").value)
        assertTrue(answer("sin_rad(pi/2)").approximate)
        assertTrue(NaturalCalculator.calculate("sin(30)") is CalculationOutcome.NeedsInput)
    }

    @Test fun invalidAndAmbiguousRequestsDoNotInventAnswers() {
        listOf("", "hello", "1/0", "sqrt(-1)", "2 +", "2 3", "2^^3", "2^1000000",
            "101!", "2 kg to m", "x*x*x = 1", "1,23 + 1", "what did I spend last week",
            "(".repeat(40) + "1" + ")".repeat(40), "1".repeat(1001)).forEach {
            assertTrue(it, NaturalCalculator.calculate(it) is CalculationOutcome.NeedsInput)
        }
    }

    @Test fun reviewRegressionsDoNotFabricateAnswers() {
        listOf("one two", "twenty thirty", "2 1000", "split 1,23 between 3 people", "x/3*3=x", "one hundred hundred").forEach {
            assertTrue(it, NaturalCalculator.calculate(it) is CalculationOutcome.NeedsInput)
        }
        assertEquals("-1", answer("one-two").value)
        assertEquals("21", answer("twenty-one").value)
        val roots = answer("x^2 + 1e40*x + 1 = 0").value
        assertFalse(roots, roots.contains("x = 0"))
    }
}
