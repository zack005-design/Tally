package com.smartexpense

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.File
import java.time.*

/** Screenshots use isolated illustrative data, never the installed user's Room database. */
class UiPreviewTest {
    @get:Rule val compose=createComposeRule()
    private val month=YearMonth.now()
    private fun date(day:Int)=month.atDay(day).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val people=listOf(Person("self","You"),Person("r","Rahul"),Person("k","Kevin"),Person("a","Arjun"))
    private val records=listOf(
        Record("meal",Kind.EXPENSE,"Shared dinner",89000,22250,"food","flat",date(9),"",false,listOf("bank"),people.map {it.id}),
        Record("grocery",Kind.EXPENSE,"Grocery restock",145000,145000,"groceries",null,date(8),"",false,listOf("bank"),listOf("self")),
        Record("metro",Kind.EXPENSE,"Metro recharge",50000,50000,"transport",null,date(7),"",false,listOf("cash"),listOf("self")),
        Record("subscriptions",Kind.EXPENSE,"Subscriptions",89900,89900,"other",null,date(6),"",false,listOf("bank"),listOf("self")),
        Record("rent",Kind.EXPENSE,"Rent",250000,250000,"other",null,date(5),"",false,listOf("bank"),listOf("self")),
        Record("meals",Kind.EXPENSE,"Food this week",96850,96850,"food",null,date(4),"",false,listOf("bank"),listOf("self")),
        Record("refund",Kind.REFUND,"Grocery refund",12000,12000,"groceries",null,date(9),"",false,listOf("bank"),listOf("self")),
        Record("income",Kind.INCOME,"Freelance payout",1800000,0,null,null,date(3),"",false,listOf("bank"),listOf("self")))
    private fun snapshot():Snapshot {
        val report=Reports.period(records,month.atDay(1),month.atEndOfMonth())
        val draft=EntryUiState(amount="890",description="Shared dinner",accountId="bank",shared=true,participants=people.map {it.id},paid=mapOf("self" to "500","r" to "290","k" to "100","a" to "0")).draft()
        val obligations=Finance.settlements(Finance.net(Finance.split(draft.amount,draft.participants,draft.splitRule),draft.contributions))
        val debts=obligations.mapIndexed {i,it->Debt("d$i","meal",it.from,it.to,it.amount,it.amount,date(9),"flat")}
        return Snapshot(accounts=listOf(Account("bank","Bank","Bank",2358050,false),Account("cash","Cash","Cash",100000,false)),people=people,groups=listOf(Group("flat","Flatmates",people.map {it.id})),categories=listOf(Category("food","Food",0xFFA6E3C4,"food",false),Category("groceries","Groceries",0xFFE9B393,"shopping",false),Category("transport","Transport",0xFFC2CCD1,"transport",false),Category("other","Other",0xFF8B969D,"other",false)),budgets=listOf(Budget("budget",month.toString(),null,1200000)),records=records,debts=debts,dashboard=DashboardMetrics(10250,report.spending,report,431000),selectedReport=report,reportMonth=month.toString(),previousPeriodSpending=594000,transactionCount=records.size)
    }
    private fun render(route:String,large:Boolean=false,content:@Composable ()->Unit) {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,if(large) 2f else density.fontScale)) {
                TrackerTheme {Box(if(large) Modifier.width(androidx.compose.ui.unit.Dp(320f)).fillMaxHeight() else Modifier.fillMaxSize()) {
                    Scaffold(bottomBar={LedgerNavigation(route,{})}) {padding->Box(Modifier.fillMaxSize().padding(padding)) {content()}}
                }}
            }
        }
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.getExternalFilesDir(null),"$name.png")
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        listOf("mkdir -p /sdcard/Download/smart-expense-previews", "cp ${file.absolutePath} /sdcard/Download/smart-expense-previews/$name.png").forEach {command->
            val output=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(output).use {assertEquals("",it.readBytes().toString(Charsets.UTF_8).trim())}
        }
    }
    @Test fun overviewPreview() {val s=snapshot();render("home") {HomeScreen(s,{_,_->},{},{},{},{})};compose.onNodeWithText("₹24,580.50").assertExists();capture("overview")}
    @Test fun activityPreview() {val s=snapshot();render("activity") {ActivityScreen(s,{})};compose.onNodeWithText("Shared dinner").assertExists();capture("activity")}
    @Test fun addPreview() {val s=snapshot();render("add") {EntryScreen(EntryUiState(amount="890",description="Shared dinner",accountId="bank",categoryId="food",shared=true,participants=people.map {it.id},paid=mapOf("self" to "500","r" to "290","k" to "100","a" to "0")),s.accounts,s.people,s.categories,s.groups,{},{},{})};compose.onNodeWithText("Total amount (₹)").assertExists();capture("split-review");compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save transaction"));capture("split-review-details")}
    @Test fun describePreview() {val s=snapshot();render("add") {EntryScreen(EntryUiState(),s.accounts,s.people,s.categories,s.groups,{},{},{},records=s.records)};capture("describe")}
    @Test fun manualPreview() {val s=snapshot();render("add") {EntryScreen(EntryUiState(),s.accounts,s.people,s.categories,s.groups,{},{},{})};compose.onNodeWithText("Manual").performClick();compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(0);capture("manual")}
    @Test fun splitPreview() {val s=snapshot();render("split") {SplitScreen(s,{},{},{},{_,_->},{},{_,_,_,_->})};compose.onAllNodesWithText("₹277.50").onFirst().assertExists();capture("split")}
    @Test fun insightsPreview() {val s=snapshot();render("insights") {InsightsScreen(s,month.toString(),{},{_,_,_->})};compose.onAllNodesWithText("₹6420.00").onFirst().assertExists();capture("insights")}
    @Test fun narrowLargeTextKeepsQuickActionAndMenuUsable() {
        compose.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {TrackerTheme {androidx.compose.material3.Surface(color=Tokens.background,modifier=Modifier.width(androidx.compose.ui.unit.Dp(320f)).fillMaxHeight()) {HomeScreen(snapshot(),{_,_->},{},{},{},{})}}}}
        compose.onNodeWithContentDescription("Manage tracker").assertIsDisplayed().performClick()
        compose.onNodeWithText("Groups").assertIsDisplayed()
        compose.onNodeWithText("Groups").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Expense"))
        compose.onNodeWithText("Expense").assertIsDisplayed().performClick()
        capture("overview-large-text")
    }
    @Test fun allNavigationDestinationsRemainInteractive() {
        var route=""
        compose.setContent {TrackerTheme {LedgerNavigation("home") {route=it}}}
        listOf("Home" to "home","Activity" to "activity","Add" to "add","Split" to "split","Insights" to "insights").forEach {(label,value)->compose.onNodeWithText(label).performClick();assertEquals(value,route)}
    }
    @Test fun largeTextActivityPreview() {val s=snapshot();render("activity",true) {ActivityScreen(s,{})};compose.onNodeWithText("Expenses").assertIsDisplayed().performClick();compose.onNodeWithText("Shared dinner").assertExists();capture("activity-large-text")}
    @Test fun largeTextAddKeepsSaveReachable() {
        val s=snapshot();render("add",true) {EntryScreen(EntryUiState(amount="100",description="Lunch",accountId="bank",categoryId="food"),s.accounts,s.people,s.categories,s.groups,{},{},{})}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save transaction"))
        compose.onNodeWithText("Save transaction").assertIsDisplayed().assertIsEnabled();capture("add-large-text")
    }
    @Test fun largeTextSplitPreview() {val s=snapshot();render("split",true) {SplitScreen(s,{},{},{},{_,_->},{},{_,_,_,_->})};compose.onNode(hasScrollAction()).performScrollToNode(hasText("Record repayment"));compose.onNodeWithText("Record repayment").assertIsDisplayed();capture("split-large-text")}
    @Test fun largeTextInsightsPreview() {val s=snapshot();render("insights",true) {InsightsScreen(s,month.toString(),{},{_,_,_->})};compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Daily spending"));compose.onNodeWithText("Daily spending").assertIsDisplayed();capture("insights-large-text")}
    @Test fun largeTextNavigationKeepsEveryDestinationUsable() {
        var route="home"
        compose.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {TrackerTheme {Box(Modifier.width(androidx.compose.ui.unit.Dp(320f))) {LedgerNavigation(route) {route=it}}}}}
        listOf("Home" to "home","Activity" to "activity","Add" to "add","Split" to "split","Insights" to "insights").forEach {(label,value)->compose.onNodeWithText(label).assertIsDisplayed().performClick();assertEquals(value,route)}
        capture("navigation-large-text")
    }
}





