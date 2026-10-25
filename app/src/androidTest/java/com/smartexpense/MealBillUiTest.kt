package com.smartexpense

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp

class MealBillUiTest {
    @get:Rule val compose=createComposeRule()
    private val accounts=listOf(Account("wallet","Wallet","Cash",100000,false))
    private val people=listOf(Person("self","You"),Person("f","Rahul"))
    private val initial=EntryUiState(amount="330",description="Dinner",accountId="wallet",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "300","f" to "30"),method="Itemized",items="120=f\n150=f\n60=f",billItemNames=listOf("Paneer biryani","Veg noodles","Lime soda"))
    private fun scrollTo(text:String)=compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null),"meal-$name.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }

    @Test fun sayingWhatIAteAssignsTheRestAndShowsTheActualRepayment() {
        var saved:EntryDraft?=null
        var current=initial
        compose.setContent {var state by remember {mutableStateOf(initial)};TrackerTheme {EntryScreen(state,accounts,people,emptyList(),emptyList(),{state=it;current=it},{},{saved=state.draft()},hasBill=true)}}
        compose.onNodeWithText("What did you eat?").assertExists()
        compose.onNodeWithText("What did you eat?").performTextInput("I ate paneer biryani")
        compose.onNodeWithText("Apply my items").performScrollTo().performClick()
        capture("food-selection")
        assertEquals(listOf("self"),namedMealItems(current.billItemNames,current.items)[0].people)
        assertEquals(listOf("f"),namedMealItems(current.billItemNames,current.items)[1].people)
        scrollTo("Lime soda")
        compose.onNodeWithContentDescription("Share Lime soda equally").performScrollTo().performClick()
        compose.onNodeWithText("Continue to payments").performClick()
        compose.onNodeWithText("Review bill").assertIsEnabled().performClick()
        scrollTo("Rahul owes You")
        compose.onAllNodesWithText("₹150.00").onFirst().assertExists()
        capture("review")
        compose.onNodeWithText("Save transaction").performClick()
        assertEquals(mapOf("self" to 15000L,"f" to 18000L),Finance.split(saved!!.amount,saved!!.participants,saved!!.splitRule))
        assertEquals(mapOf("self" to 30000L,"f" to 3000L),saved!!.contributions)
    }

    @Test fun unknownFoodDoesNotPartiallyChangeAssignments() {
        var current=initial
        compose.setContent {var state by remember {mutableStateOf(initial)};TrackerTheme {EntryScreen(state,accounts,people,emptyList(),emptyList(),{state=it;current=it},{},{},hasBill=true)}}
        compose.onNodeWithText("What did you eat?").performTextInput("biryani and pizza")
        compose.onNodeWithText("Apply my items").performScrollTo().performClick()
        assertEquals(initial.items,current.items)
        compose.onNodeWithText("Could not find",substring=true).assertExists()
    }

    @Test fun friendItemsCanBeEnteredWithoutLosingMyOtherItems() {
        var current=initial
        compose.setContent {var state by remember {mutableStateOf(initial)};TrackerTheme {EntryScreen(state,accounts,people,emptyList(),emptyList(),{state=it;current=it},{},{},hasBill=true)}}
        compose.onNodeWithText("What did you eat?").performTextInput("biryani and lime soda")
        compose.onNodeWithText("Apply my items").performScrollTo().performClick()
        compose.onNodeWithText("Rahul's items",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("What did Rahul eat?").performScrollTo().performTextInput("lime soda")
        compose.onNodeWithText("Apply Rahul's items").performScrollTo().performClick()
        val rows=namedMealItems(current.billItemNames,current.items)
        assertEquals(listOf("self"),rows[0].people)
        assertEquals(listOf("f"),rows[1].people)
        assertEquals(listOf("f"),rows[2].people)
        assertEquals(initial.paid,current.paid)
        compose.onNodeWithText("Continue to payments").performClick()
        compose.onNodeWithText("Review bill").performClick()
        scrollTo("Rahul owes You")
        compose.onNodeWithText("₹180.00").assertExists()
    }

    @Test fun unmatchedBillTotalAndMissingPaymentsBlockReview() {
        compose.setContent {var state by remember {mutableStateOf(initial.copy(amount="360",paid=emptyMap()))};TrackerTheme {EntryScreen(state,accounts,people,emptyList(),emptyList(),{state=it},{},{},hasBill=true)}}
        compose.onNodeWithText("Continue to payments").assertIsNotEnabled()
        scrollTo("Shared taxes / charges (₹)")
        compose.onNodeWithText("Shared taxes / charges (₹)").performTextInput("30")
        compose.onNodeWithText("Continue to payments").assertIsEnabled().performClick()
        compose.onNodeWithText("Review bill").assertIsNotEnabled()
    }

    @Test fun reapplyingMyItemsKeepsExplicitlySharedDishes() {
        var current=initial.copy(items="120=f\n150=f\n60=self+f")
        compose.setContent {var state by remember {mutableStateOf(current)};TrackerTheme {EntryScreen(state,accounts,people,emptyList(),emptyList(),{state=it;current=it},{},{},hasBill=true)}}
        compose.onNodeWithText("What did you eat?").performTextInput("biryani")
        compose.onNodeWithText("Apply my items").performScrollTo().performClick()
        assertEquals(listOf("self","f"),namedMealItems(current.billItemNames,current.items)[2].people)
    }

    @Test fun invalidPaymentCanStillBeCorrectedAfterGoingBackToFood() {
        compose.setContent {var state by remember {mutableStateOf(initial)};TrackerTheme {EntryScreen(state,accounts,people,emptyList(),emptyList(),{state=it},{},{},hasBill=true)}}
        compose.onNodeWithText("Continue to payments").performClick()
        compose.onNodeWithText("You paid (₹)").performTextReplacement("300.")
        compose.onNodeWithContentDescription("Back").performScrollTo().performClick()
        compose.onNodeWithText("Continue to payments").assertIsEnabled().performClick()
        compose.onNodeWithText("Review bill").assertIsNotEnabled()
        compose.onNodeWithText("You paid (₹)").performTextReplacement("300")
        compose.onNodeWithText("Review bill").assertIsEnabled()
    }

    @Test fun zeroPaymentAndSharedFoodWorkAt320DpWithDoubleText() {
        val state=initial.copy(items="120=self\n150=f\n60=self+f",paid=mapOf("self" to "0","f" to "330"))
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                TrackerTheme {Box(Modifier.width(320.dp).fillMaxHeight()) {EntryScreen(state,accounts,people,emptyList(),emptyList(),{},{},{},hasBill=true)}}
            }
        }
        compose.onNodeWithText("Continue to payments").assertIsDisplayed().performClick()
        compose.onNodeWithText("Review bill").assertIsEnabled().performClick()
        scrollTo("You owe Rahul")
        compose.onNodeWithText("You owe Rahul").assertIsDisplayed()
        compose.onNodeWithText("Save transaction").assertIsDisplayed().assertIsEnabled()
        capture("review-large")
    }
}
