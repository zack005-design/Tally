package com.smartexpense
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.ui.*
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
@AndroidEntryPoint class MainActivity:ComponentActivity() {
    private val model:AppViewModel by viewModels()
    private val deviceLock by lazy {com.smartexpense.data.DeviceLockController(this)}
    @javax.inject.Inject lateinit var reminders:com.smartexpense.data.ReminderCoordinator
    @javax.inject.Inject lateinit var budgetAlerts:com.smartexpense.data.BudgetAlertCoordinator
    @javax.inject.Inject lateinit var backupReminders:com.smartexpense.data.BackupReminderCoordinator
    @javax.inject.Inject lateinit var preferences:com.smartexpense.data.Preferences
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);preferHighestRefreshRate();val lock=deviceLock;enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT));setContent {
            val mode by preferences.themeMode.collectAsStateWithLifecycle(initialValue=com.smartexpense.domain.ThemeMode.System)
            val dark=mode.isDark(androidx.compose.foundation.isSystemInDarkTheme())
            // Status and navigation bar icons must contrast with the app background, not the system theme.
            androidx.compose.runtime.LaunchedEffect(dark) {
                val transparent=android.graphics.Color.TRANSPARENT
                val bars=if(dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent,transparent)
                enableEdgeToEdge(statusBarStyle=bars,navigationBarStyle=bars)
            }
            TrackerTheme(darkTheme=dark) { androidx.compose.runtime.CompositionLocalProvider(LocalDeviceLock provides lock) {SecureLedgerSurface(lock.view,lock::unlock) {TrackerApp(model)}} }
        }
        if(savedInstanceState==null) handleLaunch(intent)
        lifecycleScope.launch {repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                try {withContext(Dispatchers.IO) {backupReminders.syncNotifications()}}
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {android.util.Log.w("BackupReminders","Could not recover reminder schedule",e)}
            }
            launch {model.repository.changes.collect {
                try {withContext(Dispatchers.IO) {reminders.syncNotifications()}}
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {android.util.Log.w("LocalReminders","Could not reconcile local reminders",e)}
                try {withContext(Dispatchers.IO) {budgetAlerts.check()}}
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {android.util.Log.w("BudgetAlerts","Could not check budget alerts",e)}
                try {com.smartexpense.widget.QuickExpenseWidget.refresh(this@MainActivity,model.repository)}
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {android.util.Log.w("TallyWidget","Could not update expense shortcuts",e)}
            }}
            var seen:java.time.LocalDate?=null
            while(true) {
                val current=java.time.LocalDate.now()
                if(seen!=current && !model.state.value.loading) {model.refreshWallet();seen=current}
                delay(30000)
            }
        }}
    }
    override fun onPause() {deviceLock.background();super.onPause()}
    private fun handleLaunch(intent:android.content.Intent?) {
        when(intent?.action) {
            com.smartexpense.data.BackupReminderCoordinator.OPEN_BACKUP->model.requestLaunch("backup")
            com.smartexpense.data.BudgetAlertCoordinator.OPEN_BUDGETS->model.requestLaunch("budgets")
            "com.smartexpense.QUICK_ADD"->model.requestLaunch("quick-add")
            com.smartexpense.widget.QuickExpenseWidget.ACTION_FAVOURITE->intent.getStringExtra(com.smartexpense.widget.QuickExpenseWidget.EXTRA_FAVOURITE_ID)?.let(model::requestFavourite)
        }
    }
    override fun onNewIntent(intent:android.content.Intent) {super.onNewIntent(intent);setIntent(intent);handleLaunch(intent)}
    override fun onDestroy() {deviceLock.destroy();super.onDestroy()}
    /** Asks for the panel's fastest mode at the current resolution (120 Hz where available); the system still lowers it for battery saver. */
    private fun preferHighestRefreshRate() {
        val display = if (android.os.Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        val current = display?.mode ?: return
        val best = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }.maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }
}

