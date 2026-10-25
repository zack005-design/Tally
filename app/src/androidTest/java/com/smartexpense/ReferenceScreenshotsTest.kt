package com.smartexpense

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.*
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Real composables, fictional data, and an isolated repository; never opens the user's ledger. */
class ReferenceScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: TrackerDatabase
    private lateinit var model: AppViewModel
    private val models = ViewModelStore()
    private lateinit var focusManager: FocusManager
    private var keyboard: SoftwareKeyboardController? = null
    private val today = LocalDate.now()
    private val people = listOf(Person("self", "You"), Person("rahul", "Rahul"), Person("ananya", "Ananya"))
    private val timestamp = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val fixture = Snapshot(
        accounts = listOf(Account("w", "Wallet", "Cash", 120000, false), Account("g", "GPay", "Bank", 500000, false), Account("s", "Savings", "Bank", 1200000, false)),
        walletSources = WalletSources("w", "g", today.toString().take(7)),
        people = people,
        groups = listOf(Group("flat", "Flatmates", people.map { it.id }), Group("trip", "Weekend trips", people.map { it.id })),
        categories = listOf(Category("food", "Food & drinks", 0xFFFFC7DC, "🍴", false), Category("travel", "Travel", 0xFFB9DAFF, "✈", false), Category("shopping", "Shopping", 0xFFFFC7DC, "♧", false), Category("bills", "Bills", 0xFFB9DAFF, "▤", false), Category("other", "Other", 0xFFD9CAFF, "•••", false)),
        records = listOf(
            Record("lunch", Kind.EXPENSE, "Lunch with friends", 90000, 30000, "food", "flat", timestamp, "", false, listOf("g"), people.map { it.id }),
            Record("snacks", Kind.EXPENSE, "Snacks", 6000, 6000, "food", null, timestamp - 86400000, "", false, listOf("w")),
            Record("transfer", Kind.TRANSFER, "Transfer to GPay", 50000, 0, null, null, timestamp - 3600000, "", false, listOf("w", "g"))),
        debts = listOf(Debt("d1", "lunch", "rahul", "self", 30000, 30000, timestamp, "flat"), Debt("d2", "lunch", "ananya", "self", 30000, 30000, timestamp, "flat")),
        groupSpending = mapOf("flat" to 90000L),
        goals = listOf(SavingsGoal("emergency", "Emergency fund", 5000000, listOf(GoalContribution("c", today.toString(), 1500000))), SavingsGoal("holiday", "Holiday", 3000000, listOf(GoalContribution("c2", today.toString(), 800000)))),
        templates = listOf(RecurringTemplate("rent", Kind.EXPENSE, 1200000, "Rent", "g", today.withDayOfMonth(1).toString(), "Monthly"), RecurringTemplate("salary", Kind.INCOME, 4500000, "Salary", "g", today.withDayOfMonth(1).toString(), "Monthly")),
        favourites = listOf(FavouriteExpense("coffee", "Coffee", 8000, "g", "food"), FavouriteExpense("metro", "Metro ride", 4000, "g", "travel")),
        categoryRules = listOf(CategoryRule("amma", "Amma’s", "food"), CategoryRule("metro", "Metro", "travel"), CategoryRule("basket", "BigBasket", "shopping")),
        diary = mapOf(today.toString() to "Lunch with Rahul and Ananya. A quiet afternoon at home."),
        dashboard = DashboardMetrics(today = 30000, week = 36000), transactionCount = 3
    )

    @Before fun createIsolatedModel() {
        compose.runOnIdle {
            database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackerDatabase::class.java).build()
            model = AppViewModel(FinanceRepository(database), BackupService(database), BillScanner(ApplicationProvider.getApplicationContext()), SavedStateHandle())
            models.put("reference", model)
        }
    }

    @After fun closeIsolatedModel() {
        compose.runOnIdle { models.clear() }
        database.close()
    }

    private fun render(large: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            focusManager = LocalFocusManager.current
            keyboard = LocalSoftwareKeyboardController.current
            val density = LocalDensity.current
            val windowWidth = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
            val width = if (large) 320f else 393f
            CompositionLocalProvider(LocalDensity provides Density(windowWidth / width, if (large) 2f else 1f)) {
                CompositionLocalProvider(LocalEditorialBack provides {}) {EditorialPreviewTheme(content)}
            }
        }
    }

    private fun shell(command: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {
            assertEquals("", it.readBytes().toString(Charsets.UTF_8).trim())
        }
    }

    private fun capture(name: String) {
        val inputs=compose.onAllNodes(hasSetTextAction() and isFocused())
        if(inputs.fetchSemanticsNodes().isNotEmpty()) {
            val input=inputs.onFirst()
            if(input.fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ImeAction)==androidx.compose.ui.text.input.ImeAction.Done) input.performImeAction()
        }
        compose.runOnIdle { focusManager.clearFocus(force = true); keyboard?.hide() }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(250, 3000)
        android.os.SystemClock.sleep(750)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "exact-$name.png")
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { screenshot.recycle() }
        shell("mkdir -p /sdcard/Download/tally-exact-reference")
        shell("cp ${file.absolutePath} /sdcard/Download/tally-exact-reference/$name.png")
    }

    @Test fun accounts() { render { AccountsScreen(fixture, model, {}) }; capture("accounts") }
    @Test fun accountsLarge() { render(true) { AccountsScreen(fixture, model, {}) }; capture("accounts-320dp-2x") }
    @Test fun groups() {
        render { GroupsScreen(fixture, model) }; capture("groups")
        compose.onNodeWithText("Flatmates").performClick()
        compose.onNodeWithText("Edit group").performScrollTo().performClick()
        compose.onNodeWithText("Add member").performClick()
        val member=compose.onNode(hasText("You") and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState))
        member.assertIsOn().performClick().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Done choosing members").performClick()
        capture("group-editor")
    }
    @Test fun categories() {
        render { CategoriesScreen(fixture, model) }; capture("categories")
        compose.onNodeWithText("Food & drinks").performClick(); capture("category-editor")
    }
    @Test fun rules() {
        render { CategoryRulesScreen(fixture, false, {}, {}) }; capture("merchant-rules")
        compose.onNodeWithText("Amma’s").performClick(); capture("merchant-rule-editor")
    }
    @Test fun settings() { render { SettingsOverviewScreen(fixture.localSettings) {} }; capture("settings") }
    @Test fun lockSettings() { render { AppLockSettingsContent(DeviceLockView(false, true, credentialAvailable = true)) {} }; capture("app-lock") }
    @Test fun tools() {render {MoreScreen {}};capture("tools")}
    @Test fun defaultSplit() {render {GroupDefaultsScreen(fixture,false,{},{})};capture("default-split")}
    @Test fun gpaySchedule() {render {GpaySettingsScreen(fixture,false,{_,_->},{_,_,_,_,_->},{})};capture("gpay-schedule-off");compose.onNodeWithText("Enable monthly reset").performScrollTo().performClick();capture("gpay-schedule")}
    @Test fun balanceCheck() {
        render {BalanceCheckScreen(fixture,false,{},{},{_,_,_,_,_->})}
        compose.onNodeWithText("Account").performClick()
        compose.onNodeWithText("Wallet").performClick()
        compose.onNodeWithText("Actual balance (₹)").performTextInput("1150")
        capture("balance-check")
    }
    @Test fun planner() {render {MoneyPlannerScreen(fixture)};capture("money-planner")}
    @Test fun budgets() {
        val month=today.toString().take(7)
        val data=fixture.copy(reportMonth=month,selectedReport=PeriodReport(842000,0,mapOf("food" to 412000L,"travel" to 170000L,"shopping" to 260000L),emptyMap()),budgets=listOf(Budget("all",month,null,1200000),Budget("b1",month,"food",500000),Budget("b2",month,"travel",200000),Budget("b3",month,"shopping",500000)))
        render {BudgetsScreen(data,month,{},{},{_,_,_,_->})};capture("budgets")
    }
    @Test fun monthlyReview() {
        val month=today.toString().take(7)
        val review=MonthlyReview(month,mapOf("w" to 1000000L),mapOf("w" to 1620000L),2500000,200000,0,0,1940000,2100000,20000,0,0,true)
        render {MonthlyReviewScreen(fixture,review,month,{},{})};capture("monthly-review")
    }
    @Test fun backup() {render {BackupScreen(model)};capture("backup")}
    @Test fun reminders() {render {RemindersScreen(model)};capture("reminders")}
    @Test fun export() {
        kotlinx.coroutines.runBlocking {
            model.repository.initialize();model.repository.configureWallet(120000,gpayOpening=500000)
            val account=model.repository.snapshot().walletSources!!.gpayId
            model.repository.save(EntryDraft(kind=Kind.INCOME,amount=2500000,description="Salary",accountId=account))
            model.repository.save(EntryDraft(amount=18000,description="Lunch",accountId=account))
        }
        render {LocalExportScreen(model)}
        compose.waitUntil(10000) {compose.onAllNodesWithText("₹25,000").fetchSemanticsNodes().isNotEmpty()}
        capture("export")
    }
    @Test fun foreignBill() {
        render {ManualExchangeScreen {_,_,_,_,_->}}
        compose.onNodeWithContentDescription("Original amount").performTextInput("25.00")
        compose.onNodeWithContentDescription("INR per one USD").performTextInput("83.20")
        capture("foreign-bill")
    }
    @Test fun wallet() { render { WalletScreen(fixture, false, { _, _, _, _ -> }, { _, _, _, _ -> }, { _, _, _ -> }, {}, {}) }; capture("wallet") }
    @Test fun addMoney() {
        render { WalletScreen(fixture, false, { _, _, _, _ -> }, { _, _, _, _ -> }, { _, _, _ -> }, {}, {}, initialAddMoney = true, onTopupDetailed={_,_,_,_,done->done()}) }
        compose.onNodeWithContentDescription("Amount to add (₹)").performTextInput("2000")
        compose.onNodeWithText("Projected balance (Wallet)").performScrollTo()
        capture("add-money")
    }
    @Test fun goals() { render { GoalsScreen(fixture, false, { _, _, _, _ -> }, { _, _, _ -> }, {}) }; capture("goals") }
    @Test fun favourites() { render { FavouritesScreen(fixture, false, {}, { _, _ -> }, {}) }; capture("favourites") }
    @Test fun recurring() { render { RecurringScreen(fixture, false, { _, _ -> }, {}, { _, _ -> }) }; capture("recurring") }
    @Test fun activity() { render { ActivityScreen(fixture, {}) }; capture("activity") }
    @Test fun calendar() { render { SpendingCalendarScreen(fixture, {}, today) }; capture("calendar") }
    @Test fun diary() { render { DiaryScreen(fixture, false, { _, _, _ -> }, { _, _ -> }, {}, today.toString()) }; capture("diary") }
    @Test fun friends() { render { SplitScreen(fixture, {}, {}, {}, { _, _ -> }, {}, { _, _, _, _ -> }) }; capture("friends") }
    @Test fun friendDetail() {
        render {SplitScreen(fixture,{},{},{},{_,_->},{},{_,_,_,_->})}
        compose.onNodeWithText("Rahul").performClick();capture("friend-detail")
    }
    @Test fun receiptImport() {
        val scanner=BillScanner(ApplicationProvider.getApplicationContext())
        render {BillImportScreen(scanner,model.repository,fixture,{_,_,_->},{})};capture("receipt-import")
    }
    @Test fun receiptReview() {
        val text="Amma's\nPizza 500.00\nPasta 300.00\nDrinks 100.00\nTotal 900.00"
        val bitmap=android.graphics.Bitmap.createBitmap(600,280,android.graphics.Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.BLACK;textSize=32f}
        text.lines().forEachIndexed {i,line->canvas.drawText(line,32f,42f+i*42f,paint)}
        val output=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
        val scanner=BillScanner(ApplicationProvider.getApplicationContext())
        var prepared:BillSuggestion?=null
        val initial=ScannedBill(output.toByteArray(),"image/png",text)
        render {BillImportScreen(scanner,model.repository,fixture,{_,suggestion,_->prepared=suggestion},{},initialBill=initial)}
        compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Original bill for checking recognition").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(10000) {compose.onAllNodes(hasText("Continue to split") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        capture("receipt-review")
        compose.onNodeWithText("Edit scanned text").performScrollTo().performClick()
        compose.onNodeWithText("Edit recognition").performTextReplacement("Changed\nTotal 999.00")
        compose.onAllNodesWithText("Cancel").onLast().performClick()
        org.junit.Assert.assertNull(prepared)
        compose.onNodeWithText("Continue to split").performClick()
        assertEquals(90000L,prepared!!.total)
    }
    @Test fun repayment() {
        render {RepaymentScreen(fixture,fixture.debts.first(),model,onSaved={})}
        compose.onNodeWithContentDescription("Amount (₹)").performTextInput("150")
        capture("repayment")
    }
    @Test fun offset() {render {OffsetReviewSurface("Rahul",20000,"All groups",{},{})};capture("offset")}
    @Test fun transactionDetail() {
        val record=fixture.records.first()
        val detail=RecordDetail(record,listOf(Movement("g",-60000)),mapOf("self" to 30000L,"rahul" to 30000L,"ananya" to 30000L),mapOf("self" to 60000L,"rahul" to 30000L,"ananya" to 0L),emptyList(),emptyMap())
        render {TransactionDetailView(record,detail,fixture,onEdit={},onDuplicate={},onActions={})};capture("transaction-detail")
    }
    @Test fun personalTransactionDetail() {
        val record=fixture.records.first().copy(description="Lunch",amount=18000,personalAmount=18000,personIds=listOf("self"),notes="Team lunch at cafe.")
        val detail=RecordDetail(record,listOf(Movement("g",-18000)),mapOf("self" to 18000L),mapOf("self" to 18000L),emptyList(),emptyMap())
        render {TransactionDetailView(record,detail,fixture.copy(debts=emptyList()),hasReceipt=true,onEdit={},onDuplicate={},onRefund={},onActions={})};capture("transaction-personal-detail")
    }
    @Test fun refund() {
        val original=fixture.records[1].copy(personIds=listOf("self"))
        render {var form by remember {mutableStateOf(EntryUiState(kind=Kind.REFUND,amount="20.50",description="Partial refund",accountId="w",refundOf=original.id,notes="Partial refund"))};EntryScreen(form,fixture.accounts,people,fixture.categories,fixture.groups,{form=it},{},{},records=listOf(original))};capture("refund")
    }
    @Test fun describedEntry() {
        render {AdvancedEntryScreen(EntryUiState(text="Coffee 120 gpay"),fixture.accounts,people,fixture.categories,fixture.groups,{},{},{})};capture("described-entry")
    }
    @Test fun incomeEntry() { entry(EntryUiState(kind = Kind.INCOME, amount = "2000", description = "Monthly allowance", accountId = "g"), "income-entry") }
    @Test fun transfer() { entry(EntryUiState(kind = Kind.TRANSFER, amount = "500", description = "Move money", accountId = "w", destinationId = "g"), "transfer") }
    @Test fun sharedEntry() {
        entry(EntryUiState(amount = "900", description = "Dinner", accountId = "g", categoryId = "food", shared = true, groupId = "flat", participants = people.map { it.id }, paid = mapOf("self" to "600", "rahul" to "300", "ananya" to "0"),items="500=self+rahul\n300=ananya\n100=self+rahul+ananya",billItemNames=listOf("Pizza","Pasta","Drinks")), "shared-entry")
        compose.onNodeWithText("Continue").performClick(); capture("shared-payments")
        compose.onNodeWithText("Continue").performClick(); capture("shared-method")
        compose.onNodeWithText("By item").performScrollTo().performClick()
        compose.onNodeWithText("Continue").performClick(); capture("shared-items")
        compose.onNodeWithText("Review").performClick(); capture("shared-review")
    }
    private fun entry(initial: EntryUiState, name: String) {
        render {
            var value by remember { mutableStateOf(initial) }
            EntryScreen(value, fixture.accounts, people, fixture.categories, fixture.groups, { value = it }, {}, {}, walletSources = fixture.walletSources)
        }
        capture(name)
    }
    @Test fun setupFilled() {
        render { BalanceSetupScreen(Snapshot(), false) { _, _, _, _ -> } }
        compose.onNodeWithContentDescription("Wallet balance (₹)").performScrollTo().performTextInput("1200")
        compose.onNodeWithContentDescription("GPay balance (₹)").performScrollTo().performTextInput("5000")
        compose.runOnIdle { focusManager.clearFocus(force = true); keyboard?.hide() }
        compose.onNodeWithText("Your\nstarting point.").performScrollTo(); capture("setup-filled")
    }
}
