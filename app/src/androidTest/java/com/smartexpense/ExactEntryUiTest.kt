package com.smartexpense

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExactEntryUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun equalMethodStaysOptionalAndReviewDoesNotSave() {
        var saved=0
        compose.setContent {
            var form by remember {mutableStateOf(EntryUiState(amount="900",description="Dinner",accountId="a",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "900","f" to "0")))}
            TrackerTheme {EntryScreen(form,listOf(Account("a","Cash","Cash",100000,false)),listOf(Person("self","You"),Person("f","Rahul")),emptyList(),emptyList(),{form=it},{},{saved++})}
        }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Equal split").assertExists()
        compose.onNodeWithText("Continue").performClick()
        
        compose.onNodeWithText("Save shared bill").assertIsEnabled()
        compose.onNodeWithText("Add and assign items").assertDoesNotExist()
        assertEquals(0,saved)
        compose.onNodeWithText("Save shared bill").performClick()
        assertEquals(1,saved)
    }

    @Test fun transactionShowsMetadataAndKeepsActualPaymentSeparate() {
        val record=Record("bill",Kind.EXPENSE,"Dinner",90000,45000,null,null,0,"Private note",false,listOf("a"),listOf("self","f"))
        val detail=RecordDetail(record,listOf(Movement("a",-90000)),mapOf("self" to 45000L,"f" to 45000L),mapOf("self" to 90000L,"f" to 0L),emptyList(),emptyMap())
        compose.setContent {TrackerTheme {TransactionDetailView(record,detail,Snapshot(accounts=listOf(Account("a","Cash","Cash",100000,false)),people=listOf(Person("self","You"),Person("f","Rahul"))),onActions={})}}
        compose.onNodeWithText("Transaction").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Payments and shares"))
        compose.onNodeWithText("Payments and shares").assertExists()
    }

    @Test fun friendsRootSearchIncludesSettledPeopleAndOpensTheirDetail() {
        compose.setContent {TrackerTheme {SplitScreen(
            Snapshot(people=listOf(Person("self","You"),Person("f","Rahul"),Person("z","Kiran",listOf("K")))),
            {},{},{},{_,_->},{},{_,_,_,_->}
        )}}
        compose.onNodeWithText("Search people").performTextInput("Kiran")
        compose.onNodeWithText("Rahul").assertDoesNotExist()
        compose.onNode(hasText("Kiran") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onNodeWithText("Record repayment").assertExists()
        compose.onNodeWithText("Edit friend").assertExists()
    }
}
