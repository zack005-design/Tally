package com.smartexpense.data

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartexpense.MainActivity
import com.smartexpense.R
import com.smartexpense.domain.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

interface ReminderGateway {
    fun allowed():Boolean
    fun reconcile(next:Instant?,hasDue:Boolean)
    fun show():Boolean
}

@Singleton class AndroidReminderGateway @Inject constructor(@ApplicationContext private val context:Context):ReminderGateway {
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    private fun channel() {notifications.createNotificationChannel(NotificationChannel("recurring-review","Recurring entry reminders",NotificationManager.IMPORTANCE_DEFAULT))}
    override fun allowed():Boolean {channel();return notifications.areNotificationsEnabled() && notifications.getNotificationChannel("recurring-review")?.importance!=NotificationManager.IMPORTANCE_NONE && (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)}
    override fun reconcile(next:Instant?,hasDue:Boolean) {
        val intent=Intent(context,ReminderReceiver::class.java).setAction(ACTION).putExtra("expectedAt",next?.toEpochMilli()?:0L)
        val pending=PendingIntent.getBroadcast(context,404,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(pending)
        if(next!=null) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toEpochMilli(),pending)
        if(!hasDue) notifications.cancel(404)
    }
    override fun show():Boolean {
        if(!allowed()) return false
        val open=PendingIntent.getActivity(context,404,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,"recurring-review").setSmallIcon(R.drawable.ic_calendar)
            .setContentTitle("Review your recurring entries").setContentText("Open Tally to review due templates. Entries are recorded only when you save.")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return try {notifications.notify(404,notification);true} catch(e:SecurityException) {false}
    }
    companion object {const val ACTION="com.smartexpense.RECURRING_REMINDER"}
}

@Singleton class ReminderCoordinator @Inject constructor(private val repository:FinanceRepository,private val gateway:ReminderGateway,private val clock:Clock) {
    private val mutex=Mutex()
    suspend fun syncNotifications(expectedAt:Long?=null)=mutex.withLock {
        val settings=repository.reminderSettings()
        val snapshot=repository.snapshot(Int.MAX_VALUE)
        val templates=snapshot.templates.filter {template->snapshot.accounts.any {it.id==template.accountId && !it.archived}}
        val recorded=snapshot.records.map {it.id}.toSet()
        val now=clock.instant().atZone(ZoneId.systemDefault())
        val due=templates.any {template->template.occurrences(now.toLocalDate()).any {template.entryId(it) !in recorded}}
        if(!settings.enabled || !gateway.allowed()) {gateway.reconcile(null,false);return@withLock}
        if(expectedAt!=null) {
            val expected=Instant.ofEpochMilli(expectedAt).atZone(now.zone)
            val valid=expectedAt>0 && !expected.isAfter(now) && ReminderSchedule.matches(settings,expected) && settings.lastNotifiedDate!=now.toLocalDate().toString()
            if(valid && due && gateway.show()) repository.markReminderDelivered(now.toLocalDate().toString())
        }
        gateway.reconcile(ReminderSchedule.next(settings,templates,recorded,now)?.toInstant(),due)
    }
}

@AndroidEntryPoint class ReminderReceiver:BroadcastReceiver() {
    @Inject lateinit var coordinator:ReminderCoordinator
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in listOf(AndroidReminderGateway.ACTION,Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val result=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {withTimeout(8000) {coordinator.syncNotifications(if(intent.action==AndroidReminderGateway.ACTION) intent.getLongExtra("expectedAt",0L) else null)}}
            catch(e:TimeoutCancellationException) {android.util.Log.w("LocalReminders","Reminder reconciliation timed out; app opening will retry")}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {android.util.Log.w("LocalReminders","Reminder reconciliation deferred until next app opening",e)}
            finally {result.finish()}
        }
    }
}
