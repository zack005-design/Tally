package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class ChatHistoryImportTest {
    private val account = Account("cash", "Wallet", "Cash", 100000, false)
    private val snapshot = Snapshot(accounts = listOf(account))
    private fun row(id: String = "1") = ChatHistoryEntry(id, "EXPENSE", "2026-10-20", "Lunch", "250.00", "Cash")
    private fun history(vararg rows: ChatHistoryEntry) = ChatHistory("chat-1", "October 2026; available messages only", rows.toList())
    private fun preview(vararg rows: ChatHistoryEntry) = ChatHistoryImport.preview(history(*rows), snapshot, mapOf("Cash" to "cash"), ZoneId.of("Asia/Kolkata"))

    @Test fun exactExpenseBuildsIndividualDraftAndSignedCashEffect() {
        val result = preview(row())
        assertTrue(result.rows.single().issues.isEmpty())
        assertEquals(25000L, result.rows.single().draft!!.amount)
        assertEquals(mapOf("self" to 25000L), result.rows.single().draft!!.contributions)
        assertEquals(-25000L, result.cashChanges(setOf("1"))["cash"])
    }
    @Test fun fractionalPaiseInvalidDateAndOverflowRemainUnselectable() {
        listOf(row().copy(amount = "1.001"), row().copy(date = "2026-02-29"), row().copy(amount = "92233720368547758.08")).forEach {
            assertNull(preview(it).rows.single().draft)
            assertTrue(preview(it).rows.single().issues.isNotEmpty())
        }
    }
    @Test fun missingFactsAndUnsupportedKindsAreVisible() {
        listOf(row().copy(date = null), row().copy(account = null), row().copy(shared = true), row().copy(kind = "REPAYMENT"), row().copy(unresolved = listOf("amount uncertain"))).forEach {
            assertEquals(1, preview(it).rows.size)
            assertNull(preview(it).rows.single().draft)
        }
    }
    @Test fun unknownCategoryNeedsExplicitMappingOrUncategorisedChoice() {
        val h = history(row().copy(category = "Food"))
        assertNull(ChatHistoryImport.preview(h, snapshot, mapOf("Cash" to "cash")).rows.single().draft)
        val mapped = ChatHistoryImport.preview(h, snapshot, mapOf("Cash" to "cash"), categoryMapping = mapOf("Food" to ""))
        assertNull(mapped.rows.single().draft!!.categoryId)
    }
    @Test fun archivedAccountIsRejected() {
        val result = ChatHistoryImport.preview(history(row()), snapshot.copy(accounts = listOf(account.copy(archived = true))), mapOf("Cash" to "cash"))
        assertNull(result.rows.single().draft)
    }
    @Test fun duplicateSourceIdsRejectWholeHistory() {
        assertThrows(IllegalArgumentException::class.java) { preview(row(), row()) }
    }
    @Test fun legitimateRepeatedPurchasesRemainSeparateButWarn() {
        val result = preview(row("1"), row("2"))
        assertEquals(2, result.rows.count { it.draft != null })
        assertTrue(result.rows.all { it.possibleDuplicate })
        assertEquals(-50000L, result.cashChanges(setOf("1", "2"))["cash"])
    }
    @Test fun stableIdsDoNotDependOnTimezoneAndSourceBoundariesCannotCollide() {
        val h = history(row())
        val first = ChatHistoryImport.preview(h, snapshot, mapOf("Cash" to "cash"), ZoneId.of("Asia/Kolkata")).rows.single().draft!!
        val second = ChatHistoryImport.preview(h, snapshot, mapOf("Cash" to "cash"), ZoneId.of("UTC")).rows.single().draft!!
        assertEquals(first.id, second.id)
        assertNotEquals(ChatHistoryImport.transactionId("a:b", "c"), ChatHistoryImport.transactionId("a", "b:c"))
    }
    @Test fun unknownSelectionAndSelectedTotalOverflowReject() {
        val result = preview(row())
        assertThrows(IllegalArgumentException::class.java) { result.cashChanges(setOf("absent")) }
        val large = preview(row("1").copy(amount = "92233720368547758.07"), row("2"))
        assertThrows(ArithmeticException::class.java) { large.cashChanges(setOf("1", "2")) }
    }
    @Test fun tooManyRowsAndBlankCoverageReject() {
        assertThrows(IllegalArgumentException::class.java) { ChatHistoryImport.validate(history(row()).copy(coverage = "")) }
        assertThrows(IllegalArgumentException::class.java) { ChatHistoryImport.validate(history().copy(entries = List(10001) { row(it.toString()) })) }
    }
    @Test fun reviewTokenExpiresAcrossLocalMidnightButRemainsStableWithinDay() {
        val zone = ZoneId.of("Asia/Kolkata")
        fun clock(instant: String) = java.time.Clock.fixed(java.time.Instant.parse(instant), zone)
        val before = ChatHistoryImport.reviewToken(listOf("same ledger"), clock("2026-10-31T18:29:59Z"))
        val sameDay = ChatHistoryImport.reviewToken(listOf("same ledger"), clock("2026-10-31T12:00:00Z"))
        val after = ChatHistoryImport.reviewToken(listOf("same ledger"), clock("2026-10-31T18:30:01Z"))
        assertEquals(before, sameDay)
        assertNotEquals(before, after)
    }
}
