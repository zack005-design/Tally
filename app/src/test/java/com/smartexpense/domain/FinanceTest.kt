package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class FinanceTest {
    private val people = listOf("self", "rahul", "kevin", "arjun")
    @Test fun mandatoryMeal() {
        val shares = Finance.split(89000, people, SplitRule.Equal)
        assertEquals(listOf(22250L,22250L,22250L,22250L), people.map { shares.getValue(it) })
        val net = Finance.net(shares, mapOf("self" to 50000L,"rahul" to 29000L,"kevin" to 10000L,"arjun" to 0L))
        assertEquals(mapOf("self" to 27750L,"rahul" to 6750L,"kevin" to -12250L,"arjun" to -22250L), net)
        val settlements = Finance.settlements(net)
        assertEquals(34500L, settlements.sumOf { it.amount })
        assertEquals(27750L, settlements.filter { it.to == "self" }.sumOf { it.amount })
    }
    @Test fun equalRoundingIsStable() { assertEquals(listOf(3334L,3333L,3333L), Finance.split(10000, listOf("a","b","c"),SplitRule.Equal).values.toList()) }
    @Test fun weightedRounding() { assertEquals(mapOf("a" to 333L,"b" to 667L),Finance.split(1000,listOf("a","b"),SplitRule.Weighted(mapOf("a" to 1L,"b" to 2L)))) }
    @Test fun percentages() { assertEquals(mapOf("a" to 2500L,"b" to 7500L),Finance.split(10000,listOf("a","b"),SplitRule.Percentage(mapOf("a" to 2500L,"b" to 7500L)))) }
    @Test fun fixedShares() { assertEquals(mapOf("a" to 400L,"b" to 600L),Finance.split(1000,listOf("a","b"),SplitRule.Fixed(mapOf("a" to 400L,"b" to 600L)))) }
    @Test fun itemizedShares() { assertEquals(mapOf("a" to 500L,"b" to 100L),Finance.split(600,listOf("a","b"),SplitRule.Itemized(listOf(SplitItem(400,listOf("a")),SplitItem(200,listOf("a","b")))))) }
    @Test(expected=IllegalArgumentException::class) fun mismatchedContributions() { Finance.net(mapOf("a" to 100L),mapOf("a" to 99L)) }
    @Test(expected=IllegalArgumentException::class) fun duplicatePeopleRejected() { Finance.split(100,listOf("a","a"),SplitRule.Equal) }
    @Test(expected=IllegalArgumentException::class) fun invalidPercentages() { Finance.split(100,listOf("a"),SplitRule.Percentage(mapOf("a" to 9900L))) }
    @Test fun largeWeightedSplitDoesNotOverflow() { assertEquals(Long.MAX_VALUE,Finance.split(Long.MAX_VALUE,listOf("a","b"),SplitRule.Weighted(mapOf("a" to Long.MAX_VALUE,"b" to Long.MAX_VALUE))).values.fold(0L,Math::addExact)) }
    @Test fun moneyParsing() { assertEquals(22250L,Money.parse("222.50")); assertEquals("₹222.50",Money.format(22250)) }
    @Test(expected=IllegalArgumentException::class) fun fractionalPaiseRejected() { Money.parse("1.001") }
    @Test fun repaymentsNeverBecomeIncome() {
        val events = listOf(LedgerEvent("opening",Kind.ADJUSTMENT,200000, listOf(Movement("bank",200000))),LedgerEvent("meal",Kind.EXPENSE,22250,listOf(Movement("bank",-50000))),LedgerEvent("repayment",Kind.REPAYMENT,27750,listOf(Movement("bank",27750))))
        assertEquals(177750L,Finance.balances(events).getValue("bank")); assertEquals(22250L,Finance.spending(events)); assertEquals(0L,Finance.income(events))
    }
    @Test fun transfersAreNeutral() { val events=listOf(LedgerEvent("transfer",Kind.TRANSFER,50000,listOf(Movement("bank",-50000),Movement("cash",50000)))); assertEquals(0L,Finance.balances(events).values.sum()); assertEquals(0L,Finance.spending(events)) }
    @Test fun reversalAndRefund() { val events=listOf(LedgerEvent("e",Kind.EXPENSE,10000,listOf(Movement("a",-10000))),LedgerEvent("refund",Kind.REFUND,2000,listOf(Movement("a",2000)))); assertEquals(8000L,Finance.spending(events)); assertEquals(-8000L,Finance.balances(events).getValue("a")); assertEquals(0L,Finance.spending(events.map { it.copy(reversed=true) })) }
    @Test fun repaymentAllocatesOldestFirst() { assertEquals(mapOf("a" to 100L,"b" to 50L),Finance.allocate(150,listOf("a" to 100L,"b" to 100L))) }
    @Test(expected=IllegalArgumentException::class) fun overpaymentRejected() { Finance.allocate(201,listOf("a" to 100L,"b" to 100L)) }
}
