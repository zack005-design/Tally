package com.smartexpense

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.*
import java.io.File

class EditorialExtensionUiTest {
    @get:Rule val compose=createComposeRule()
    private val people=listOf(Person("self","You"),Person("r","Rahul"),Person("k","Kevin"),Person("a","Arjun"))
    private val accounts=listOf(Account("bank","Bank","Bank",2458050,false))
    private lateinit var focus:FocusManager
    private fun render(large:Boolean=false,content:@Composable ()->Unit) {
        compose.setContent {focus=LocalFocusManager.current;val d=LocalDensity.current;val width=LocalConfiguration.current.screenWidthDp*d.density
            CompositionLocalProvider(LocalDensity provides if(large) Density(width/320f,2f) else d) {EditorialPreviewTheme(content)}
        }
    }
    private fun capture(name:String) {
        val focusedInputs=compose.onAllNodes(hasSetTextAction() and isFocused())
        if(focusedInputs.fetchSemanticsNodes().isNotEmpty()) focusedInputs.onFirst().performImeAction()
        compose.runOnIdle {focus.clearFocus()};compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        // Popup windows and the OS keyboard render outside Compose's test clock.
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(250,3000)
        android.os.SystemClock.sleep(350)
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png")
        instrumentation.uiAutomation.takeScreenshot().let {bitmap->file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        listOf("mkdir -p /sdcard/Download/editorial-extensions","cp ${file.absolutePath} /sdcard/Download/editorial-extensions/$name.png").forEach {cmd->android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use {assertEquals("",it.readBytes().toString(Charsets.UTF_8).trim())}}
    }
    @Test fun calendarPreview() {render {EditorialDatePicker("Choose date",LocalDate.of(2026,10,10),{},{})};compose.onNodeWithText("Use date →").assertIsDisplayed();capture("calendar")}
    @Test fun calendarLargeTextKeepsConfirmationReachable() {render(true) {EditorialDatePicker("Choose date",LocalDate.of(2024,2,29),{},{})};compose.onNodeWithText("Use date →").performScrollTo().assertIsDisplayed();
        // Text semantics measure glyph bounds, not the 40sp line box. This still excludes the ~19dp normal-size label.
        compose.onNodeWithText("Use date →",useUnmergedTree=true).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(32f));capture("calendar-large-text")}
    @Test fun timeEditingPreservesDate() {
        var date="2024-02-29T15:37"
        render {Column {DateTimeField(date) {date=it}}}
        compose.onNodeWithText("15:37").performClick()
        compose.onNodeWithText("Hour").performTextReplacement("03")
        compose.onNodeWithText("Minute").performTextReplacement("05")
        capture("time")
        compose.onNodeWithText("Use time →").performClick()
        assertEquals("2024-02-29T03:05",date)
    }
    @Test fun monthYearSelectionPreview() {render {EditorialMonthPicker(YearMonth.of(2024,2),{},{})};compose.onNodeWithText("Year").performTextReplacement("2028");capture("month-year")}
    @Test fun searchableChoicePreview() {render {Choice("Account","bank",listOf("bank" to "Bank","cash" to "Cash","wallet" to "Wallet"),{})};compose.onNodeWithText("Account").performClick();compose.onNodeWithText("Search options").assertExists();capture("choice")}
    @Test fun incompleteBillCalculatesWithoutInventingPayments() {
        val form=EntryUiState(amount="890",shared=true,participants=people.map {it.id},paid=mapOf("self" to "500"))
        var changed=form
        render {Page("Live bill") {item {AutoCalculationPanel(form,accounts,{changed=it},people)}}}
        compose.onNodeWithText("You · share: ₹222.50").assertExists()
        compose.onNodeWithText("Unassigned payment: ₹390.00").assertExists()
        assertEquals(mapOf("self" to "500"),changed.paid)
        capture("live-calculation")
        compose.onNodeWithText("I paid all").performScrollTo().performClick()
        assertEquals("890.00",changed.paid["self"])
        assertEquals("0.00",changed.paid["r"])
    }
    @Test fun plannerRecalculatesWhileTyping() {
        render {MoneyPlannerScreen(Snapshot(accounts=accounts))}
        compose.onNodeWithText("Available money (₹)").performTextInput("10000")
        compose.onNodeWithText("Committed costs (₹)").performTextReplacement("2000")
        compose.onNodeWithText("Desired savings (₹)").performTextReplacement("1000")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("After costs and desired savings"))
        compose.runOnIdle {focus.clearFocus()}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Daily allowance"))
        compose.onNodeWithText("₹7,000.00").assertExists()
        capture("money-planner")
    }
    @Test fun transactionDetailsPreview() {
        val r=Record("bill",Kind.EXPENSE,"Shared dinner",89000,22250,"food",null,LocalDate.of(2026,10,9).atTime(19,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),"Dinner with friends",false)
        val detail=RecordDetail(r,listOf(Movement("bank",-50000)),people.associate {it.id to 22250L},mapOf("self" to 50000,"r" to 29000,"k" to 10000,"a" to 0),emptyList(),emptyMap())
        render {TransactionDetailView(r,detail,Snapshot(accounts=accounts,people=people,categories=listOf(Category("food","Food",0,"food",false))),{})}
        compose.onNodeWithText("₹890.00").assertExists();capture("transaction-detail")
    }
    @Test fun reverseConfirmationCancelsWithoutMutation() {
        var saved=0;var canceled=0
        render {EditorialConfirmSheet("Reverse this transaction?","The original record stays in history. Its account movement, spending and debts will be undone.","Reverse",{canceled++}) {saved++}}
        capture("confirmation")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0,saved);assertEquals(1,canceled)
    }
    @Test fun budgetDraftStaysOpenUntilSaveSucceeds() {
        var finish:(()->Unit)?=null
        val month=YearMonth.of(2026,10).toString()
        render {InsightsScreen(Snapshot(reportMonth=month),month,{},{_,_,_->},onBudgetSave={_,_,_,done->finish=done})}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Set budget"))
        compose.onNodeWithText("Set budget").performClick()
        compose.onNodeWithText("Budget amount (₹)").performTextInput("2500")
        compose.onNodeWithText("Save budget").performClick()
        compose.onNodeWithText("Budget amount (₹)").assertExists()
        compose.onNode(hasSetTextAction() and hasText("2500")).assertExists()
        capture("budget-editor")
        compose.runOnIdle {finish!!.invoke()}
        compose.onNodeWithText("Budget amount (₹)").assertDoesNotExist()
    }
    @Test fun plannerLargeTextKeepsSourceLabelsUsable() {
        render(true) {MoneyPlannerScreen(Snapshot(accounts=accounts))}
        compose.onNodeWithText("Expected income").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Expected income (₹)").assertExists()
        capture("planner-large-text")
    }
    @Test fun describedSentenceCalculatesBeforeContinue() {
        render {var form by remember {mutableStateOf(EntryUiState())};EntryScreen(form,accounts,people,emptyList(),emptyList(),{form=it},{},{})}
        compose.onNode(hasSetTextAction()).performTextInput("Dinner with friends ₹890. I paid ₹500, Rahul paid ₹290, Kevin paid ₹100 and Arjun paid nothing. Split equally.")
        compose.onNodeWithText("Detected amount: ₹890.00").assertExists()
        compose.onNodeWithText("Your share: ₹222.50").assertExists()
        compose.onNodeWithText("You receive: ₹277.50").assertExists()
    }
}
