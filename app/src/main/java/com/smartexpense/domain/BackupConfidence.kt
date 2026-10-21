package com.smartexpense.domain

import java.io.OutputStream
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class BackupStatus(val lastExport:Long=0,val reminderEnabled:Boolean=false,val nextReminderAt:Long=0,val weeklyDay:Int?=null,val hour:Int=20,val minute:Int=0) {
    fun isDue(now:Long,expectedAt:Long)=reminderEnabled && nextReminderAt>0 && expectedAt==nextReminderAt && now>=nextReminderAt
}
fun nextBackupReminder(now:Long):Long=Instant.ofEpochMilli(now).plus(7,ChronoUnit.DAYS).toEpochMilli()

/** Calendar schedules are opt-in; existing interval reminders keep their seven-day cadence. */
fun nextBackupReminder(now:Long,weeklyDay:Int,hour:Int,minute:Int,zone:ZoneId):Long {
    val day=DayOfWeek.of(weeklyDay)
    val time=LocalTime.of(hour,minute)
    val instant=Instant.ofEpochMilli(now)
    val date=instant.atZone(zone).toLocalDate().with(TemporalAdjusters.nextOrSame(day))
    var next=date.atTime(time).atZone(zone)
    if(!next.toInstant().isAfter(instant)) next=date.plusWeeks(1).atTime(time).atZone(zone)
    return next.toInstant().toEpochMilli()
}

/** Success is recorded only after the output stream has closed successfully. */
suspend fun writeBackup(bytes:ByteArray,open:()->OutputStream,onSuccess:suspend ()->Unit) {
    open().use {it.write(bytes);it.flush()}
    onSuccess()
}
