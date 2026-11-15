package com.smartexpense.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptItemsTest {
    @Test fun reviewedImportItemsWinOverRecognisedText() {
        val notes="Imported printed bill · reviewed locally\nPaneer tikka: 320.00\nNaan x2: 90.50\nnot an item\n: 10"
        assertEquals(listOf(BillItem("Paneer tikka",32000),BillItem("Naan x2",9050)),ReceiptItems.from(notes,"Other\nTea 20.00\nTotal 20.00"))
    }

    @Test fun ordinaryNotesAreNeverTreatedAsItems() {
        assertEquals(emptyList<BillItem>(),ReceiptItems.from("Private: 500",null))
    }

    @Test fun fallsBackToTheAttachedReceipt() {
        val items=ReceiptItems.from("","Cafe Blue\nTea 20.00\nSandwich 80.00\nTotal 100.00")
        assertEquals(listOf(20_00L,80_00L),items.map {it.amount})
    }
}
