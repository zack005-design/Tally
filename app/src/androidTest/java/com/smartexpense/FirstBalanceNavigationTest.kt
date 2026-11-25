package com.smartexpense

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FirstBalanceNavigationTest {
    @get:Rule val compose=createComposeRule()
    private var database:TrackerDatabase?=null
    private val models=ViewModelStore()
    @After fun close() {compose.runOnIdle {models.clear()};database?.close()}
    private fun capture(name:String) {
        val context=ApplicationProvider.getApplicationContext<Context>()
        File(context.getExternalFilesDir(null),"first-balance-$name.png").outputStream().use {
            compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun firstLaunchSetupThenTodayAndReopenWithoutSetup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build();database=db
        val repo=FinanceRepository(db)
        lateinit var first:AppViewModel
        compose.runOnIdle {first=AppViewModel(repo,BackupService(db),BillScanner(context),SavedStateHandle());models.put("first",first)}
        val current=mutableStateOf(first)
        compose.setContent {key(current.value) {TrackerTheme {TrackerApp(current.value)}}}
        compose.waitUntil(10000) {!first.state.value.loading}
        compose.onNodeWithText("Welcome to Tally").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithContentDescription("Step 2 of 4").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Your starting balances").assertIsDisplayed()
        capture("setup")
        compose.onNodeWithContentDescription("Wallet balance in rupees").performScrollTo().performTextInput("250")
        compose.onNodeWithContentDescription("GPay balance in rupees").performScrollTo().performTextInput("1000")
        compose.onNodeWithContentDescription("GPay balance in rupees").performImeAction()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Start using Tally").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Available to spend").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Summary") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("₹1,250").assertExists()
        capture("today")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Add money"))
        compose.onNodeWithText("Add money").performClick()
        compose.onNodeWithText("Destination").performClick()
        compose.onAllNodesWithText("GPay").onLast().performClick()
        compose.onNodeWithContentDescription("Amount to add (₹)").performTextInput("100")
        androidx.test.espresso.Espresso.closeSoftKeyboard();compose.waitForIdle()
        capture("add-money")
        compose.onAllNodesWithText("Add money").onLast().performScrollTo().performClick()
        compose.waitUntil(10000) {runBlocking {repo.snapshot().accounts.single {it.name=="GPay"}.balance==110000L}}
        compose.runOnIdle {
            val reopened=AppViewModel(repo,BackupService(db),BillScanner(context),SavedStateHandle())
            models.put("reopened",reopened);current.value=reopened
        }
        compose.waitUntil(10000) {!current.value.state.value.loading}
        compose.onNode(hasText("Summary") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Welcome to Tally").assertDoesNotExist()
        compose.onNodeWithText("₹1,350").assertExists()
    }
}
