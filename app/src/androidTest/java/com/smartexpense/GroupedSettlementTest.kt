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
class GroupedSettlementTest {
    @Test fun repaymentsAndOffsetsRemainInTheirGroupHistory()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Wallet","Cash",100000)
            val friend=repo.createPerson("Maya");val group=repo.saveGroup("Lunch",listOf("self",friend))
            repo.save(EntryDraft(amount=10000,description="I paid",accountId=account,groupId=group,participants=listOf("self",friend),contributions=mapOf("self" to 10000L,friend to 0L)))
            val repayment=repo.recordRepayment(friend,"self",1000,account,"Part payment",groupId=group)
            assertEquals(group,repo.snapshot().records.single {it.id==repayment}.groupId)
            repo.save(EntryDraft(amount=10000,description="Maya paid",accountId=account,groupId=group,participants=listOf("self",friend),contributions=mapOf("self" to 0L,friend to 10000L)))
            repo.recordOffset(friend,"self",1000,group)
            assertEquals(2,repo.snapshot().records.count {it.kind==Kind.OFFSET && it.groupId==group})
            assertEquals(0L,repo.report(java.time.LocalDate.now(),java.time.LocalDate.now()).income)
        } finally {db.close()}
    }
}
