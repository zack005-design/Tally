package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ChatHistoryImportUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unresolvedRowsRemainVisibleAndCannotBeSelected() {
        val source = ChatHistoryEntry("one", "REPAYMENT", description = "Friend paid back", amount = "100.00", unresolved = listOf("Use repayment workflow"))
        val row = HistoryImportRow(source, null, listOf("Use repayment workflow"), false, false)
        var toggled = false
        compose.setContent { TrackerTheme { HistoryImportRowCard(row, false, true) { toggled = true } } }
        compose.onNodeWithText("Friend paid back").assertIsDisplayed()
        compose.onNodeWithText("Use repayment workflow").assertIsDisplayed()
        compose.onNodeWithContentDescription("Select Friend paid back").assertIsNotEnabled()
        assertFalse(toggled)
    }
    @Test fun supportedDuplicateShowsWarningAndSelectionChanges() {
        val source = ChatHistoryEntry("one", "EXPENSE", "2026-10-20", "Lunch", "250.00", "Cash")
        val row = HistoryImportRow(source, EntryDraft(amount = 25000, description = "Lunch", accountId = "cash"), emptyList(), true, false)
        var toggled = false
        compose.setContent { TrackerTheme { HistoryImportRowCard(row, false, true) { toggled = true } } }
        compose.onNodeWithText("Possible duplicate — review before selecting").assertIsDisplayed()
        compose.onNodeWithContentDescription("Select Lunch").performClick()
        compose.runOnIdle { assertTrue(toggled) }
    }
}
