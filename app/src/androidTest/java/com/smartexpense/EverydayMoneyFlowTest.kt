package com.smartexpense

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

class EverydayMoneyFlowTest {
    @get:Rule val compose=createComposeRule()
    private var database:TrackerDatabase?=null
    private val models=ViewModelStore()
    private lateinit var model:AppViewModel
    private val lock=mutableStateOf(DeviceLockView(false,true))
    private fun start(launch:String?=null,locked:Boolean=false,seed:Boolean=false) {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build();database=db
        val repo=FinanceRepository(db)
        runBlocking {
            repo.initialize();repo.configureWallet(100000,gpayOpening=100000)
            if(seed) repo.save(EntryDraft(amount=2500,description="Coffee",accountId=repo.snapshot().walletSources!!.walletId,date=LocalDate.now().minusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        }
        lock.value=DeviceLockView(locked,!locked)
        compose.runOnIdle {model=AppViewModel(repo,BackupService(db),BillScanner(context),SavedStateHandle());models.put("everyday",model);if(launch!=null) model.requestLaunch(launch)}
        compose.setContent {TrackerTheme {SecureLedgerSurface(lock.value,{}) {TrackerApp(model)}}}
        compose.waitUntil(10000) {!model.state.value.loading};compose.waitForIdle()
    }
    @After fun close() {compose.runOnIdle {models.clear()};database?.close()}

    @Test fun repeatingOldExpenseUsesTodayAndWritesOnlyOnSave() {
        start(seed=true)
        val before=runBlocking {model.repository.snapshot()}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Coffee") and hasText("Opens a draft for today"))
        compose.onNode(hasText("Coffee") and hasText("Opens a draft for today")).performClick()
        compose.waitUntil(10000) {model.entry.value.description=="Coffee" && !model.state.value.busy}
        compose.runOnIdle {
            assertEquals(LocalDate.now(),LocalDateTime.parse(model.entry.value.date).toLocalDate())
            assertNull(model.entry.value.replaces)
        }
        assertEquals(before.transactionCount,runBlocking {model.repository.snapshot().transactionCount})
        compose.runOnIdle {model.saveEntry {}}
        compose.waitUntil(10000) {!model.state.value.busy && model.entry.value.description.isEmpty()}
        val after=runBlocking {model.repository.snapshot()}
        assertEquals(before.transactionCount+1,after.transactionCount)
        assertEquals(before.accounts.single {it.name=="Wallet"}.balance-2500,after.accounts.single {it.name=="Wallet"}.balance)
    }

    @Test fun quickLaunchRemainsPendingWhileLockedAndDoesNotRecordExpense() {
        start(launch="quick-add",locked=true)
        val count=runBlocking {model.repository.snapshot().transactionCount}
        compose.onNodeWithText("Tally is locked").assertExists()
        compose.runOnIdle {assertEquals("quick-add",model.launchRequest.value);lock.value=DeviceLockView(true,true)}
        compose.waitUntil(10000) {model.launchRequest.value==null}
        compose.onNodeWithText("Continue to payments").assertExists()
        assertEquals(count,runBlocking {model.repository.snapshot().transactionCount})
    }

    @Test fun favouriteWidgetWaitsForUnlockAndOnlyOpensADraft() {
        start(locked=true)
        val account=runBlocking {model.repository.snapshot().walletSources!!.walletId}
        runBlocking {model.repository.saveFavourite(FavouriteExpense("widget-coffee","Coffee",12345,account,null))}
        compose.waitUntil(10000) {model.state.value.snapshot.favourites.any {it.id=="widget-coffee"}}
        val before=runBlocking {model.repository.snapshot()}
        compose.runOnIdle {model.requestFavourite("widget-coffee")}
        compose.onNodeWithText("Tally is locked").assertExists()
        compose.runOnIdle {
            assertEquals("favourite",model.launchRequest.value)
            assertEquals("widget-coffee",model.requestedFavouriteId)
            assertNotEquals("Coffee",model.entry.value.description)
            lock.value=DeviceLockView(true,true)
        }
        compose.waitUntil(10000) {model.launchRequest.value==null && model.entry.value.description=="Coffee"}
        compose.runOnIdle {assertEquals("123.45",model.entry.value.amount);assertNull(model.requestedFavouriteId)}
        val after=runBlocking {model.repository.snapshot()}
        assertEquals(before.transactionCount,after.transactionCount)
        assertEquals(before.accounts,after.accounts)
    }

    @Test fun favouriteShortcutRequiresConfirmationBeforeDiscardingAnEntryOrReceipt() {
        start()
        val account=runBlocking {model.repository.snapshot().walletSources!!.walletId}
        runBlocking {model.repository.saveFavourite(FavouriteExpense("widget-coffee","Coffee",12345,account,null))}
        compose.waitUntil(10000) {model.state.value.snapshot.favourites.any {it.id=="widget-coffee"}}
        compose.runOnIdle {
            model.change(model.entry.value.copy(amount="55.50",description="Unfinished lunch",notes="Keep this draft"))
            model.billExpected.value=true
            model.requestFavourite("widget-coffee")
        }
        compose.waitUntil(5000) {compose.onAllNodesWithText("Replace your unfinished entry?").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle {assertEquals("Unfinished lunch",model.entry.value.description);assertTrue(model.billExpected.value)}
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {assertEquals("55.50",model.entry.value.amount);assertTrue(model.billExpected.value);model.requestFavourite("widget-coffee")}
        compose.onNodeWithText("Discard draft and open").performClick()
        compose.waitUntil(5000) {model.entry.value.description=="Coffee"}
        compose.runOnIdle {assertFalse(model.billExpected.value);assertEquals("123.45",model.entry.value.amount)}
    }

    @Test fun balancePreviewRejectsChangedBalanceWithoutCallingAdjustment() {
        var adjustments=0
        val snapshot=mutableStateOf(Snapshot(accounts=listOf(Account("cash","Wallet","Cash",10000,false))))
        compose.setContent {TrackerTheme {BalanceCheckScreen(snapshot.value,false,{},{},{_,_,_,_,_->adjustments++})}}
        compose.onNodeWithText("Account").performClick();compose.onNodeWithText("Wallet").performClick()
        compose.onNodeWithText("Actual balance (₹)").performTextInput("120")
        compose.onNodeWithText("Reason").performScrollTo().performClick()
        compose.onNodeWithText("Reason for adjustment").performTextInput("Counted cash")
        compose.onNodeWithText("Reason for adjustment").performImeAction()
        compose.onNodeWithText("Done").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Review adjustment"))
        compose.onNodeWithText("Review adjustment").performClick()
        compose.runOnIdle {assertEquals(0,adjustments);snapshot.value=snapshot.value.copy(accounts=listOf(Account("cash","Wallet","Cash",11000,false)))}
        compose.onNodeWithText("Record adjustment").assertIsNotEnabled()
        compose.runOnIdle {assertEquals(0,adjustments)}
    }
}
