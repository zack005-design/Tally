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
