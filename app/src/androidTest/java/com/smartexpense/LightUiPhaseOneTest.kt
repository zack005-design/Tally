package com.smartexpense

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Acceptance coverage for the new shell; fixtures never open the user's database. */
class LightUiPhaseOneTest {
    @get:Rule val compose = createComposeRule()
    private val accounts = listOf(Account("w", "Wallet", "Cash", 120000, false), Account("g", "GPay", "Bank", 500000, false))
    private val snapshot = Snapshot(accounts = accounts, walletSources = WalletSources("w", "g", "2026-10"), dashboard = DashboardMetrics(today = 28000),
        categories = listOf(Category("food", "Food & drinks", 0, "food", false)),
        records = listOf(
            Record("lunch", Kind.EXPENSE, "Lunch", 18000, 18000, "food", null, System.currentTimeMillis(), "", false, listOf("w")),
            Record("coffee", Kind.EXPENSE, "Coffee", 10000, 10000, "food", null, System.currentTimeMillis() - 3600000, "", false, listOf("g"))
        ))

    private fun continueButton(): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Continue to Tally"))
        return compose.onNodeWithText("Continue to Tally")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "light-phase1-$name.png")
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        listOf("mkdir -p /sdcard/Download/tally-light-phase1", "cp ${file.absolutePath} /sdcard/Download/tally-light-phase1/$name.png").forEach { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { output ->
                assertEquals("", output.readBytes().toString(Charsets.UTF_8).trim())
            }
        }
    }

    @Test fun lightPaletteIsUsedEvenWhenDarkIsRequested() {
        var background = Color.Unspecified
        var primary = Color.Unspecified
        compose.setContent { TrackerTheme(darkTheme = true) { background = MaterialTheme.colorScheme.background; primary = MaterialTheme.colorScheme.primary } }
        compose.runOnIdle { assertEquals(Color(0xFFF6F7F9), background); assertEquals(Color(0xFF0066FF), primary) }
    }

    @Test fun navigationIncludesSpendingAndFreshComposeAction() {
        var destination = ""
        compose.setContent { EditorialPreviewTheme { LedgerNavigation("home") { destination = it } } }
        compose.onNodeWithText("Overview").assertIsSelected()
        compose.onNodeWithText("Spending").performClick()
        assertEquals("insights", destination)
        compose.onNodeWithText("Friends").performClick()
        assertEquals("split", destination)
        compose.onNodeWithContentDescription("Add expense").performClick()
        assertEquals("add", destination)
    }

    @Test fun narrowLargeTextNavigationKeepsAllActionsReachable() {
        var destination = ""
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                EditorialPreviewTheme { Box(Modifier.width(320.dp)) { LedgerNavigation("home") { destination = it } } }
            }
        }
        listOf("Overview" to "home", "Spending" to "insights", "Friends" to "split").forEach { (label, route) ->
            compose.onNodeWithText(label).assertIsDisplayed().performClick(); assertEquals(route, destination)
        }
        compose.onNodeWithContentDescription("Add expense").assertIsDisplayed().performClick()
        assertEquals("add", destination)
        capture("navigation-large")
    }

    @Test fun setupRequiresValidBalancesAndSubmitsExactPaise() {
        var saved: Pair<Long, Long>? = null
        compose.setContent { EditorialPreviewTheme { BalanceSetupScreen(Snapshot(), false) { w, g, _, _ -> saved = w to g } } }
        compose.onNodeWithText("Your\nstarting point.").assertExists()
        continueButton().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Wallet balance (₹)").performScrollTo().performTextInput("250.50")
        compose.onNodeWithContentDescription("GPay balance (₹)").performScrollTo().performTextInput("0")
        continueButton().assertIsEnabled().performClick()
        assertEquals(25050L to 0L, saved)
        capture("setup")
    }

    @Test fun setupRejectsNegativeAndBlocksBusySubmission() {
        var busy by mutableStateOf(false)
        compose.setContent { EditorialPreviewTheme { BalanceSetupScreen(Snapshot(), busy) { _, _, _, _ -> fail("Invalid setup saved") } } }
        compose.onNodeWithContentDescription("Wallet balance (₹)").performScrollTo().performTextInput("-1")
        compose.onNodeWithContentDescription("GPay balance (₹)").performScrollTo().performTextInput("100")
        continueButton().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Wallet balance (₹)").performScrollTo().performTextReplacement("0")
        compose.runOnIdle { busy = true }
        continueButton().assertIsNotEnabled()
    }

    @Test fun setupReusesExistingAccountsWithoutReplacingTheirBalances() {
        var saved: List<Any?>? = null
        compose.setContent { EditorialPreviewTheme { BalanceSetupScreen(Snapshot(accounts = accounts), false) { w, g, wId, gId -> saved = listOf(w, g, wId, gId) } } }
        compose.onNodeWithText("Wallet account").performScrollTo().performClick()
        compose.onAllNodesWithText("Wallet").onLast().performClick()
        compose.onNodeWithText("GPay account").performScrollTo().performClick()
        compose.onAllNodesWithText("GPay").onLast().performClick()
        continueButton().performClick()
        assertEquals(listOf(0L, 0L, "w", "g"), saved)
    }

    @Test fun setupLargeTextCanReachAndSubmitBothFields() {
        var saved = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                EditorialPreviewTheme { Box(Modifier.width(320.dp)) { BalanceSetupScreen(Snapshot(), false) { _, _, _, _ -> saved = true } } }
            }
        }
        compose.onNodeWithContentDescription("Wallet balance (₹)").performScrollTo().performTextInput("1200")
        compose.onNodeWithContentDescription("GPay balance (₹)").performScrollTo().performTextInput("5000")
        compose.onNodeWithContentDescription("GPay balance (₹)").performImeAction()
        continueButton().assertIsEnabled().assertIsDisplayed()
        capture("setup-large")
        compose.onNodeWithText("Continue to Tally").performClick()
        compose.runOnIdle { assertTrue(saved) }
    }

    @Test fun setupPreviewShowsComputedTotalBeforeSaving() {
        compose.setContent { EditorialPreviewTheme { BalanceSetupScreen(Snapshot(), false) { _, _, _, _ -> } } }
        compose.onNodeWithContentDescription("Wallet balance (₹)").performScrollTo().performTextInput("1200")
        compose.onNodeWithContentDescription("GPay balance (₹)").performScrollTo().performTextInput("5000")
        compose.onNodeWithContentDescription("GPay balance (₹)").performImeAction()
        compose.onNodeWithText("Your\nstarting point.").performScrollTo()
        compose.onNodeWithText("₹6,200").assertIsDisplayed()
        capture("setup-preview")
    }

    @Test fun overviewShowsRealBalancesAndHidesEveryAccountAmount() {
        var route = ""
        compose.setContent { EditorialPreviewTheme { Scaffold(bottomBar = { LedgerNavigation("home") {} }) { padding ->
            Box(Modifier.padding(padding)) { HomeScreen(snapshot, { _, _ -> }, {}, {}, {}, { route = it }) }
        } } }
        compose.onAllNodesWithText("Overview").onFirst().assertExists()
        compose.onNodeWithText("TOTAL AVAILABLE").assertExists()
        compose.onNodeWithText("₹6,200").assertIsDisplayed()
        capture("overview")
        compose.onNodeWithContentDescription("Hide balance").performClick()
        listOf("₹6,200", "₹1,200", "₹5,000").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithContentDescription("Show balance").performClick()
        compose.onNodeWithText("Add money").performScrollTo().performClick()
        assertEquals("wallet/add-money", route)
        compose.onNodeWithContentDescription("Settings").performScrollTo().performClick()
        assertEquals("more", route)
    }

    @Test fun toolsKeepPlanningReportsAndPrivacyReachable() {
        var route = ""
        compose.setContent { EditorialPreviewTheme { MoreScreen { route = it } } }
        compose.onNodeWithText("Tools").assertExists()
        compose.onNodeWithText("Money & planning").performScrollTo().performClick()
        assertEquals("tools/money", route)
        compose.onNodeWithText("History & reports").performScrollTo().performClick()
        assertEquals("tools/history", route)
        compose.onNodeWithText("Settings & backup").performScrollTo().performClick()
        assertEquals("tools/settings", route)
        capture("tools")
    }
}
