package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EverydayMoneyUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun todayActionsOpenExistingTools() {
        var destination=""
        var kind:Kind?=null
        compose.setContent {TrackerTheme {HomeScreen(Snapshot(accounts=listOf(com.smartexpense.domain.Account("w","Wallet","Cash",5000,false))),{k,_->kind=k},{},{},{},{destination=it})}}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Record income"))
        compose.onNodeWithText("Record income").performClick()
        compose.runOnIdle {assertEquals(Kind.INCOME,kind)}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Money planner"))
        compose.onNodeWithText("Money planner").performClick()
        compose.runOnIdle {assertEquals("planner",destination)}
    }
}
