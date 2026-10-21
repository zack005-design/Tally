package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneOffset

class BillSummaryTest {
    private val record=Record("bill",Kind.EXPENSE,"Dinner",90000,30000,null,null,0,"PRIVATE NOTE",false)
    private val detail=RecordDetail(record,emptyList(),mapOf("self" to 30000,"f" to 60000),mapOf("self" to 90000,"f" to 0),emptyList(),emptyMap())
    @Test fun summaryUsesBillSpecificRemainingDebtsAndOmitsNotes() {
        val result=billSummary(detail,listOf(Person("f","Rahul")),listOf(Debt("d","bill","f","self",60000,20000,0),Debt("other","otherbill","self","f",80000,80000,0)),ZoneOffset.UTC)
        assertTrue(result.contains("You paid ₹900.00"));assertTrue(result.contains("Rahul paid ₹0.00"))
        assertTrue(result.contains("Rahul owes You: ₹200.00"))
        assertFalse(result.contains("PRIVATE NOTE"));assertFalse(result.contains("₹800.00"))
    }
    @Test fun settledBillIsLabelledAndReversedIsRejected() {
        assertTrue(billSummary(detail,emptyList(),emptyList(),ZoneOffset.UTC).contains("No outstanding amounts on this bill"))
        try {billSummary(detail.copy(record=record.copy(reversed=true)),emptyList(),emptyList());fail("Shared reversed bill")} catch(_:IllegalArgumentException) {}
    }
}
