package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExactBudgetsUiTest {
    @get:Rule val compose=createComposeRule()
    private val snapshot=Snapshot(
        reportMonth="2026-10",selectedReport=PeriodReport(842000,0,mapOf("food" to 412000L),emptyMap()),
        categories=listOf(Category("food","Food",0,"food",false)),
        budgets=listOf(Budget("overall","2026-10",null,1200000),Budget("food-budget","2026-10","food",500000))
    )

    @Test fun realMonthlyLimitsAndCategoryEditorKeepExactSaveCallback() {
        var saved:Triple<String,String?,Long>?=null
        var complete:(()->Unit)?=null
        compose.setContent {TrackerTheme {BudgetsScreen(snapshot,"2026-10",{},{},{month,category,amount,done->saved=Triple(month,category,amount);complete=done})}}
        compose.onNodeWithText("₹8,420").assertExists()
        compose.onNodeWithText("spent of ₹12,000").assertExists()
        compose.onNodeWithText("82%").assertExists()
        compose.onNodeWithText("Food").performClick()
        compose.onNodeWithText("Budget amount (₹)").apply {performTextReplacement("5500.25");performImeAction()}
        compose.onNodeWithText("Save budget").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(Triple("2026-10","food",550025L),saved)}
        compose.onNodeWithText("Budget amount (₹)").assertExists()
        compose.runOnIdle {complete!!.invoke()}
        compose.onNodeWithText("Budget amount (₹)").assertDoesNotExist()
    }

    @Test fun categoryLimitsDoNotInventAnOverallLimit() {
        compose.setContent {TrackerTheme {BudgetsScreen(snapshot.copy(budgets=snapshot.budgets.filter {it.categoryId!=null}),"2026-10",{},{},{_,_,_,_->})}}
        compose.onNodeWithText("₹8,420").assertExists()
        compose.onNodeWithText("spent this month").assertExists()
        compose.onNodeWithText("No overall limit set. Category budgets are shown separately.").assertExists()
    }

    @Test fun changingMonthHidesTheOldReportUntilMatchingDataArrives() {
        compose.setContent {TrackerTheme {BudgetsScreen(snapshot,"2026-11",{},{},{_,_,_,_->})}}
        compose.onNodeWithText("Loading this month's report…").assertExists()
        compose.onNodeWithText("₹8,420").assertDoesNotExist()
        compose.onNodeWithText("Food").assertDoesNotExist()
    }
}
