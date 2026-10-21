package com.smartexpense.domain
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ReminderScheduleTest {
    @Test fun springGapDeliveryMatchesTheResolvedWallTime() {
        val settings=ReminderSettings(true,2,30)
        val now=ZonedDateTime.parse("2026-03-08T01:00:00-05:00[America/New_York]")
        val next=ReminderSchedule.next(settings,listOf(template),emptySet(),now)!!
        assertEquals(3,next.hour)
        assertTrue(ReminderSchedule.matches(settings,next))
        assertFalse(ReminderSchedule.matches(settings.copy(hour=1),next))
    }
    private val template=RecurringTemplate("rent",Kind.EXPENSE,100,"Rent","w","2026-01-31","Monthly")
    @Test fun disabledAndRecordedDatesDoNotProduceAnEarlyReminder() {
        val now=ZonedDateTime.parse("2026-01-31T10:00:00+05:30[Asia/Kolkata]")
        assertNull(ReminderSchedule.next(ReminderSettings(),listOf(template),emptySet(),now))
        val next=ReminderSchedule.next(ReminderSettings(true,20,0),listOf(template),setOf(template.entryId(LocalDate.parse("2026-01-31"))),now)!!
        assertEquals("2026-02-28",next.toLocalDate().toString());assertEquals(20,next.hour)
    }
    @Test fun overdueEntryRemindsTomorrowAfterTheChosenTimeAndHandlesDst() {
        val now=ZonedDateTime.parse("2026-03-07T21:00:00-05:00[America/New_York]")
        val next=ReminderSchedule.next(ReminderSettings(true,20,0),listOf(template),emptySet(),now)!!
        assertEquals("2026-03-08",next.toLocalDate().toString());assertEquals(20,next.hour);assertEquals(ZoneOffset.ofHours(-4),next.offset)
    }
}
