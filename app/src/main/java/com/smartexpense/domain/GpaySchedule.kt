package com.smartexpense.domain

import java.time.*

data class GpaySchedule(val enabled:Boolean=false,val target:Long=500000,val day:Int=1,val startMonth:String=YearMonth.now().toString(),val nextMonth:String=YearMonth.now().plusMonths(1).toString()) {
    init {require(target>=0 && day in 1..31);YearMonth.parse(startMonth);YearMonth.parse(nextMonth)}
    fun dueDate(month:YearMonth):LocalDate=month.atDay(minOf(day,month.lengthOfMonth()))
    fun nextDate(today:LocalDate):LocalDate? {
        if(!enabled) return null
        var month=maxOf(YearMonth.parse(nextMonth),YearMonth.from(today))
        if(dueDate(month)<=today) month=month.plusMonths(1)
        return dueDate(month)
    }
}

/** Editing/enabling is prospective and never catches up disabled time. */
fun firstResetMonth(day:Int,startMonth:String,today:LocalDate):String {
    require(day in 1..31)
    var month=maxOf(YearMonth.parse(startMonth),YearMonth.from(today))
    if(month.atDay(minOf(day,month.lengthOfMonth()))<=today) month=month.plusMonths(1)
    return month.toString()
}
