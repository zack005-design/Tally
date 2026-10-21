package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The reference projection must preserve exact paise through the existing callback. */
class ExactAccountsUiTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = Snapshot(
        accounts = listOf(Account("w", "Wallet", "Cash", 120000, false), Account("g", "GPay", "Bank", 500000, false)),
        walletSources = WalletSources("w", "g", "2026-10")
    )

    @Test fun addMoneyShowsProjectedBalanceAndSubmitsExactAmount() {
        var saved: Triple<String, Long, Boolean>? = null
        compose.setContent {
            EditorialPreviewTheme {
                WalletScreen(snapshot, false, { _, _, _, _ -> }, { id, amount, topup, _ -> saved = Triple(id, amount, topup) },
                    { _, _, _ -> }, {}, {}, initialAddMoney = true)
            }
        }
        compose.onAllNodesWithText("Cancel").assertCountEquals(1)
        compose.onNodeWithContentDescription("Amount to add (₹)").performTextInput("20.50")
        compose.onNodeWithText("Projected balance (Wallet)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹1,220.50").assertExists()
        compose.onAllNodesWithText("Add money").onLast().performScrollTo().performClick()
        assertEquals(Triple("w", 2050L, true), saved)
    }
}
