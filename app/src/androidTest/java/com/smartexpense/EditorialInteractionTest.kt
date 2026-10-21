package com.smartexpense

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class EditorialInteractionTest {
    @get:Rule val compose=createComposeRule()
    private val accounts=listOf(Account("a","Cash","Cash",100000,false),Account("b","Bank","Bank",100000,false))
    private val people=listOf(Person("self","You"),Person("r","Rahul"))
    @Test fun changingRefundExpenseAlsoChangesItsCategory() {
        var changed=EntryUiState()
        val original=Record("original",Kind.EXPENSE,"Grocery expense",10000,10000,"groceries",null,0,"",false,personIds=listOf("self"))
        compose.setContent {TrackerTheme {AdvancedEntryScreen(EntryUiState(kind=Kind.REFUND,amount="10",description="Refund",accountId="a",categoryId="food"),accounts,people,emptyList(),emptyList(),{changed=it},{},{},records=listOf(original))}}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Original expense"))
        compose.onNodeWithText("Original expense").performClick()
        compose.onNodeWithText("Grocery expense").performClick()
        assertEquals("original",changed.refundOf)
        assertEquals("groceries",changed.categoryId)
    }
    @Test fun recentSuggestionCreatesStructuredDraftWithoutDuplicatingNumbers() {
        var changed=EntryUiState()
        val record=Record("r",Kind.EXPENSE,"Spent 1450 on groceries",145000,145000,null,null,0,"",false)
        compose.setContent {TrackerTheme {AdvancedEntryScreen(EntryUiState(),accounts,people,emptyList(),emptyList(),{changed=it},{},{},records=listOf(record))}}
        compose.onNodeWithContentDescription("Use Spent 1450 on groceries template").performClick()
        assertEquals("1450.00",changed.amount)
        assertEquals(record.description,changed.description)
        assertEquals("",changed.accountId)
    }
    @Test fun splitCanReturnToPersonalEntryWithoutLosingDraft() {
        var changed=EntryUiState()
        val form=EntryUiState(amount="100",description="Meal",accountId="a",shared=true,participants=listOf("self","r"),paid=mapOf("self" to "100","r" to "0"))
        compose.setContent {TrackerTheme {AdvancedEntryScreen(form,accounts,people,emptyList(),emptyList(),{changed=it},{},{})}}
        compose.onNodeWithContentDescription("Entry options").performClick()
        compose.onNodeWithText("Make personal expense").performClick()
        assertEquals(false,changed.shared)
        assertEquals("100",changed.amount)
        assertEquals("Meal",changed.description)
    }
    @Test fun sameAccountTransferCannotBeSaved() {
        compose.setContent {TrackerTheme {AdvancedEntryScreen(EntryUiState(kind=Kind.TRANSFER,amount="100",description="Transfer",accountId="a",destinationId="a"),accounts,people,emptyList(),emptyList(),{},{},{})}}
        compose.onNodeWithText("Record transfer").assertIsDisplayed().assertIsNotEnabled()
    }
    @Test fun dateRowStillOffersTimeEditing() {
        compose.setContent {TrackerTheme {AdvancedEntryScreen(EntryUiState(amount="100",description="Lunch",accountId="a"),accounts,people,emptyList(),emptyList(),{},{},{})}}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Date"))
        compose.onNodeWithText("Date").performClick()
        compose.onNodeWithText("Change time").assertIsDisplayed()
    }
    @Test fun navigationHonorsTwoTimesSystemText() {
        compose.setContent {val d=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(d.density,2f)) {TrackerTheme {LedgerNavigation("home",{})}}}
        compose.onNodeWithText("Friends",useUnmergedTree=true).assertHeightIsAtLeast(24.dp)
    }
    @Test fun descriptionContinueStaysVisibleWithKeyboard() {
        compose.setContent {var form by remember {mutableStateOf(EntryUiState())};TrackerTheme {AdvancedEntryScreen(form,accounts,people,emptyList(),emptyList(),{form=it},{},{})}}
        compose.onNode(hasSetTextAction()).performClick().performTextInput("Spent 250 on lunch")
        compose.onNodeWithText("Continue").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun changingContributionsUpdatesReviewAndBlocksUnbalancedSave() {
        compose.setContent {
            var form by remember {mutableStateOf(EntryUiState(amount="100",description="Lunch",accountId="a",shared=true,participants=listOf("self","r"),paid=mapOf("self" to "100","r" to "0")))}
            TrackerTheme {AdvancedEntryScreen(form,accounts,people,emptyList(),emptyList(),{form=it},{},{})}
        }
        compose.onNodeWithText("₹100").performClick()
        compose.onNodeWithText("You paid (₹)").performTextReplacement("60")
        compose.onNodeWithText("You paid (₹)").performImeAction()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save transaction"))
        compose.onNodeWithText("Save transaction").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("₹0"))
        compose.onNodeWithText("₹0").performClick()
        compose.onNodeWithText("Rahul paid (₹)").performTextReplacement("40")
        compose.onNodeWithText("Rahul paid (₹)").performImeAction()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("You will receive"))
        compose.onNodeWithText("₹10.00").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save transaction"))
        compose.onNodeWithText("Save transaction").assertIsEnabled()
    }
}
