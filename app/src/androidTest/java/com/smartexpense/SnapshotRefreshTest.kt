package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

class SnapshotRefreshTest {
    @Test fun metadataRefreshDoesNotReloadHistoryButFinancialWritesDo()=runBlocking {
        val queries=CopyOnWriteArrayList<String>()
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java)
            .setQueryCallback({sql,_->queries.add(sql)},Executor {it.run()}).build()
        val channel=Channel<Snapshot>(Channel.UNLIMITED)
        var job:Job?=null
        try {
            val repo=FinanceRepository(db);repo.initialize()
            val account=repo.createAccount("Test cash","Cash",100000)
            val expense=EntryDraft(amount=1000,description="Tea",accountId=account)
            repo.save(expense)
            job=launch(Dispatchers.Default) {repo.snapshots.collect {channel.send(it)}}
            withTimeout(10000) {while(channel.receive().records.none {it.id==expense.id}) {}}
            queries.clear()
            db.dao().saveDiary(DiaryEntity("2026-11-11","A metadata-only change"))
            val diary=withTimeout(10000) {var value=channel.receive();while(value.diary["2026-11-11"]!="A metadata-only change") value=channel.receive();value}
            assertTrue(diary.records.any {it.id==expense.id})
            assertFalse("Diary edits must not hydrate all transaction records: $queries",queries.any {it.startsWith("SELECT * FROM transactions ORDER BY",true)})
            repo.reverse(expense.id)
            val reversed=withTimeout(10000) {var value=channel.receive();while(value.records.none {it.id==expense.id && it.reversed}) value=channel.receive();value}
            assertEquals(100000L,reversed.accounts.single().balance)
            assertTrue(queries.any {it.startsWith("SELECT * FROM transactions ORDER BY",true)})
        } finally {job?.cancelAndJoin();channel.close();db.close()}
    }
}
