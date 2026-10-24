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
import java.time.*

@RunWith(AndroidJUnit4::class)
class PersonalWalletRepositoryTest {
    private fun time(day:LocalDate)=day.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    @Test fun resetOnceNoCarryoverAndPreserveCurrentMonthSpending()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize()
            repo.configureWallet(200000,today=LocalDate.of(2026,10,11),gpayOpening=500000)
            repo.saveGpaySchedule(true,500000,1,"2026-11",LocalDate.of(2026,10,11))
            val sources=repo.snapshot().walletSources!!
            repo.save(EntryDraft(amount=100000,description="October",accountId=sources.gpayId,date=time(LocalDate.of(2026,10,12))))
            repo.save(EntryDraft(amount=50000,description="November",accountId=sources.gpayId,date=time(LocalDate.of(2026,11,2))))
            repo.refreshMonthlyGpay(LocalDate.of(2026,11,3))
            assertEquals(450000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            repo.refreshMonthlyGpay(LocalDate.of(2026,11,4))
            repo.refreshMonthlyGpay(LocalDate.of(2026,10,30))
            assertEquals(1,repo.snapshot().records.count {it.description.startsWith("GPay monthly reset")})
            assertEquals(200000L,repo.snapshot().accounts.single {it.id==sources.walletId}.balance)
            repo.refreshMonthlyGpay(LocalDate.of(2027,1,2))
            assertEquals(500000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            assertEquals(3,repo.snapshot().records.count {it.description.startsWith("GPay monthly reset")})
            repo.save(EntryDraft(amount=70000,description="Backdated October",accountId=sources.gpayId,date=time(LocalDate.of(2026,10,25))))
            assertEquals(500000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            assertEquals(100000L,repo.snapshot().records.single {it.id=="gpay-reset:${sources.gpayId}:2026-11"}.amount)
            assertEquals(70000L,repo.snapshot().records.single {it.description=="GPay reset recalculation"}.amount)
            val backup=BackupService(db);backup.restore(backup.exportFixture(),charArrayOf())
            repo.refreshMonthlyGpay(LocalDate.of(2027,1,3))
            assertEquals(3,repo.snapshot().records.count {it.description.startsWith("GPay monthly reset")})
        } finally {db.close()}
    }
    @Test fun splitTwoSourcesRepaymentCorrectionAndBackup()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(200000,gpayOpening=500000)
            val sources=repo.snapshot().walletSources!!;val a=repo.createPerson("A");val b=repo.createPerson("B")
            val draft=EntryDraft(amount=90000,description="Dinner",accountId=sources.walletId,participants=listOf("self",a,b),contributions=mapOf("self" to 60000L,a to 30000L,b to 0L),selfPayments=mapOf(sources.walletId to 20000L,sources.gpayId to 40000L))
            repo.save(draft);repo.save(draft)
            assertEquals(180000L,repo.snapshot().accounts.single {it.id==sources.walletId}.balance)
            assertEquals(460000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            assertEquals(30000L,repo.snapshot().records.single {it.id==draft.id}.personalAmount)
            assertEquals(draft.selfPayments,repo.loadDraft(draft.id).selfPayments)
            val loaded=repo.loadDraft(draft.id);repo.save(loaded.copy(description="Corrected"),draft.id)
            assertEquals(460000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            repo.recordRepayment(b,"self",10000,sources.gpayId,"Part repayment")
            assertEquals(470000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            val backup=BackupService(db);backup.restore(backup.exportFixture(),charArrayOf())
            assertEquals(20000L,repo.snapshot().debts.single {it.outstanding>0}.outstanding)
            assertEquals(470000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            try {repo.save(draft.copy(id="bad",selfPayments=mapOf(sources.walletId to 1L)));fail("Invalid source allocation")} catch(_:IllegalArgumentException) {}
            assertTrue(repo.snapshot().records.none {it.id=="bad"})
        } finally {db.close()}
    }
    @Test fun adoptionPreservesBalancesAndLegacyBackup()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();val wallet=repo.createAccount("Cash","Cash",150000);val gpay=repo.createAccount("UPI","Bank",123000)
            repo.configureWallet(0,wallet,gpay)
            assertEquals(123000L,repo.snapshot().accounts.single {it.id==gpay}.balance)
            val backup=BackupService(db);val json=org.json.JSONObject(backup.exportFixture().toString(Charsets.UTF_8));json.put("version",1);json.remove("walletSources")
            backup.restore(json.toString().toByteArray(),charArrayOf())
            assertNull(repo.snapshot().walletSources)
            assertEquals(150000L,repo.snapshot().accounts.single {it.id==wallet}.balance)
        } finally {db.close()}
    }
    @Test fun migrationPreservesVersionOneHistory()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="wallet-migration-${java.util.UUID.randomUUID()}.db"
        val schema=org.json.JSONObject(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("com.smartexpense.data.TrackerDatabase/1.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null).use {sql->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity=entities.getJSONObject(i);sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
                val indices=entity.optJSONArray("indices")?:org.json.JSONArray();for(j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
            }
            sql.execSQL("INSERT INTO accounts VALUES ('cash','Pocket cash','Cash',0)")
            sql.execSQL("INSERT INTO transactions VALUES ('opening','ADJUSTMENT','Opening balance',12345,0,NULL,NULL,1,'',0,NULL)")
            sql.execSQL("INSERT INTO entries VALUES ('entry','opening','cash',12345)")
            sql.version=1
        }
        val db=Room.databaseBuilder(context,TrackerDatabase::class.java,name).addMigrations(AppModule.migration1To2,AppModule.migration2To3,AppModule.migration3To4,AppModule.migration4To5).build()
        try {
            val repo=FinanceRepository(db);repo.initialize()
            assertEquals(12345L,repo.snapshot().accounts.single().balance)
            assertNull(repo.snapshot().walletSources)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun restoreWorkflowAppliesOverdueResetImmediately()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        val store=androidx.lifecycle.ViewModelStore()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(200000,gpayOpening=500000)
            val sources=repo.snapshot().walletSources!!
            repo.save(EntryDraft(amount=100000,description="Old meal",accountId=sources.gpayId))
            val backup=BackupService(db);val json=org.json.JSONObject(backup.exportFixture().toString(Charsets.UTF_8))
            val previous=YearMonth.now().minusMonths(1)
            json.getJSONArray("walletSources").getJSONArray(0).put(3,previous.toString())
            json.put("gpaySchedule",org.json.JSONArray().put(org.json.JSONArray().put(1).put(true).put(500000).put(1).put(previous.toString()).put(YearMonth.now().toString())))
            val tx=json.getJSONArray("transactions");for(i in 0 until tx.length()) tx.getJSONArray(i).put(7,time(previous.atDay(15)))
            val bytes=json.toString().toByteArray()
            lateinit var model:com.smartexpense.ui.AppViewModel
            val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync {model=com.smartexpense.ui.AppViewModel(repo,backup,com.smartexpense.data.BillScanner(androidx.test.core.app.ApplicationProvider.getApplicationContext()),androidx.lifecycle.SavedStateHandle());store.put("test",model);model.prepareRestore(bytes,"Old backup","")}
            model.restorePrepared()
            assertEquals(500000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            assertEquals(YearMonth.now().toString(),repo.snapshot().walletSources!!.lastResetMonth)
        } finally {androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {store.clear()};db.close()}
    }
}


