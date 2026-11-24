package com.smartexpense.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class NetWorthTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 24)
    private fun at(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun netWorthAddsReceivablesAndSubtractsDebts() {
        val accounts = listOf(Account("wallet", "Wallet", "Cash", 50_000, false), Account("bank", "Bank", "Bank", 1_000_000, false),
            Account("old", "Old card", "Other", 0, true))
        val debts = listOf(Debt("d1", "t1", "rahul", "self", 30_000, 20_000, 0), Debt("d2", "t2", "self", "asha", 15_000, 15_000, 0))
        val worth = NetWorths.build(accounts, emptyList(), debts, today, zone = zone)
        assertEquals(1_050_000L, worth.accounts)
        assertEquals(20_000L, worth.owedToYou)
        assertEquals(15_000L, worth.youOwe)
        assertEquals(1_055_000L, worth.net)
        assertEquals(listOf("Bank", "Wallet"), worth.byAccount.map { it.first.name })
    }

    @Test fun trendRebuildsMonthEndTotalsFromDatedEntriesAndIgnoresReversals() {
        val movements = listOf(
            DatedMovement("bank", 100_000, at(LocalDate.of(2026, 6, 15)), false),
            DatedMovement("bank", -20_000, at(LocalDate.of(2026, 9, 30)), false),
            DatedMovement("wallet", 5_000, at(LocalDate.of(2026, 10, 1)), false),
            DatedMovement("wallet", 999_999, at(LocalDate.of(2026, 10, 2)), true),
            DatedMovement("bank", -1_000, at(LocalDate.of(2026, 11, 24)), false),
            DatedMovement("bank", 7_000, at(LocalDate.of(2026, 11, 25)), false))
        val trend = NetWorths.build(emptyList(), movements, emptyList(), today, zone = zone).trend
        assertEquals((6L..11L).map { YearMonth.of(2026, it.toInt()) }, trend.map { it.month })
        assertEquals(listOf(100_000L, 100_000L, 100_000L, 80_000L, 85_000L, 84_000L), trend.map { it.accounts })
    }
}
