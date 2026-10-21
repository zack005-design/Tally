package com.smartexpense

import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Test

class SalmonBillTest {
    private val names=listOf("Bara Chirashi","Smoked Salmon","Blackcurrant","Lemmy Lemonade")
    private fun draft()=EntryUiState(amount="44",description="Lunch",shared=true,participants=listOf("self","f"),method="Itemized",items="15.89=f\n15.89=f\n4.67=f\n4.67=f",billItemNames=names,itemCharges="2.88",paid=mapOf("self" to "44","f" to "0"))
    @Test fun salmonAndProportionalTaxGiveSeventeenAndTwentySeven() {
        val s=draft().withMealDescription("I ate smoked salmon","self")
        val result=automaticBill(s).getOrThrow()
        assertEquals(mapOf("self" to 1700L,"f" to 2700L),result.shares)
        assertEquals(listOf(SuggestedSettlement("f","self",2700)),result.settlements)
    }
    @Test fun friendDescriptionChangesSelectedItemsWithoutMovingOtherItemsOrPayments() {
        val s=draft().withMealDescription("smoked salmon and blackcurrant","self")
        val updated=s.withMealDescription("blackcurrant","f")
        assertEquals(listOf("self"),namedMealItems(names,updated.items)[1].people)
        assertEquals(listOf("f"),namedMealItems(names,updated.items)[2].people)
        assertEquals(s.paid,updated.paid)
        assertEquals("2.88",updated.itemCharges)
    }
    @Test fun unknownFriendFoodDoesNotApplyPartialChanges() {
        assertTrue(runCatching {draft().withMealDescription("salmon and pizza","f")}.isFailure)
    }
    @Test fun partialPaymentsReduceTheRemainingDebt() {
        val s=draft().withMealDescription("smoked salmon","self").copy(paid=mapOf("self" to "30","f" to "14"))
        assertEquals(listOf(SuggestedSettlement("f","self",1300)),automaticBill(s).getOrThrow().settlements)
    }
    @Test fun friendPayingEverythingShowsWhatIOweInstead() {
        val s=draft().withMealDescription("smoked salmon","self").copy(paid=mapOf("self" to "0","f" to "44"))
        assertEquals(listOf(SuggestedSettlement("self","f",1700)),automaticBill(s).getOrThrow().settlements)
    }
    @Test fun explicitSharedRowsStaySharedWhenEnteringFriendFood() {
        val s=draft().copy(items="15.89=f\n15.89=self\n4.67=self+f\n4.67=f")
        val updated=s.withMealDescription("blackcurrant","f")
        assertEquals(listOf("self","f"),namedMealItems(names,updated.items)[2].people)
        assertEquals(s.paid,updated.paid)
    }
    @Test fun receiptRowsExcludePaymentTaxAndHeaderMetadata() {
        val text="""NUDE Sea Food
            |EMB Outlet 3
            |Gst Reg No: 201423107N
            |Pager : 9
            |Pax:1
            |1 * Bara Chirashi ₹15.89
            |1 * Smoked Salmon ₹15.89
            |1 Blackcurrant ₹4.67
            |1 Lemmy Lemonade ₹4.67
            |SUBTOTAL ₹41.12
            |7% GST ₹2.88
            |TOTAL ₹44.00
            |VISA ₹44.00""".trimMargin()
        val bill=BillRecognition.parse(text)
        assertEquals(listOf("Bara Chirashi","Smoked Salmon","1 Blackcurrant","1 Lemmy Lemonade"),bill.items.map {it.name})
        assertEquals(4112L,Finance.total(bill.items.map {it.amount}))
        assertEquals(4400L,bill.total)
    }
    @Test fun numericFoodNamesStayMatchableAndOnlyExplicitQuantitiesAreRemoved() {
        val bill=BillRecognition.parse("Cafe\n7 Up ₹50\n65 Chicken ₹250\n2 x Coffee ₹100\nTotal ₹400")
        assertEquals(listOf("7 Up","65 Chicken","Coffee"),bill.items.map {it.name})
        assertEquals(setOf(0),matchMealItems("I ate 7 Up",bill.items.map {it.name}).getOrThrow())
    }
}
