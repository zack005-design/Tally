package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class FavouriteExpenseTest {
    @Test fun templateCreatesNewDraftAndNeverReusesTransactionIdentity() {
        val favourite=FavouriteExpense("tea","Tea",2500,"cash","food")
        val a=favourite.draft(1000);val b=favourite.draft(2000)
        assertNotEquals(a.id,b.id);assertEquals(1000L,a.date);assertEquals(2000L,b.date)
        assertEquals(2500L,a.amount);assertEquals("cash",a.accountId);assertEquals("food",a.categoryId)
        assertEquals(listOf("self"),a.participants);assertEquals(Kind.EXPENSE,a.kind)
    }
    @Test fun invalidFavouriteIsRejected() {
        try {FavouriteExpense("id","",100,"cash",null);fail("Empty description accepted")} catch(_:IllegalArgumentException) {}
        try {FavouriteExpense("id","Tea",0,"cash",null);fail("Zero amount accepted")} catch(_:IllegalArgumentException) {}
    }
}
