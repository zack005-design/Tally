package com.smartexpense

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ExactRecordUiTest {
    @get:Rule val compose=createComposeRule()
    private val accounts=listOf(Account("wallet","Wallet","Cash",100000,false),Account("bank","Bank","Bank",500000,false))

    @Test fun transferSwapChangesBothAccountsAndSavesExactPaiseOnlyOnTap() {
        var current=EntryUiState(kind=Kind.TRANSFER,amount="123.45",description="Move money",accountId="wallet",destinationId="bank")
        var saved:EntryDraft?=null
        compose.setContent {
            var form by remember {mutableStateOf(current)}
            TrackerTheme {AdvancedEntryScreen(form,accounts,emptyList(),emptyList(),emptyList(),{form=it;current=it},{},{saved=form.draft()})}
        }
        compose.onNodeWithContentDescription("Swap accounts").performClick()
        compose.runOnIdle {assertEquals("bank",current.accountId);assertEquals("wallet",current.destinationId);assertNull(saved)}
        compose.onNodeWithText("Record transfer").assertIsEnabled().performClick()
        compose.runOnIdle {assertEquals(12345L,saved!!.amount);assertEquals("bank",saved!!.accountId);assertEquals("wallet",saved!!.destinationAccountId)}
    }

    @Test fun sameSourceAndDestinationBlocksTransfer() {
        var saved=0
        compose.setContent {TrackerTheme {AdvancedEntryScreen(EntryUiState(kind=Kind.TRANSFER,amount="1.01",description="Transfer",accountId="wallet",destinationId="wallet"),accounts,emptyList(),emptyList(),emptyList(),{},{},{saved++})}}
        compose.onNodeWithText("Record transfer").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,saved)}
    }

    @Test fun incomeProjectsCentsAndSavesTheSameExactDraft() {
        var saved:EntryDraft?=null
        val form=EntryUiState(kind=Kind.INCOME,amount="123.45",description="Income",accountId="wallet")
        compose.setContent {TrackerTheme {AdvancedEntryScreen(form,accounts,emptyList(),emptyList(),emptyList(),{},{},{saved=form.draft()})}}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("₹1,123.45"))
        compose.onNodeWithText("₹1,123.45").assertExists()
        compose.runOnIdle {assertNull(saved)}
        compose.onNodeWithText("Save income").assertIsEnabled().performClick()
        compose.runOnIdle {assertEquals(12345L,saved!!.amount);assertEquals(Kind.INCOME,saved!!.kind);assertEquals("wallet",saved!!.accountId)}
    }

    @Test fun reversedOriginalExpenseBlocksRefund() {
        assertRefundBlocked(Record("original",Kind.EXPENSE,"Lunch",10000,10000,null,null,0,"",true,personIds=listOf("self")))
    }

    @Test fun sharedOriginalExpenseBlocksRefund() {
        assertRefundBlocked(Record("original",Kind.EXPENSE,"Shared lunch",10000,5000,null,null,0,"",false,personIds=listOf("self","friend")))
    }

    private fun assertRefundBlocked(original:Record) {
        var saved=0
        val form=EntryUiState(kind=Kind.REFUND,amount="10",description="Refund",accountId="wallet",refundOf=original.id)
        compose.setContent {TrackerTheme {AdvancedEntryScreen(form,accounts,emptyList(),emptyList(),emptyList(),{},{},{saved++},records=listOf(original))}}
        compose.onNodeWithText("Save refund").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,saved)}
    }
}
