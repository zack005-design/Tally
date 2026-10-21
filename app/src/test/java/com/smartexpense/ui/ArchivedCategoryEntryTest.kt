package com.smartexpense.ui
import com.smartexpense.domain.*
import org.junit.Assert.*
import org.junit.Test
class ArchivedCategoryEntryTest {
    @Test fun archivedCategoryRequiresAnExplicitReplacement() {
        val accounts=listOf(Account("cash","Cash","Cash",10000,false))
        val categories=listOf(Category("old","Old",0,"",true),Category("new","New",0,"",false))
        val state=EntryUiState(amount="1",description="Coffee",accountId="cash",categoryId="old")
        assertFalse(state.canReview(accounts,categories))
        assertTrue(state.copy(categoryId=null).canReview(accounts,categories))
        assertTrue(state.copy(categoryId="new").canReview(accounts,categories))
    }
}
