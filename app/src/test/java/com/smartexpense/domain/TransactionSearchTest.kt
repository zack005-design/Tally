package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class TransactionSearchTest {
    private fun record(amount: Long, personal: Long) =
        Record("r", Kind.EXPENSE, "Coffee", amount, personal, null, null, 0L, "", false)

    @Test fun readsAmountQueries() {
        assertEquals(8000L, TransactionSearch.amount("80"))
        assertEquals(8000L, TransactionSearch.amount(" ₹80 "))
        assertEquals(8000L, TransactionSearch.amount("₹ 80"))
        assertEquals(125000L, TransactionSearch.amount("1,250"))
        assertEquals(2050L, TransactionSearch.amount("20.50"))
    }

    @Test fun ignoresTextAndUnusableAmounts() {
        assertNull(TransactionSearch.amount(""))
        assertNull(TransactionSearch.amount("coffee"))
        assertNull(TransactionSearch.amount("coffee 80"))
        assertNull(TransactionSearch.amount("#goa"))
        assertNull(TransactionSearch.amount("0"))
        assertNull(TransactionSearch.amount("-80"))
        assertNull(TransactionSearch.amount("20.505"))
        assertNull(TransactionSearch.amount("99999999999999999999"))
    }

    @Test fun matchesTotalOrShareIgnoringSign() {
        assertTrue(TransactionSearch.matchesAmount(record(90000, 30000), 90000))
        assertTrue(TransactionSearch.matchesAmount(record(90000, 30000), 30000))
        assertTrue(TransactionSearch.matchesAmount(record(-5000, -5000), 5000))
        assertFalse(TransactionSearch.matchesAmount(record(90000, 30000), 8000))
    }
}
