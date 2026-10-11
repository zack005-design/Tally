package com.smartexpense.domain

import java.time.*

data class WalletSources(val walletId:String,val gpayId:String,val lastResetMonth:String,val resetAt:Long?=null,val schedule:GpaySchedule=GpaySchedule())

object MonthlyGpay {
    const val allocation=500000L
    fun dailyAllowance(balance:Long,today:LocalDate):Long = balance.coerceAtLeast(0)/(today.lengthOfMonth()-today.dayOfMonth+1)
}

/** Source amounts describe only the user's actual payment, never the total shared bill. */
fun expensePayments(draft:EntryDraft):Map<String,Long> {
    val paid=draft.contributions["self"]?:0L
    require(paid>=0) {"Your payment cannot be negative"}
    if(draft.selfPayments.isEmpty()) return if(paid==0L) emptyMap() else mapOf(draft.accountId to paid)
    require(draft.selfPayments.keys.all(String::isNotBlank) && draft.selfPayments.values.all {it>=0}) {"Payment sources need non-negative amounts"}
    require(Finance.total(draft.selfPayments.values)==paid) {"Source payments must equal your payment"}
    return draft.selfPayments.filterValues {it>0}
}

data class SpendingCalendar(val spending:Long,val days:Map<LocalDate,List<Record>>,val dailySpending:Map<LocalDate,Long>)
fun spendingCalendar(records:List<Record>,month:YearMonth,zone:ZoneId=ZoneId.systemDefault()):SpendingCalendar {
    val days=records.filterNot {it.reversed}.filter {YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone))==month}
        .groupBy {Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()}
    val spending=days.mapValues {(_,entries)->Finance.total(entries.map {when(it.kind) {Kind.EXPENSE->it.personalAmount;Kind.REFUND->Math.negateExact(it.personalAmount);else->0L}})}
    return SpendingCalendar(Finance.total(spending.values),days,spending)
}

/** Shared charges and discounts follow each person's item subtotal, with exact paise rounding. */
fun foodSplitRule(lines:String,people:List<String>,charges:Long=0,discount:Long=0):SplitRule {
    require(charges>=0 && discount>=0) {"Charges and discount cannot be negative"}
    val items=lines.lines().filter(String::isNotBlank).map {line->
        val parts=line.split('=');require(parts.size==2) {"Add item amounts and people"}
        SplitItem(Money.parse(parts[0]),parts[1].split('+').map(String::trim))
    }
    val subtotal=Finance.total(items.map {it.amount})
    val shares=Finance.split(subtotal,people,SplitRule.Itemized(items))
    val total=Math.subtractExact(Math.addExact(subtotal,charges),discount)
    require(total>0) {"Discount must leave a positive bill"}
    if(charges==0L && discount==0L) return SplitRule.Itemized(items)
    val chargeShares=if(charges==0L) people.associateWith {0L} else Finance.split(charges,people,SplitRule.Weighted(shares))
    val gross=shares.mapValues {(id,amount)->Math.addExact(amount,chargeShares.getValue(id))}
    val discounts=if(discount==0L) people.associateWith {0L} else Finance.split(discount,people,SplitRule.Weighted(gross))
    return SplitRule.Fixed(gross.mapValues {(id,amount)->Math.subtractExact(amount,discounts.getValue(id))})
}
