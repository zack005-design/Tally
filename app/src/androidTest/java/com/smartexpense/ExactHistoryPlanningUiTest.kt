package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Financial callbacks stay authoritative behind the compact reference layouts. */
class ExactHistoryPlanningUiTest {
    @get:Rule val compose = createComposeRule()
    private val account = Account("wallet", "Wallet", "Cash", 100000, false)

    @Test fun advancedAmountFilterAppliesAndCancelRestoresThePriorFilter() {
        val records = listOf(
            Record("lunch", Kind.EXPENSE, "Lunch", 18000, 18000, null, null, 0L, "", false),
            Record("groceries", Kind.EXPENSE, "Groceries", 30000, 30000, null, null, 0L, "", false)
        )
        compose.setContent { TrackerTheme { ActivityScreen(Snapshot(records = records), {}) } }
        compose.onNodeWithText("Filter").performClick()
        compose.onNodeWithText("Advanced filters").performScrollTo().performClick()
        compose.onNodeWithText("Person").performScrollTo().assertExists()
        compose.onNodeWithText("Group").performScrollTo().assertExists()
        compose.onNodeWithText("Minimum (₹)").performScrollTo().performTextInput("200")
        compose.onNodeWithText("Minimum (₹)").performImeAction()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Lunch").performScrollTo().assertExists()
        compose.onNodeWithText("Filter").performScrollTo().performClick()
        compose.onNodeWithText("Minimum (₹)").performScrollTo().performTextInput("200")
        compose.onNodeWithText("Minimum (₹)").performImeAction()
        compose.onNodeWithText("Apply filters").performScrollTo().performClick()
        compose.onNodeWithText("Lunch").assertDoesNotExist()
        compose.onNodeWithText("Groceries").assertExists()
    }

    @Test fun allocationSubmitsExactPaiseAndKeepsTheSheetUntilSaveCompletes() {
        var saved: Pair<String, Long>? = null
        var complete: (() -> Unit)? = null
        val snapshot = Snapshot(accounts = listOf(account), goals = listOf(SavingsGoal("laptop", "New laptop", 5000000)))
        compose.setContent { TrackerTheme { GoalsScreen(snapshot, false, { _, _, _, _ -> fail("Goal editor must not save") }, { id, amount, done -> saved = id to amount; complete = done }, {}) } }
        compose.onNodeWithText("New laptop").performClick()
        compose.onNodeWithText("Planned amount (₹)").performTextInput("1000.25")
        compose.onNodeWithText("Planned amount (₹)").performImeAction()
        compose.onNodeWithText("Save planned allocation").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("laptop" to 100025L, saved); assertEquals(100000L, account.balance) }
        compose.onNodeWithText("Planned amount (₹)").assertExists()
        compose.runOnIdle { complete!!.invoke() }
        compose.onNodeWithText("Planned amount (₹)").assertDoesNotExist()
    }

    @Test fun recurringOccurrenceOpensADraftWithoutSavingATemplate() {
        val date = LocalDate.now()
        val template = RecurringTemplate("rent", Kind.EXPENSE, 800000, "Rent", account.id, date.toString(), "Monthly")
        var requested: Pair<String, LocalDate>? = null
        compose.setContent { TrackerTheme { RecurringScreen(Snapshot(accounts = listOf(account), templates = listOf(template)), false, { _, _ -> fail("Opening an occurrence must not save a template") }, { fail("Opening an occurrence must not delete") }, { value, occurrence -> requested = value.id to occurrence }) } }
        compose.onNodeWithText("Rent").performClick()
        compose.onNodeWithText("Review & record · $date").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("rent" to date, requested); assertEquals(100000L, account.balance) }
    }

    @Test fun favouritesKeepSixLimitWhileUseOnlyRequestsADraft() {
        val values = (1..6).map { FavouriteExpense("f$it", "Favourite $it", it * 10000L, account.id, null) }
        var requested: String? = null
        compose.setContent { TrackerTheme { FavouritesScreen(Snapshot(accounts = listOf(account), favourites = values), false, { requested = it.id }, { _, _ -> fail("Use must not save a template") }, { fail("Use must not delete") }) } }
        compose.onAllNodesWithText("Use").onFirst().performClick()
        compose.runOnIdle { assertEquals("f1", requested) }
        compose.onNodeWithText("Add favourite · 6/6").performScrollTo().assertIsNotEnabled()
    }

    @Test fun weekAndYearUseFullReportsWhileBudgetPreviewRemainsMonthly() {
        val requested = mutableListOf<Pair<LocalDate, LocalDate>>()
        val snapshot = Snapshot(reportMonth = "2024-02", selectedReport = PeriodReport(5000, 0, emptyMap(), emptyMap()))
        compose.setContent { TrackerTheme {
            InsightsScreen(snapshot, "2024-02", {}, { _, _, _ -> }, onPeriodReport = { from, through ->
                requested += from to through
                PeriodReport(if(java.time.temporal.ChronoUnit.DAYS.between(from, through) == 6L) 11100 else 22200, 0, emptyMap(), emptyMap())
            })
        } }
        compose.onNodeWithText("Week").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(LocalDate.of(2024, 2, 26) to LocalDate.of(2024, 3, 3), requested.last()) }
        compose.onNodeWithText("₹111").assertExists()
        compose.onNodeWithText("Year").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(LocalDate.of(2024, 1, 1) to LocalDate.of(2024, 12, 31), requested.last()) }
        compose.onNodeWithText("₹222").assertExists()
        compose.onNodeWithText("Set budget").performScrollTo().performClick()
        compose.onNodeWithText("Budget amount (₹)").performTextInput("100")
        compose.onNodeWithText("Budget amount (₹)").performImeAction()
        compose.onNodeWithText("Already spent").performScrollTo().assertExists()
        compose.onAllNodesWithText("₹50.00").assertCountEquals(2)
        compose.onAllNodesWithText("Cancel").assertCountEquals(1)
    }

    @Test fun dailyBreakdownRetainsRefundAndZeroDays() {
        val date = LocalDate.of(2024, 2, 26)
        compose.setContent { TrackerTheme { TrendChart(mapOf(date to -10000L), startDate = date, endDate = date.plusDays(6)) } }
        compose.onNodeWithText("Daily breakdown").performClick()
        compose.onNodeWithText(date.toString()).assertExists()
        compose.onNodeWithText("−₹100").assertExists()
        compose.onNodeWithText(date.plusDays(1).toString()).assertExists()
    }
}
