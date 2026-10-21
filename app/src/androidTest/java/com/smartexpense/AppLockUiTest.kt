package com.smartexpense

import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.data.DeviceLockView
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test

class AppLockUiTest {
    @get:Rule val compose=createComposeRule()
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
