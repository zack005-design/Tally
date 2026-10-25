package com.smartexpense

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises real navigation and persistence, rather than a save callback alone. */
class StudioNavigationTest {
    @get:Rule val compose=createComposeRule()
    private var database:TrackerDatabase?=null
    private val models=ViewModelStore()
    private lateinit var model:AppViewModel
    private fun start():String {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        database=db
        val repo=FinanceRepository(db)
        val friend=runBlocking {repo.initialize();repo.configureWallet(120000,gpayOpening=500000);repo.finishWelcome();repo.createPerson("Rahul")}
        compose.runOnIdle {model=AppViewModel(repo,BackupService(db),BillScanner(context),SavedStateHandle());models.put("studio",model)}
        compose.setContent {TrackerTheme {TrackerApp(model)}}
        compose.waitUntil(10000) {!model.state.value.loading}
        compose.waitForIdle()
        return friend
    }
    @After fun close() {compose.runOnIdle {models.clear()};database?.close()}

    @Test fun savingActualPaymentsOpensCommittedReceiptAndReturnsHome() {
        val friend=start()
        compose.onNodeWithContentDescription("Add expense").performClick()
        compose.runOnIdle {
            model.change(model.entry.value.copy(amount="330",description="Dinner",shared=true,participants=listOf("self",friend),paid=mapOf("self" to "300",friend to "30")))
        }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Save shared bill").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Transaction").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Overview") and isSelectable()).assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Rahul owes You"))
        compose.onNodeWithText("Rahul owes You").assertExists()
        compose.onNodeWithText("₹135.00").assertExists()
        val snapshot=runBlocking {model.repository.snapshot()}
        assertEquals(90000L,snapshot.accounts.single {it.name=="Wallet"}.balance)
        assertEquals(16500L,snapshot.records.single {it.description=="Dinner"}.personalAmount)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        compose.onNode(hasText("Overview") and isSelectable()).assertIsSelected()
    }

    @Test fun incomeRemainsReachableFromSettingsAfterRemovingTheAddTab() {
        start()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Money & planning"))
        compose.onNodeWithText("Money & planning").performClick()
        compose.onNodeWithText("Record income").performClick()
        compose.runOnIdle {assertEquals(Kind.INCOME,model.entry.value.kind)}
        compose.onNode(hasText("Overview") and isSelectable()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertExists()
    }
}
