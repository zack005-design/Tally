package com.smartexpense.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A planned event shown on Summary. Opening one never records anything. */
data class UpcomingItem(val id:String,val label:String,val date:LocalDate,val amount:Long,val kind:Kind?,val destination:String)

/** First occurrence on or after [today]. Monthly dates are counted from the first date, so the 31st is kept. */
fun RecurringTemplate.nextOccurrence(today:LocalDate):LocalDate {
    val first=LocalDate.parse(firstDate)
    if(first>=today) return first
    fun at(step:Long)=when(frequency) {"Daily"->first.plusDays(step);"Weekly"->first.plusWeeks(step);else->first.plusMonths(step)}
    var step=when(frequency) {
        "Daily"->ChronoUnit.DAYS.between(first,today)
        "Weekly"->ChronoUnit.WEEKS.between(first,today)
        else->ChronoUnit.MONTHS.between(YearMonth.from(first),YearMonth.from(today))
    }
    while(at(step)<today) step++
    return at(step)
}

/** Recurring drafts and the GPay reset due within [days], soonest first, without occurrences already recorded. */
fun comingUp(snapshot:Snapshot,today:LocalDate,days:Long=14,limit:Int=3):List<UpcomingItem> {
    val recorded=snapshot.records.mapTo(HashSet()) {it.id}
    val recurring=snapshot.templates.map {template->
        var date=template.nextOccurrence(today)
        if(template.entryId(date) in recorded) date=template.nextOccurrence(date.plusDays(1))
        UpcomingItem(template.id,template.description,date,template.amount,template.kind,"recurring")
    }
    val reset=snapshot.walletSources?.schedule?.let {schedule->
        schedule.nextDate(today)?.let {UpcomingItem("gpay-reset","GPay reset",it,schedule.target,null,"gpay-settings")}
    }
    return (recurring+listOfNotNull(reset)).filter {it.date<=today.plusDays(days)}
        .sortedWith(compareBy<UpcomingItem> {it.date}.thenBy {it.label}.thenBy {it.id}).take(limit)
}

/**
 * Spending for the seven days ending [today]. This month comes from the complete month report;
 * earlier days come from [Snapshot.records], which can be a limited page of history.
 */
fun lastSevenDays(snapshot:Snapshot,today:LocalDate):List<Pair<LocalDate,Long>> {
    val earlier=Reports.period(snapshot.records,today.minusDays(6),today).daily
    return (6L downTo 0L).map {back->
        val date=today.minusDays(back)
        date to when {
            date==today->snapshot.dashboard.today
            YearMonth.from(date)==YearMonth.from(today)->snapshot.dashboard.month.daily[date]?:0L
            else->earlier[date]?:0L
        }
    }
}
