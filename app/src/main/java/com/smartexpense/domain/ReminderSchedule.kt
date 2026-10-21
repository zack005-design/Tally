package com.smartexpense.domain
import java.time.*
import java.time.temporal.ChronoUnit

data class ReminderSettings(val enabled:Boolean=false,val hour:Int=20,val minute:Int=0,val lastNotifiedDate:String="") {
    init {require(hour in 0..23 && minute in 0..59);if(lastNotifiedDate.isNotBlank()) LocalDate.parse(lastNotifiedDate)}
}
object ReminderSchedule {
    fun matches(settings:ReminderSettings,scheduled:ZonedDateTime):Boolean=scheduled.toLocalDate().atTime(settings.hour,settings.minute).atZone(scheduled.zone).toInstant()==scheduled.toInstant()
    /** Calendar recurrence stays in local wall time across DST and zone changes. */
    fun next(settings:ReminderSettings,templates:List<RecurringTemplate>,recorded:Set<String>,now:ZonedDateTime):ZonedDateTime? {
        if(!settings.enabled) return null
        val today=now.toLocalDate()
        return templates.map {template->
            val first=LocalDate.parse(template.firstDate)
            var date=if(template.occurrences(today).any {template.entryId(it) !in recorded}) today
            else if(first>today) first else when(template.frequency) {
                "Daily"->today.plusDays(1)
                "Weekly"->first.plusWeeks(ChronoUnit.DAYS.between(first,today)/7+1)
                else->{val index=ChronoUnit.MONTHS.between(YearMonth.from(first),YearMonth.from(today));val candidate=first.plusMonths(index);if(candidate>today) candidate else first.plusMonths(index+1)}
            }
            var at=date.atTime(settings.hour,settings.minute).atZone(now.zone)
            if(!at.isAfter(now)) {date=date.plusDays(1);at=date.atTime(settings.hour,settings.minute).atZone(now.zone)}
            at
        }.minOrNull()
    }
}
