package com.smartexpense

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.Assert.assertTrue

@RunWith(Parameterized::class)
class EditorialAdaptiveTest(private val width:Int,private val scale:Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="width={0} font={1}")
        fun sizes()=listOf(320,360,393,412,440,600).flatMap {w->listOf(1f,1.3f,1.5f,2f).map {f->arrayOf<Any>(w,f)}}
    }
    @get:Rule val compose=createComposeRule()
    @Test fun allFiveScreensKeepAmountsActionsAndNavigationReachable() {
        val stage=mutableIntStateOf(0)
        val people=listOf(Person("self","You"),Person("r","Rahul"),Person("k","Kevin"),Person("a","Arjun"))
        val accounts=listOf(Account("bank","Bank","Bank",2458050,false))
        val snapshot=Snapshot(accounts=accounts,people=people,records=listOf(Record("meal",Kind.EXPENSE,"Shared dinner",89000,22250,null,null,0,"",false,listOf("bank"),people.map {it.id})))
        val split=EntryUiState(amount="890",description="Dinner with friends",accountId="bank",shared=true,participants=people.map {it.id},paid=mapOf("self" to "500","r" to "290","k" to "100","a" to "0"))
        compose.setContent {
            val density=LocalDensity.current
            // Fit the entire requested logical width, including wide phones and tablets,
            // into the emulator's physical display instead of silently testing 412dp again.
            CompositionLocalProvider(LocalDensity provides Density(androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width/maxOf(width,412).toFloat(),scale)) {
                TrackerTheme {Box(Modifier.width(width.dp).fillMaxHeight()) {
                    Scaffold(bottomBar={LedgerNavigation(if(stage.intValue==0) "home" else "add",{})}) {padding->Box(Modifier.fillMaxSize().padding(padding)) {
                        key(stage.intValue) {when(stage.intValue) {
                            0->HomeScreen(snapshot,{_,_->},{},{},{},{})
                            1->AdvancedEntryScreen(EntryUiState(),accounts,people,emptyList(),emptyList(),{},{},{})
                            2->AdvancedEntryScreen(EntryUiState(amount="100",description="Lunch",accountId="bank"),accounts,people,emptyList(),emptyList(),{},{},{})
                            3->AdvancedEntryScreen(split,accounts,people,emptyList(),emptyList(),{},{},{})
                            else->ActivityScreen(snapshot,{})
                        }}
                    }}
                }}
            }
        }
        listOf("Overview","Spending","Friends").forEach {label->
            val tab=compose.onNode(hasText(label) and hasClickAction())
            tab.assertIsDisplayed()
            val bounds=tab.fetchSemanticsNode().boundsInRoot
            assertTrue("$label crosses the navigation width",bounds.left>=0 && bounds.right<=compose.onRoot().fetchSemanticsNode().boundsInRoot.right)
        }
        // With one account, total and account balance are both intentionally shown.
        compose.onAllNodesWithText("₹24,580.50").assertCountEquals(2)
        compose.runOnIdle {stage.intValue=1}

        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.runOnIdle {stage.intValue=2}
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Save transaction"))
        compose.onNodeWithText("Save transaction").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle {stage.intValue=3}
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("₹277.50"))
        compose.onNodeWithText("₹277.50").assertIsDisplayed()
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Save transaction"))
        compose.onNodeWithText("Save transaction").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle {stage.intValue=4}
        compose.onNodeWithText("Filter").assertIsDisplayed().performClick()
        compose.onNodeWithText("Apply filters").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Shared dinner").assertExists()
    }
}




