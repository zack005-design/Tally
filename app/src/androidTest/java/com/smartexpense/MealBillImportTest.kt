package com.smartexpense

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.*
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MealBillImportTest {
    private val text="Cafe\nPaneer biryani 120.00\nVeg noodles 150.00\nLime soda 60.00\nTotal 330.00"
    @Test fun importedItemsSaveTheirActualSplitAndOriginalBill()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        val store=ViewModelStore();val instrumentation=InstrumentationRegistry.getInstrumentation()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(100000);val friend=repo.createPerson("Rahul")
            lateinit var model:AppViewModel
            instrumentation.runOnMainSync {model=AppViewModel(repo,BackupService(db),BillScanner(context),SavedStateHandle());store.put("meal",model)}
            withTimeout(5000) {while(model.state.value.loading) delay(10)}
            val bytes=java.io.ByteArrayOutputStream().also {output->android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888).let {bitmap->bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()}}.toByteArray()
            val bill=ScannedBill(bytes,"image/png",text)
            val completed=CompletableDeferred<String>()
            instrumentation.runOnMainSync {
                model.prepareBill(bill,BillRecognition.parse(text),null)
                val selected=model.entry.value.withMealFriend(friend)
                model.change(selected.copy(items=assignMealItems(selected.items,setOf(0),setOf(2),friend),paid=mapOf("self" to "300",friend to "30")))
                model.saveEntry {completed.complete(it)}
            }
            val id=withTimeout(10000) {completed.await()}
            val saved=repo.detail(id)
            assertEquals(mapOf("self" to 15000L,friend to 18000L),saved.shares)
            assertEquals(mapOf("self" to 30000L,friend to 3000L),saved.contributions)
            assertEquals(15000L,repo.snapshot().debts.single {it.transactionId==id}.outstanding)
            assertArrayEquals(bytes,repo.receipt(id)!!.bytes)
            assertTrue(model.entry.value.billItemNames.isEmpty())
        } finally {instrumentation.runOnMainSync {store.clear()};db.close()}
    }

    @Test fun importedNamesSurviveSavedDraftRestorationAndResetWithNewEntry()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        val store=ViewModelStore();val instrumentation=InstrumentationRegistry.getInstrumentation()
        try {
            val repo=FinanceRepository(db);repo.initialize()
            val state=SavedStateHandle()
            lateinit var model:AppViewModel
            lateinit var restored:AppViewModel
            instrumentation.runOnMainSync {
                model=AppViewModel(repo,BackupService(db),BillScanner(context),state);store.put("first",model)
                model.prepareBill(ScannedBill(byteArrayOf(),"image/png",text),BillRecognition.parse(text),null)
                restored=AppViewModel(repo,BackupService(db),BillScanner(context),state);store.put("second",restored)
                assertEquals(listOf("Paneer biryani","Veg noodles","Lime soda"),restored.entry.value.billItemNames)
                assertEquals(model.entry.value.items,restored.entry.value.items)
                restored.newEntry()
                assertTrue(restored.entry.value.billItemNames.isEmpty())
            }
        } finally {instrumentation.runOnMainSync {store.clear()};db.close()}
    }
}
