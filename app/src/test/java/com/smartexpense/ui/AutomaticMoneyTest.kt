package com.smartexpense.ui

import com.smartexpense.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AutomaticMoneyTest {
    private val people = listOf("self", "a", "b", "c")

    @Test fun incompletePaymentsStillShowSharesAndRemainingWithoutInventingNet() {
        val state = EntryUiState(amount="890", shared=true, participants=people, paid=mapOf("self" to "500"))
        val bill = automaticBill(state).getOrThrow()
        assertEquals(mapOf("self" to 22250L, "a" to 22250L, "b" to 22250L, "c" to 22250L), bill.shares)
        assertEquals(50000L, bill.paidTotal)
        assertEquals(39000L, bill.remaining)
        assertNull(bill.net)
        assertTrue(bill.settlements.isEmpty())
        assertEquals(setOf("a", "b", "c"), bill.missingPayments)
        assertEquals(mapOf("self" to "500"), state.paid)
    }

    @Test fun explicitReconciledContributionsProduceNetAndSettlements() {
        val bill = automaticBill(EntryUiState(amount="100", shared=true, participants=listOf("self","a"), paid=mapOf("self" to "100", "a" to "0"))).getOrThrow()
        assertEquals(mapOf("self" to 5000L, "a" to -5000L), bill.net)
        assertEquals(listOf(SuggestedSettlement("a", "self", 5000L)), bill.settlements)
    }

    @Test fun missingZeroPaymentsRemainProvisionalEvenWhenPaidTotalMatches() {
        val bill = automaticBill(EntryUiState(amount="100", shared=true, participants=listOf("self","a"), paid=mapOf("self" to "100"))).getOrThrow()
        assertEquals(0L, bill.remaining)
        assertNull(bill.net)
    }

    @Test fun aSinglePaiseIsAssignedByParticipantOrder() {
        val bill = automaticBill(EntryUiState(amount="0.01", shared=true, participants=people)).getOrThrow()
        assertEquals(mapOf("self" to 1L, "a" to 0L, "b" to 0L, "c" to 0L), bill.shares)
    }

    @Test fun invalidPercentagesAreRejectedBeforePayments() {
        assertTrue(automaticBill(EntryUiState(amount="100", shared=true, participants=listOf("self","a"), method="Percentage", values=mapOf("self" to "50", "a" to "49"))).isFailure)
    }

    @Test fun overpaymentHasNegativeRemainingAndNoSettlement() {
        val bill = automaticBill(EntryUiState(amount="100", shared=true, participants=listOf("self","a"), paid=mapOf("self" to "110", "a" to "0"))).getOrThrow()
        assertEquals(-1000L, bill.remaining)
        assertNull(bill.net)
    }

    @Test fun paymentOverflowIsRejected() {
        val max = "92233720368547758.07"
        assertTrue(automaticBill(EntryUiState(amount=max, shared=true, participants=listOf("self","a"), paid=mapOf("self" to max,"a" to "0.01"))).isFailure)
    }

    @Test fun explicitPayAllSetsOtherPaymentsToZero() {
        val state=EntryUiState(amount="0.03",shared=true,participants=listOf("self","a"))
        assertEquals(mapOf("self" to "0.03","a" to "0.00"),payAll(state).getOrThrow().paid)
        assertTrue(payAll(state.copy(participants=listOf("a","b"))).isFailure)
    }

    @Test fun explicitEqualPaymentsUseExactPaiseRemainder() {
        assertEquals(mapOf("self" to "0.01","a" to "0.00"),equalPayments(EntryUiState(amount="0.01",shared=true,participants=listOf("self","a"))).getOrThrow().paid)
    }

    @Test fun explicitRemainingShareFillsOnlyTheMissingValue() {
        val state=EntryUiState(amount="100",shared=true,participants=listOf("self","a"),method="Fixed",values=mapOf("self" to "33.33"))
        assertEquals(mapOf("self" to "33.33","a" to "66.67"),fillRemainingShare(state).getOrThrow().values)
        assertEquals(mapOf("self" to "33.33","a" to "66.67"),fillRemainingShare(state.copy(method="Percentage")).getOrThrow().values)
        assertTrue(fillRemainingShare(state.copy(values=emptyMap())).isFailure)
    }

    @Test fun transferProjectsBothAccountBalances() {
        val accounts=listOf(Account("x","Cash","Cash",10000,false),Account("y","Bank","Bank",20000,false))
        val result=projectedBalances(EntryUiState(kind=Kind.TRANSFER,amount="25",accountId="x",destinationId="y"),accounts).getOrThrow()
        assertEquals(listOf(ProjectedBalance("x","Cash",10000,7500),ProjectedBalance("y","Bank",20000,22500)),result)
    }

    @Test fun missingOwnContributionDoesNotInventAccountMovement() {
        assertTrue(projectedBalances(EntryUiState(amount="100",accountId="x",shared=true,participants=listOf("self","a"),paid=mapOf("a" to "100")),listOf(Account("x","Cash","Cash",10000,false))).isFailure)
    }

    @Test fun projectedAccountOverflowIsRejected() {
        assertTrue(projectedBalances(EntryUiState(kind=Kind.INCOME,amount="0.01",accountId="x"),listOf(Account("x","Cash","Cash",Long.MAX_VALUE,false))).isFailure)
    }

    @Test fun plannerSubtractsCostsAndSavingsAndUsesInclusiveDays() {
        val plan=moneyPlan(1000000,200000,100000,listOf(5000,3000,2000),LocalDate.of(2026,10,1),LocalDate.of(2026,10,10))
        assertEquals(700000L,plan.remaining)
        assertEquals(10L,plan.days)
        assertEquals(70000L,plan.dailyAllowance)
        assertEquals(listOf(500000L,300000L,200000L),plan.allocations)
        assertEquals(0L,plan.unallocated)
    }

    @Test fun plannerAllowsUnallocatedPercentAndKeepsPaiseRemainder() {
        val plan=moneyPlan(1,0,0,listOf(5000,3000,2000),LocalDate.of(2026,10,1),LocalDate.of(2026,10,1))
        assertEquals(listOf(1L,0L,0L),plan.allocations)
        assertEquals(1L,plan.dailyAllowance)
        assertEquals(2000L,moneyPlan(10000,0,0,listOf(5000,3000,0),LocalDate.of(2026,10,1),LocalDate.of(2026,10,2)).unallocated)
    }

    @Test fun plannerRejectsOverallocationNegativeInputsAndPastEndDate() {
        val date=LocalDate.of(2026,10,1)
        assertTrue(runCatching {moneyPlan(10000,0,0,listOf(5000,3000,3000),date,date)}.isFailure)
        assertTrue(runCatching {moneyPlan(-1,0,0,listOf(5000,3000,2000),date,date)}.isFailure)
        assertTrue(runCatching {moneyPlan(100,-1,0,listOf(5000,3000,2000),date,date)}.isFailure)
        assertTrue(runCatching {moneyPlan(100,0,-1,listOf(5000,3000,2000),date,date)}.isFailure)
        assertTrue(runCatching {moneyPlan(100,101,0,listOf(5000,3000,2000),date,date)}.isFailure)
        assertTrue(runCatching {moneyPlan(100,0,0,listOf(5000,3000,2000),date,date.minusDays(1))}.isFailure)
    }

    @Test fun savingsAllocationIsNotDeductedAgainFromDailyAllowance() {
        val date=LocalDate.of(2026,10,1)
        val plan=moneyPlan(1000000,200000,100000,listOf(5000,3000,2000),date,date)
        assertEquals(200000L,plan.allocations[2])
        assertEquals(700000L,plan.dailyAllowance)
    }

    @Test fun plannerHandlesMaximumLongWithoutProductOverflow() {
        val date=LocalDate.of(2026,10,1)
        val plan=moneyPlan(Long.MAX_VALUE,0,0,listOf(5000,3000,2000),date,date)
        assertEquals(Long.MAX_VALUE,Finance.total(plan.allocations))
        assertTrue(runCatching {moneyPlan(Long.MAX_VALUE,Long.MAX_VALUE,1,listOf(5000,3000,2000),date,date)}.isFailure)
    }
}
