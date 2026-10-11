package com.smartexpense
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class) class BackupTest {
    @Test fun restorePreservesBalanceAndRejectsBadData()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {val repository=FinanceRepository(db);repository.initialize();repository.createAccount("Cash","Cash",12345);val backup=BackupService(db);val bytes=backup.export(charArrayOf());backup.restore(bytes,charArrayOf());assertEquals(12345L,repository.snapshot().accounts.single().balance)
            try {backup.restore("{\"version\":999}".toByteArray(),charArrayOf());fail("Invalid version")} catch(_:IllegalArgumentException) { }
            assertEquals(12345L,repository.snapshot().accounts.single().balance)
        } finally {db.close()}
    }
    @Test fun oversizedRefundInBackupIsRejectedBeforeReplacingData()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repository=FinanceRepository(db);repository.initialize();val account=repository.createAccount("Cash","Cash",10000)
            val expense=com.smartexpense.domain.EntryDraft(amount=1000,description="Lunch",accountId=account)
            repository.save(expense);repository.save(com.smartexpense.domain.EntryDraft(kind=com.smartexpense.domain.Kind.REFUND,amount=500,description="Refund",accountId=account,refundOf=expense.id))
            val service=BackupService(db);val json=org.json.JSONObject(service.export(charArrayOf()).toString(Charsets.UTF_8));val transactions=json.getJSONArray("transactions");var refundId=""
            for(i in 0 until transactions.length()) {val row=transactions.getJSONArray(i);if(row.getString(1)=="REFUND") {row.put(3,1500);row.put(4,1500);refundId=row.getString(0)}}
            val entries=json.getJSONArray("entries");for(i in 0 until entries.length()) {val row=entries.getJSONArray(i);if(row.getString(1)==refundId) row.put(3,1500)}
            var rejected=false;try {service.restore(json.toString().toByteArray(),charArrayOf())} catch(_:IllegalArgumentException) {rejected=true}
            assertTrue("Refund larger than original must be rejected",rejected);assertEquals(9500L,repository.snapshot().accounts.single().balance)
        } finally {db.close()}
    }
    @Test fun fabricatedCyclesInBalancedBillAreRejected()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();val account=repo.createAccount("Cash","Cash",10000);val friend=repo.createPerson("Friend")
            val expense=com.smartexpense.domain.EntryDraft(amount=10000,description="Balanced",accountId=account,participants=listOf("self",friend),contributions=mapOf("self" to 5000L,friend to 5000L));repo.save(expense)
            val service=BackupService(db);val json=org.json.JSONObject(service.export(charArrayOf()).toString(Charsets.UTF_8));val obligations=json.getJSONArray("obligations")
            obligations.put(org.json.JSONArray(listOf("fake-one",expense.id,"self",friend,10000)));obligations.put(org.json.JSONArray(listOf("fake-two",expense.id,friend,"self",10000)))
            var rejected=false;try {service.inspect(json.toString().toByteArray(),charArrayOf())} catch(_:IllegalArgumentException) {rejected=true};assertTrue("Zero-net fake debt cycles must be rejected",rejected)
        } finally {db.close()}
    }
}
