package com.smartexpense.domain

import java.time.*

data class CashMovement(val transactionId:String,val accountId:String,val amount:Long)
data class MonthlyReview(val month:String,val openingByAccount:Map<String,Long>,val closingByAccount:Map<String,Long>,val income:Long,val funding:Long,val corrections:Long,val resetChanges:Long,val spending:Long,val cashPaid:Long,val refunds:Long,val repaymentsReceived:Long,val repaymentsPaid:Long,val hasHistory:Boolean) {
    val opening:Long get()=Finance.total(openingByAccount.values)
    val closing:Long get()=Finance.total(closingByAccount.values)
    val available:Long get()=Finance.total(listOf(opening,income,funding,resetChanges.coerceAtLeast(0)))
    val surplus:Long get()=Math.subtractExact(available,spending)
}

/** Reconstruct cash from the whole ledger. Month end excludes the next month's first instant. */
fun monthlyReview(records:List<Record>,movements:List<CashMovement>,month:YearMonth,zone:ZoneId=ZoneId.systemDefault()):MonthlyReview {
    val from=month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val until=month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val active=records.filterNot {it.reversed}.associateBy {it.id}
    val valid=movements.filter {it.transactionId in active}
    fun balances(boundary:Long)=valid.filter {active.getValue(it.transactionId).date<boundary}.groupBy {it.accountId}.mapValues {(_,entries)->Finance.total(entries.map {it.amount})}
    val entries=valid.filter {active.getValue(it.transactionId).date in from until until}
    fun cash(kind:Kind,predicate:(CashMovement)->Boolean={true})=Finance.total(entries.filter {active.getValue(it.transactionId).kind==kind && predicate(it)}.map {it.amount})
    val selected=active.values.filter {it.date in from until until}
    val adjustments=entries.filter {active.getValue(it.transactionId).kind==Kind.ADJUSTMENT}
    val normal=adjustments.filterNot {it.transactionId.startsWith("gpay-reset")}
    val resets=adjustments.filter {it.transactionId.startsWith("gpay-reset")}
    return MonthlyReview(month.toString(),balances(from),balances(until),cash(Kind.INCOME),Finance.total(normal.filter {it.amount>0}.map {it.amount}),Finance.total(normal.filter {it.amount<0}.map {it.amount}),Finance.total(resets.map {it.amount}),Finance.total(selected.map {when(it.kind) {Kind.EXPENSE->it.personalAmount;Kind.REFUND->Math.negateExact(it.personalAmount);else->0L}}),Math.negateExact(cash(Kind.EXPENSE)),cash(Kind.REFUND),cash(Kind.REPAYMENT) {it.amount>0},Math.negateExact(cash(Kind.REPAYMENT) {it.amount<0}),active.values.any {it.date<until})
}
