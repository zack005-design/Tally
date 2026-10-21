package com.smartexpense.data
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.smartexpense.domain.*
private val Context.preferences by preferencesDataStore("tracker-settings")
@Singleton class Preferences @Inject constructor(@param:ApplicationContext private val context:Context) {
    private val compact=booleanPreferencesKey("compact-amounts")
    val compactAmounts=context.preferences.data.map { it[compact]?:false }
    suspend fun setCompact(value:Boolean) { context.preferences.edit { it[compact]=value } }
    private val lastExport=longPreferencesKey("last-successful-backup-export")
    private val backupEnabled=booleanPreferencesKey("weekly-backup-reminder")
    private val backupNext=longPreferencesKey("next-backup-reminder")
    private val backupDay=intPreferencesKey("backup-reminder-weekday")
    private val backupHour=intPreferencesKey("backup-reminder-hour")
    private val backupMinute=intPreferencesKey("backup-reminder-minute")
    val backupStatus=context.preferences.data.map {BackupStatus(it[lastExport]?:0,it[backupEnabled]?:false,it[backupNext]?:0,it[backupDay],it[backupHour]?:20,it[backupMinute]?:0)}
    private fun nextBackup(now:Long,values:androidx.datastore.preferences.core.Preferences):Long = values[backupDay]?.let {day->
        nextBackupReminder(now,day,values[backupHour]?:20,values[backupMinute]?:0,java.time.ZoneId.systemDefault())
    }?:nextBackupReminder(now)
    suspend fun markBackupExported(now:Long) {context.preferences.edit {it[lastExport]=now;if(it[backupEnabled]==true) it[backupNext]=nextBackup(now,it)}}
    suspend fun setBackupReminder(enabled:Boolean,now:Long) {context.preferences.edit {it[backupEnabled]=enabled;it[backupNext]=if(enabled) nextBackup(now,it) else 0}}
    suspend fun setBackupSchedule(day:Int?,hour:Int,minute:Int,enabled:Boolean,now:Long) {
        require(day==null || day in 1..7);java.time.LocalTime.of(hour,minute)
        context.preferences.edit {
            if(day==null) it.remove(backupDay) else it[backupDay]=day
            it[backupHour]=hour;it[backupMinute]=minute;it[backupEnabled]=enabled
            it[backupNext]=if(enabled) nextBackup(now,it) else 0
        }
    }
    suspend fun refreshBackupCalendar(now:Long) {context.preferences.edit {if(it[backupEnabled]==true && it[backupDay]!=null) it[backupNext]=nextBackup(now,it)}}
    suspend fun markBackupReminderDelivered(expected:Long,now:Long) {context.preferences.edit {if(it[backupEnabled]==true && it[backupNext]==expected) it[backupNext]=nextBackup(now,it)}}
}
