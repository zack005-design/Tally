package com.smartexpense

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuidedEntryTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=java.io.File(context.getExternalFilesDir(null),"guided-$name.png")
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun actualPaymentsReachReviewWithoutAssigningEqualPayments() {
        var saved:EntryDraft?=null
        compose.setContent {
            var form by remember {mutableStateOf(EntryUiState(amount="330",description="Dinner",accountId="a",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "300","f" to "30")))}
            TrackerTheme {EntryScreen(form,listOf(Account("a","Cash","Cash",100000,false)),listOf(Person("self","You"),Person("f","Friend")),emptyList(),emptyList(),{form=it},{},{saved=form.draft()})}
        }
        compose.onNodeWithText("1 of 5").assertExists()
        capture("bill")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("2 of 5").assertExists()
        capture("payments")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Friend owes You"))
        compose.onNodeWithText("Friend owes You").assertExists()
        compose.onNodeWithText("I paid all").assertDoesNotExist()
        capture("review")
        compose.onNodeWithText("Save shared bill").performClick()
        assertEquals(30000L,saved!!.contributions["self"])
        assertEquals(3000L,saved!!.contributions["f"])
    }
    @Test fun zeroPaymentCanBeReviewedAndBackKeepsTheDraft() {
        var current=EntryUiState(amount="330",description="Dinner",accountId="a",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "0","f" to "330"))
        compose.setContent {var form by remember {mutableStateOf(current)};TrackerTheme {EntryScreen(form,listOf(Account("a","Cash","Cash",100000,false)),listOf(Person("self","You"),Person("f","Friend")),emptyList(),emptyList(),{form=it;current=it},{},{})}}
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("You owe Friend"))
        compose.onNodeWithText("You owe Friend").assertExists()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.onNodeWithText("3 of 5").assertExists()
        assertEquals("0",current.paid["self"])
        assertEquals("330",current.paid["f"])
    }
    @Test fun missingPaymentBlocksReviewInsteadOfInventingEqualPayments() {
        compose.setContent {TrackerTheme {EntryScreen(EntryUiState(amount="330",description="Dinner",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "300")),emptyList(),listOf(Person("f","Friend")),emptyList(),emptyList(),{},{},{})}}
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }
    @Test fun itemizedDraftKeepsBillAndPaymentsWhenGoingBack() {
        var current=EntryUiState(amount="330",description="Dinner",accountId="a",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "300","f" to "30"))
        compose.setContent {var form by remember {mutableStateOf(current)};TrackerTheme {EntryScreen(form,listOf(Account("a","Cash","Cash",100000,false)),listOf(Person("f","Friend")),emptyList(),emptyList(),{form=it;current=it},{},{})}}
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("By item").performScrollTo().performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("4 of 5").assertExists()
        compose.onNodeWithText("Review").assertIsNotEnabled()
        repeat(3) {compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()}
        compose.onNodeWithText("1 of 5").assertExists()
        assertEquals("300",current.paid["self"])
        compose.onNodeWithContentDescription("Total amount").performScrollTo().performTextClearance()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }
    @Test fun moreSeparatesSettingsFromMoneyTools() {
        var destination=""
        compose.setContent {TrackerTheme {MoreScreen(onNavigate={destination=it})}}
        compose.onNodeWithText("Money & planning").performClick()
        assertEquals("tools/money",destination)
        compose.onNodeWithText("App lock").assertDoesNotExist()
        compose.onNodeWithText("Settings & backup").performClick()
        assertEquals("tools/settings",destination)
    }
    @Test fun savedReceiptShowsPaymentsSharesAndDebt() {
        val record=Record("bill",Kind.EXPENSE,"Dinner",33000,16500,null,null,0,"",false,listOf("a"),listOf("self","f"))
        val detail=RecordDetail(record,listOf(Movement("a",-30000)),mapOf("self" to 16500L,"f" to 16500L),mapOf("self" to 30000L,"f" to 3000L),emptyList(),emptyMap())
        val snapshot=Snapshot(people=listOf(Person("self","You"),Person("f","Friend")),debts=listOf(Debt("d","bill","f","self",13500,13500,0)))
        compose.setContent {TrackerTheme {TransactionDetailView(record,detail,snapshot,onActions={})}}
        compose.onNodeWithText("Transaction").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Friend owes You"))
        compose.onNodeWithText("Friend owes You").assertExists()
        capture("receipt")
    }
    @Test fun narrowScreenWithLargeTextKeepsReviewAndSaveReachable() {
        compose.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            val width=androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(width/320f,2f)) {
                TrackerTheme {EntryScreen(EntryUiState(amount="330",description="Dinner",accountId="a",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "300","f" to "30")),listOf(Account("a","Cash","Cash",100000,false)),listOf(Person("f","Friend")),emptyList(),emptyList(),{},{},{})}
            }
        }
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        compose.onNodeWithText("Save shared bill").assertIsDisplayed().assertIsEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Friend owes You"))
        compose.onNodeWithText("Friend owes You").assertIsDisplayed()
        capture("review-large-text")
    }
}
