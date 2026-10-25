package com.smartexpense

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.*
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File

class LocalFeaturePreviewTest {
    @get:Rule val compose=createComposeRule()
    private val store=ViewModelStore()
    private var database:TrackerDatabase?=null
    @After fun close() {compose.runOnIdle {store.clear()};database?.close()}
    private fun matrix(width:Int,scale:Float) {
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build();database=db
        val repo=FinanceRepository(db);runBlocking {repo.initialize()}
        lateinit var model:AppViewModel
        compose.runOnIdle {model=AppViewModel(repo,BackupService(db),BillScanner(context),SavedStateHandle());store.put("preview",model)}
        var screen by mutableStateOf("lock")
        compose.setContent {
            val pixels=androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
            CompositionLocalProvider(LocalDensity provides Density(pixels/width.toFloat(),scale)) {TrackerTheme {Box(Modifier.fillMaxSize()) {
                when(screen) {
                    "lock"->SecureLedgerSurface(DeviceLockView(true,false),{}) {androidx.compose.material3.Text("Hidden ledger")}
                    "lock-settings"->AppLockSettingsContent(DeviceLockView(true,true,credentialAvailable=true),{})
                    "reminders"->RemindersScreen(model)
                    "bills"->BillImportScreen(model.billScanner,repo,Snapshot(),{_,_,_->},{})
                    "export"->LocalExportScreen(model)
                }
            }}}
        }
        val directory=File(context.getExternalFilesDir(null),"local-finance-previews").apply {mkdirs()}
        listOf("lock","lock-settings","reminders","bills","export").forEach {name->
            compose.runOnIdle {screen=name};compose.waitForIdle()
            if(name=="lock") compose.onNodeWithText("Hidden ledger").assertDoesNotExist()
            val screenshot=compose.onRoot().captureToImage().asAndroidBitmap()
            try {File(directory,"$name-$width-${scale}x.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {screenshot.recycle()}
        }
    }
    @Test fun normal()=matrix(393,1f)
    @Test fun narrowLargeText()=matrix(320,2f)
}
