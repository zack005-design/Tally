package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FirstBalanceSetupRepositoryTest {
    @Test fun setupPersistsAndBothTopupsLeaveSpendingUnchanged()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize()
            repo.configureWallet(25050,gpayOpening=10000)
            val initial=repo.snapshot();val sources=initial.walletSources!!
            assertTrue(initial.localSettings.welcomeDone)
            assertEquals("home",launchDestination(initial))
            repo.initialize()
            assertEquals(2,repo.snapshot().accounts.size)
            repo.addWalletFunds(sources.walletId,5000)
            repo.addWalletFunds(sources.gpayId,2000)
            val s=repo.snapshot()
            assertEquals(30050L,s.accounts.single {it.id==sources.walletId}.balance)
            assertEquals(12000L,s.accounts.single {it.id==sources.gpayId}.balance)
            assertEquals(0L,s.dashboard.today)
            assertEquals(0L,s.dashboard.month.income)
            assertTrue(s.records.filter {it.description.contains("top-up")}.all {it.kind==Kind.ADJUSTMENT})
            try {repo.configureWallet(100);fail("Repeated setup accepted")} catch(_:IllegalArgumentException) {}
            assertEquals(2,repo.snapshot().accounts.size)
        } finally {db.close()}
    }
    @Test fun historicalTopupKeepsItsDateAndNoteAndRecalculatesGpayReset()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(120000,gpayOpening=500000)
            val sources=repo.snapshot().walletSources!!
            repo.saveGpaySchedule(true,500000,1,"2026-11")
            repo.refreshMonthlyGpay(java.time.LocalDate.of(2026,11,2))
            val date=java.time.LocalDate.of(2026,10,19).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertEquals(500000L,repo.previewWalletFunds(sources.gpayId,5000,date))
            repo.addWalletFunds(sources.gpayId,5000,date=date,note="Monthly allowance")
            val result=repo.snapshot();val topup=result.records.single {it.description.contains("cash top-up")}
            assertEquals(date,topup.date);assertTrue(topup.notes.contains("Monthly allowance"))
            assertEquals(500000L,result.accounts.single {it.id==sources.gpayId}.balance)
            assertEquals(0L,result.dashboard.month.spending)
            val count=result.records.size
            assertTrue(runCatching {repo.addWalletFunds(sources.walletId,100,date=date,note="x".repeat(501))}.isFailure)
            assertEquals(count,repo.snapshot().records.size)
        } finally {db.close()}
    }
    @Test fun monthlyCashRowsUseStoredParticipantsAndActualPayments()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(100000,gpayOpening=0)
            val wallet=repo.snapshot().walletSources!!.walletId
            val friend=repo.createPerson("Friend")
            repo.save(EntryDraft(amount=90000,description="Shared meal",accountId=wallet,participants=listOf("self",friend),contributions=mapOf("self" to 60000L,friend to 30000L)))
            repo.save(EntryDraft(amount=8000,description="Coffee",accountId=wallet))
            val review=repo.monthlyReview(java.time.YearMonth.now().toString())
            assertEquals(60000L,review.sharedCashPaid);assertEquals(8000L,review.personalCashPaid)
            assertEquals(68000L,review.cashPaid);assertEquals(53000L,review.spending)
        } finally {db.close()}
    }
}
