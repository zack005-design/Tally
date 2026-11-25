package com.smartexpense.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class AssistantPlannerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 25)
    private val food = Category("food", "Food", 0, "", false)
    private val travel = Category("travel", "Travel", 0, "", false)
    private val gpay = Account("gpay", "GPay", "Bank", 0, false)
    private var next = 0
    private fun record(day: LocalDate, description: String, paise: Long, category: String? = "food", kind: Kind = Kind.EXPENSE) =
        Record("r${next++}", kind, description, paise, paise, category, null, day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            "", false, listOf("gpay"), listOf("self"))
    private val records = listOf(
        record(today, "Chai", 2_000), record(today.minusDays(2), "Lunch", 30_000), record(LocalDate.of(2026, 10, 5), "Chai", 1_500),
        record(LocalDate.of(2026, 10, 9), "Train to Pune", 90_000, "travel"), record(today, "Salary", 5_000_000, null, Kind.INCOME))
    private val data = AssistantPlanner.Data(records, listOf(food, travel), listOf(gpay), listOf(Budget("b", "2026-11", "food", 100_000)), today, zone)
    private fun run(program: String, slots: Map<String, String> = emptyMap()) = AssistantPlanner.execute(program.split(" "), slots, data)

    @Test fun comparesTwoPeriodsWithShownSteps() {
        val answer = run("spend this_month cat CAT1 ; spend last_month cat CAT1 ; compare \$1 \$2 ; answer", mapOf("CAT1" to "Food"))
        assertTrue(answer.text, answer.text.startsWith("Food this month is higher: ₹320.00 against ₹15.00"))
        assertEquals(3, answer.steps.size)
    }

    @Test fun textBudgetShareAndTop() {
        assertTrue(run("spend this_month text WORD1 ; answer", mapOf("WORD1" to "chai")).text.startsWith("You spent ₹20.00"))
        val budget = run("budget cat CAT1 ; answer", mapOf("CAT1" to "food"))
        assertTrue(budget.text, budget.text.startsWith("You have ₹680.00 left in your Food budget"))
        val share = run("spend month october cat CAT1 ; spend month october ; share \$1 \$2 ; answer", mapOf("CAT1" to "Food"))
        assertTrue(share.text, share.text.contains("1.6%"))
        assertTrue(run("top last_month ; answer").text.startsWith("Your top categories last month: Travel ₹900.00; Food ₹15.00"))
        assertTrue(run("income this_month ; answer").text.contains("₹50000.00"))
    }

    @Test fun savingsPlansAndWhatIfAreExact() {
        assertTrue(run("saveplan NUM1 months NUM2 ; answer", mapOf("NUM1" to "1 lakh", "NUM2" to "12")).text.startsWith("Save ₹8333.34 a month"))
        assertTrue(run("savetime NUM1 per NUM2 ; answer", mapOf("NUM1" to "50,000", "NUM2" to "7k")).text.contains("in 8 months"))
        val whatIf = run("whatif cat CAT1 cut NUM1 ; answer", mapOf("CAT1" to "Travel", "NUM1" to "50"))
        assertTrue(whatIf.text, whatIf.text.startsWith("Cutting Travel by 50% would save about ₹450.00 a month, or ₹5400.00 a year"))
    }

    @Test fun invalidProgramsAreRejected() {
        listOf("spend ; answer", "spend this_month cat CAT9 ; answer", "compare \$1 \$2 ; answer", "spend this_month",
            "launch rockets ; answer", "saveplan NUM1 months NUM2 ; answer", "help weather ; answer").forEach { program ->
            assertTrue(program, runCatching { run(program, mapOf("NUM1" to "0", "NUM2" to "0")) }.isFailure)
        }
    }

    @Test fun anonymiserKeepsQuestionWordsAndMasksNamesAndNumbers() {
        val (tokens, slots) = AssistantPlanner.anonymise("How much did I spend on Food at chai stalls over ₹2,000 last month?",
            listOf("Food"), listOf("GPay"), setOf("how", "much", "did", "i", "spend", "on", "at", "over", "last", "month"))!!
        assertEquals("how much did i spend on CAT1 at WORD1 WORD2 over NUM1 last month ?", tokens.joinToString(" "))
        assertEquals(mapOf("CAT1" to "Food", "WORD1" to "chai", "WORD2" to "stalls", "NUM1" to "2,000"), slots)
        assertTrue(AssistantPlanner.looksLikeQuestion("did I spend more on food this month?"))
        assertFalse(AssistantPlanner.looksLikeQuestion("coffee 45 cash"))
    }
}
