package com.smartexpense

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.smartexpense.ui.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import java.io.File

class IosPolishTest {
    @get:Rule val compose = createComposeRule()

    private fun publishPreview(file: File) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/Download/tally-ios-polish",
            "cp ${file.absolutePath} /sdcard/Download/tally-ios-polish/${file.name}").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
                org.junit.Assert.assertEquals("", it.readBytes().toString(Charsets.UTF_8).trim())
            }
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ios-polish").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(directory, "$name.png")
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        publishPreview(file)
    }

    @Test fun launcherProvidesAThemedIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.applicationInfo.loadIcon(context.packageManager)
        assertTrue(icon is AdaptiveIconDrawable)
        if (android.os.Build.VERSION.SDK_INT >= 33) org.junit.Assert.assertNotNull((icon as AdaptiveIconDrawable).monochrome)
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432)
        icon.draw(Canvas(bitmap))
        val directory = File(context.getExternalFilesDir(null), "ios-polish").apply { mkdirs() }
        val file = File(directory, "launcher.png")
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        publishPreview(file)
    }

    @Test fun navigationRemainsReachableAt320dpAndDoubleText() {
        compose.setContent {
            val pixels = LocalResources.current.displayMetrics.widthPixels
            CompositionLocalProvider(LocalDensity provides Density(pixels / 320f, 2f)) {
                var route by remember { mutableStateOf("home") }
                TrackerTheme { LedgerNavigation(route) { route = it } }
            }
        }
        listOf("Overview", "Spending", "Friends").forEach { label ->
            compose.onNode(hasText(label) and isSelectable()).assertIsDisplayed().performClick().assertIsSelected()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            org.junit.Assert.assertEquals("$label should not break across lines", 1, layouts.single().lineCount)
        }
        compose.onNodeWithContentDescription("Add expense").assertIsDisplayed().assertHasClickAction()
        capture("navigation-large-text")
    }

    @Test fun companionGuideExpandsAndCollapsesWithDoubleText() {
        var prepared = false
        compose.setContent {
            val pixels = LocalResources.current.displayMetrics.widthPixels
            CompositionLocalProvider(LocalDensity provides Density(pixels / 320f, 2f)) {
                TrackerTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    TallyCompanionCard { prepared = true }
                } }
            }
        }
        compose.onNodeWithText("Review with Tally Companion").performClick()
        compose.onNodeWithText("Prepare CSV for Companion").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(prepared)
        capture("companion-large-text")
        compose.onNodeWithText("Review with Tally Companion").performScrollTo().performClick()
        compose.onNodeWithText("Prepare CSV for Companion").assertDoesNotExist()
    }

    @Test fun companionPreparesCsvWithoutChangingTheLedger() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrackerDatabase::class.java).build()
        val repo = FinanceRepository(db)
        runBlocking { repo.initialize(); repo.configureWallet(120000, gpayOpening = 500000) }
        val before = runBlocking { repo.snapshot() }
        val models = ViewModelStore()
        lateinit var model: AppViewModel
        try {
            compose.runOnIdle {
                model = AppViewModel(repo, BackupService(db), BillScanner(context), SavedStateHandle())
                models.put("companion", model)
            }
            compose.setContent { TrackerTheme { LocalExportScreen(model) } }
            compose.waitUntil(10000) { !model.state.value.loading }
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Review with Tally Companion"))
            compose.onNodeWithText("Review with Tally Companion").performClick()
            compose.onNodeWithText("Prepare CSV for Companion").performScrollTo().performClick()
            compose.onNode(hasText("Transactions") and isSelectable()).assertIsSelected()
            compose.onNode(hasText("CSV") and isSelectable()).assertIsSelected()
            compose.onNode(hasText("Export report") and hasClickAction()).assertIsEnabled()
            capture("companion-export")
            val after = runBlocking { repo.snapshot() }
            org.junit.Assert.assertEquals(before.accounts, after.accounts)
            org.junit.Assert.assertEquals(before.records, after.records)
        } finally {
            compose.runOnIdle { models.clear() }
            db.close()
        }
    }

    @Test fun narrowAccountRowKeepsItsLabelReadableBesideALargeBalance() {
        compose.setContent {
            val pixels = LocalResources.current.displayMetrics.widthPixels
            CompositionLocalProvider(LocalDensity provides Density(pixels / 320f, 1f)) {
                TrackerTheme {
                    Box(Modifier.fillMaxWidth()) {
                        GroupedCard {
                            GroupedRow("Everyday spending account", R.drawable.ic_wallet_outline,
                                value = "₹99,99,999.99") {}
                        }
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Everyday spending account", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Account label squeezed to ${layouts.single().lineCount} lines", layouts.single().lineCount <= 2)
        compose.onNodeWithText("₹99,99,999.99").assertIsDisplayed()
        capture("account-row-compact")
    }
}
