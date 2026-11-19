package com.smartexpense
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class) class YearSummaryUiTest {
    @get:Rule val compose=createComposeRule()
    private val thisYear=LocalDate.now().year

    private fun summary(year:Int,spending:Long,previous:Long,records:Int=3)=YearSummary(
        year=year,through=LocalDate.of(year,12,31),spending=spending,income=500000,
        months=List(12) {if(it==2) spending else 0L},categories=if(spending>0) mapOf<String?,Long>("food" to spending) else emptyMap(),
        previousSpending=previous,recordCount=records,earliestYear=thisYear-1)

    @Test fun showsTotalsComparisonAndOnlyOffersYearsWithRecords() {
        val requested=mutableListOf<Int>()
        compose.setContent {TrackerTheme(darkTheme=true) {YearSummaryScreen(Snapshot(categories=listOf(Category("food","Food",0xFFFFAB73,"•",false)))) {year->
            requested+=year
            if(year==thisYear) summary(year,150000,100000) else summary(year,100000,0)
        }}}
        compose.onAllNodesWithText("₹1,500").onFirst().assertExists()
        compose.onNodeWithText("50% more",substring=true).assertExists()
        compose.onNodeWithText("Food").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Next year").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous year").performClick()
        compose.onNodeWithText("No spending recorded",substring=true).assertExists()
        compose.onNodeWithContentDescription("Previous year").assertIsNotEnabled()
        assertEquals(listOf(thisYear,thisYear-1),requested)
    }

    @Test fun emptyYearExplainsThereIsNothingToSummarise() {
        compose.setContent {TrackerTheme {YearSummaryScreen(Snapshot()) {year->summary(year,0,0,records=0)}}}
        compose.onNodeWithText("Nothing recorded in $thisYear").assertExists()
    }
}
