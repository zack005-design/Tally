package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.setContent {TrackerTheme {HomeScreen(Snapshot(),{k,_->kind=k},{},{},{},{destination=it})}}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Income"))
        compose.onNodeWithText("Income").performClick()
        compose.runOnIdle {assertEquals(Kind.INCOME,kind)}
        compose.onNodeWithText("Scan bill").performClick()
        compose.runOnIdle {assertEquals("bill-import",destination)}
        compose.onNodeWithText("Plan").performClick()
        compose.runOnIdle {assertEquals("planner",destination)}
    }
}
