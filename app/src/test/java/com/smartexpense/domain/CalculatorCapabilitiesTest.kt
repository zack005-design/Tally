package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class CalculatorCapabilitiesTest {
    private fun answer(input: String) = NaturalCalculator.calculate(input).let {
        it as? CalculationOutcome.Answer ?: error("$input: $it")
    }
    @Test fun descriptiveStatisticsHaveKnownAnswers() {
        mapOf("sum 10, 20, 30" to "60", "average 10, 20, 30" to "20",
            "median 9, 1, 4, 2" to "3", "minimum -3, 2, 8" to "-3",
            "maximum -3, 2, 8" to "8", "population variance 2, 4, 6" to "2.666666666666666666666666666666667").forEach { (input, value) ->
            assertEquals(input, value, answer(input).value)
        }
        assertTrue(answer("average 1, 2, 2").approximate)
        assertEquals("2", answer("population standard deviation 2, 2, 6, 6").value)
        assertTrue(answer("population standard deviation 1, 2, 3").approximate)
    }
    @Test fun ratioReductionAndPercentageChangeAreExplicit() {
        assertEquals("2:3:4", answer("simplify ratio 12:18:24").value)
        assertEquals("0:1", answer("simplify ratio 0:10").value)
        assertEquals("25%", answer("percentage change from 80 to 100").value)
        assertEquals("-20%", answer("percentage change from 100 to 80").value)
    }
    @Test fun tipsAndReverseTaxShowComponents() {
        assertEquals("550", answer("tip ten percent on five hundred").value)
        assertEquals("1.005", answer("tip 0.5% on 1").value)
        assertEquals("100", answer("remove 0.5% tax from 100.5").value)
        val tip = answer("tip 10% on 500")
        assertEquals("550", tip.value)
        assertTrue(tip.expression.contains("tip 50"))
        val tax = answer("remove 18% tax from 1180")
        assertEquals("1000", tax.value)
        assertTrue(tax.expression.contains("tax 180"))
    }
    @Test fun interestRequiresExplicitPeriodsAndCompounding() {
        assertEquals("200", answer("simple interest on one thousand at ten percent per year for two years").value)
        assertEquals("0.005", answer("simple interest on 1 at 0.5% per year for 1 year").value)
        assertEquals("200", answer("simple interest on 1000 at 10% per year for 2 years").value)
        assertEquals("210", answer("compound interest on 1000 at 10% per year for 2 years compounded yearly").value)
        val monthly = answer("compound interest on 1000 at 12% per year for 1 year compounded monthly")
        assertEquals("126.825030131969720661201", monthly.value)
        assertTrue(monthly.expression.contains("final amount"))
    }
    @Test fun unclearOrInvalidInputsAskRatherThanGuess() {
        listOf("average", "average 1,000", "average 1, 2 apples", "simplify ratio 0:0",
            "simplify ratio -2:3", "percentage change from 0 to 100", "percentage change from -10 to 20",
            "tip -10% on 100", "tip 101% on 100", "remove 101% tax from 100",
            "simple interest on 1000 at 10% for 2 years", "compound interest on 1000 at 10% per year for 2 years",
            "simple interest on -1000 at 10% per year for 2 years",
            "compound interest on 1000 at 10% per year for 1000 years compounded monthly",
            "average " + List(101) { "1" }.joinToString(", ")).forEach {
            assertTrue(it, NaturalCalculator.calculate(it) is CalculationOutcome.NeedsInput)
        }
    }
    @Test fun compoundForecastPreservesZeroAndTinyPositiveInterest() {
        assertEquals("0", answer("compound interest on 12345678901234567890123456789012345 at 10% per year for 0 years compounded monthly").value)
        val tiny = answer("compound interest on 1 at 0.0000000000000000000000000000000000000001% per year for 12 years compounded yearly").value.toBigDecimal()
        assertTrue(tiny.signum() > 0)
        assertTrue((tiny - java.math.BigDecimal("1.2e-41")).abs() < java.math.BigDecimal("1e-70"))
    }
    @Test fun varianceResistsLargeOffsetAndMedianDoesNotMutateInput() {
        val offset = java.math.BigInteger.TEN.pow(40)
        val text = listOf(offset, offset + java.math.BigInteger.TWO, offset + java.math.BigInteger.valueOf(4)).joinToString(", ")
        assertEquals("2.666666666666666666666666666666667", answer("population variance $text").value)
        assertEquals("3", answer("median 9, 1, 4, 2").value)
        assertEquals("16", answer("sum 9, 1, 4, 2").value)
    }

    @Test fun sampleStatisticsUseBesselsCorrection() {
        assertEquals("4", answer("sample variance 2, 4, 6").value)
        assertEquals("2", answer("sample standard deviation 2, 4, 6").value)
        assertEquals("2.5", answer("sample variance 1, 2, 3, 4, 5").value)
        assertFalse(NaturalCalculator.calculate("sample variance 5") is CalculationOutcome.Answer)
        assertFalse(NaturalCalculator.calculate("standard deviation 2, 4, 6") is CalculationOutcome.Answer)
    }

    @Test fun gstCanBeAddedOrRemoved() {
        assertEquals("1180", java.math.BigDecimal(answer("add 18% gst to 1000").value).stripTrailingZeros().toPlainString())
        assertEquals("1000", java.math.BigDecimal(answer("remove 18% gst from 1180").value).stripTrailingZeros().toPlainString())
    }

    @Test fun loanEmiMatchesTheStandardFormula() {
        val emi = answer("emi on 100000 at 12% per year for 12 months")
        // 100000 × 0.01 × 1.01^12 / (1.01^12 − 1) = 8884.878867…
        assertEquals(8884.88, emi.value.toDouble(), 0.005)
        assertTrue(emi.approximate)
        assertTrue(emi.expression.contains("12 months"))
        assertEquals(8884.88, answer("loan emi for 100000 at 12% per annum for 1 year").value.toDouble(), 0.005)
        assertEquals("1000", answer("emi on 12000 at 0% per year for 12 months").value)
        assertEquals(10623.52, answer("emi on 500000 at 10% per year for 5 years").value.toDouble(), 0.005)
        listOf("emi on 100000 at 12% per year for 1.5 months", "emi on 100000 at 120% per year for 12 months",
            "emi on 100000 at 12% per year for 0 months", "emi on 100000").forEach {
            assertFalse(it, NaturalCalculator.calculate(it) is CalculationOutcome.Answer)
        }
    }
}

