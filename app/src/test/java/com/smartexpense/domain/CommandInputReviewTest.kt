package com.smartexpense.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CommandInputReviewTest {
    private val fallback = RuleBasedTextParser(Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneId.of("Asia/Kolkata")))
    private val parser = CommandExpenseTextParser(null, fallback)

    @Test fun selfPaidPersonalExpenseUsesTheLearnedDescriptionAndAccount() {
        val model = java.io.File("src/main/assets/tally-command.bin").inputStream().use(ScratchCommandModel::load)
        val parsed = CommandExpenseTextParser(model, fallback).parse("I paid 45 for coffee using cash", emptyList())
        assertEquals("coffee", parsed.description)
        assertEquals(4500L, parsed.amount)
        assertEquals("wallet", parsed.fromAccount)
        assertEquals(mapOf("self" to 4500L), parsed.contributions)
    }

    @Test fun unsafePersonalExpensesRemainIncompleteEvenWithoutTheModel() {
        listOf("coffee -45 cash", "coffee 45.999 cash", "coffee 12,34 cash",
            "coffee 45 cash google pay", "coffee 45 wallet g-pay",
            "coffee 45 dollars cash", "paid 40 and 70 for coffee using cash",
            "I did not pay 45 for coffee using cash", "I will spend 90 on lunch through gpay",
            "We will spend 90 on lunch through gpay", "Plan lunch 90 cash", "No expense coffee 45 cash",
            "I paid 45 dollars for coffee using cash", "I paid 45 for coffee using cash and gpay",
            "I paid 45.999 for coffee using cash", "I paid -45 for coffee using cash",
            "Can I spend 80 on lunch from wallet?").forEach {
            val draft = parser.parse(it, emptyList())
            assertNull(it, draft.amount)
            assertTrue(it, "amount" in draft.missing)
            assertTrue(it, draft.warnings.isNotEmpty())
        }
    }

    @Test fun validDatesAndDecimalAmountsArePreserved() {
        val text = "Spent 99.50 on coffee using cash on 2026-10-20"
        val parsed = parser.parse(text, emptyList())
        assertEquals(9950L, parsed.amount)
        assertEquals(java.time.LocalDate.of(2026, 10, 20), parsed.date)
        assertEquals(4500L, parser.parse("Spent 45.", emptyList()).amount)
    }

    @Test fun transferIncomeAndSharedBillBehaviorIsPreserved() {
        listOf("Transferred 1000 from bank to cash", "Salary 45000 gpay",
            "Food for 900. I paid 500, Rahul paid 400. Split equally.").forEach {
            val people = listOf(PersonRef("rahul", "Rahul"))
            assertEquals(it, fallback.parse(it, people), parser.parse(it, people))
        }
    }
}
