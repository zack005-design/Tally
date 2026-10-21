package com.smartexpense.data

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartexpense.MainActivity
import com.smartexpense.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class BackupReminderCoordinator @Inject constructor(@param:ApplicationContext private val context:Context,private val preferences:Preferences,private val clock:Clock) {
    private val mutex=Mutex()
    suspend fun syncNotifications(expectedAt:Long?=null,refreshCalendar:Boolean=false)=mutex.withLock {
        val alarms=context.getSystemService(AlarmManager::class.java)
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("backup-review","Backup reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val now=clock.millis()
        if(refreshCalendar) preferences.refreshBackupCalendar(now)
        val state=preferences.backupStatus.first()
        val allowed=manager.areNotificationsEnabled() && manager.getNotificationChannel("backup-review")?.importance!=NotificationManager.IMPORTANCE_NONE && (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
        if(allowed && expectedAt!=null && state.isDue(now,expectedAt)) {
            val open=PendingIntent.getActivity(context,405,Intent(context,MainActivity::class.java).setAction(OPEN_BACKUP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=NotificationCompat.Builder(context,"backup-review").setSmallIcon(R.drawable.ic_calendar).setContentTitle("Keep a backup of Tally")
                .setContentText("Open Tally to export a local backup.").setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            try {manager.notify(405,notification);preferences.markBackupReminderDelivered(expectedAt,now)}
            catch(e:SecurityException) {android.util.Log.w("BackupReminders","Notification permission changed",e)}
        }
        val current=preferences.backupStatus.first()
        val intent=Intent(context,BackupReminderReceiver::class.java).setAction(ACTION).putExtra("expectedAt",current.nextReminderAt)
        val pending=PendingIntent.getBroadcast(context,405,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(pending)
        if(current.reminderEnabled && allowed) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,maxOf(current.nextReminderAt,now+60000),pending)
        if(!current.reminderEnabled || !allowed) manager.cancel(405)
    }
    companion object {
        const val ACTION="com.smartexpense.BACKUP_REMINDER"
        const val OPEN_BACKUP="com.smartexpense.OPEN_BACKUP"
    }
}

@AndroidEntryPoint class BackupReminderReceiver:BroadcastReceiver() {
    @Inject lateinit var coordinator:BackupReminderCoordinator
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in listOf(BackupReminderCoordinator.ACTION,Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {withTimeout(8000) {coordinator.syncNotifications(if(intent.action==BackupReminderCoordinator.ACTION) intent.getLongExtra("expectedAt",0) else null,refreshCalendar=intent.action in listOf(Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED))}}
            catch(e:TimeoutCancellationException) {android.util.Log.w("BackupReminders","Schedule recovery deferred until next app opening",e)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {android.util.Log.w("BackupReminders","Could not reconcile backup reminders",e)}
            finally {pending.finish()}
        }
    }
}

