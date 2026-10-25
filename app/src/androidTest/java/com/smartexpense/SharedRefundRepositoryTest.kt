package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SharedRefundRepositoryTest {
    @Test fun refundPreservesOriginalAndSettlementsAndRoundTripsBackup()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize()
            val cash=repo.createAccount("Fixture cash","Cash",200000)
            val friend=repo.createPerson("Fixture friend")
            val date=java.time.LocalDate.of(2026,10,20).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val bill=EntryDraft(amount=90000,description="Shared dinner",accountId=cash,date=date,participants=listOf("self",friend),contributions=mapOf("self" to 90000L,friend to 0L))
            repo.save(bill)
            repo.recordRepayment(friend,"self",45000,cash,"Settled original bill",date=date+500)
            val refund=SharedRefundDraft(originalId=bill.id,amount=30000,description="Dinner refund",accountId=cash,shares=mapOf("self" to 15000L,friend to 15000L),received=mapOf("self" to 30000L,friend to 0L),date=date+1000)
            val id=repo.saveSharedRefund(refund)
            repo.saveSharedRefund(refund)
            val snapshot=repo.snapshot()
            assertEquals(185000L,snapshot.accounts.single().balance)
            assertEquals(15000L,snapshot.records.single {it.id==id}.personalAmount)
            assertFalse(snapshot.records.single {it.id==bill.id}.reversed)
            assertEquals(15000L,snapshot.debts.single {it.transactionId==id}.outstanding)
            assertEquals("self",snapshot.debts.single {it.transactionId==id}.from)
            assertEquals(friend,snapshot.debts.single {it.transactionId==id}.to)
            assertEquals(30000L,repo.report(java.time.LocalDate.of(2026,10,1),java.time.LocalDate.of(2026,10,31)).spending)
            val backup=BackupService(db);backup.restore(backup.exportFixture(),charArrayOf())
            assertEquals(185000L,repo.snapshot().accounts.single().balance)
            try {repo.saveSharedRefund(refund.copy(received=mapOf("self" to 0L,friend to 30000L)));fail("Retry mismatch")} catch(_:IllegalArgumentException) {}
            val payment=repo.recordRepayment("self",friend,15000,cash,"Return refund share")
            try {repo.reverse(id);fail("Reverse dependent repayment first")} catch(_:IllegalArgumentException) {}
            repo.reverse(payment);repo.reverse(id)
            assertEquals(155000L,repo.snapshot().accounts.single().balance)
            assertEquals(45000L,repo.sharedRefundContext(bill.id).remaining.getValue("self"))
        } finally {db.close()}
    }

    @Test fun perPersonCapsAreAtomicAndCashMayGoToFriend()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();val cash=repo.createAccount("Fixture cash","Cash",10000);val friend=repo.createPerson("Fixture friend")
            val bill=EntryDraft(amount=1000,description="Shared tea",accountId=cash,participants=listOf("self",friend),contributions=mapOf("self" to 1000L,friend to 0L))
            repo.save(bill)
            val refund=SharedRefundDraft(originalId=bill.id,amount=500,description="Tea refund",accountId=cash,shares=mapOf("self" to 500L,friend to 0L),received=mapOf("self" to 0L,friend to 500L))
            repo.saveSharedRefund(refund)
            assertEquals(9000L,repo.snapshot().accounts.single().balance)
            val count=repo.snapshot().transactionCount
            try {repo.saveSharedRefund(refund.copy(id="too-much",amount=1,shares=mapOf("self" to 1L,friend to 0L),received=mapOf("self" to 1L,friend to 0L)));fail("Participant cap")} catch(_:IllegalArgumentException) {}
            assertEquals(count,repo.snapshot().transactionCount)
            val backup=BackupService(db)
            val json=org.json.JSONObject(backup.exportFixture().toString(Charsets.UTF_8))
            assertEquals(8,json.getInt("version"))
            val shares=json.getJSONArray("shares")
            for(index in 0 until shares.length()) {
                val row=shares.getJSONArray(index)
                if(row.getString(0)==refund.id && row.getString(1)=="self") row.put(2,501)
            }
            try {backup.restore(json.toString().toByteArray(),charArrayOf());fail("Invalid refund backup")} catch(_:IllegalArgumentException) {}
            assertEquals(count,repo.snapshot().transactionCount)
            assertEquals(9000L,repo.snapshot().accounts.single().balance)
            try {repo.reverse(bill.id);fail("Linked refund")} catch(_:IllegalArgumentException) {}
        } finally {db.close()}
    }

    @Test fun fullRefundCanBeRetriedButNotReinterpretedAsPersonalRefund()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();val cash=repo.createAccount("Fixture cash","Cash",10000);val friend=repo.createPerson("Fixture friend")
            val bill=EntryDraft(amount=1000,description="Shared tea",accountId=cash,participants=listOf("self",friend),contributions=mapOf("self" to 1000L,friend to 0L))
            repo.save(bill)
            val refund=SharedRefundDraft(originalId=bill.id,amount=1000,description="Full refund",accountId=cash,shares=mapOf("self" to 500L,friend to 500L),received=mapOf("self" to 1000L,friend to 0L))
            repo.saveSharedRefund(refund);repo.saveSharedRefund(refund)
            assertTrue(repo.sharedRefundContext(bill.id).remaining.values.all {it==0L})
            assertEquals(10000L,repo.snapshot().accounts.single().balance)
            try {repo.loadDraft(refund.id);fail("Shared refund cannot use generic editing")} catch(_:IllegalArgumentException) {}
            try {repo.save(EntryDraft(id=refund.id,kind=Kind.REFUND,amount=1000,description=refund.description,accountId=cash,refundOf=bill.id,date=refund.date));fail("Shared refund cannot be retried as personal")} catch(_:IllegalArgumentException) {}
        } finally {db.close()}
    }
}
