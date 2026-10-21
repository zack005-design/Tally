package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExactGroupDefaultsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewUsesTheRealRuleAndSaveKeepsOnlyAGroupDefault() {
        var saved: GroupDefault? = null
        val snapshot = Snapshot(
            people = listOf(Person("self", "You"), Person("r", "Rahul"), Person("a", "Ananya")),
            groups = listOf(Group("flat", "Flatmates", listOf("self", "r", "a")))
        )
        compose.setContent { TrackerTheme { GroupDefaultsScreen(snapshot, false, { saved = it }, {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Preview (₹900 expense)"))
        compose.onAllNodesWithText("₹300").assertCountEquals(3)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Shares"))
        compose.onNodeWithText("Shares").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save default"))
        compose.onNodeWithText("Save default").assertIsNotEnabled()
        listOf("You weight" to "1", "Rahul weight" to "1", "Ananya weight" to "2").forEach { (label, value) ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).performTextInput(value)
            compose.onNodeWithText(label).performImeAction()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Preview (₹900 expense)"))
        compose.onAllNodesWithText("₹225").assertCountEquals(2)
        compose.onNodeWithText("₹450").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save default"))
        compose.onNodeWithText("Save default").performClick()
        compose.runOnIdle {
            assertEquals("flat", saved!!.groupId)
            assertEquals("Weighted", saved!!.method)
            assertEquals(mapOf("self" to "1", "r" to "1", "a" to "2"), saved!!.values)
            assertEquals(mapOf("self" to 22500L, "r" to 22500L, "a" to 45000L), Finance.split(90000, listOf("self", "r", "a"), saved!!.rule(listOf("self", "r", "a"))))
        }
    }
}
