package com.smartexpense

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.ui.*
import com.smartexpense.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class BackupSurfaceTest {
    @get:Rule val compose=createComposeRule()
    private var db:TrackerDatabase?=null
    private val models=ViewModelStore()
    private fun model():AppViewModel {
        val database=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build();db=database
        val repository=FinanceRepository(database)
        runBlocking {repository.initialize()}
        return AppViewModel(repository,BackupService(database),BillScanner(androidx.test.core.app.ApplicationProvider.getApplicationContext()),SavedStateHandle()).also {models.put("backup-surface",it)}
    }
    @After fun close() {compose.runOnIdle {models.clear()};db?.close()}
    private fun capture(name:String) {
        compose.waitForIdle();val i=InstrumentationRegistry.getInstrumentation();val f=File(i.targetContext.getExternalFilesDir(null),"$name.png")
        i.uiAutomation.takeScreenshot().let {b->f.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
        listOf("mkdir -p /sdcard/Download/editorial-extensions","cp ${f.absolutePath} /sdcard/Download/editorial-extensions/$name.png").forEach {cmd->android.os.ParcelFileDescriptor.AutoCloseInputStream(i.uiAutomation.executeShellCommand(cmd)).use {assertEquals("",it.readBytes().toString(Charsets.UTF_8).trim())}}
    }
    @Test fun backupReviewAndDraftSurviveCompositionRecreation() {
        lateinit var m:AppViewModel;compose.runOnIdle {m=model()}
        var show by mutableStateOf(true)
        compose.setContent {EditorialPreviewTheme {if(show) BackupScreen(m) else Text("Recreating")}}
        compose.onNodeWithText("Export backup").assertIsNotEnabled()
        compose.onNodeWithText("Backup password").performTextInput("fixture password")
        compose.onNodeWithText("Export backup").assertIsEnabled()
        capture("backup")
        val bytes=runBlocking {m.backup.export("fixture password".toCharArray())}
        compose.runOnIdle {m.prepareRestore(bytes,"2 accounts · 4 records (fictional preview)","fixture password");show=false}
        compose.runOnIdle {show=true}
        compose.onNodeWithText("Replace local data?").assertExists();capture("restore-review")
        assertEquals("fixture password",m.backupUi.value.password)
        compose.onNodeWithText("Cancel").performClick();assertNull(m.backupUi.value.restoreSummary)
    }
    @Test fun preparedRestoreUsesInspectionPasswordAndClearsAfterSuccess() {
        lateinit var m:AppViewModel;compose.runOnIdle {m=model()}
        runBlocking {
            m.repository.createAccount("Fixture cash","Cash",12345)
            val bytes=m.backup.export("original secret".toCharArray())
            m.prepareRestore(bytes,"Fictional valid backup","original secret")
            m.setBackupPassword("new field value")
            m.restorePrepared()
            assertNull(m.backupUi.value.restoreSummary)
            assertEquals(12345L,m.repository.snapshot().accounts.single().balance)
        }
    }
}

