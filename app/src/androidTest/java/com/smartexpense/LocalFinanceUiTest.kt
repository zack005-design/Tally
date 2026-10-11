package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.ui.*
import com.smartexpense.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalFinanceUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun diaryNoteSaveDoesNotCreateAnExpense() {
        var note="";var added=false
        compose.setContent {TrackerTheme {DiaryScreen(Snapshot(),false,{_,text,done->note=text;done()},{_,_->added=true},{},"2026-10-11")}}
        compose.onNodeWithText("Daily note").performTextInput("Lunch and shopping")
        compose.onNodeWithText("Save daily note").performScrollTo().performClick()
        compose.runOnIdle {assertEquals("Lunch and shopping",note);assertFalse(added)}
    }
    @Test fun welcomeCanBeSkippedWithoutOnlineRegistration() {
        var finished:Boolean?=null
        compose.setContent {TrackerTheme {WelcomeScreen(false) {finished=it}}}
        compose.onNodeWithText("Skip welcome").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(false,finished)}
    }
    @Test fun manualGpayAndResetOffRemainIndependent() {
        var balance:Long?=null;var scheduled:Boolean?=null
        val s=Snapshot(accounts=listOf(Account("w","Wallet","Cash",20000,false),Account("g","GPay","Bank",30000,false)),walletSources=WalletSources("w","g","2026-10"))
        compose.setContent {TrackerTheme {GpaySettingsScreen(s,false,{value,done->balance=value;done()},{enabled,_,_,_,done->scheduled=enabled;done()},{})}}
        compose.onNodeWithText("Actual GPay balance (₹)").performTextReplacement("750")
        compose.onNodeWithText("Save GPay balance").performScrollTo().performClick()
        compose.onNodeWithText("Save reset settings").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(75000L,balance);assertEquals(false,scheduled)}
    }
}
