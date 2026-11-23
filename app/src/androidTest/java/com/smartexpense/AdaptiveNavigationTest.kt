package com.smartexpense

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Navigation adapts to short (landscape) windows and large text without losing actions. */
class AdaptiveNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun landscapeRailKeepsEveryDestinationAndAddReachable() {
        var route = ""
        compose.setContent { TrackerTheme { Box(Modifier.size(915.dp, 400.dp)) { EditorialNavigationRail("home") { route = it } } } }
        compose.onNodeWithText("Summary").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Spending").assertIsDisplayed().performClick(); assertEquals("insights", route)
        compose.onNodeWithText("Split").assertIsDisplayed().performClick(); assertEquals("split", route)
        compose.onNodeWithContentDescription("Search").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("search", route)
    }

    @Test fun largeTextOnALargePhoneKeepsAddOnTheTabRow() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                TrackerTheme { Box(Modifier.width(412.dp)) { LedgerNavigation("home") {} } }
            }
        }
        val tab = compose.onNodeWithText("Summary").fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        // The add button shares the tab row instead of taking a second row below it.
        assert(add.top < tab.bottom && add.bottom > tab.top) { "Add button moved to its own row: tab=$tab add=$add" }
    }
}
