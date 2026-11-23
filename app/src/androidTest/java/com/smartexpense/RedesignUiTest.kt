package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class RedesignUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overviewKeepsQuickEntryReachableAndManagementAccessible() {
        var chosen:Kind?=null
        var destination=""
        compose.setContent {TrackerTheme {HomeScreen(Snapshot(),{kind,_->chosen=kind},{},{},{},{destination=it})}}
        compose.onAllNodesWithText("₹0.00").assertCountEquals(0)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Add an expense"))
        compose.onNodeWithText("Add an expense").assertIsDisplayed().performClick()
        assertEquals(Kind.EXPENSE,chosen)
        compose.onNodeWithContentDescription("Settings").performClick()
        assertEquals("more",destination)
    }

    @Test fun activityTypeFilterKeepsExpenseFilteringAvailable() {
        val records=listOf(
            Record("l",Kind.EXPENSE,"Lunch",25000,25000,null,null,0,"",false),
            Record("i",Kind.INCOME,"Salary",200000,0,null,null,0,"",false))
        compose.setContent {TrackerTheme {ActivityScreen(Snapshot(records=records),{})}}
        compose.onNodeWithText("Filter").performClick()
        compose.onNodeWithText("Type").performScrollTo().performClick()
        compose.onNodeWithText("Expense").performScrollTo().performClick()
        compose.onNodeWithText("Apply filters").performScrollTo().performClick()
        compose.onNodeWithText("Lunch").assertExists()
        compose.onNodeWithText("Salary").assertDoesNotExist()
    }

    @Test fun describeEntryProducesReviewableDraftWithoutSaving() {
        var parsed=false
        var saved=false
        compose.setContent {TrackerTheme {AdvancedEntryScreen(EntryUiState(),emptyList(),emptyList(),emptyList(),emptyList(),{},{parsed=true},{saved=true})}}
        // Describe is the default entry mode; Continue parses the text into a reviewable draft.
        compose.onNodeWithText("Describe entry").assertExists()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(true,parsed)
        assertEquals(false,saved)
    }
}



