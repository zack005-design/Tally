package com.smartexpense

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.smartexpense.data.SharedRefundContext
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SharedRefundUiTest(private val width:Int,private val fontScale:Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="width={0} font={1}")
        fun sizes()=listOf(arrayOf<Any>(320,2f),arrayOf<Any>(412,1f))
    }
    @get:Rule val compose=createComposeRule()
    @Test fun refundRequiresReviewAndSavesSeparateSharesAndCash() {
        val original=Record("bill",Kind.EXPENSE,"Dinner",90000,45000,null,null,0,"",false,personIds=listOf("self","friend"))
        val snapshot=Snapshot(accounts=listOf(Account("cash","Cash","Cash",100000,false)),people=listOf(Person("self","You"),Person("friend","Friend")))
        var saved:SharedRefundDraft?=null
        val restoration=StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides Density(androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width/412f,fontScale)) {
                TrackerTheme {Box(Modifier.width(width.dp).fillMaxHeight()) {SharedRefundEditor(SharedRefundContext(original,mapOf("self" to 45000L,"friend" to 45000L)),snapshot,false,{saved=it},{})}}
            }
        }
        compose.onNodeWithText("Refund total (₹)").performTextInput("300")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Review refund").performScrollTo().assertIsDisplayed().performClick()
        assertNull(saved)
        compose.onNodeWithText("Your spending reduction").assertExists()
        compose.onNodeWithText("Save refund").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(15000L,saved!!.shares.getValue("self"))
            assertEquals(30000L,saved!!.received.getValue("self"))
        }
    }

    @Test fun savedRefundDetailsNameCashAsReceived() {
        val record=Record("refund",Kind.REFUND,"Dinner refund",30000,15000,null,null,0,"",false)
        val detail=RecordDetail(record,listOf(Movement("cash",30000)),mapOf("self" to 15000L,"friend" to 15000L),mapOf("self" to 30000L,"friend" to 0L),emptyList(),emptyMap())
        val snapshot=Snapshot(people=listOf(Person("self","You"),Person("friend","Friend")))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width/412f,fontScale)) {
                TrackerTheme {Box(Modifier.width(width.dp).fillMaxHeight()) {TransactionDetailView(record,detail,snapshot,onActions={})}}
            }
        }
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("Friend"))
        compose.onAllNodesWithText("Received").assertCountEquals(2)
        compose.onAllNodesWithText("Paid").assertCountEquals(0)
        compose.onNodeWithText("Refund shares and cash received").assertExists()
    }
}
