package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import com.smartexpense.data.DeviceLockController
import com.smartexpense.data.DeviceLockView
import com.smartexpense.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

val LocalDeviceLock=staticCompositionLocalOf<DeviceLockController?> {null}

@Composable fun SecureLedgerSurface(view:DeviceLockView,onUnlock:(Boolean)->Unit,content:@Composable ()->Unit) {
    val stateHolder=rememberSaveableStateHolder()
    if(!view.enabled || view.unlocked) stateHolder.SaveableStateProvider("ledger") {content()}
    // This gate sits outside TrackerApp's Scaffold, so it owns its background and insets.
    else Surface(Modifier.fillMaxSize(),color=Tokens.background,contentColor=Tokens.text) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Page("Tally is locked") {
                item {Column(Modifier.fillMaxWidth().padding(vertical=18.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    ReferenceIcon(R.drawable.ic_lock_outline,size=76.dp)
                    Text("Your local finances stay private",style=MaterialTheme.typography.bodyMedium,
                        color=Tokens.muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }}
                item {Panel {
                    Text("Authenticate using Android's secure prompt to view your ledger.",style=MaterialTheme.typography.bodyLarge)
                    view.message?.let {Text(it,color=Tokens.muted)}
                    EditorialPrimaryButton(if(view.busy) "Authenticating…" else "Unlock Tally",!view.busy) {onUnlock(false)}
                    OutlinedButton({onUnlock(true)},Modifier.fillMaxWidth(),enabled=!view.busy) {Text("Use device PIN / pattern / password")}
                }}
            }
        }
    }
}

@Composable fun AppLockSettingsScreen() {
    val controller=LocalDeviceLock.current
    val view=controller?.view
    AppLockSettingsContent(view) {controller?.requestSetting(it)}
}

@Composable fun AppLockSettingsContent(view:DeviceLockView?,onSetting:(Boolean)->Unit) {
    Page("App lock") {
        item {Column(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            ReferenceIcon(R.drawable.ic_lock_outline,size=76.dp)
            Text("Keep your data private",style=MaterialTheme.typography.titleLarge)
            Text("Use your device’s biometrics or credentials to unlock Tally.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }}
        item {GroupedCard {
            ReferenceToggleRow("Require unlock",view?.enabled==true,{onSetting(it)},enabled=view?.credentialAvailable==true && !view.busy)
        }}
        item {Text("Unlock with Android biometrics or device credentials. Locks when you leave Tally.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
        item {GroupedCard {
            ReferenceToggleRow("Block screenshots and previews",view?.enabled==true,null,supporting="Follows App lock")
        }}
        item {Text("Screenshots and recent-app previews are blocked when App lock is enabled.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
        if(view?.credentialAvailable!=true) item {ReferenceInfo("Set a device PIN, pattern or password in Android settings before enabling app lock.")}
        view?.message?.let {message->item {ReferenceInfo(message)}}
        item {Text("Lock settings stay on this device. App lock does not encrypt the database.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
    }
}
