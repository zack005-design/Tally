package com.smartexpense.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class SpendingConclusionsTest {
    private val zone=ZoneOffset.UTC
    private fun r(id:String,date:String,kind:Kind,personal:Long,reversed:Boolean=false)=Record(id,kind,id,99999,personal,null,null,LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(),"",reversed)
    @Test fun shorterMonthUsesOnlyCommonElapsedDaysAndPersonalShares() {
        val rows=listOf(r("previous","2024-02-29",Kind.EXPENSE,500),r("current","2024-03-29",Kind.EXPENSE,800),r("late","2024-03-30",Kind.EXPENSE,900),r("refund","2024-03-29",Kind.REFUND,100),r("transfer","2024-03-01",Kind.TRANSFER,10000),r("reversed","2024-03-01",Kind.EXPENSE,9000,true))
        val result=spendingComparison(rows,LocalDate.parse("2024-03-31"),zone)
        assertEquals(29,result.days);assertEquals(700L,result.current);assertEquals(500L,result.previous)
    }
    @Test fun zeroBaselineHasNoPercentageAndBudgetUsesToday() {
        val today=LocalDate.parse("2026-10-19")
        assertNull(spendingComparison(emptyList(),today,zone).percentChange)
        val allowance=budgetAllowance(13000,0,today)
        assertEquals(13,allowance.daysRemaining);assertEquals(1000L,allowance.perDay)
        val over=budgetAllowance(100,200,today)
        assertEquals(-100L,over.remaining);assertEquals(0L,over.perDay)
        assertEquals(13000L,budgetAllowance(13000,0,LocalDate.parse("2026-10-31")).perDay)
    }
}
