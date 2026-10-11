package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PersonalWalletTest {
    @Test fun monthlyDailyAllowanceDoesNotIncludeReceivables() {
        assertEquals(500000L, MonthlyGpay.allocation)
        assertEquals(10000L, MonthlyGpay.dailyAllowance(210000, LocalDate.of(2026,10,11)))
        assertEquals(0L, MonthlyGpay.dailyAllowance(-1, LocalDate.of(2026,10,31)))
        assertEquals(5000L, MonthlyGpay.dailyAllowance(5000, LocalDate.of(2028,2,29)))
    }
    @Test fun twoSourcesMustEqualOnlyMyContribution() {
        val d=EntryDraft(amount=90000,description="Dinner",accountId="wallet",participants=listOf("self","a","b"),contributions=mapOf("self" to 60000L,"a" to 30000L,"b" to 0L),selfPayments=mapOf("wallet" to 20000L,"gpay" to 40000L))
        assertEquals(d.selfPayments,expensePayments(d))
        assertThrows(IllegalArgumentException::class.java) {expensePayments(d.copy(selfPayments=mapOf("wallet" to 90000L)))}
        assertThrows(IllegalArgumentException::class.java) {expensePayments(d.copy(selfPayments=mapOf("wallet" to -1L,"gpay" to 60001L)))}
    }
    @Test fun calendarUsesSharesRefundsAndExcludesReversals() {
        val date=LocalDate.of(2026,10,11)
        val time=date.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val records=listOf(Record("one",Kind.EXPENSE,"Meal",90000,30000,null,null,time,"",false),Record("two",Kind.EXPENSE,"Reversed",10000,10000,null,null,time,"",true),Record("three",Kind.REFUND,"Refund",5000,5000,null,null,time,"",false),Record("four",Kind.REPAYMENT,"Received",20000,0,null,null,time,"",false))
        val result=spendingCalendar(records,YearMonth.from(date))
        assertEquals(25000L,result.spending)
        assertEquals(3,result.days.getValue(date).size)
        assertEquals(25000L,result.dailySpending.getValue(date))
        assertTrue(spendingCalendar(records,YearMonth.of(2026,11)).days.isEmpty())
    }
    @Test fun itemChargesAndDiscountsConserveBill() {
        val rule=foodSplitRule("200 = self\n100 = a",listOf("self","a"),3000,6000)
        val shares=Finance.split(27000,listOf("self","a"),rule)
        assertEquals(18000L,shares.getValue("self"))
        assertEquals(9000L,shares.getValue("a"))
    }
}
