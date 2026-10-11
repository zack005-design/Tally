package com.smartexpense
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class) class ReportQueryTest {
    @Test fun boundedDashboardSummarizesEntireLedgerAndLargeHistoryLoads()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Cash","Cash",100000);val time=System.currentTimeMillis()
            db.withTransaction {for(i in 1..1005) {val id="expense-$i";db.dao().insertTransaction(TransactionEntity(id,"EXPENSE","Expense $i",1,1,null,null,time,""));db.dao().insertShares(listOf(ShareEntity(id,"self",1)));db.dao().insertContributions(listOf(ContributionEntity(id,"self",1)));db.dao().insertEntries(listOf(AccountEntryEntity("entry-$i",id,account,-1)))}}
            val bounded=repo.snapshot(5);assertEquals(5,bounded.records.size);assertEquals(1005L,bounded.dashboard.today);assertEquals(98995L,bounded.accounts.single().balance)
            val history=repo.snapshot();assertEquals(1006,history.records.size);assertTrue(history.records.filter {it.kind==com.smartexpense.domain.Kind.EXPENSE}.all {it.accountIds==listOf(account) && it.personIds==listOf("self")})
        } finally {db.close()}
    }
}
