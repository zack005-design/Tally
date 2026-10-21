package com.smartexpense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.BackupService
import com.smartexpense.data.BillScanner
import com.smartexpense.data.FinanceRepository
import com.smartexpense.data.LocalCommandParser
import com.smartexpense.data.TrackerDatabase
import com.smartexpense.ui.AppViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real background parsing against disposable in-memory data, with no saves. */
@RunWith(AndroidJUnit4::class)
class LocalCommandDraftFlowTest {
    @Test fun resultsRespectManualEditsLatestRequestAndNewDrafts() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TrackerDatabase::class.java).build()
        val store = ViewModelStore()
        lateinit var model: AppViewModel
        val repository = FinanceRepository(db)
        try {
            repository.initialize()
            repository.configureWallet(100000)
            val before = repository.snapshot().transactionCount
            instrumentation.runOnMainSync {
                model = AppViewModel(repository, BackupService(db), BillScanner(context), SavedStateHandle(), LocalCommandParser(context))
                store.put("command-flow", model)
            }
            withTimeout(10000) { model.state.first { !it.loading } }
            fun startParse(): Job {
                val owner = requireNotNull(model.viewModelScope.coroutineContext[Job])
                val existing = owner.children.toSet()
                model.parseText()
                return owner.children.single { it !in existing }
            }
            lateinit var pending: Job
            instrumentation.runOnMainSync {
                model.change(model.entry.value.copy(text="Lunch 180 GPay"))
                pending = startParse()
                model.change(model.entry.value.copy(description="Manually edited"))
            }
            withTimeout(10000) { pending.join() }
            instrumentation.runOnMainSync { assertEquals("Manually edited", model.entry.value.description) }

            lateinit var superseded: Job
            instrumentation.runOnMainSync {
                model.change(model.entry.value.copy(text="Lunch 180 GPay"))
                superseded = startParse()
                model.change(model.entry.value.copy(text="Coffee 45 cash"))
                pending = startParse()
            }
            withTimeout(10000) { joinAll(superseded, pending) }
            instrumentation.runOnMainSync {
                assertEquals("Coffee", model.entry.value.description)
                assertEquals("45.00", model.entry.value.amount)
                assertTrue(model.entry.value.accountId.isNotBlank())
                model.change(model.entry.value.copy(text="Lunch 180 GPay"))
                pending = startParse()
                model.newEntry()
            }
            withTimeout(10000) { pending.join() }
            instrumentation.runOnMainSync {
                assertEquals("", model.entry.value.description)
                assertEquals("", model.entry.value.amount)
                model.change(model.entry.value.copy(text="I did not pay 45 for coffee using cash"))
                pending = startParse()
            }
            withTimeout(10000) { pending.join() }
            instrumentation.runOnMainSync {
                assertEquals("", model.entry.value.amount)
                assertTrue(model.entry.value.parserNotice.contains("negated"))
                model = AppViewModel(repository, BackupService(db), BillScanner(context), SavedStateHandle())
                store.put("rules-only-command-flow", model)
                model.change(model.entry.value.copy(text="I paid 45 dollars for coffee using cash", amount="1.00"))
                pending = startParse()
            }
            withTimeout(10000) { pending.join() }
            instrumentation.runOnMainSync { assertEquals("", model.entry.value.amount) }
            assertEquals("Parsing must never save a record", before, repository.snapshot().transactionCount)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            db.close()
        }
    }
}
