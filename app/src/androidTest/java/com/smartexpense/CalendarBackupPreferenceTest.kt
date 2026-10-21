package com.smartexpense

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.smartexpense.data.Preferences
import com.smartexpense.domain.nextBackupReminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.*

class CalendarBackupPreferenceTest {
    @Test fun weeklySchedulePersistsWithoutEnablingOrExportingBackup()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Preference verification only uses the isolated reference app",context.packageName.endsWith(".reference"))
        val preferences=Preferences(context)
        val original=preferences.backupStatus.first()
        val now=Instant.parse("2026-10-20T10:00:00Z").toEpochMilli()
        try {
            preferences.setBackupSchedule(7,20,15,false,now)
            val saved=Preferences(context).backupStatus.first()
            assertEquals(7,saved.weeklyDay);assertEquals(20,saved.hour);assertEquals(15,saved.minute)
            assertFalse(saved.reminderEnabled);assertEquals(0L,saved.nextReminderAt)
            assertEquals(original.lastExport,saved.lastExport)
            preferences.setBackupReminder(true,now)
            assertEquals(nextBackupReminder(now,7,20,15,ZoneId.systemDefault()),preferences.backupStatus.first().nextReminderAt)
            preferences.refreshBackupCalendar(now+86400000)
            assertEquals(nextBackupReminder(now+86400000,7,20,15,ZoneId.systemDefault()),preferences.backupStatus.first().nextReminderAt)
        } finally {
            preferences.setBackupSchedule(original.weeklyDay,original.hour,original.minute,original.reminderEnabled,System.currentTimeMillis())
        }
    }
}
