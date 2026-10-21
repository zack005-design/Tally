package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class MealBillTest {
    private val names=listOf("Paneer biryani","Veg noodles","Lime soda")
    @Test fun descriptionMatchesNamedRowsWithoutGuessingPayments() {
        assertEquals(setOf(0,2),matchMealItems("I ate paneer biryani and lime soda",names).getOrThrow())
    }
    @Test fun aUniqueShortNameCanIdentifyAnItem() {
        assertEquals(setOf(0),matchMealItems("biryani",names).getOrThrow())
    }
    @Test fun ambiguityAndUnknownNamesFailRatherThanPartiallyAssigningFood() {
        assertTrue(matchMealItems("chicken",listOf("Chicken curry","Chicken biryani")).isFailure)
        assertTrue(matchMealItems("biryani and pizza",names).isFailure)
        assertTrue(matchMealItems("tea",listOf("Tea","Tea")).isFailure)
    }
    @Test fun negationSharedAndQuantityInstructionsNeedExplicitRowSelection() {
        listOf("I did not eat biryani","half biryani","we shared biryani","one of the two biryani").forEach {
            assertTrue(it,matchMealItems(it,names).isFailure)
        }
    }
    @Test fun anItemNameContainingAndIsMatchedBeforeSplittingTheDescription() {
        assertEquals(setOf(0),matchMealItems("I ate rice and dal",listOf("Rice and dal","Tea")).getOrThrow())
    }
    @Test fun remainingRowsBelongToFriendAndSharedDishIsSplitEqually() {
        val lines=assignMealItems("120=self\n150=self\n60=self",setOf(0),setOf(2),"friend")
        assertEquals("120.00=self\n150.00=friend\n60.00=self+friend",lines)
        val shares=Finance.split(33000,listOf("self","friend"),foodSplitRule(lines,listOf("self","friend")))
        assertEquals(mapOf("self" to 15000L,"friend" to 18000L),shares)
        val settlement=Finance.settlements(Finance.net(shares,mapOf("self" to 30000L,"friend" to 3000L))).single()
        assertEquals(SuggestedSettlement("friend","self",15000),settlement)
    }
    @Test fun sharedChargesUseTheExistingPaiseAllocation() {
        val lines=assignMealItems("120=self\n150=self\n60=self",setOf(0),setOf(2),"friend")
        assertEquals(mapOf("self" to 16364L,"friend" to 19636L),Finance.split(36000,listOf("self","friend"),foodSplitRule(lines,listOf("self","friend"),3000)))
    }
    @Test fun invalidIndexesCannotSilentlyLoseAnItem() {
        assertTrue(runCatching {assignMealItems("120=self",setOf(3),emptySet(),"friend")}.isFailure)
    }
    @Test fun serviceAndDeliveryChargesAreNotAssignedAsFoodToTheFriend() {
        val bill=BillRecognition.parse("Cafe\nBiryani 120\nNoodles 150\nService charge 20\nDelivery fee 10\nTotal 300")
        assertEquals(listOf("Biryani","Noodles"),bill.items.map {it.name})
    }
    @Test fun malformedOrSignedPricesCannotBecomePositiveFoodAmounts() {
        listOf("123.456","-100","- 100","₹-100","-₹100").forEach {price->
            val bill=BillRecognition.parse("Cafe\nBiryani $price\nTotal $price")
            assertTrue(price,bill.items.isEmpty())
            assertNull(price,bill.total)
        }
    }
}
