package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FirstBalanceSetupUiTest {
    @get:Rule val compose=createComposeRule()
    private fun reveal(matcher:SemanticsMatcher):SemanticsNodeInteraction {
        if(compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(matcher)
        val node=compose.onNode(matcher)
        node.performScrollTo()
        return node
    }

    @Test fun setupRequiresBothBalancesAndAcceptsZero() {
        var saved:Pair<Long,Long>?=null
        compose.setContent {EditorialPreviewTheme {BalanceSetupScreen(Snapshot(),false) {w,g,_,_->saved=w to g}}}
        reveal(hasText("Continue to Tally")).assertIsNotEnabled()
        reveal(hasContentDescription("Wallet balance (₹)")).performTextInput("250.50")
        reveal(hasContentDescription("GPay balance (₹)")).apply {performTextInput("0");performImeAction()}
        reveal(hasText("Continue to Tally")).performClick()
        assertEquals(25050L to 0L,saved)
    }

    @Test fun setupRejectsNegativeAndBusySubmission() {
        compose.setContent {EditorialPreviewTheme {BalanceSetupScreen(Snapshot(),false) {_,_,_,_->fail("Invalid balance saved")}}}
        reveal(hasContentDescription("Wallet balance (₹)")).performTextInput("-1")
        reveal(hasContentDescription("GPay balance (₹)")).apply {performTextInput("100");performImeAction()}
        reveal(hasText("Continue to Tally")).assertIsNotEnabled()
    }

    @Test fun addMoneyCanTargetGpay() {
        val s=Snapshot(accounts=listOf(Account("w","Wallet","Cash",0,false),Account("g","GPay","Bank",10000,false)),walletSources=WalletSources("w","g","2026-10"))
        var saved:Triple<String,Long,Boolean>?=null
        compose.setContent {EditorialPreviewTheme {WalletScreen(s,false,{_,_,_,_->},{id,amount,topup,done->saved=Triple(id,amount,topup);done()},{_,_,_->},{},{},initialAddMoney=true)}}
        compose.onNodeWithText("Destination").performClick()
        compose.onAllNodesWithText("GPay").onLast().performClick()
        reveal(hasContentDescription("Amount to add (₹)")).apply {performTextInput("50");performImeAction()}
        compose.onAllNodesWithText("Add money").onLast().performScrollTo().performClick()
        assertEquals(Triple("g",5000L,true),saved)
    }

    @Test fun todayShowsTodaySpendingAndAddMoney() {
        compose.setContent {EditorialPreviewTheme {HomeScreen(Snapshot(accounts=listOf(Account("w","Wallet","Cash",5000,false)),dashboard=DashboardMetrics(today=1200)),{_,_->},{},{},{},{})}}
        compose.onNodeWithText("Overview").assertIsDisplayed()
        reveal(hasText("Spent today")).assertIsDisplayed()
        compose.onNodeWithText("₹12").assertIsDisplayed()
        reveal(hasText("Add money")).assertIsDisplayed()
    }
}
