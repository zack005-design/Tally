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
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign

val LocalDeviceLock=staticCompositionLocalOf<DeviceLockController?> {null}

@Composable fun SecureLedgerSurface(view:DeviceLockView,onUnlock:(Boolean)->Unit,content:@Composable ()->Unit) {
    val stateHolder=rememberSaveableStateHolder()
    if(!view.enabled || view.unlocked) stateHolder.SaveableStateProvider("ledger") {content()}
    // This gate sits outside TrackerApp's Scaffold, so it owns its background and insets.
    else Surface(Modifier.fillMaxSize(),color=Tokens.background,contentColor=Tokens.text) {
        LockGate(view,onUnlock)
    }
}

/** iOS-style lock: the app mark and date up top, one big unlock target, and the fallback as a quiet link. */
@Composable private fun LockGate(view:DeviceLockView,onUnlock:(Boolean)->Unit) {
    var shown by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {shown=true}
    val rise by androidx.compose.animation.core.animateFloatAsState(if(shown) 1f else 0f,iosSpring(androidx.compose.animation.core.Spring.StiffnessLow),label="lock entrance")
    val pulse=androidx.compose.animation.core.rememberInfiniteTransition(label="unlock pulse")
    val ring by pulse.animateFloat(1f,1.18f,androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400,easing=androidx.compose.animation.core.FastOutSlowInEasing),androidx.compose.animation.core.RepeatMode.Reverse),label="ring")
    val today=remember {java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM"))}
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=24.dp).graphicsLayer {alpha=rise;translationY=(1f-rise)*48.dp.toPx()},
        horizontalAlignment=Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(.8f))
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(22.dp))) {
            Image(painterResource(R.drawable.ic_launcher_bg_tally),null,Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_fg_tally),null,Modifier.fillMaxSize().scale(1.5f))
        }
        Spacer(Modifier.height(20.dp))
        Text(today,style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
        Text("Tally is locked",Modifier.semantics {heading()},style=MaterialTheme.typography.headlineLarge,textAlign=TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Your money stays private on this phone.",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted,textAlign=TextAlign.Center)
        Spacer(Modifier.weight(1f))
        // A large, softly breathing target, like Face ID's prompt area.
        val interaction=remember {androidx.compose.foundation.interaction.MutableInteractionSource()}
        Box(Modifier.size(132.dp),contentAlignment=Alignment.Center) {
            if(!view.busy) Box(Modifier.size(96.dp).scale(ring).clip(CircleShape).background(Tokens.accent.copy(alpha=.10f)))
            Surface(onClick=lightHapticAction {onUnlock(false)},enabled=!view.busy,interactionSource=interaction,shape=CircleShape,color=Tokens.paper,shadowElevation=6.dp,
                modifier=Modifier.size(84.dp).softPress(interaction).semantics {contentDescription="Unlock with fingerprint or face"}) {
                Box(contentAlignment=Alignment.Center) {
                    if(view.busy) CircularProgressIndicator(Modifier.size(30.dp),strokeWidth=3.dp,color=Tokens.accent)
                    else Icon(painterResource(R.drawable.ic_lock_outline),null,Modifier.size(34.dp),tint=Tokens.accent)
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(view.message!=null) {
            Text(view.message.orEmpty(),Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall,color=Tokens.negative,textAlign=TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        EditorialPrimaryButton(if(view.busy) "Authenticating…" else "Unlock Tally",!view.busy) {onUnlock(false)}
        TextButton({onUnlock(true)},Modifier.padding(vertical=8.dp).heightIn(min=48.dp),enabled=!view.busy) {
            Text("Use PIN, pattern or password",style=MaterialTheme.typography.labelLarge,color=Tokens.accent)
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
