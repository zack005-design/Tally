package com.smartexpense

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorialPickerTest {
    @get:Rule val compose=createComposeRule()
    @Test fun calendarIsCustomAndCancelLeavesValueUntouched() {
        var value="2024-02-29T15:37"
        compose.setContent {TrackerTheme {Column {DateTimeField(value) {value=it}}}}
        compose.onNodeWithText("29 February 2024").performClick()
        compose.onNodeWithText("Use date →").assertExists()
        // The sheet header and footer both offer cancellation; exercise the header action.
        compose.onAllNodesWithText("Cancel").onFirst().performClick()
        assertEquals("2024-02-29T15:37",value)
    }
    @Test fun changingDateRetainsTimeAndHandlesLeapMonth() {
        var value="2024-02-29T15:37"
        compose.setContent {TrackerTheme {Column {DateTimeField(value) {value=it}}}}
        compose.onNodeWithText("29 February 2024").performClick()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithContentDescription("15 March 2024").performClick()
        compose.onNodeWithText("Use date →").performClick()
        assertEquals("2024-03-15T15:37",value)
    }
    @Test fun choiceOffersSearchAndPreservesStableId() {
        var selected="a"
        compose.setContent {TrackerTheme {Choice("Account",selected,listOf("a" to "Cash","b" to "Bank")) {selected=it}}}
        compose.onNodeWithText("Account").performClick()
        compose.onNodeWithText("Search options").performTextInput("Bank")
        compose.onNode(hasText("Bank") and !hasSetTextAction()).performClick()
        assertEquals("b",selected)
    }
}
