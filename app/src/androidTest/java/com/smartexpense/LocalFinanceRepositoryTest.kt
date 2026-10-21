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
class LocalFinanceRepositoryTest {
    private fun db()=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
    @Test fun billSpecificDefaultMethodsRoundTripWithoutInventingAmounts()=runBlocking {
        val database=db()
        try {
            val repo=FinanceRepository(database);repo.initialize();repo.configureWallet(500000,gpayOpening=0)
            val friend=repo.createPerson("Rahul");val group=repo.saveGroup("Flatmates",listOf("self",friend))
            for(method in listOf("Fixed","Itemized")) {
                val value=GroupDefault(group,method,emptyMap())
                repo.saveGroupDefault(value)
                assertEquals(value,repo.snapshot().groupDefaults.single())
                assertTrue(runCatching {value.rule(listOf("self",friend))}.isFailure)
                val backup=BackupService(database);val bytes=backup.export(charArrayOf());repo.deletePlan("group:$group");backup.restore(bytes,charArrayOf())
                assertEquals(value,repo.snapshot().groupDefaults.single())
            }
        } finally {database.close()}
    }
    @Test fun planningBackupAndExplicitRecurringSaveNeverMoveGoalCash()=runBlocking {
        val db=db()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(500000,gpayOpening=0)
            val account=repo.snapshot().walletSources!!.walletId
            val today=LocalDate.now()
            val template=RecurringTemplate("rent",Kind.EXPENSE,10000,"Rent",account,today.toString(),"Monthly")
            repo.saveTemplate(template);repo.saveGoal("Emergency",200000,"emergency");repo.allocateGoal("emergency",50000)
            assertEquals(500000L,repo.snapshot().accounts.single {it.id==account}.balance)
            assertTrue(repo.snapshot().records.none {it.kind==Kind.EXPENSE})
            val draft=EntryDraft(id=template.entryId(today),amount=template.amount,description=template.description,accountId=account,date=today.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            repo.save(draft);repo.save(draft)
            assertEquals(1,repo.snapshot().records.count {it.kind==Kind.EXPENSE})
            assertEquals(490000L,repo.snapshot().accounts.single {it.id==account}.balance)
            val before=repo.snapshot();val backup=BackupService(db);val bytes=backup.export(charArrayOf())
            repo.deletePlan("goal:emergency");repo.deletePlan("template:rent");backup.restore(bytes,charArrayOf())
            assertEquals(before.goals,repo.snapshot().goals);assertEquals(before.templates,repo.snapshot().templates)
            val bad=org.json.JSONObject(bytes.toString(Charsets.UTF_8));val rows=bad.getJSONArray("localPlans")
            for(i in 0 until rows.length()) {val row=rows.getJSONArray(i);if(row.getString(1)=="template") {val payload=org.json.JSONObject(row.getString(2)).put("amount",1.5);row.put(2,payload.toString())}}
            assertTrue(runCatching {backup.restore(bad.toString().toByteArray(),charArrayOf())}.isFailure)
            assertEquals(before.accounts,repo.snapshot().accounts)
        } finally {db.close()}
    }
    @Test fun manualGpayOffByDefaultAndNewDataRoundTrips()=runBlocking {
        val db=db()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(200000,gpayOpening=123000)
            val sources=repo.snapshot().walletSources!!
            repo.refreshMonthlyGpay(LocalDate.now().plusMonths(8))
            assertEquals(123000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            assertFalse(repo.snapshot().walletSources!!.schedule.enabled)
            repo.saveDiary("2026-10-11","Lunch with friends\nKeep receipts")
            repo.saveProfile("Aniru","star");repo.finishWelcome()
            val before=repo.snapshot();assertEquals(323000L,Finance.total(before.accounts.map {it.balance}))
            val backup=BackupService(db);val bytes=backup.export(charArrayOf())
            repo.saveDiary("2026-10-11","");repo.saveProfile("Changed","leaf")
            backup.restore(bytes,charArrayOf())
            val restored=repo.snapshot()
            assertEquals(before.diary,restored.diary);assertEquals(before.localSettings,restored.localSettings)
            assertEquals(before.accounts,restored.accounts)
            val legacy=org.json.JSONObject(bytes.toString(Charsets.UTF_8)).put("version",2)
            listOf("gpaySchedule","resetTargets","diary","localSettings").forEach {legacy.remove(it)}
            backup.restore(legacy.toString().toByteArray(),charArrayOf())
            assertTrue(repo.snapshot().diary.isEmpty());assertFalse(repo.snapshot().walletSources!!.schedule.enabled)
            assertEquals(123000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
        } finally {db.close()}
    }
    @Test fun scheduleTargetSurvivesChangesAndDisabledPeriods()=runBlocking {
        val db=db()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(0,gpayOpening=30000)
            val sources=repo.snapshot().walletSources!!;val now=LocalDate.now();val first=YearMonth.from(now).plusMonths(1)
            repo.saveGpaySchedule(true,120000,1,first.toString(),now)
            repo.refreshMonthlyGpay(first.atDay(2));repo.refreshMonthlyGpay(first.atDay(3))
            assertEquals(120000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            assertEquals(1,repo.snapshot().records.count {it.id.startsWith("gpay-reset:")})
            repo.saveGpaySchedule(false,120000,1,first.toString(),first.atDay(3))
            repo.refreshMonthlyGpay(first.plusMonths(5).atDay(3))
            assertEquals(1,repo.snapshot().records.count {it.id.startsWith("gpay-reset:")})
            repo.saveGpaySchedule(true,250000,28,first.toString(),first.atDay(3))
            repo.refreshMonthlyGpay(first.atEndOfMonth())
            assertEquals(1,repo.snapshot().records.count {it.id.startsWith("gpay-reset:")})
            repo.save(EntryDraft(amount=1000,description="Before first reset",accountId=sources.gpayId,date=now.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
            assertEquals(120000L,repo.snapshot().accounts.single {it.id==sources.gpayId}.balance)
            val backup=BackupService(db);backup.restore(backup.export(charArrayOf()),charArrayOf())
            assertEquals(120000L,db.dao().resetTargets().first().target)
        } finally {db.close()}
    }
    @Test fun monthlyReviewUsesWholeLedgerAndDiaryNeverDuplicatesExpenses()=runBlocking {
        val db=db()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(500000,gpayOpening=100000)
            val wallet=repo.snapshot().walletSources!!.walletId
            val date=LocalDate.now();val timestamp=date.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            repeat(7) {repo.save(EntryDraft(amount=1000,description="Meal $it",accountId=wallet,date=timestamp))}
            repo.saveDiary(date.toString(),"Seven meals");repo.saveDiary(date.toString(),"Changed note")
            assertEquals(5,repo.snapshot(5).records.size)
            val review=repo.monthlyReview(YearMonth.from(date).toString())
            assertEquals(7000L,review.spending);assertEquals(593000L,review.closing)
            repo.saveDiary(date.toString(),"")
            assertTrue(repo.snapshot().diary.isEmpty());assertEquals(7000L,repo.monthlyReview(YearMonth.from(date).toString()).spending)
        } finally {db.close()}
    }
}


