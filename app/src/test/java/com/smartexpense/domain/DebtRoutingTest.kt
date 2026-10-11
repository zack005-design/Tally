package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test
class DebtRoutingTest {
    private val debts=listOf(Debt("one","e1","rahul","self",10000,10000,0),Debt("two","e2","self","kevin",10000,10000,1))
    @Test fun indirectPaymentAllocatesBothOriginalDebts() {assertEquals(mapOf("one" to 10000L,"two" to 10000L),DebtRouting.allocate(10000,"rahul","kevin",debts));assertEquals(10000L,DebtRouting.capacity("rahul","kevin",debts))}
    @Test fun suggestionsCanActuallyBeRecorded() {val suggestions=DebtRouting.suggestions(debts);assertEquals(listOf(SuggestedSettlement("rahul","kevin",10000)),suggestions)}
    @Test fun disconnectedGroupsDoNotSuggestUnrecordablePayment() {val all=debts+listOf(Debt("three","e3","sam","anna",5000,5000,0));DebtRouting.suggestions(all).forEach {assertTrue(DebtRouting.capacity(it.from,it.to,all)>=it.amount)}}
    @Test(expected=IllegalArgumentException::class) fun overpaymentRejected() {DebtRouting.allocate(10001,"rahul","kevin",debts)}
}
