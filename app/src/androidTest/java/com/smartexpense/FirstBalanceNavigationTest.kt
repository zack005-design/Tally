package com.smartexpense

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.onNodeWithText("Your\nstarting point.").assertIsDisplayed()
        capture("setup")
        compose.onNodeWithContentDescription("Wallet balance (₹)").performTextInput("250")
        compose.onNodeWithContentDescription("GPay balance (₹)").performScrollTo().performTextInput("1000")
        compose.onNodeWithContentDescription("GPay balance (₹)").performImeAction()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Continue to Tally"))
        compose.onNodeWithText("Continue to Tally").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("TOTAL AVAILABLE").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Overview") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("₹1,250").assertExists()
        capture("today")
        compose.onNodeWithText("Add money").performScrollTo().performClick()
        compose.onNodeWithText("To GPay",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Amount to add (₹)").performTextInput("100")
        capture("add-money")
        compose.onNodeWithText("Add to GPay").performClick()
        compose.waitUntil(10000) {runBlocking {repo.snapshot().accounts.single {it.name=="GPay"}.balance==110000L}}
        compose.runOnIdle {
            val reopened=AppViewModel(repo,BackupService(db),BillScanner(context),SavedStateHandle())
            models.put("reopened",reopened);current.value=reopened
        }
        compose.waitUntil(10000) {!current.value.state.value.loading}
        compose.onNode(hasText("Overview") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Your\nstarting point.").assertDoesNotExist()
        compose.onNodeWithText("₹1,350").assertExists()
    }
}
