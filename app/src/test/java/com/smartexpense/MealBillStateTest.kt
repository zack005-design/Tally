package com.smartexpense

import com.smartexpense.ui.*
import com.smartexpense.domain.*
import org.junit.Assert.*
import org.junit.Test

class MealBillStateTest {
    @Test fun choosingFriendAssignsRemainderButDoesNotInventPayments() {
        val state=EntryUiState(items="120=self\n210=self",billItemNames=listOf("Biryani","Noodles"),paid=mapOf("self" to "300"))
        val selected=state.withMealFriend("f")
        assertEquals("120.00=f\n210.00=f",selected.items)
        assertEquals("300",selected.paid["self"])
        assertEquals("",selected.paid["f"])
        assertEquals("Itemized",selected.method)
    }
    @Test fun changingFriendPreservesMineAndSharedRowsButNotOldFriendsPayment() {
        val state=EntryUiState(shared=true,participants=listOf("self","old"),items="120=self\n150=old\n60=self+old",billItemNames=listOf("A","B","C"),paid=mapOf("self" to "300","old" to "30"))
        val selected=state.withMealFriend("new")
        assertEquals("120.00=self\n150.00=new\n60.00=self+new",selected.items)
        assertEquals(mapOf("self" to "300","new" to ""),selected.paid)
    }
    @Test fun namesDoNotLeakToUnrelatedDraftsOrGroups() {
        val state=EntryUiState(billItemNames=listOf("Meal"))
        assertTrue(state.withGroup(Group("g","Group",listOf("self","f"))).billItemNames.isEmpty())
        assertTrue(state.reuse(Record("r",Kind.EXPENSE,"Lunch",100,100,null,null,0,"",false)).billItemNames.isEmpty())
    }
}
