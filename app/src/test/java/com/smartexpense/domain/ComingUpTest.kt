package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ComingUpTest {
    private val today=LocalDate.of(2026,11,24)
    private fun template(id:String,first:String,frequency:String)=RecurringTemplate(id,Kind.EXPENSE,1200000,"Rent $id","bank",first,frequency)
    private fun record(id:String,date:LocalDate,amount:Long)=Record(id,Kind.EXPENSE,"Lunch",amount,amount,null,null,date.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),"",false)

    @Test fun nextOccurrenceIsNeverInThePast() {
        assertEquals(LocalDate.of(2026,12,1),template("a","2026-12-01","Monthly").nextOccurrence(today))
        assertEquals(LocalDate.of(2026,12,1),template("a","2026-01-01","Monthly").nextOccurrence(today))
        assertEquals(today,template("a","2026-11-10","Weekly").nextOccurrence(today))
        assertEquals(today,template("a","2026-01-01","Daily").nextOccurrence(today))
    }
    @Test fun monthEndDatesDoNotDrift() {
        assertEquals(LocalDate.of(2026,11,30),template("a","2026-08-31","Monthly").nextOccurrence(today))
        assertEquals(LocalDate.of(2026,12,31),template("a","2026-08-31","Monthly").nextOccurrence(LocalDate.of(2026,12,1)))
    }
    @Test fun recordedOccurrencesAndDistantItemsAreLeftOut() {
        val weekly=template("w","2026-11-24","Weekly")
        val far=template("f","2027-02-01","Monthly")
        val items=comingUp(Snapshot(templates=listOf(far,weekly),records=listOf(record(weekly.entryId(today),today,100))),today)
        assertEquals(listOf(LocalDate.of(2026,12,1)),items.map {it.date})
    }
    @Test fun gpayResetIsIncludedOnlyWhenEnabled() {
        fun snapshot(enabled:Boolean)=Snapshot(walletSources=WalletSources("w","g","2026-11",schedule=GpaySchedule(enabled,500000,1,"2026-11","2026-12")))
        assertEquals(listOf("GPay reset"),comingUp(snapshot(true),today).map {it.label})
        assertTrue(comingUp(snapshot(false),today).isEmpty())
    }
    @Test fun sevenDaysCrossMonthsWithoutLosingSpending() {
        val day=LocalDate.of(2026,12,2)
        val snapshot=Snapshot(records=listOf(record("n",LocalDate.of(2026,11,30),700)),
            dashboard=DashboardMetrics(today=300,month=PeriodReport(500,0,emptyMap(),mapOf(LocalDate.of(2026,12,1) to 200L))))
        val week=lastSevenDays(snapshot,day)
        assertEquals(7,week.size)
        assertEquals(listOf(0L,0L,0L,0L,700L,200L,300L),week.map {it.second})
    }
}
