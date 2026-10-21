package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MonthlyReviewTest {
    @Test fun carryoverIsNotNewFundingSurplus() {
        val review=monthlyReview(listOf(record("opening",Kind.ADJUSTMENT,200000,0,"2026-09-30"),record("food",Kind.EXPENSE,50000,50000,"2026-10-05")),listOf(CashMovement("opening","w",200000),CashMovement("food","w",-50000)),YearMonth.of(2026,10),zone)
        assertEquals(150000L,review.closing)
        assertEquals(-50000L,review.netFunding)
    }
    private val zone=ZoneId.of("Asia/Kolkata")
    private fun time(date:String)=LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun record(id:String,kind:Kind,amount:Long,personal:Long,date:String,reversed:Boolean=false)=Record(id,kind,id,amount,personal,null,null,time(date),"",reversed)
    @Test fun sharedSpendingAndActualCashRemainDistinct() {
        val records=listOf(record("open",Kind.ADJUSTMENT,200000,0,"2026-09-30"),record("meal",Kind.EXPENSE,90000,30000,"2026-10-05"),record("pay",Kind.REPAYMENT,10000,0,"2026-10-06"))
        val movements=listOf(CashMovement("open","wallet",200000),CashMovement("meal","wallet",-60000),CashMovement("pay","wallet",10000))
        val review=monthlyReview(records,movements,YearMonth.of(2026,10),zone)
        assertEquals(200000,review.opening);assertEquals(30000,review.spending);assertEquals(150000,review.closing)
        assertEquals(60000,review.cashPaid);assertEquals(10000,review.repaymentsReceived)
    }
    @Test fun nextMonthResetDoesNotReplaceClosingBalance() {
        val records=listOf(record("open",Kind.ADJUSTMENT,123000,0,"2026-10-01"),record("gpay-reset:a:2026-11",Kind.ADJUSTMENT,377000,0,"2026-11-01"))
        val movements=records.map {CashMovement(it.id,"gpay",it.amount)}
        assertEquals(123000,monthlyReview(records,movements,YearMonth.of(2026,10),zone).closing)
        assertEquals(500000,monthlyReview(records,movements,YearMonth.of(2026,11),zone).closing)
    }
    @Test fun transfersCancelAndReversalsDoNotCount() {
        val records=listOf(record("open",Kind.ADJUSTMENT,10000,0,"2026-09-01"),record("transfer",Kind.TRANSFER,4000,0,"2026-10-01"),record("bad",Kind.EXPENSE,2000,2000,"2026-10-02",true))
        val moves=listOf(CashMovement("open","wallet",10000),CashMovement("transfer","wallet",-4000),CashMovement("transfer","gpay",4000),CashMovement("bad","wallet",-2000))
        val review=monthlyReview(records,moves,YearMonth.of(2026,10),zone)
        assertEquals(10000,review.closing);assertEquals(0,review.spending);assertEquals(0,review.income)
    }
    @Test fun reviewSeparatesSharedCashPaymentsFromPersonalSpending() {
        val records=listOf(record("meal",Kind.EXPENSE,90000,30000,"2026-10-05").copy(personIds=listOf("self","friend")),record("coffee",Kind.EXPENSE,8000,8000,"2026-10-06").copy(personIds=listOf("self")))
        val review=monthlyReview(records,listOf(CashMovement("meal","w",-60000),CashMovement("coffee","w",-8000)),YearMonth.of(2026,10),zone)
        assertEquals(68000L,review.cashPaid)
        assertEquals(60000L,review.sharedCashPaid)
        assertEquals(8000L,review.personalCashPaid)
        assertEquals(38000L,review.spending)
    }
}
