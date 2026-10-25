package com.smartexpense

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.smartexpense.ui.EditorialSegmentedControl
import com.smartexpense.ui.Page
import com.smartexpense.ui.EditorialBottomSheet
import com.smartexpense.ui.EditorialSaveSheet
import com.smartexpense.ui.Field
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SharedScreenQualityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pageBackStaysReachableAfterScrollingALongScreen() {
        var returned = false
        compose.setContent {
            EditorialPreviewTheme {
                Page("Account tools", onBack = { returned = true }) {
                    items(80) { index ->
                        Text("Tool row $index", Modifier.fillMaxWidth().heightIn(min = 60.dp))
                    }
                }
            }
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Tool row 79"))
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        assertTrue(returned)
    }

    @Test fun compactSegmentsHaveFullHeightTargetsAndExposeSelection() {
        var minimumPixels = 0f
        var selected = ""
        compose.setContent {
            minimumPixels = with(LocalDensity.current) { 48.dp.toPx() }
            EditorialPreviewTheme {
                EditorialSegmentedControl("month", listOf("month" to "Month", "week" to "Week")) {
                    selected = it
                }
            }
        }
        compose.onNodeWithText("Month").assertIsSelected()
        val bounds = compose.onNodeWithText("Week").fetchSemanticsNode().boundsInRoot
        assertTrue("Visible segment target is ${bounds.height}px, expected at least ${minimumPixels}px", bounds.height >= minimumPixels - 1f)
        compose.onNodeWithText("Week").performClick()
        assertTrue(selected == "week")
    }

    @Test fun largeTitlePagesKeepTheirBackAction() {
        var returned = false
        compose.setContent {
            EditorialPreviewTheme { Page("Tools", largeTitle = true, onBack = { returned = true }) {} }
        }
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        assertTrue(returned)
    }

    @Test fun parentLabelledPagesKeepTheirProvidedActions() {
        var helped = false
        compose.setContent {
            EditorialPreviewTheme {
                Page("App lock", onBack = {}, actions = { TextButton({ helped = true }) { Text("Help") } }) {}
            }
        }
        compose.onNodeWithText("Help").assertIsDisplayed().performClick()
        assertTrue(helped)
    }

    @Test fun sheetCancelStaysReachableAfterScrollingItsEditor() {
        var dismissed = false
        compose.setContent {
            EditorialPreviewTheme {
                EditorialBottomSheet("Long editor", { dismissed = true }) {
                    repeat(60) { Text("Sheet row $it", Modifier.fillMaxWidth().heightIn(min = 60.dp)) }
                }
            }
        }
        compose.onNodeWithText("Sheet row 59").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }

    @Test fun saveSheetKeepsItsActionVisibleWithAFocusedLargeTextField() {
        var saved = false
        compose.setContent {
            val width = LocalResources.current.displayMetrics.widthPixels
            CompositionLocalProvider(LocalDensity provides Density(width / 320f, 2f)) {
                var value by remember { mutableStateOf("") }
                EditorialPreviewTheme {
                    EditorialSaveSheet("Edit recurring template", {}, true, { saved = true }) {
                        repeat(12) { Text("Editor row $it", Modifier.heightIn(min = 48.dp)) }
                        Field("Description", value, { value = it })
                    }
                }
            }
        }
        compose.onNodeWithText("Description").performScrollTo().performClick().performTextInput("Reviewed")
        compose.onNodeWithText("Save").assertIsDisplayed().performClick()
        assertTrue(saved)
    }
}
