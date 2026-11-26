package com.smartexpense
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class) class TagsRepositoryTest {
    private lateinit var db:TrackerDatabase
    private lateinit var repository:FinanceRepository
    private lateinit var account:String

    @Before fun setUp()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        repository=FinanceRepository(db);repository.initialize();account=repository.createAccount("Cash","Cash",100000)
    }
    @After fun tearDown() {db.close()}

    private suspend fun expense(description:String="Dinner",amount:Long=1000):String =
        repository.save(EntryDraft(amount=amount,description=description,accountId=account))

    @Test fun tagsAreNormalisedShownEverywhereAndNeverChangeMoney()=runBlocking {
        val id=expense()
        val before=repository.snapshot().accounts.single().balance
        repository.saveTags(id,listOf("#Goa trip"," goa TRIP ","work"))
        assertEquals(listOf("Goa trip","work"),repository.snapshot().records.single {it.id==id}.tags)
        assertEquals(listOf("Goa trip","work"),repository.detail(id).record.tags)
        assertEquals(listOf("Goa trip","work"),repository.observeSnapshots().first().records.single {it.id==id}.tags)
        assertEquals(before,repository.snapshot().accounts.single().balance)
        repository.saveTags(id,emptyList())
        assertTrue(repository.snapshot().records.single {it.id==id}.tags.isEmpty())
    }

    @Test fun editingKeepsTagsAndReversedRecordsCannotBeTagged()=runBlocking<Unit> {
        val id=expense()
        repository.saveTags(id,listOf("Goa trip"))
        val replacement=repository.save(EntryDraft(amount=1500,description="Dinner",accountId=account),replaces=id)
        assertNotEquals(id,replacement)
        assertEquals(listOf("Goa trip"),repository.snapshot().records.single {it.id==replacement}.tags)
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repository.saveTags(id,listOf("Late"))}}
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repository.saveTags("missing",listOf("x"))}}
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repository.saveTags(replacement,(1..11).map {"t$it"})}}
    }

    @Test fun yearlySummaryIncludesTagTotals()=runBlocking {
        val id=expense(amount=2500)
        repository.saveTags(id,listOf("Goa trip"))
        val summary=repository.yearSummary(java.time.LocalDate.now().year)
        assertEquals(mapOf("Goa trip" to 2500L),summary.tags)
    }

    @Test fun backupsKeepTagsAndRejectTagsForUnknownTransactions()=runBlocking {
        val id=expense()
        repository.saveTags(id,listOf("Goa trip","work"))
        val backup=BackupService(db)
        val bytes=backup.exportFixture()
        repository.saveTags(id,emptyList())
        backup.restore(bytes,charArrayOf())
        assertEquals(listOf("Goa trip","work"),repository.snapshot().records.single {it.id==id}.tags)

        val json=org.json.JSONObject(bytes.toString(Charsets.UTF_8))
        json.getJSONArray("localPlans").put(org.json.JSONArray(listOf("tags:missing","tags",org.json.JSONObject().put("transaction","missing").put("tags",org.json.JSONArray(listOf("x"))).toString())))
        var rejected=false
        try {backup.restore(json.toString().toByteArray(),charArrayOf())} catch(_:IllegalArgumentException) {rejected=true}
        assertTrue("Tags for a missing transaction must be rejected",rejected)
        assertEquals(listOf("Goa trip","work"),repository.snapshot().records.single {it.id==id}.tags)
    }
}
