package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun persistenceRepaymentAndAtomicValidation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrackerDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db)
            repo.initialize()
            val account = repo.createAccount("Bank", "Bank", 200000)
            val rahul = repo.createPerson("Rahul")
            val kevin = repo.createPerson("Kevin")
            val arjun = repo.createPerson("Arjun")
            val meal = EntryDraft(kind=Kind.EXPENSE, amount=89000, description="Meal", accountId=account, participants=listOf("self",rahul,kevin,arjun), contributions=mapOf("self" to 50000L,rahul to 29000L,kevin to 10000L,arjun to 0L))
            repo.save(meal)
            assertEquals(150000L,repo.snapshot().accounts.single().balance)
            assertEquals(22250L,repo.snapshot().records.single { it.description == "Meal" }.personalAmount)
            val count=repo.snapshot().records.size
            try { repo.save(meal.copy(id="bad",contributions=mapOf("self" to 1L))); fail("Must reject mismatched bill") } catch(_: IllegalArgumentException) { }
            assertEquals(count,repo.snapshot().records.size)
            repo.save(meal) // idempotent retry
            assertEquals(count,repo.snapshot().records.size)
            repo.recordRepayment(kevin,"self",12250,account,"Partial settlement")
            assertEquals(162250L,repo.snapshot().accounts.single().balance)
            val repayment=repo.snapshot().records.single { it.kind == Kind.REPAYMENT }
            repo.reverse(repayment.id)
            assertEquals(150000L,repo.snapshot().accounts.single().balance)
        } finally { db.close() }
    }
}
