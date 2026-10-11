package com.smartexpense.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
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

@Composable fun BackupScreen(model:AppViewModel) {
    val context=LocalContext.current;val service=remember {model.backup}
    val backupUi by model.backupUi.collectAsStateWithLifecycle()
    val password=backupUi.password
    val visible=backupUi.visible
    val preview=backupUi.restoreSummary
    val appState by model.state.collectAsStateWithLifecycle()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri -> if(uri!=null) model.action("Backup exported") {
        val exportPassword=model.exportPassword()
        try {val bytes=service.export(exportPassword);withContext(Dispatchers.IO) {requireNotNull(context.contentResolver.openOutputStream(uri)).use {it.write(bytes)}}} finally {exportPassword.fill('\u0000');model.clearExport()}
    } else model.clearExport()}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> if(uri!=null) model.action("Backup validated. Review before restoring.") {
        val bytes=withContext(Dispatchers.IO) {requireNotNull(context.contentResolver.openInputStream(uri)).use {input->val buffer=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192);var count=input.read(chunk);while(count>=0) {require(buffer.size()+count<=32*1024*1024) {"Backup is too large"};buffer.write(chunk,0,count);count=input.read(chunk)};buffer.toByteArray()}}
        val secret=password.toCharArray()
        try {model.prepareRestore(bytes,service.inspect(bytes,secret),password)} finally {secret.fill('\u0000');bytes.fill(0)}
    }}
    Page("Backup & restore","Choose local files using Android's document picker.") {
        item {Panel {
            Text("Optional backup password",style=MaterialTheme.typography.titleLarge)
            TextField(password,model::setBackupPassword,enabled=!appState.busy,modifier=Modifier.fillMaxWidth(),shape=Tokens.field,label={Text("Password (optional)")},visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(model::toggleBackupPassword) {Icon(painterResource(R.drawable.ic_visibility),if(visible) "Hide password" else "Show password")}},colors=TextFieldDefaults.colors(unfocusedContainerColor=Tokens.inputSurface,focusedContainerColor=Tokens.inputSurface,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,focusedIndicatorColor=Tokens.accent))
            Text("Password backups use AES-256-GCM and PBKDF2. Keep your password safe; it cannot be recovered.",color=Tokens.muted)
            EditorialPrimaryButton("Export backup",!appState.busy) {model.prepareExport(password);export.launch("smart-expense-${java.time.LocalDate.now()}.setbackup")}
            OutlinedButton({import.launch(arrayOf("application/octet-stream","application/json","*/*"))},Modifier.fillMaxWidth(),enabled=!appState.busy) {Text("Choose backup to restore")}
            Text("App data stays in private local storage. Android cloud backup is disabled. The database is not separately encrypted.",color=Tokens.muted)
        }}
    }
    if(preview!=null) EditorialConfirmSheet("Replace local data?","Validated backup: $preview. Restoring replaces all current app records. Export your current data first if you want to keep it.","Restore",{if(!appState.busy) model.clearRestore()},enabled=!appState.busy) {model.action("Backup restored") {model.restorePrepared()}}
}
