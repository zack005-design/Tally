package com.smartexpense.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.math.BigDecimal
import java.math.RoundingMode

fun recentPersonalExpenses(records:List<Record>):List<Record> = records
    .filter {it.kind==Kind.EXPENSE && !it.reversed && it.personIds==listOf("self")}
    .sortedWith(compareByDescending<Record> {it.date}.thenBy {it.id}).take(3)

data class SpendingComparison(val days:Int,val current:Long,val previous:Long) {
    val percentChange:BigDecimal? get()=if(previous<=0) null else
        BigDecimal.valueOf(current).subtract(BigDecimal.valueOf(previous)).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(previous),0,RoundingMode.HALF_UP)
}

fun spendingComparison(records:List<Record>,today:LocalDate,zone:ZoneId=ZoneId.systemDefault()):SpendingComparison {
    val month=YearMonth.from(today);val previous=month.minusMonths(1)
    val days=minOf(today.dayOfMonth,previous.lengthOfMonth())
    return SpendingComparison(days,Reports.period(records,month.atDay(1),month.atDay(days),zone).spending,
        Reports.period(records,previous.atDay(1),previous.atDay(days),zone).spending)
}

data class BudgetAllowance(val remaining:Long,val daysRemaining:Int,val perDay:Long)
fun budgetAllowance(budget:Long,spent:Long,today:LocalDate):BudgetAllowance {
    require(budget>=0)
    val remaining=Math.subtractExact(budget,spent)
    val days=today.lengthOfMonth()-today.dayOfMonth+1
    return BudgetAllowance(remaining,days,maxOf(0,remaining)/days)
}
