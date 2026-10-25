package com.smartexpense

import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.smartexpense.data.DeviceLockView
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test

class AppLockUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun lockedSurfacePaintsItsBackgroundAndRespectsSystemBars() {
        var topInset = 0f
        var expectedBackground = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {TrackerTheme {
            topInset = WindowInsets.safeDrawing.getTop(LocalDensity.current).toFloat()
            expectedBackground = Tokens.background
            SecureLedgerSurface(DeviceLockView(true,false),{}) {}
        }}
        val title = compose.onNodeWithText("Tally is locked").fetchSemanticsNode().boundsInRoot
        assertTrue("Lock title must remain below system bars", title.top >= topInset)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(expectedBackground, pixels[pixels.width / 2, pixels.height - 1])
    }

    @Test fun unlockActionsRemainReachableWithLargeTextOnSmallScreen() {
        val requests = mutableListOf<Boolean>()
        compose.setContent {TrackerTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                Box(Modifier.requiredSize(320.dp,480.dp)) {
                    SecureLedgerSurface(DeviceLockView(true,false),{requests.add(it)}) {}
                }
            }
        }}
        compose.onNodeWithText("Unlock Tally").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Use device PIN / pattern / password").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(listOf(false,true),requests)}
    }

    @Test fun authenticationInProgressDisablesBothUnlockActions() {
        compose.setContent {TrackerTheme {
            SecureLedgerSurface(DeviceLockView(true,false,busy=true),{error("Duplicate authentication")}) {}
        }}
        compose.onNodeWithText("Authenticating…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Use device PIN / pattern / password").performScrollTo().assertIsNotEnabled()
    }
    @Test fun lockedLedgerIsAbsentAndEditorSurvivesRelock() {
        var view by mutableStateOf(DeviceLockView(true,false))
        compose.setContent {TrackerTheme {SecureLedgerSurface(view,{}) {
            var draft by rememberSaveable {mutableStateOf("")}
            TextField(draft,{draft=it},label={androidx.compose.material3.Text("Private diary draft")})
        }}}
        compose.onNodeWithText("Private diary draft").assertDoesNotExist()
        compose.onNodeWithText("Unlock Tally").performScrollTo().performClick()
        compose.onNodeWithText("Private diary draft").assertDoesNotExist()
        compose.runOnIdle {view=view.copy(unlocked=true)}
        compose.onNodeWithText("Private diary draft").performTextInput("Saved across locking")
        compose.runOnIdle {view=view.copy(unlocked=false)}
        compose.onNodeWithText("Saved across locking").assertDoesNotExist()
        compose.runOnIdle {view=view.copy(unlocked=true)}
        compose.onNodeWithText("Saved across locking").assertExists()
    }
}
