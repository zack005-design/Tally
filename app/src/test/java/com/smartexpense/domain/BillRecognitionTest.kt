package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class BillRecognitionTest {
    @Test fun totalExcludesSubtotalAndKeepsEditableItems() {
        val bill=BillRecognition.parse("Cafe Tally\n12/10/2026\nTea 50.00\nCake 100.00\nSubtotal 150.00\nTax 7.50\nGrand Total INR 157.50")
        assertEquals(15750L,bill.total)
        assertEquals("2026-10-12",bill.date)
        assertEquals(listOf(5000L,10000L),bill.items.map {it.amount})
        assertEquals("Cafe Tally",bill.merchant)
    }
    @Test fun conflictingTotalsAndUnreadableTextRequireManualAmount() {
        assertNull(BillRecognition.parse("Store\nTotal 100.00\nTotal 110.00").total)
        assertNull(BillRecognition.parse("blurry unreadable").total)
        assertNull(BillRecognition.parse("Store\nTotal USD 12.00").total)
        assertNull(BillRecognition.parse("Store\n31/02/2026\nTotal 100.00").date)
    }
    @Test fun repeatedSameTotalIsOneCandidateAndCommaGroupsAreHandled() {
        assertEquals(123450L,BillRecognition.parse("Store\nTOTAL ₹1,234.50\nAmount due 1,234.50").total)
    }
}
