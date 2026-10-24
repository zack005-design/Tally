package com.smartexpense

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Permanent regressions for the October 19 audit. */
class AuditRegressionTest {
    @get:Rule val compose=createComposeRule()
    @Test fun foreignReceiptRequiresFreshInrItemReview()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        val models=androidx.lifecycle.ViewModelStore()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(100000);repo.finishWelcome()
            val friend=repo.createPerson("Friend")
            repo.saveGroup("Dinner group",listOf("self",friend))
            lateinit var model:AppViewModel
            compose.runOnIdle {model=AppViewModel(repo,BackupService(db),BillScanner(context),androidx.lifecycle.SavedStateHandle());models.put("foreign",model)}
            compose.setContent {TrackerTheme {TrackerApp(model)}}
            compose.waitUntil(10000) {!model.state.value.loading}
            compose.onNodeWithContentDescription("Add expense").performClick()
            compose.runOnIdle {
                val text="Cafe\nLunch 10.00\nTotal 10.00"
                model.prepareBill(ScannedBill(byteArrayOf(),"image/png",text),BillRecognition.parse(text),null)
                model.change(model.entry.value.copy(shared=true,participants=listOf("self",friend),paid=mapOf("self" to "10",friend to "0"),itemCharges="1",itemDiscount="1"))
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Personal expense or advanced item editing"))
            compose.onNodeWithText("Personal expense or advanced item editing").performClick()
            compose.onNodeWithText("Enter a manual exchange rate").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Original amount").performTextInput("10")
            compose.onNodeWithContentDescription("INR per one USD").performTextInput("80")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Use in expense"))
            compose.onNodeWithText("Use in expense").performClick()
            compose.runOnIdle {
                assertEquals(80000L,Money.parse(model.entry.value.amount))
                assertTrue("Foreign rows must not silently become INR shares",model.entry.value.items.isBlank())
                assertTrue(model.entry.value.billItemNames.isEmpty())
                assertTrue(model.billExpected.value)
                assertTrue(model.entry.value.notes.contains("Lunch: 10"))
                assertTrue(model.entry.value.paid.isEmpty())
                assertTrue(model.entry.value.itemCharges.isEmpty())
                assertTrue(model.entry.value.itemDiscount.isEmpty())
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Use a saved group",substring=true))
            compose.onNodeWithText("Use a saved group").performClick()
            compose.onAllNodesWithText("Dinner group").onLast().performClick()
            compose.runOnIdle {
                assertTrue("Choosing a group must not restore foreign rows",model.entry.value.items.isBlank())
                assertTrue(model.entry.value.copy(method="Itemized").preview().isFailure)
            }
        } finally {compose.runOnIdle {models.clear()};db.close()}
    }
    @Test fun invalidDecimalTotalMustNotBeRecognizedAsSuffix() {
        assertNull("123.456 must not become 456 rupees",BillRecognition.parse("Store\nGrand Total 123.456").total)
    }
    @Test fun negativeTotalMustNotBecomePositiveExpense() {
        assertNull("-100.00 must not become +100 rupees",BillRecognition.parse("Store\nGrand Total -100.00").total)
    }
    @Test fun ownExportWithSavedLongNameMustRestore()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize()
            val name="x".repeat(10001)
            val friend=repo.createPerson(name)
            val backup=BackupService(db)
            backup.restore(backup.exportFixture(),charArrayOf())
            assertEquals(name,repo.snapshot().people.single {it.id==friend}.name)
            backup.restore(backup.export("local password".toCharArray()),"local password".toCharArray())
            assertEquals(name,repo.snapshot().people.single {it.id==friend}.name)
        } finally {db.close()}
    }
    @Test fun correctedScannedExpenseMustKeepReceiptAccessible()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Cash","Cash",100000)
            val bitmap=android.graphics.Bitmap.createBitmap(10,10,android.graphics.Bitmap.Config.ARGB_8888)
            val output=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
            val original=EntryDraft(amount=10000,description="Scanned dinner",accountId=account)
            repo.saveWithBill(original,ScannedBill(output.toByteArray(),"image/png","Store\nTotal 100"))
            val replacement=original.copy(id=java.util.UUID.randomUUID().toString(),description="Corrected dinner")
            val saved=repo.save(replacement,original.id)
            assertNotNull("The active corrected expense must expose its original receipt",repo.receipt(saved))
            val correctedAgain=repo.save(replacement.copy(id=java.util.UUID.randomUUID().toString(),description="Second correction"),saved)
            assertArrayEquals(output.toByteArray(),repo.receipt(correctedAgain)!!.bytes)
            assertArrayEquals(output.toByteArray(),repo.receipt(original.id)!!.bytes)
            assertEquals(correctedAgain,repo.savedReceiptId(output.toByteArray()))
            assertArrayEquals(output.toByteArray(),repo.receiptChanges(correctedAgain).first()!!.bytes)
            assertTrue(runCatching {repo.saveWithBill(original.copy(id="duplicate"),ScannedBill(output.toByteArray(),"image/png","Store\nTotal 100"))}.isFailure)
            assertNull(db.dao().transaction("duplicate"))
            val backup=BackupService(db)
            backup.restore(backup.exportFixture(),charArrayOf())
            assertArrayEquals(output.toByteArray(),repo.receipt(correctedAgain)!!.bytes)
            assertEquals(correctedAgain,repo.savedReceiptId(output.toByteArray()))
            repo.removeReceipt(correctedAgain)
            assertNull(repo.receipt(correctedAgain));assertNull(repo.receipt(original.id))
            assertNull(repo.savedReceiptId(output.toByteArray()))
        } finally {db.close()}
    }
    @Test fun firstCreatedGroupMustBecomeSelectableWithoutReopeningDefaults() {
        var snapshot by mutableStateOf(Snapshot())
        compose.setContent {TrackerTheme {GroupDefaultsScreen(snapshot,false,{},{})}}
        compose.onNodeWithText("Create a group first").assertExists()
        compose.runOnIdle {snapshot=Snapshot(groups=listOf(Group("g","Dinner",listOf("self","f"))),people=listOf(Person("f","Friend")))}
        compose.onNodeWithText("Save default").assertExists()
    }
    @Test fun reselectingCurrentGroupMustPreserveActualPayments() {
        val group=Group("g","Dinner group",listOf("self","f"))
        var current=EntryUiState(amount="330",description="Dinner",accountId="a",shared=true,groupId="g",participants=listOf("self","f"),paid=mapOf("self" to "300","f" to "30"))
        compose.setContent {
            var form by remember {mutableStateOf(current)}
            TrackerTheme {EntryScreen(form,listOf(Account("a","Cash","Cash",100000,false)),listOf(Person("f","Friend")),emptyList(),listOf(group),{form=it;current=it},{},{})}
        }
        compose.onNodeWithText("Add a friend").performScrollTo().performClick()
        compose.onNodeWithText("Use a saved group").performClick()
        compose.onAllNodesWithText("Dinner group").onLast().performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals("Reselecting the current group must not erase the payment","300",current.paid["self"])
    }
    @Test fun diarySharedActionMustBeReachableAfterTypingAtLargeText() {
        lateinit var focus:androidx.compose.ui.focus.FocusManager
        var keyboard:androidx.compose.ui.platform.SoftwareKeyboardController?=null
        val pixels=ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.widthPixels
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(pixels/320f,2f)) {
                focus=androidx.compose.ui.platform.LocalFocusManager.current;keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                TrackerTheme {DiaryScreen(Snapshot(accounts=listOf(Account("a","Cash","Cash",100000,false))),false,{_,_,done->done()},{_,_->},{},"2026-10-11")}
            }
        }
        compose.onNodeWithText("Daily note").performTextInput("A note for the day")
        compose.runOnIdle {focus.clearFocus();keyboard?.hide()}
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Save daily note"))
        compose.onNodeWithText("Save daily note").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Add a shared bill for this day"))
        compose.onNodeWithText("Add a shared bill for this day").performScrollTo()
        compose.onNodeWithText("Add a shared bill for this day").assertIsDisplayed()
    }
}


