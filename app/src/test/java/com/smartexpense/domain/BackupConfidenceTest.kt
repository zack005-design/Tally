package com.smartexpense.domain

import java.io.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackupConfidenceTest {
    @Test fun failedWriteOrCloseNeverMarksExportSuccessful()=runBlocking {
        var marked=false
        val broken=object:ByteArrayOutputStream() {override fun close() {throw IOException("disk failed")}}
        try {writeBackup(byteArrayOf(1,2),{broken}) {marked=true};fail("Failed close accepted")} catch(_:IOException) {}
        assertFalse(marked)
        val output=ByteArrayOutputStream()
        writeBackup(byteArrayOf(4,5),{output}) {marked=true}
        assertArrayEquals(byteArrayOf(4,5),output.toByteArray());assertTrue(marked)
    }
    @Test fun reminderRequiresOptInAndMatchingDueAlarm() {
        val now=Instant.parse("2026-10-19T10:00:00Z").toEpochMilli()
        val due=nextBackupReminder(now)
        assertEquals(Instant.parse("2026-10-26T10:00:00Z").toEpochMilli(),due)
        assertFalse(BackupStatus().isDue(due,due))
        val status=BackupStatus(0,true,due)
        assertFalse(status.isDue(due-1,due));assertFalse(status.isDue(due,now))
        assertTrue(status.isDue(due,due))
    }
    @Test fun weeklyCalendarReminderUsesChosenLocalDayAndTime() {
        val zone=ZoneId.of("Asia/Kolkata")
        val before=Instant.parse("2026-10-25T13:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-25T14:30:00Z").toEpochMilli(),nextBackupReminder(before,7,20,0,zone))
        val at=Instant.parse("2026-10-25T14:30:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-11-01T14:30:00Z").toEpochMilli(),nextBackupReminder(at,7,20,0,zone))
    }
    @Test fun weeklyCalendarReminderHandlesDstGapWithoutLooping() {
        val now=Instant.parse("2026-03-07T12:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),nextBackupReminder(now,7,2,30,ZoneId.of("America/New_York")))
        assertTrue(runCatching {nextBackupReminder(now,8,20,0,ZoneId.of("UTC"))}.isFailure)
    }
}
