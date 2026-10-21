package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EverydayMoneyRepositoryTest {
    @Test fun reconciliationRejectsStalePreviewAndKeepsReason()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize()
            val id=repo.createAccount("Cash","Cash",10000)
            repo.reconcile(id,12000,expectedBalance=10000,reason="Counted cash")
            var s=repo.snapshot()
            assertEquals(12000L,s.accounts.single().balance)
            assertEquals(0L,s.dashboard.month.spending);assertEquals(0L,s.dashboard.month.income)
            assertTrue(s.records.any {it.kind==Kind.ADJUSTMENT && it.notes.contains("Counted cash")})
            try {repo.reconcile(id,15000,expectedBalance=10000,reason="Stale");fail("Stale preview accepted")} catch(_:IllegalArgumentException) {}
            assertEquals(12000L,repo.snapshot().accounts.single().balance)
            repo.reconcile(id,9000,expectedBalance=12000,reason="Recounted")
            s=repo.snapshot();assertEquals(9000L,s.accounts.single().balance)
            repo.updateAccount(s.accounts.single().copy(archived=true))
            try {repo.reconcile(id,1000,expectedBalance=9000,reason="Archived");fail("Archived accepted")} catch(_:IllegalArgumentException) {}
            assertEquals(9000L,repo.snapshot().accounts.single().balance)
        } finally {db.close()}
    }
}
