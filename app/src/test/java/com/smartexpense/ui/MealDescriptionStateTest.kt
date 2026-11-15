package com.smartexpense.ui

import com.smartexpense.domain.namedMealItems
import org.junit.Assert.assertEquals
import org.junit.Test

class MealDescriptionStateTest {
    private val initial=EntryUiState(amount="330",description="Dinner",accountId="wallet",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "300","f" to "30"),method="Itemized",items="120=f\n150=f\n60=f",billItemNames=listOf("Paneer biryani","Veg noodles","Lime soda"))

    @Test fun sayingWhatIAteAssignsThoseRowsToMeAndTheRestToMyFriend() {
        val next=initial.withMealDescription("I ate paneer biryani","self")
        assertEquals(listOf(listOf("self"),listOf("f"),listOf("f")),namedMealItems(next.billItemNames,next.items).map {it.people})
        assertEquals(initial.paid,next.paid)
    }
}
