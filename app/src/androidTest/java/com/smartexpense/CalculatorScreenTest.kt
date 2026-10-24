package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import com.smartexpense.ui.CalculatorScreen
import com.smartexpense.ui.TrackerTheme
import org.junit.Rule
import org.junit.Test

class CalculatorScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun calculate() {
        compose.onNode(hasText("Calculate") and hasClickAction()).performScrollTo()
        compose.waitForIdle()
        compose.onNode(hasText("Calculate") and hasClickAction()).assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    @Test fun naturalCalculationAndEditingClearOldAnswer() {
        compose.setContent { TrackerTheme { CalculatorScreen() } }
        compose.onNodeWithText("What would you like to calculate?").performScrollTo().performTextInput("0.1 + 0.2")
        calculate()
        // The result follows the examples in a lazy list and may not be composed yet.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Supports arithmetic", substring = true))
        compose.waitUntil(5000) { compose.onAllNodesWithText("0.3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0.3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("What would you like to calculate?").performScrollTo().performTextReplacement("1/0")
        compose.onNodeWithText("0.3").assertDoesNotExist()
    }

    @Test fun guidedSplitWorksAtLargeFontAndNarrowWidth() {
        compose.setContent { TrackerTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(320.dp)) { CalculatorScreen() }
            }
        } }
        compose.onNodeWithText("Split a bill").performScrollTo().performClick()
        compose.onNodeWithText("Bill total in rupees").performScrollTo().performTextInput("100")
        compose.onNodeWithText("How many people?").performScrollTo().performTextInput("3")
        calculate()
        compose.waitUntil(5000) { compose.onAllNodesWithText("₹33.34 + ₹33.33 + ₹33.33").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("₹33.34 + ₹33.33 + ₹33.33").performScrollTo().assertIsDisplayed()
    }
}
