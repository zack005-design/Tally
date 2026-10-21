package com.smartexpense

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test

class PhotoActionTest {
    @get:Rule val compose=createComposeRule()
    @Test fun personalTransactionExposesReferenceActions() {
        val record=Record("lunch",Kind.EXPENSE,"Lunch",18000,18000,null,"food",0,"Team lunch",false,accountIds=listOf("w"))
        var edits=0;var duplicates=0;var refunds=0
        val snapshot=Snapshot(accounts=listOf(Account("w","Wallet","Cash",120000,false)))
        compose.setContent {TrackerTheme {CompositionLocalProvider(LocalEditorialBack provides {}) {TransactionDetailView(record,null,snapshot,onEdit={edits++},onDuplicate={duplicates++},onRefund={refunds++},onActions={})}}}
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Duplicate"))
        compose.onNodeWithText("Edit",substring=false).assertExists()
        compose.onNodeWithText("Duplicate",substring=false).assertExists()
        compose.onNodeWithText("Record refund",substring=false).performClick()
        compose.onNodeWithText("Duplicate",substring=false).performClick()
        compose.onNodeWithText("Edit",substring=false).performClick()
        org.junit.Assert.assertEquals(listOf(1,1,1),listOf(edits,duplicates,refunds))
    }
}
