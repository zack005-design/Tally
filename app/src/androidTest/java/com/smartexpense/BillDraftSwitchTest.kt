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
import java.time.LocalDate

class BillDraftSwitchTest {
    @Test fun switchingFromBillToCorrectionOrTemplateClearsAttachment()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        val store=ViewModelStore();val instrumentation=InstrumentationRegistry.getInstrumentation()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(100000)
            val account=repo.snapshot().walletSources!!.walletId
            val original=repo.save(EntryDraft(amount=1000,description="Original meal",accountId=account))
            lateinit var model:AppViewModel
            instrumentation.runOnMainSync {model=AppViewModel(repo,BackupService(db),BillScanner(context),SavedStateHandle());store.put("switch",model)}
            withTimeout(5000) {while(model.state.value.loading) delay(10)}
            val bill=ScannedBill(byteArrayOf(-119,80,78,71,13,10,26,10),"image/png","Store\nTotal 20.00")
            instrumentation.runOnMainSync {model.prepareBill(bill,BillRecognition.parse(bill.text),null)}
            val ready=CompletableDeferred<Unit>()
            instrumentation.runOnMainSync {model.edit(original) {ready.complete(Unit)}}
            withTimeout(5000) {ready.await()}
            assertFalse(model.billExpected.value)
            val saved=CompletableDeferred<Unit>()
            instrumentation.runOnMainSync {model.change(model.entry.value.copy(amount="20.00"));model.saveEntry {saved.complete(Unit)}}
            withTimeout(5000) {saved.await()}
            assertTrue(repo.detail(original).record.reversed)
            assertEquals(98000L,repo.snapshot().accounts.single {it.id==account}.balance)
            instrumentation.runOnMainSync {
                model.prepareBill(bill,BillRecognition.parse(bill.text),null)
                model.recurringEntry(RecurringTemplate("rent",Kind.EXPENSE,1000,"Rent",account,LocalDate.now().toString(),"Monthly"),LocalDate.now())
            }
            assertFalse(model.billExpected.value)
        } finally {instrumentation.runOnMainSync {store.clear()};db.close()}
    }
}
