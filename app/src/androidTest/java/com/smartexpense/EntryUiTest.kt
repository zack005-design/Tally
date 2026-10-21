package com.smartexpense
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import com.smartexpense.ui.*
import com.smartexpense.domain.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.*
class EntryUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun entryShowsCalculatedShareAndSave() {
        compose.setContent { TrackerTheme { AdvancedEntryScreen(EntryUiState(amount="100",description="Lunch",accountId="a"),listOf(Account("a","Cash","Cash",100000,false)),emptyList(),emptyList(),emptyList(),{}, {}, {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Personal spending: ₹100.00"))
        compose.onNodeWithText("Personal spending: ₹100.00").assertExists()
        compose.onNodeWithText("Save transaction").assertHasClickAction()
    }
    @Test fun multiPayerFormShowsExactShareAndSavesTheReviewedDraft() {
        var saved:EntryDraft?=null
        compose.setContent {
            var form by remember {mutableStateOf(EntryUiState(amount="890",description="Meal",accountId="bank",shared=true,participants=listOf("self","r","k","a"),paid=mapOf("self" to "500","r" to "290","k" to "100","a" to "0")))}
            TrackerTheme {AdvancedEntryScreen(form,listOf(Account("bank","Bank","Bank",200000,false)),listOf(Person("self","You"),Person("r","Rahul"),Person("k","Kevin"),Person("a","Arjun")),emptyList(),emptyList(),{form=it},{},{saved=form.draft()})}
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Personal spending: ₹222.50"))
        compose.onNodeWithText("Personal spending: ₹222.50").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save transaction"))
        compose.onNodeWithText("Save transaction").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Save transaction").performClick()
        org.junit.Assert.assertEquals(89000L,saved!!.amount)
        org.junit.Assert.assertEquals(50000L,saved!!.contributions.getValue("self"))
    }
}
