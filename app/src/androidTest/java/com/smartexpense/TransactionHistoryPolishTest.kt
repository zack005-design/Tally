package com.smartexpense

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.smartexpense.domain.Kind
import com.smartexpense.domain.Record
import com.smartexpense.domain.Snapshot
import com.smartexpense.ui.ActivityScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class TransactionHistoryPolishTest {
    @get:Rule val compose = createComposeRule()
    private val timestamp = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()
    private fun record(index: Int) = Record(
        "record-$index", Kind.EXPENSE, "Expense $index", 10000, 10000,
        null, null, timestamp, "", false
    )

    @Test fun longDayOnlyComposesVisibleTransactionsAndKeepsDetailsReachable() {
        var opened = ""
        compose.setContent {
            EditorialPreviewTheme {
                ActivityScreen(Snapshot(records = List(180, ::record)), { opened = it })
            }
        }
        compose.onNodeWithText("Expense 0").assertIsDisplayed()
        compose.onNodeWithText("Expense 179").assertDoesNotExist()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Expense 179"))
        compose.onNodeWithText("Expense 179").assertIsDisplayed().performClick()
        assertEquals("record-179", opened)
    }

    @Test fun emptySearchCanBeResetWithoutOpeningFilterSheet() {
        compose.setContent {
            EditorialPreviewTheme { ActivityScreen(Snapshot(records = listOf(record(0))), {}) }
        }
        compose.onNodeWithText("Search transactions").performTextInput("no such expense")
        compose.onNodeWithText("Search transactions").performImeAction()
        compose.onNodeWithText("No matching activity").assertIsDisplayed()
        compose.onNodeWithText("Reset search & filters").assertIsDisplayed().performClick()
        compose.onNodeWithText("Expense 0").assertIsDisplayed()
        compose.onNodeWithText("No matching activity").assertDoesNotExist()
    }

    @Test fun searchIgnoresAccidentalSurroundingSpaces() {
        compose.setContent {
            EditorialPreviewTheme { ActivityScreen(Snapshot(records = listOf(record(12))), {}) }
        }
        compose.onNodeWithText("Search transactions").performTextInput("  expense 12  ")
        compose.onNodeWithText("Search transactions").performImeAction()
        compose.onNodeWithText("Expense 12").assertIsDisplayed()
    }

    @Test fun filterControlsRemainReachableWithLargeText() {
        compose.setContent {
            val width = LocalResources.current.displayMetrics.widthPixels
            CompositionLocalProvider(LocalDensity provides Density(width / 320f, 2f)) {
                EditorialPreviewTheme { ActivityScreen(Snapshot(records = listOf(record(0))), {}) }
            }
        }
        compose.onNodeWithText("All dates", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Use month →").performScrollTo().performClick()
        compose.onNodeWithText("Filter").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Apply filters").performScrollTo().assertIsDisplayed().performClick()
    }
}
