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
class ChatHistoryRepositoryTest {
    private fun history(amount: String = "250.00") = ChatHistory("test-chat", "2026-10-20, synthetic", listOf(ChatHistoryEntry("one", "EXPENSE", "2026-10-20", "Lunch", amount, "Cash")))
    @Test fun previewRollsBackAndRetrySkipsWithoutChangingExistingRecords() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackerDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db); repo.initialize()
            val cash = repo.createAccount("Cash", "Cash", 100000)
            val before = repo.snapshot()
            val review = repo.previewChatHistory(history(), setOf("one"), mapOf("Cash" to cash), emptyMap())
            assertEquals(before, repo.snapshot())
            assertEquals(75000L, review.afterBalances[cash])
            val imported = repo.importChatHistory(history(), setOf("one"), mapOf("Cash" to cash), emptyMap(), review.token)
            assertEquals(1, imported.imported)
            val retry = repo.previewChatHistory(history(), setOf("one"), mapOf("Cash" to cash), emptyMap())
            assertEquals(1, retry.skipped)
            assertEquals(0, repo.importChatHistory(history(), setOf("one"), mapOf("Cash" to cash), emptyMap(), retry.token).imported)
            assertEquals(75000L, repo.snapshot().accounts.single {it.id == cash}.balance)
            assertTrue(repo.snapshot().records.containsAll(before.records))
        } finally { db.close() }
    }
    @Test fun changedSameIdAndStaleReviewRejectWithoutWrites() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackerDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db); repo.initialize(); val cash = repo.createAccount("Cash", "Cash", 100000)
            val review = repo.previewChatHistory(history(), setOf("one"), mapOf("Cash" to cash), emptyMap())
            repo.createAccount("Another", "Cash", 100)
            val before = repo.snapshot()
            try { repo.importChatHistory(history(), setOf("one"), mapOf("Cash" to cash), emptyMap(), review.token); fail("stale review") } catch (_: IllegalArgumentException) {}
            assertEquals(before, repo.snapshot())
            val fresh = repo.previewChatHistory(history(), setOf("one"), mapOf("Cash" to cash), emptyMap())
            repo.importChatHistory(history(), setOf("one"), mapOf("Cash" to cash), emptyMap(), fresh.token)
            val saved = repo.snapshot()
            try { repo.previewChatHistory(history("300.00"), setOf("one"), mapOf("Cash" to cash), emptyMap()); fail("changed ID") } catch (_: IllegalArgumentException) {}
            assertEquals(saved, repo.snapshot())
        } finally { db.close() }
    }
    @Test fun invalidLaterRowRollsBackEntireBatch() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackerDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db); repo.initialize(); val cash = repo.createAccount("Cash", "Cash", 100000)
            val h = history().copy(entries = history().entries + history().entries.single().copy(sourceId = "two", amount = "1.001"))
            val before = repo.snapshot()
            try { repo.previewChatHistory(h, setOf("one", "two"), mapOf("Cash" to cash), emptyMap()); fail("invalid row") } catch (_: IllegalArgumentException) {}
            assertEquals(before, repo.snapshot())
        } finally { db.close() }
    }
    @Test fun conflictAfterFirstWriteRollsBackAndArchivedAccountRejects() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackerDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db); repo.initialize(); val cash = repo.createAccount("Cash", "Cash", 100000)
            val original = history().copy(entries = listOf(history().entries.single().copy(sourceId = "two")))
            val review = repo.previewChatHistory(original, setOf("two"), mapOf("Cash" to cash), emptyMap())
            repo.importChatHistory(original, setOf("two"), mapOf("Cash" to cash), emptyMap(), review.token)
            val conflicting = history().copy(entries = history().entries + original.entries.single().copy(amount = "300.00"))
            val before = repo.snapshot()
            try { repo.previewChatHistory(conflicting, setOf("one", "two"), mapOf("Cash" to cash), emptyMap()); fail("conflict") } catch (_: IllegalArgumentException) {}
            assertEquals(before, repo.snapshot())
            repo.updateAccount(before.accounts.single {it.id == cash}.copy(archived = true))
            try { repo.previewChatHistory(history(), setOf("one"), mapOf("Cash" to cash), emptyMap()); fail("archived") } catch (_: IllegalArgumentException) {}
            assertFalse(repo.snapshot().records.any {it.id == ChatHistoryImport.transactionId("test-chat", "one")})
        } finally { db.close() }
    }
    @Test fun historicalGpayResetPreviewMatchesCommitWithoutLeakingPriorMonthCash() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrackerDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db); repo.initialize()
            repo.configureWallet(100000, today = java.time.LocalDate.of(2026, 10, 1), gpayOpening = 500000)
            val gpay = repo.snapshot().walletSources!!.gpayId
            repo.saveGpaySchedule(true, 500000, 1, "2026-11", java.time.LocalDate.of(2026, 10, 1))
            repo.refreshMonthlyGpay(java.time.LocalDate.of(2026, 11, 2))
            val before = repo.snapshot()
            val review = repo.previewChatHistory(history(), setOf("one"), mapOf("Cash" to gpay), emptyMap())
            assertEquals(500000L, review.afterBalances[gpay])
            assertEquals(before, repo.snapshot())
            repo.importChatHistory(history(), setOf("one"), mapOf("Cash" to gpay), emptyMap(), review.token)
            assertEquals(review.afterBalances, repo.snapshot().accounts.associate {it.id to it.balance})
            assertTrue(repo.snapshot().records.any {it.description == "GPay reset recalculation"})
        } finally { db.close() }
    }
    @Test fun jsonContractRejectsNumbersVersionsLimitsAndPreservesUnknownDetails() {
        val json = """{"format":"tally-chat-history","formatVersion":1,"currency":"INR","historyId":"chat","coverage":"synthetic","entries":[{"sourceId":"one","kind":"EXPENSE","date":"2026-10-20","description":"Lunch","amount":"250.00","account":"Cash"}]}"""
        assertEquals("250.00", ChatHistoryJson.parse(json).entries.single().amount)
        listOf(json.replace("\"250.00\"", "250.00"), json.replace("\"formatVersion\":1", "\"formatVersion\":2"), json + "{}", json.replace("\"currency\":\"INR\"", "\"currency\":\"USD\""), " ".repeat(ChatHistoryImport.MAX_BYTES + 1)).forEach {
            assertThrows(IllegalArgumentException::class.java) { ChatHistoryJson.parse(it) }
        }
    }
}
