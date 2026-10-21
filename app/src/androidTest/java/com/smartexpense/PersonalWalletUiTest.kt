package com.smartexpense

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor

class PersonalWalletUiTest {
    @get:Rule val compose=createComposeRule()
    private val day=LocalDate.of(2026,10,11)
    private val people=listOf(Person("self","You"),Person("maya","Maya"),Person("dev","Dev"))
    private val fixture=Snapshot(accounts=listOf(Account("wallet","Wallet","Cash",140000,false),Account("gpay","GPay","Bank",430000,false)),people=people,groups=listOf(Group("lunch","Lunch crew",people.map {it.id})),walletSources=WalletSources("wallet","gpay","2026-10"),dashboard=DashboardMetrics(today=30000,week=30000,month=PeriodReport(30000,0,emptyMap(),mapOf(day to 30000))),records=listOf(Record("meal",Kind.EXPENSE,"Dinner together",90000,30000,null,"lunch",day.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),"",false,listOf("wallet"),people.map {it.id})))
    private fun reveal(matcher:SemanticsMatcher):SemanticsNodeInteraction {
        if(compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(matcher)
        val node=compose.onNode(matcher)
        node.performScrollTo()
        return node
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png")
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        listOf("mkdir -p /sdcard/Download/wallet-previews","cp ${file.absolutePath} /sdcard/Download/wallet-previews/$name.png").forEach {cmd->ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use {assertEquals("",it.readBytes().toString(Charsets.UTF_8).trim())}}
    }
    @Test fun walletCardsAndMonthlyPace() {
        compose.setContent {EditorialPreviewTheme {WalletBalanceCards(fixture,true,{},day)}}
        compose.onNodeWithText("Wallet").assertIsDisplayed()
        compose.onNodeWithText("GPay").assertIsDisplayed()
        compose.onNodeWithText("₹1,400").assertIsDisplayed()
        compose.onNodeWithText("₹4,300").assertIsDisplayed()
        compose.onNodeWithText("Until your next refill").assertDoesNotExist()
        capture("wallet-balances")
    }
    @Test fun calendarShowsPersonalShareAndSelectedDay() {
        compose.setContent {EditorialPreviewTheme {SpendingCalendarScreen(fixture,{},day)}}
        compose.onNodeWithText("Month").assertIsDisplayed()
        reveal(hasContentDescription("11 October 2026, spending ₹300.00, 1 entries")).performClick()
        reveal(hasText("Total spent")).assertIsDisplayed()
        compose.onNodeWithText("₹300").assertIsDisplayed()
        reveal(hasText("Dinner together"))
        compose.onNodeWithText("Dinner together").assertIsDisplayed()
        capture("calendar-day")
    }
    @Test fun sourceSelectionAndSecondSourceAreVisible() {
        var state=EntryUiState(amount="900",description="Meal",accountId="wallet",shared=true,participants=people.map {it.id},paid=mapOf("self" to "600","maya" to "300","dev" to "0"))
        compose.setContent {var value by remember {mutableStateOf(state)};EditorialPreviewTheme {Page("Payment sources") {item {Panel {PaymentSourcePicker(value,fixture.accounts,{value=it;state=it})}}}}}
        compose.onNodeWithText("GPay · ₹4,300.00").performClick()
        assertEquals("gpay",state.accountId)
        compose.onNodeWithText("Pay from two sources").performClick()
        compose.onNodeWithText("Second payment source").assertIsDisplayed()
        capture("two-sources")
    }
    @Test fun repeatGroupPreparesParticipantsWithoutInventingPayments() {
        val draft=EntryUiState().withGroup(fixture.groups.single())
        assertTrue(draft.shared)
        assertEquals(people.map {it.id},draft.participants)
        assertEquals("",draft.paid["maya"])
        assertEquals("",draft.paid["self"])
    }
    @Test fun calendarLargeTextRemainsUsable() {
        compose.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {EditorialPreviewTheme {SpendingCalendarScreen(fixture,{},day)}}}
        reveal(hasText("Month")).assertIsDisplayed()
        reveal(hasContentDescription("11 October 2026, spending ₹300.00, 1 entries")).performClick()
        capture("calendar-large-text")
    }
    @Test fun calendarKeepsWholeWeekVisibleAndCanCollapseToWeek() {
        compose.setContent {EditorialPreviewTheme {SpendingCalendarScreen(fixture,{},day)}}
        compose.onNodeWithText("Month",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Mon").assertIsDisplayed()
        compose.onNodeWithText("Sun").assertIsDisplayed()
        compose.onNodeWithText("Week",useUnmergedTree=true).performClick()
        compose.onNodeWithContentDescription("11 October 2026, spending ₹300.00, 1 entries").assertIsDisplayed()
        compose.onNodeWithContentDescription("18 October 2026, spending ₹0.00, 0 entries").assertDoesNotExist()
        capture("calendar-week")
    }
    @Test fun calendarMonthNavigationAndTodayRestoreSelection() {
        compose.setContent {EditorialPreviewTheme {SpendingCalendarScreen(fixture,{},day)}}
        compose.onNodeWithContentDescription("Next calendar month").performClick()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        reveal(hasText("Today")).performClick()
        reveal(hasText("October 2026")).assertIsDisplayed()
        compose.onNodeWithContentDescription("11 October 2026, spending ₹300.00, 1 entries").assertIsSelected()
    }
    @Test fun calendarWeekArrowsMoveSevenDays() {
        compose.setContent {EditorialPreviewTheme {SpendingCalendarScreen(fixture,{},day)}}
        compose.onNodeWithText("Week",useUnmergedTree=true).performClick()
        compose.onNodeWithContentDescription("Next calendar week").performClick()
        compose.onNodeWithContentDescription("18 October 2026, spending ₹0.00, 0 entries").assertIsSelected()
        compose.onNodeWithContentDescription("Previous calendar week").performClick()
        compose.onNodeWithContentDescription("11 October 2026, spending ₹300.00, 1 entries").assertIsSelected()
        repeat(3) {compose.onNodeWithContentDescription("Next calendar week").performClick()}
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 November 2026, spending ₹0.00, 0 entries").assertIsSelected()
        compose.onNodeWithContentDescription("26 October 2026, spending ₹0.00, 0 entries").assertExists()
    }
    @Test fun calendarRefundKeepsSignedDayTotalAndIncomingEntry() {
        val refund=Record("refund",Kind.REFUND,"Lunch refund",5000,5000,null,null,day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),"",false,listOf("wallet"),listOf("self"))
        compose.setContent {EditorialPreviewTheme {SpendingCalendarScreen(fixture.copy(records=fixture.records+refund),{},day)}}
        reveal(hasContentDescription("12 October 2026, spending ₹-50.00, 1 entries")).performClick()
        reveal(hasText("Total spent")).assertIsDisplayed()
        compose.onNodeWithText("−₹50").assertExists()
        reveal(hasText("Lunch refund")).assertIsDisplayed()
        compose.onNodeWithText("+₹50").assertExists()
        capture("calendar-refund")
    }
    @Test fun calendarLargeTextFitsNarrowScreen() {
        compose.setContent {
            val pixels=androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.widthPixels
            CompositionLocalProvider(LocalDensity provides Density(pixels/320f,2f)) {EditorialPreviewTheme {SpendingCalendarScreen(fixture,{},day)}}
        }
        reveal(hasContentDescription("11 October 2026, spending ₹300.00, 1 entries"))
        val right=compose.onRoot().fetchSemanticsNode().boundsInRoot.right
        (5..11).forEach {date->
            val bounds=compose.onNodeWithText(date.toString(),useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Date $date crosses the viewport",bounds.left>=0 && bounds.right<=right)
        }
        capture("calendar-narrow-large-text")
    }
    @Test fun walletSetupAcceptsCustomCash() {
        var cash:Long?=null
        compose.setContent {EditorialPreviewTheme {WalletScreen(Snapshot(),false,{value,_,_,_->cash=value},{_,_,_,_->},{_,_,_->},{},{})}}
        reveal(hasText("Cash in your Wallet (₹)")).apply {performTextInput("2000");performImeAction()}
        reveal(hasText("Start my wallet")).assertIsNotEnabled()
        reveal(hasText("Money in your GPay (₹)")).apply {performTextInput("0");performImeAction()}
        reveal(hasText("Start my wallet"))
        capture("wallet-setup")
        compose.onNodeWithText("Start my wallet").performClick()
        assertEquals(200000L,cash)
    }
    @Test fun walletHomeAndQuickGroup() {
        var chosen:String?=null
        compose.setContent {EditorialPreviewTheme {SplitScreen(fixture,{},{},{},{_,_->},{},{_,_,_,_->},onRepeatGroup={chosen=it.id})}}
        reveal(hasText("Settlement history")).performClick()
        reveal(hasText("Same group again · Lunch crew")).assertIsDisplayed()
        capture("quick-group")
        compose.onNodeWithText("Same group again · Lunch crew").performClick()
        assertEquals("lunch",chosen)
    }
    @Test fun overviewShowsPrimaryBalances() {
        compose.setContent {EditorialPreviewTheme {HomeScreen(fixture,{_,_->},{},{},{},{})}}
        compose.onNodeWithText("₹5,700").assertIsDisplayed()
        compose.onNodeWithText("Wallet").assertIsDisplayed()
        capture("wallet-overview")
    }
    @Test fun itemizedChargesAndDiscountControls() {
        val initial=EntryUiState(amount="300",description="Lunch",accountId="wallet",shared=true,participants=people.map {it.id},method="Itemized",items="200 = self\n100 = maya",paid=mapOf("self" to "300","maya" to "0","dev" to "0"))
        compose.setContent {var value by remember {mutableStateOf(initial)};EditorialPreviewTheme {AdvancedEntryScreen(value,fixture.accounts,people,emptyList(),fixture.groups,{value=it},{},{})}}
        reveal(hasText("Shared taxes / charges (₹)")).assertIsDisplayed()
        reveal(hasText("Bill discount (₹)")).assertIsDisplayed()
        capture("food-split")
    }
}
