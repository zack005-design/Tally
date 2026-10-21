package com.smartexpense.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.smartexpense.R
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.smartexpense.data.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.domain.*
import com.smartexpense.data.Preferences
import com.smartexpense.data.BackupReminderCoordinator
import java.time.*

@Composable fun BackupScreen(model:AppViewModel) {
    val context=LocalContext.current;val service=remember {model.backup}
    val preferences=remember(context) {Preferences(context.applicationContext)}
    val reminder=remember(context) {BackupReminderCoordinator(context.applicationContext,preferences,Clock.systemUTC())}
    val status by preferences.backupStatus.collectAsStateWithLifecycle(initialValue=BackupStatus())
    var denied by remember {mutableStateOf(false)}
    fun setReminder(enabled:Boolean) {model.action("Backup reminder settings saved") {preferences.setBackupReminder(enabled,System.currentTimeMillis());withContext(Dispatchers.IO) {reminder.syncNotifications()}}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->denied=!granted;if(granted) setReminder(true)}
    val backupUi by model.backupUi.collectAsStateWithLifecycle()
    val password=backupUi.password
    val visible=backupUi.visible
    val preview=backupUi.restoreSummary
    val appState by model.state.collectAsStateWithLifecycle()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri -> if(uri!=null) model.action("Backup exported") {
        val exportPassword=model.exportPassword()
        try {
            val bytes=service.export(exportPassword)
            try {withContext(Dispatchers.IO) {writeBackup(bytes,{requireNotNull(context.contentResolver.openOutputStream(uri)) {"Could not open backup destination"}}) {preferences.markBackupExported(System.currentTimeMillis())};reminder.syncNotifications()}}
            finally {bytes.fill(0)}
        } finally {exportPassword.fill('\u0000');model.clearExport()}
    } else model.clearExport()}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> if(uri!=null) model.action("Backup validated. Review before restoring.") {
        val bytes=withContext(Dispatchers.IO) {requireNotNull(context.contentResolver.openInputStream(uri)).use {input->val buffer=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192);var count=input.read(chunk);while(count>=0) {require(buffer.size()+count<=32*1024*1024) {"Backup is too large"};buffer.write(chunk,0,count);count=input.read(chunk)};buffer.toByteArray()}}
        val secret=password.toCharArray()
        try {model.prepareRestore(bytes,service.inspect(bytes,secret),password)} finally {secret.fill('\u0000');bytes.fill(0)}
    }}
    var details by rememberSaveable {mutableStateOf(false)}
    Page("Backup & restore") {
        item {Column(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            ReferenceIcon(R.drawable.ic_document_outline,size=76.dp)
            Text("Keep a copy of your data",style=MaterialTheme.typography.titleLarge)
            Text("Export a backup file to your device, or restore from a previous backup.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }}
        item {GroupedCard {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            ReferenceValueRow("Last export",if(status.lastExport==0L) "Not yet" else Instant.ofEpochMilli(status.lastExport).atZone(ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")))
            OutlinedTextField(password,model::setBackupPassword,enabled=!appState.busy,modifier=Modifier.fillMaxWidth(),shape=Tokens.field,label={Text("Password (optional)")},visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(model::toggleBackupPassword) {Icon(painterResource(R.drawable.ic_visibility),if(visible) "Hide password" else "Show password")}},colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Tokens.paper,focusedContainerColor=Tokens.paper,unfocusedBorderColor=Tokens.border,focusedBorderColor=Tokens.accent))
            Text(if(password.isEmpty()) "Add a password to encrypt your backup file." else "Password-protected encryption. Keep your password safe.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            EditorialPrimaryButton("Export backup",!appState.busy) {model.prepareExport(password);export.launch("smart-expense-${java.time.LocalDate.now()}.setbackup")}
        }}}
        item {GroupedCard {GroupedRow("Choose backup to restore",R.drawable.ic_document_outline) {if(!appState.busy) import.launch(arrayOf("application/octet-stream","application/json","*/*"))}}}
        item {Text("Password cannot be recovered. Keep your file safe.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
        item {GroupedCard {ReferenceToggleRow("Weekly backup reminder",status.reminderEnabled,{enabled->
            if(enabled && android.os.Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else setReminder(enabled)
        },enabled=!appState.busy)}}
        if(denied) item {ReferenceInfo("Notification permission was not granted. Backup reminders remain off.")}
        item {TextButton({details=!details}) {Text(if(details) "Hide backup details" else "Backup details & moving phones")}}
        if(details) item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Backups with a password use AES-256-GCM encryption. Without a password, the exported file is not encrypted.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            Text("Export to a safe location, move the file to your other phone, then choose Backup & restore there. Restore replaces that phone’s current records.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            Text("Last export records a completed export, not whether the file still exists. Weekly reminders follow your selected schedule, or the existing seven-day interval. No cloud upload.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
        }}}
    }
    if(preview!=null) EditorialBottomSheet("Restore preview",{if(!appState.busy) model.clearRestore()}) {
        Text("Replace local data?",style=MaterialTheme.typography.titleMedium)
        GroupedCard {
            Text(preview,Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)
            ReferenceValueRow("File status","Validated file",Tokens.accent)
        }
        ReferenceInfo("Restoring replaces all current records. Export your current data first if you want to keep it.",warning=true)
        Button({model.action("Backup restored") {model.restorePrepared()}},Modifier.fillMaxWidth().heightIn(min=50.dp),enabled=!appState.busy,shape=Tokens.field,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.errorContainer,contentColor=Tokens.negative)) {Text("Replace local data")}
    }
}

