package com.smartexpense.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.domain.*
import com.smartexpense.data.Preferences
import com.smartexpense.data.BackupReminderCoordinator
import com.smartexpense.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable fun RemindersScreen(model:AppViewModel) {
    val context=LocalContext.current
    val app by model.state.collectAsStateWithLifecycle()
    val preferences=remember(context) {Preferences(context.applicationContext)}
    val backupCoordinator=remember(context) {BackupReminderCoordinator(context.applicationContext,preferences,Clock.systemUTC())}
    val backupStatus by preferences.backupStatus.collectAsStateWithLifecycle(initialValue=BackupStatus())
    var settings by remember {mutableStateOf<ReminderSettings?>(null)}
    var hour by rememberSaveable {mutableStateOf(20)}
    var minute by rememberSaveable {mutableStateOf(0)}
    var picking by rememberSaveable {mutableStateOf(false)}
    var denied by remember {mutableStateOf(false)}
    var permissionForBackup by rememberSaveable {mutableStateOf(false)}
    var backupDayPicker by rememberSaveable {mutableStateOf(false)}
    var backupTimePicker by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(Unit) {settings=model.repository.reminderSettings();hour=settings!!.hour;minute=settings!!.minute}
    fun save(enabled:Boolean) {model.action("Reminder settings saved") {model.repository.saveReminderSettings(ReminderSettings(enabled,hour,minute));settings=model.repository.reminderSettings()}}
    fun saveBackup(enabled:Boolean,day:Int=backupStatus.weeklyDay?:7,backupHour:Int=backupStatus.hour,backupMinute:Int=backupStatus.minute) {model.action("Backup reminder settings saved") {preferences.setBackupSchedule(day,backupHour,backupMinute,enabled,System.currentTimeMillis());withContext(Dispatchers.IO) {backupCoordinator.syncNotifications()}}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        denied=!granted
        if(granted) {if(permissionForBackup) saveBackup(true) else save(true)}
    }
    fun enable(enabled:Boolean,backup:Boolean) {
        if(enabled && Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionForBackup=backup;permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else if(backup) saveBackup(enabled) else save(enabled)
    }
    Page("Reminders") {
        item {SectionLabel("DAILY RECURRING DRAFTS")}
        item {GroupedCard {
            ReferenceToggleRow("Daily recurring drafts",settings?.enabled==true,{enable(it,false)},enabled=settings!=null && !app.busy)
            GroupedDivider()
            GroupedRow("Time",R.drawable.ic_calendar,value=LocalTime.of(hour,minute).format(DateTimeFormatter.ofPattern("h:mm a"))) {if(settings!=null && !app.busy) picking=true}
        }}
        item {Text("A reminder to review due entries. Approximate timing depends on Android battery settings.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
        if(settings!=null && (settings!!.hour!=hour || settings!!.minute!=minute)) item {EditorialPrimaryButton("Save reminder time",!app.busy) {save(settings!!.enabled)}}
        item {SectionLabel("WEEKLY BACKUP REMINDER")}
        item {GroupedCard {
            ReferenceToggleRow("Weekly backup reminder",backupStatus.reminderEnabled,{enable(it,true)},enabled=!app.busy)
            GroupedDivider(16.dp)
            ReferenceDetailRow("Day",R.drawable.ic_calendar,java.time.DayOfWeek.of(backupStatus.weeklyDay?:7).getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale.getDefault())) {if(!app.busy) backupDayPicker=true}
            GroupedDivider(16.dp)
            ReferenceDetailRow("Time",R.drawable.ic_clock_outline,LocalTime.of(backupStatus.hour,backupStatus.minute).format(DateTimeFormatter.ofPattern("h:mm a"))) {if(!app.busy) backupTimePicker=true}
        }}
        item {Text(if(backupStatus.weeklyDay==null && backupStatus.reminderEnabled) "Current reminder repeats every seven days. Choose a day or time to use a weekly schedule. Files are never exported automatically." else "Approximate timing depends on Android battery settings. Files are never exported automatically.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
        if(denied) item {ReferenceInfo("Notification permission was not granted. The reminder remains off.")}
        item {SectionLabel("NOTIFICATION EXAMPLE")}
        item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Tally · now",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            Text("Review your due entries",style=MaterialTheme.typography.titleSmall)
            Text("Take a moment to review your drafts in Tally.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
        }}}
        item {Text("Entries are never saved automatically. Notifications contain no amounts or merchant names.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
    }
    if(backupDayPicker) EditorialBottomSheet("Backup reminder day",{backupDayPicker=false}) {
        java.time.DayOfWeek.entries.forEach {day->TextButton({saveBackup(backupStatus.reminderEnabled,day.value);backupDayPicker=false},Modifier.fillMaxWidth(),enabled=!app.busy) {Text(day.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale.getDefault()))}}
    }
    if(backupTimePicker) EditorialTimePicker(LocalTime.of(backupStatus.hour,backupStatus.minute),{backupTimePicker=false}) {saveBackup(backupStatus.reminderEnabled,backupHour=it.hour,backupMinute=it.minute);backupTimePicker=false}
    if(picking) EditorialTimePicker(LocalTime.of(hour,minute),{picking=false}) {hour=it.hour;minute=it.minute;picking=false}
}

