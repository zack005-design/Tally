package com.smartexpense

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UiLayoutAuditTest {
    @get:Rule val compose = createComposeRule()

    private fun render(scale: Float = 2f, width: Int = 320, content: @Composable () -> Unit) {
        compose.setContent {
            val pixels = LocalContext.current.resources.displayMetrics.widthPixels
            CompositionLocalProvider(LocalDensity provides Density(pixels / width.toFloat(), scale)) {
                TrackerTheme { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-audit").apply { mkdirs() }
        val file = File(directory, "$name.png")
        try { file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { screenshot.recycle() }
        listOf("mkdir -p /sdcard/Download/ui-audit", "cp ${file.absolutePath} /sdcard/Download/ui-audit/$name.png").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {
                assertEquals("", it.readBytes().toString(Charsets.UTF_8).trim())
            }
        }
    }

    @Test fun activityHeaderDoesNotSqueezeTitleBetweenActionsAtLargeText() {
        render { ActivityScreen(Snapshot(), {}) }
        val title=layout("Transactions")
        capture("activity-320dp-2x")
        assertFalse("Transactions layout width=${title.size.width} height=${title.size.height} lines=${title.lineCount} paragraphWidth=${title.multiParagraph.width} paragraphHeight=${title.multiParagraph.height}",title.didOverflowWidth || title.didOverflowHeight)
        assertEquals("Transactions".length,title.getLineEnd(title.lineCount-1,visibleEnd=true))
        capture("activity-320dp-2x")
        compose.onNodeWithText("Search transactions").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Filter"))
        compose.onNodeWithText("Filter").assertIsDisplayed().performClick()
        compose.onNodeWithText("Filter transactions").assertExists()
    }

    @Test fun plannerSegmentsKeepWholeLabelsAndExposeSelection() {
        val selected = mutableStateOf("available")
        render(1.3f) {
            Page("Planner") { item { Panel {
                EditorialSegmentedControl(selected.value, listOf("available" to "Available money", "income" to "Expected income")) { selected.value = it }
            } } }
        }
        capture("segments-320dp-1.3x")
        listOf("Available money", "Expected income").forEach { text ->
            val result = layout(text)
            assertFalse("$text overflows vertically", result.didOverflowHeight)
            assertEquals("$text loses characters", text.length, result.getLineEnd(result.lineCount - 1, visibleEnd = true))
            assertTrue("$text crosses its layout bounds", result.getBoundingBox(text.lastIndex).right <= result.size.width + 1f)
        }
        compose.onNodeWithText("Available money").assertIsSelected()
        compose.onNodeWithText("Expected income").performClick().assertIsSelected()
        capture("segments-320dp-1.3x")
    }

    @Test fun largeTextTransactionsKeepIncomeSignAndSharedBillContext() {
        val stage = mutableIntStateOf(0)
        render {
            val record = if (stage.intValue == 0) Record("income", Kind.INCOME, "Salary", 10000, 0, null, null, 0, "", false, emptyList(), listOf("self"))
                else Record("meal", Kind.EXPENSE, "Shared meal", 90000, 30000, null, null, 0, "", false, emptyList(), listOf("self", "friend"))
            TransactionRow(record) {}
        }
        compose.onNodeWithText("+₹100").assertIsDisplayed()
        compose.runOnIdle { stage.intValue = 1 }
        compose.onNodeWithText("Your share ₹300.00").assertIsDisplayed()
        capture("transaction-320dp-2x")
    }

    @Test fun resetLabelTogglesItsSwitchAndAnnouncesItsState() {
        val snapshot = Snapshot(accounts = listOf(Account("w", "Wallet", "Cash", 10000, false), Account("g", "GPay", "Bank", 10000, false)), walletSources = WalletSources("w", "g", "2026-10"))
        render(1f) { GpaySettingsScreen(snapshot, false, { _, _ -> }, { _, _, _, _, _ -> }, {}) }
        compose.onNodeWithText("Enable monthly reset").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Target amount").assertExists()
        capture("gpay-switch-320dp")
    }

    @Test fun pairedSummaryCardsKeepEqualHeightWhenDescriptionWraps() {
        render(1f, 393) {
            Page("Insights") { item {
            AdaptivePair(equalCards = true,
                first = { Panel(Modifier.fillMaxHeight().semantics(mergeDescendants = true) {}) { Text("Spending summary"); Text("Previous: ₹100") } },
                second = { Panel(Modifier.fillMaxHeight().semantics(mergeDescendants = true) {}) { Text("Income summary"); Text("Average daily spending for the selected month") } })
            } }
        }
        val first = compose.onNodeWithText("Spending summary").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Income summary").fetchSemanticsNode().boundsInRoot
        assertEquals("Summary cards should align at the bottom", first.height, second.height, 1f)
        capture("equal-summary-cards")
    }
}


