package com.smartexpense.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SmartSearchTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val categories = listOf(Category("food", "Food", 0, "", false), Category("bills", "Bills", 0, "", false),
        Category("old", "Old stuff", 0, "", true))
    private val accounts = listOf(Account("gpay", "GPay", "Bank", 0, false), Account("wallet", "Wallet", "Cash", 0, false))
    private fun ask(text: String) = SmartSearch.understand(text, categories, accounts, today)

    @Test fun amountPeriodAndTextCombine() {
        val q = ask("coffee over 100 last month")
        assertEquals("coffee", q.text)
        assertEquals(10_000L, q.minimum)
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31), q.start to q.end)
        assertTrue(q.structured)
    }

    @Test fun categoryAccountMonthAndKind() {
        val q = ask("food in october by gpay")
        assertEquals("food", q.categoryId)
        assertEquals("gpay", q.accountId)
        assertEquals(LocalDate.of(2026, 10, 1), q.start)
        assertEquals("", q.text)
        val refunds = ask("refunds under ₹500")
        assertEquals(Kind.REFUND, refunds.kind)
        assertEquals(50_000L, refunds.maximum)
        assertEquals(LocalDate.of(2025, 12, 1), ask("bills in december").start)
    }

    @Test fun rangesAndRecentDays() {
        val q = ask("between 100 and 1,000 in the last 7 days")
        assertEquals(10_000L to 100_000L, q.minimum to q.maximum)
        assertEquals(today.minusDays(6) to today, q.start to q.end)
    }

    @Test fun plainSearchesStayPlain() {
        listOf("80", "₹1,250", "masala dosa", "Old stuff").forEach {
            val q = ask(it)
            assertFalse(it, q.structured)
            assertEquals(it, it, q.text)
        }
    }
}
