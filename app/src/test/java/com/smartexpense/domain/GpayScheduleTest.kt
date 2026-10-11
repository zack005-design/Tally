package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class GpayScheduleTest {
    @Test fun shortMonthsUseTheirLastDay() {
        val schedule=GpaySchedule(true,120000,31,"2028-01","2028-01")
        assertEquals(LocalDate.of(2028,2,29),schedule.dueDate(YearMonth.of(2028,2)))
        assertEquals(LocalDate.of(2027,2,28),schedule.dueDate(YearMonth.of(2027,2)))
    }
    @Test fun scheduleChangesAreProspective() {
        assertEquals("2026-11",firstResetMonth(5,"2026-01",LocalDate.of(2026,10,11)))
        assertEquals("2027-01",firstResetMonth(5,"2027-01",LocalDate.of(2026,10,11)))
        assertEquals("2026-10",firstResetMonth(20,"2026-10",LocalDate.of(2026,10,11)))
    }
    @Test fun offHasNoNextResetOrDailyPace() {
        val off=GpaySchedule(false,500000,1,"2026-10","2026-11")
        assertNull(off.nextDate(LocalDate.of(2026,10,11)))
        val on=off.copy(enabled=true,day=20,nextMonth="2026-10")
        assertEquals(LocalDate.of(2026,10,20),on.nextDate(LocalDate.of(2026,10,11)))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidDayIsRejected() {
        GpaySchedule(true,500000,32,"2026-10","2026-10")
    }
}
