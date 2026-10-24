package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class BillRecognitionTest {
    @Test fun abbreviatedAmountHeaderAndSpacedRateKeepOnlyItemNames() {
        val bill=BillRecognition.parse("Test Cafe\nItem Qty Rate Amt\nTea 2 25. 00 50.00\nBread 1 30.00 30.00\nTotal 80.00")
        assertEquals(listOf(BillItem("Tea",5000),BillItem("Bread",3000)),bill.items)
    }
    @Test fun paymentAmountIsNotAnAdditionalPurchase() {
        val bill=BillRecognition.parse("Test Cafe\nTea 50.00\nGrand Total 50.00\nPaid Amount 50.00\nBalance 0.00")
        assertEquals(listOf(BillItem("Tea",5000)),bill.items)
    }
    @Test fun yearFirstReceiptDateIsStrictAndConflictsStillNeedReview() {
        assertEquals("2026-02-06",BillRecognition.parse("Test Cafe\nDate: 2026-02-06 19:32\nTotal 50.00").date)
        assertNull(BillRecognition.parse("Test Cafe\nDate: 2026-02-30\nTotal 50.00").date)
        assertNull(BillRecognition.parse("Test Cafe\n2026-02-06\n07/02/2026\nTotal 50.00").date)
    }
    @Test fun finalPayableTotalTakesPrecedenceOverPreTaxTotal() {
        val bill=BillRecognition.parse("Cafe\nTea 100.00\nTotal 100.00\nCGST 2.50\nSGST 2.50\nGrand Total 105.00")
        assertEquals(10500L,bill.total)
        assertNull(BillRecognition.parse("Cafe\nGrand Total 105.00\nAmount Payable 110.00").total)
    }
    @Test fun taxRegistrationAndSummaryCountsAreNotFoodOrTotals() {
        val bill=BillRecognition.parse("Cafe\nGSTIN 123456\nTea 100.00\nTaxable Value 100.00\nIGST 5.00\nTotal Items 1\nTotal Qty 1\nTotal 105.00")
        assertEquals(listOf(BillItem("Tea",10000)),bill.items)
        assertEquals(10500L,bill.total)
    }
    @Test fun separateAmountLinesAreJoinedWithoutConsumingMetadata() {
        val bill=BillRecognition.parse("Corner Cafe\nDate: 12/10/2026\nTea\n50.00\nCake\n100.00\nGrand Total\n157.50\nCGST\n3.75\nSGST\n3.75")
        assertEquals(listOf(BillItem("Tea",5000),BillItem("Cake",10000)),bill.items)
        assertEquals(15750L,bill.total)
    }
    @Test fun tableColumnsDoNotBecomePartOfFoodNames() {
        val bill=BillRecognition.parse("Cafe\nItem Qty Rate Amount\nMasala Tea 2 25.00 50.00\n7 Up 1 40.00 40.00\nGrand Total 90.00")
        assertEquals(listOf(BillItem("Masala Tea",5000),BillItem("7 Up",4000)),bill.items)
    }
    @Test fun missingItemsAreFlaggedBeforeAnyManualEdits() {
        val bill=BillRecognition.parse("Cafe\nTea 50.00\nSubtotal 150.00\nTax 7.50\nTotal 157.50")
        assertTrue(bill.warnings.any {it.contains("missing",true) && it.contains("items",true)})
    }
    @Test fun receiptDateAndInvoiceNumberDoNotHideMissingItems() {
        val bill=BillRecognition.parse("Cafe\nDate: 12/10/2026\nInvoice 123\nPhone 1234567890\nTea 50.00\nTotal 150.00")
        assertTrue(bill.warnings.any {it.contains("items may be missing")})
    }
    @Test fun longBillsDoNotSilentlyDropItemsAfterFiftyRows() {
        val text="Cafe\n"+(1..60).joinToString("\n") {"Tea 10.00"}+"\nTotal 600.00"
        assertEquals(60,BillRecognition.parse(text).items.size)
    }
    @Test fun quantitiesAreNotStrippedFromNamesWithoutATableHeader() {
        assertEquals("Formula 1 2026",BillRecognition.parse("Store\nFormula 1 2026 50.00\nTotal 50.00").items.single().name)
    }
    @Test fun taxDifferenceDoesNotClaimItemsAreMissingWhenSubtotalMatches() {
        val bill=BillRecognition.parse("Cafe\nTea 50.00\nSubtotal 50.00\nTax 2.50\nTotal 52.50")
        assertFalse(bill.warnings.any {it.contains("items may be missing")})
    }
    @Test fun signedDiscountDoesNotLookLikeAMissingItem() {
        val bill=BillRecognition.parse("Cafe\nTea 100.00\nDiscount -10.00\nTotal 90.00")
        assertFalse(bill.warnings.any {it.contains("items may be missing")})
    }
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
