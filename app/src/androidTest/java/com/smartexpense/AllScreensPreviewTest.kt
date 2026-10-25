package com.smartexpense

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.DeviceLockView
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

/** Additional real-screen previews for routes outside ReferenceScreenshotsTest. */
class AllScreensPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = Snapshot(
        accounts = listOf(Account("w", "Wallet", "Cash", 120000, false), Account("g", "GPay", "Bank", 500000, false)),
        walletSources = WalletSources("w", "g", "2026-10"),
        categories = listOf(Category("food", "Food & drinks", 0, "food", false)),
        records = listOf(Record("lunch", Kind.EXPENSE, "Lunch", 18000, 18000, "food", null,
            Instant.parse("2026-10-21T12:00:00Z").toEpochMilli(), "", false, listOf("w"))),
        reportMonth = "2026-10",
        selectedReport = PeriodReport(18000, 2500000, mapOf("food" to 18000L), emptyMap()),
        dashboard = DashboardMetrics(today = 18000)
    )

    private fun render(name: String, content: @Composable () -> Unit) {
        compose.setContent { EditorialPreviewTheme(content) }
        capture(name)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "all-screens").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun overview() {
        render("overview") {
            Scaffold(bottomBar = { LedgerNavigation("home", {}) }) { padding ->
                Box(Modifier.padding(padding)) { HomeScreen(snapshot, { _, _ -> }, {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("₹6,200").assertIsDisplayed()
    }

    @Test fun spending() { render("spending") { InsightsScreen(snapshot, "2026-10", {}, { _, _, _ -> }) } }
    @Test fun profile() { render("profile") { ProfileScreen(LocalSettings(), false, { _, _, _ -> }, {}) } }
    @Test fun welcome() { render("welcome") { WelcomeScreen(false) {} } }

    @Test fun personalExpenseStages() {
        render("expense") {
            var entry by remember { mutableStateOf(EntryUiState(amount = "180", description = "Lunch", accountId = "w", categoryId = "food")) }
            EntryScreen(entry, snapshot.accounts, emptyList(), snapshot.categories, emptyList(), { entry = it }, {}, {}, walletSources = snapshot.walletSources)
        }
        compose.onNodeWithText("Continue to payments").performClick()
        capture("expense-payment")
        compose.onNodeWithText("Review bill").performClick()
        capture("expense-review")
        compose.onNodeWithText("Save transaction").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun calendarWeek() {
        render("calendar-week") { SpendingCalendarScreen(snapshot, {}, java.time.LocalDate.of(2026, 10, 21)) }
        compose.onNodeWithText("Week").performClick()
        capture("calendar-week")
    }
    @Test fun lockedLedger() {
        render("locked") { SecureLedgerSurface(DeviceLockView(true, false, credentialAvailable = true), {}) { Text("Private ledger") } }
        compose.onNodeWithText("Private ledger").assertDoesNotExist()
        compose.onNodeWithText("Unlock Tally").assertIsDisplayed().assertIsEnabled()
    }
}
