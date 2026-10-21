package com.smartexpense

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

/** Covers recently added screens that the original five-screen matrix did not exercise. */
class SecondaryLayoutAuditTest {
    @get:Rule val compose = createComposeRule()

    @Test fun secondaryScreensKeepEditorsAndCompletionActionsReachable() = checkScreens(393, 1f)
    @Test fun secondaryScreensKeepEditorsAndCompletionActionsReachableWithLargeText() = checkScreens(320, 2f)

    private fun checkScreens(width: Int, scale: Float) {
        val stage = mutableIntStateOf(0)
        lateinit var focus: FocusManager
        var keyboard: SoftwareKeyboardController? = null
        fun finishTyping() = compose.runOnIdle { focus.clearFocus(); keyboard?.hide() }
        val snapshot = Snapshot(
            accounts = listOf(Account("w", "Wallet", "Cash", 100000, false), Account("g", "GPay", "Bank", 200000, false)),
            people = listOf(Person("self", "You"), Person("friend", "Alex")),
            groups = listOf(Group("group", "Lunch friends", listOf("self", "friend"))),
            walletSources = WalletSources("w", "g", "2026-10"),
            goals = listOf(SavingsGoal("goal", "Travel fund", 500000))
        )
        compose.setContent {
            val pixels = LocalContext.current.resources.displayMetrics.widthPixels
            CompositionLocalProvider(LocalDensity provides Density(pixels / width.toFloat(), scale)) {
                focus = LocalFocusManager.current
                keyboard = LocalSoftwareKeyboardController.current
                EditorialPreviewTheme {
                    key(stage.intValue) {
                        when (stage.intValue) {
                            0 -> ProfileScreen(LocalSettings(), false, { _, _, done -> done() }, {})
                            1 -> DiaryScreen(snapshot, false, { _, _, done -> done() }, { _, _ -> }, {}, "2026-10-11")
                            2 -> GroupDefaultsScreen(snapshot, false, {}, {})
                            3 -> RecurringScreen(snapshot, false, { _, done -> done() }, {}, { _, _ -> })
                            4 -> GoalsScreen(snapshot, false, { _, _, _, done -> done() }, { _, _, done -> done() }, {})
                            5 -> MonthlyReviewScreen(snapshot, monthlyReview(emptyList(), emptyList(), YearMonth.of(2026, 10)), "2026-10", {}, {})
                            6 -> ManualExchangeScreen { _, _, _, _, _ -> }
                            else -> WelcomeScreen(false) {}
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Your name").performTextInput("Alex")
        finishTyping()
        compose.onNodeWithText("Save profile").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { stage.intValue = 1 }
        compose.onNodeWithText("Daily note").performTextInput("A note for the day")
        finishTyping()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Save daily note"))
        compose.onNodeWithText("Save daily note").assertIsDisplayed().assertIsEnabled()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Add a shared bill for this day"))
        compose.onNodeWithText("Add a shared bill for this day").assertIsDisplayed()
        compose.runOnIdle { stage.intValue = 2 }
        compose.onNodeWithText("Save default").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { stage.intValue = 3 }
        compose.onNodeWithText("Add template").performScrollTo().performClick()
        compose.onNodeWithText("Save recurring template").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { stage.intValue = 4 }
        compose.onNodeWithText("Travel fund").performScrollTo().performClick()
        compose.onNodeWithText("Planned amount (₹)").performTextInput("100")
        finishTyping()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("Save planned allocation")))
            .performTouchInput { swipeUp() }
        compose.onNodeWithText("Save planned allocation").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { stage.intValue = 5 }
        compose.onNodeWithText("Done").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { stage.intValue = 6 }
        compose.onNodeWithContentDescription("Original amount").performTextInput("10")
        compose.onNodeWithContentDescription("INR per one USD").performScrollTo().performTextInput("80")
        finishTyping()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Use in expense"))
        compose.onNodeWithText("Use in expense").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { stage.intValue = 7 }
        compose.onNodeWithText("Next").performScrollTo().performClick()
        compose.onNodeWithText("Next").performScrollTo().performClick()
        compose.onNodeWithText("Create my profile").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
}
