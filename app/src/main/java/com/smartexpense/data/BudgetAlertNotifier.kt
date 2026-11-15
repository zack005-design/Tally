package com.smartexpense.data

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartexpense.MainActivity
import com.smartexpense.R
import com.smartexpense.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

/** Posts a local notification when this month's spending reaches 80% or 100% of a budget you set. */
@Singleton class BudgetAlertCoordinator @Inject constructor(@param:ApplicationContext private val context:Context,private val repository:FinanceRepository,private val preferences:Preferences,private val clock:Clock) {
    private val mutex=Mutex()
    private val manager get()=context.getSystemService(NotificationManager::class.java)

    fun allowed():Boolean {
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Budget alerts",NotificationManager.IMPORTANCE_DEFAULT))
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE &&
            (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    suspend fun check()=mutex.withLock {
        if(!preferences.budgetAlertsEnabled.first()) return@withLock
        val today=clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
        val month=YearMonth.from(today)
        val inputs=repository.budgetAlertInputs(month,today)
        val (alerts,levels)=BudgetAlerts.evaluate(inputs.budgets,month.toString(),inputs.report,preferences.budgetAlertLevels.first())
        if(alerts.isNotEmpty() && allowed()) alerts.forEach {show(it,inputs.categoryNames)}
        preferences.setBudgetAlertLevels(levels)
    }

    private fun show(alert:BudgetAlert,names:Map<String,String>) {
        val name=alert.categoryId?.let {names[it]?:"A category"}?:"Your monthly"
        val open=PendingIntent.getActivity(context,406,Intent(context,MainActivity::class.java).setAction(OPEN_BUDGETS),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title=if(alert.level>=100) "$name budget is used up" else "$name budget is ${alert.level}% used"
        val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_calendar).setContentTitle(title)
            .setContentText("Open Tally to review this month's spending.").setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try {manager.notify(alert.budgetId.hashCode(),notification)} catch(e:SecurityException) {android.util.Log.w("BudgetAlerts","Notification permission changed",e)}
    }

    companion object {
        const val CHANNEL="budget-alerts"
        const val OPEN_BUDGETS="com.smartexpense.OPEN_BUDGETS"
    }
}
