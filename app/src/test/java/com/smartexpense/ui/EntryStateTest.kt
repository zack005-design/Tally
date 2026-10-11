package com.smartexpense.ui
import com.smartexpense.domain.*
import org.junit.Assert.*
import org.junit.Test
class EntryStateTest {
    @Test fun repeatGroupKeepsAllActualPaymentsUnknown() {
        val state=EntryUiState().withGroup(Group("meal","Meals",listOf("self","friend")))
        assertTrue(state.paid.values.all(String::isBlank))
        assertTrue(state.preview().isFailure)
    }
    @Test fun correctionDoesNotDeductOriginalPaymentTwice() {
        val state=EntryUiState(amount="600",accountId="wallet",originalDate=0,originalMovements=mapOf("wallet" to -20000L,"gpay" to -40000L),secondAccountId="gpay",secondPayment="400")
        val accounts=listOf(Account("wallet","Wallet","Cash",180000,false),Account("gpay","GPay","Bank",460000,false))
        val result=projectedBalances(state,accounts).getOrThrow()
        assertEquals(180000L,result.first {it.accountId=="wallet"}.after)
        assertEquals(460000L,result.first {it.accountId=="gpay"}.after)
    }
    @Test fun priorMonthCorrectionDoesNotAffectResetBalance() {
        val before=java.time.LocalDateTime.of(2026,9,15,12,0)
        val reset=java.time.LocalDate.of(2026,10,1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state=EntryUiState(amount="600",accountId="gpay",date=before.toString(),originalDate=before.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),originalMovements=mapOf("gpay" to -50000L))
        assertEquals(500000L,projectedBalances(state,listOf(Account("gpay","GPay","Bank",500000,false)),WalletSources("wallet","gpay","2026-10",reset)).getOrThrow().single().after)
    }
    @Test fun changingToIncomeDoesNotValidateHiddenSplitFields() {val draft=EntryUiState(kind=Kind.INCOME,amount="100",description="Salary",accountId="a",shared=true,participants=listOf("self"),method="",paid=emptyMap()).draft();assertEquals(Kind.INCOME,draft.kind);assertEquals(10000L,draft.amount)}
    @Test fun externalOnlyExpensePreservesZeroPersonalSpending() {val d=EntryUiState(amount="100",description="Friend paid",accountId="a",shared=true,participants=listOf("friend"),paid=mapOf("friend" to "100")).draft();val shares=Finance.split(d.amount,d.participants,d.splitRule);assertEquals(0L,shares["self"]?:0);assertEquals(0L,d.contributions["self"]?:0)}
    @Test fun nonRefundDraftDoesNotRetainRefundLink() {assertNull(EntryUiState(kind=Kind.INCOME,amount="100",description="Income",accountId="a",refundOf="old-expense").draft().refundOf)}
    @Test fun reusingSharedRecordDoesNotInventContributionsOrPersonalCashMovement() {
        val record=Record("shared",Kind.EXPENSE,"Dinner",10000,5000,null,null,0,"",false,listOf("a"),listOf("self","r"))
        val form=EntryUiState().reuse(record)
        assertTrue(form.shared)
        assertEquals(listOf("self","r"),form.participants)
        assertTrue(form.paid.isEmpty())
        assertEquals("",form.method)
        assertEquals("",form.accountId)
        assertTrue(form.preview().isFailure)
    }
}
