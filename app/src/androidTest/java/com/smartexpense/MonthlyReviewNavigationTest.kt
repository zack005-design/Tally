package com.smartexpense

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.*
import com.smartexpense.ui.AppViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MonthlyReviewNavigationTest {
    @Test fun enteringReviewFromToolsLoadsItEvenWithTheSameHistoryLimit()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        val store=ViewModelStore();val main=InstrumentationRegistry.getInstrumentation()
        lateinit var model:AppViewModel
        try {
            main.runOnMainSync {model=AppViewModel(FinanceRepository(db),BackupService(db),BillScanner(context),SavedStateHandle());store.put("navigation",model);model.observeRoute("more")}
            withTimeout(10000) {while(model.state.value.loading) delay(20)}
            assertNull(model.monthlyReview.value)
            main.runOnMainSync {model.observeRoute("monthly-review")}
            val loaded=withTimeoutOrNull(5000) {while(model.monthlyReview.value==null) delay(20);model.monthlyReview.value}
            assertNotNull("Entering monthly review must load it even when no snapshot reload is necessary",loaded)
        } finally {main.runOnMainSync {store.clear()};db.close()}
    }
}
