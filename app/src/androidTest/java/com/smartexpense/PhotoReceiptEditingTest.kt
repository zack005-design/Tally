package com.smartexpense

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotoReceiptEditingTest {
    @Test fun scanQualityWarningIsVisibleAndTravelsWithTheDraft()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize()
            val bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
            val output=java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
            val warning="Camera text is uncertain. Check the amounts."
            val bill=ScannedBill(output.toByteArray(),"image/png","Store\nLunch 100.00\nTotal 100.00",listOf(warning))
            var prepared:BillSuggestion?=null
            compose.setContent {TrackerTheme {CompositionLocalProvider(LocalEditorialBack provides {}) {BillImportScreen(BillScanner(context),repo,Snapshot(),{_,suggestion,_->prepared=suggestion},{},initialBill=bill)}}}
            compose.waitUntil(30000) {compose.onAllNodes(hasText("Continue to split") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(warning))
            compose.onNodeWithText(warning).assertIsDisplayed()
            compose.onNodeWithText("Continue to split").performClick()
            assertTrue(prepared!!.warnings.contains(warning))
            assertTrue(repo.snapshot().records.isEmpty())
        } finally {db.close()}
    }
    @Test fun duplicateCheckSurvivesReviewRecreationWithoutRestarting() {
        val result=kotlinx.coroutines.CompletableDeferred<String?>()
        val review=ReceiptReviewModel(ScannedBill(byteArrayOf(1),"image/png","Lunch 100"))
        val store=androidx.lifecycle.ViewModelStore()
        var calls=0
        compose.runOnIdle {
            store.put("receipt",review)
            review.checkInitial {calls++;result.await()}
        }
        compose.waitUntil {calls==1}
        compose.runOnIdle {review.checkInitial {calls++;"incorrect"}}
        assertTrue(review.scanning.value)
        result.complete("already-saved")
        compose.waitUntil {review.duplicate.value=="already-saved" && !review.scanning.value}
        assertEquals(1,calls)
        compose.runOnIdle {store.clear()}
    }

    @get:Rule val compose=createComposeRule()
    @Test fun correctedItemsRemainDraftsUntilContinueAndCancelPreservesOriginal()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize()
            val bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
            val output=java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
            val bill=ScannedBill(output.toByteArray(),"image/png","Store\nLunch 100.00\nTotal 100.00")
            var prepared:BillSuggestion?=null
            val restoration=StateRestorationTester(compose)
            restoration.setContent {TrackerTheme {CompositionLocalProvider(LocalEditorialBack provides {}) {BillImportScreen(BillScanner(context),repo,Snapshot(),{_,suggestion,_->prepared=suggestion},{},initialBill=bill)}}}
            compose.waitUntil(30000) {compose.onAllNodes(hasText("Continue to split") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Add item"))
            compose.onNodeWithText("Lunch").performClick()
            compose.onNodeWithText("Item amount (₹)").performTextReplacement("25")
            compose.onAllNodesWithText("Cancel").onLast().performClick()
            compose.onAllNodesWithText("₹100").assertCountEquals(2)
            compose.onNodeWithText("Lunch").performClick()
            compose.onNodeWithText("Item amount (₹)").performTextReplacement("50.50")
            compose.onNodeWithText("Save item").performClick()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("₹50.50").assertExists()
            compose.onNodeWithText("Add item").performClick()
            compose.onNodeWithText("Item name").performTextInput("Snack")
            compose.onNodeWithText("Item amount (₹)").performTextInput("49.50")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithText("Item name").assertTextContains("Snack")
            compose.onNodeWithText("Item amount (₹)").assertTextContains("49.50")
            compose.onNodeWithText("Save item").performClick()
            assertNull(prepared);assertTrue(repo.snapshot().records.isEmpty())
            compose.onNodeWithText("Continue to split").performClick()
            assertEquals(listOf(5050L,4950L),prepared!!.items.map {it.amount})
            assertEquals(10000L,prepared!!.total)
            assertTrue(repo.snapshot().records.isEmpty())
        } finally {db.close()}
    }
}
