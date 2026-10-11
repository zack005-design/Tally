package com.smartexpense.domain
import java.time.LocalDate
import java.time.ZoneId
data class PeriodReport(val spending:Long,val income:Long,val categories:Map<String?,Long>,val daily:Map<LocalDate,Long>)
object Reports {
    fun period(records:List<Record>,start:LocalDate,end:LocalDate,zone:ZoneId=ZoneId.systemDefault()):PeriodReport {
        val from=start.atStartOfDay(zone).toInstant().toEpochMilli();val until=end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val filtered=records.filter { !it.reversed && it.date>=from && it.date<until }
        fun spending(record:Record)=when(record.kind) { Kind.EXPENSE->record.personalAmount;Kind.REFUND-> -record.personalAmount;else->0L }
        val categories=filtered.filter { it.kind==Kind.EXPENSE || it.kind==Kind.REFUND }.groupBy {it.categoryId}.mapValues {Finance.total(it.value.map(::spending))}
        val daily=filtered.groupBy { java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.mapValues {Finance.total(it.value.map(::spending))}
        return PeriodReport(Finance.total(filtered.map(::spending)),Finance.total(filtered.filter {it.kind==Kind.INCOME}.map {it.amount}),categories,daily)
    }
}
