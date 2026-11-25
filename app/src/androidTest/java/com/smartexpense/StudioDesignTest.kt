package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry

class StudioDesignTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null),"studio-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    private fun sample()=Snapshot(
        accounts=listOf(Account("cash","Wallet","Cash",120000,false),Account("gpay","GPay","Bank",500000,false)),
        people=listOf(Person("self","You"),Person("f","Rahul")),
        records=listOf(Record("dinner",Kind.EXPENSE,"Dinner",33000,16500,null,null,0,"",false,listOf("cash"),listOf("self","f"))),
        debts=listOf(Debt("d","dinner","f","self",13500,13500,0)),
        dashboard=DashboardMetrics(month=PeriodReport(185000,0,emptyMap(),emptyMap())))

    @Test fun homeAndBalancePrivacyPreview() {
        compose.setContent {TrackerTheme {Scaffold(bottomBar={LedgerNavigation("home",{})}) {padding->Box(Modifier.padding(padding)) {HomeScreen(sample(),{_,_->},{},{},{},{})}}}}
        compose.onNodeWithText("₹6,200").assertExists()
        compose.onNodeWithText("₹1,200").assertExists()
        capture("today")
        compose.onNodeWithContentDescription("Hide balance").performClick()
        compose.onNodeWithText("₹6,200").assertDoesNotExist()
        compose.onNodeWithText("₹1,200").assertDoesNotExist()
        compose.onNodeWithText("₹5,000").assertDoesNotExist()
    }

    @Test fun homeAt320DpAndDoubleTextKeepsPrimaryActionReachable() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                TrackerTheme {Box(Modifier.width(320.dp).fillMaxHeight()) {Scaffold(bottomBar={LedgerNavigation("home",{})}) {padding->Box(Modifier.padding(padding)) {HomeScreen(sample(),{_,_->},{},{},{},{})}}}}
            }
        }
        compose.onNodeWithContentDescription("Search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onAllNodesWithText("Summary").onFirst().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Recent"))
        compose.onNodeWithText("Recent").assertIsDisplayed()
        capture("today-large")
    }

    @Test fun friendsPreservesBothDirectionsRatherThanSilentlyNettingThem() {
        val snapshot=sample().let {it.copy(debts=it.debts+Debt("other","otherbill","self","f",5000,5000,0))}
        compose.setContent {TrackerTheme {SplitScreen(snapshot,{},{},{},{_,_->},{},{_,_,_,_->})}}
        // The per-friend row shows the net, but both directions stay visible in the summary.
        compose.onNodeWithText("You're owed ₹135",substring=true).assertExists()
        compose.onNodeWithText("You owe ₹50",substring=true).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Rahul"))
        compose.onNodeWithText("+₹85").assertExists()
        capture("friends")
    }

    @Test fun thirdPartyDebtsDoNotCreateEmptyCardsInYourFriendBalances() {
        val snapshot=Snapshot(people=listOf(Person("self","You"),Person("alice","Alice"),Person("bob","Bob")),debts=listOf(Debt("third","bill","bob","alice",10000,10000,0)))
        compose.setContent {TrackerTheme {SplitScreen(snapshot,{},{},{},{_,_->},{},{_,_,_,_->})}}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Settlement history"))
        // Friends are listed, but a debt between two other people never shows as a balance with you.
        compose.onAllNodesWithText("Settled up").assertCountEquals(2)
        compose.onNodeWithText("Owes you").assertDoesNotExist()
        compose.onNodeWithText("You owe").assertDoesNotExist()
        compose.onAllNodesWithText("Settlement history").onFirst().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Original bill obligations"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bob owes Alice"))
        compose.onNodeWithText("Bob owes Alice").assertExists()
    }

    @Test fun mainNavigationHasThreeClearDestinations() {
        var route=""
        compose.setContent {TrackerTheme {LedgerNavigation("home") {route=it}}}
        compose.onNodeWithText("Summary").assertIsSelected()
        compose.onNodeWithText("Split").performClick()
        assertEquals("split",route)
        compose.onNodeWithText("Spending").performClick()
        assertEquals("insights",route)
        compose.onNodeWithContentDescription("Search").assertExists()
        compose.onNodeWithText("Insights").assertDoesNotExist()
    }

    @Test fun homeKeepsOnePrimaryActionAndSettingsAccessible() {
        var kind:Kind?=null
        var route=""
        compose.setContent {TrackerTheme {HomeScreen(Snapshot(),{k,_->kind=k},{},{},{},{route=it})}}
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Add expense"));compose.onNodeWithText("Add expense").assertIsDisplayed().performClick()
        assertEquals(Kind.EXPENSE,kind)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Settings"));compose.onNodeWithContentDescription("Settings").performClick()
        assertEquals("more",route)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Add your money"))
        compose.onNodeWithText("Daily spending diary ›").assertDoesNotExist()
    }

    @Test fun friendsKeepsAdvancedSettlementToolsCollapsed() {
        compose.setContent {TrackerTheme {SplitScreen(Snapshot(),{},{},{},{_,_->},{},{_,_,_,_->})}}
        compose.onNodeWithText("Friends").assertExists()
        compose.onNodeWithText("Original bill obligations").assertDoesNotExist()
        compose.onAllNodesWithText("Settlement history").onFirst().performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Original bill obligations"))
        compose.onNodeWithText("Original bill obligations").assertExists()
    }
}
