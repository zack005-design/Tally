package com.smartexpense

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReferenceFidelityFlowTest {
    @get:Rule val compose=createComposeRule()
    @Test fun sharedBillChoosesMethodBeforeReviewAndNeverSavesEarly() {
        var saved:EntryDraft?=null
        compose.setContent {
            var form by remember {mutableStateOf(EntryUiState(amount="330.50",description="Dinner",accountId="w",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "300.50","f" to "30")))}
            TrackerTheme {EntryScreen(form,listOf(Account("w","Wallet","Cash",100000,false)),listOf(Person("self","You"),Person("f","Rahul")),emptyList(),emptyList(),{form=it},{},{saved=form.draft()})}
        }
        compose.onNodeWithText("1 of 5").assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("2 of 5").assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("3 of 5").assertExists()
        compose.onNodeWithText("Equal split").assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("5 of 5").assertExists()
        assertNull(saved)
        compose.onNodeWithText("Save shared bill").performClick()
        assertEquals(mapOf("self" to 30050L,"f" to 3000L),saved!!.contributions)
    }
    @Test fun itemizedStagePreservesFractionalSharesAndActualPayments() {
        var saved:EntryDraft?=null
        compose.setContent {
            var form by remember {mutableStateOf(EntryUiState(amount="30.50",description="Snack",accountId="w",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "30.50","f" to "0"),method="Itemized",items="20.50=self\n10=f",billItemNames=listOf("Coffee","Tea")))}
            TrackerTheme {EntryScreen(form,listOf(Account("w","Wallet","Cash",100000,false)),listOf(Person("self","You"),Person("f","Rahul")),emptyList(),emptyList(),{form=it},{},{saved=form.draft()})}
        }
        repeat(3) {compose.onNodeWithText("Continue").performClick()}
        compose.onNodeWithText("4 of 5").assertExists()
        compose.onNodeWithText("Review").performClick()
        assertNull(saved)
        compose.onNodeWithText("Save shared bill").performClick()
        assertEquals(mapOf("self" to 2050L,"f" to 1000L),Finance.split(saved!!.amount,saved!!.participants,saved!!.splitRule))
        assertEquals(mapOf("self" to 3050L,"f" to 0L),saved!!.contributions)
    }
    @Test fun participantGroupCancelPreservesTheEntireItemizedDraft() {
        val initial=EntryUiState(amount="30.50",description="Snack",accountId="w",groupId="a",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "30.50","f" to "0"),method="Itemized",items="20.50=self\n10=f",billItemNames=listOf("Coffee","Tea"),itemCharges="0",itemDiscount="0")
        var current=initial
        compose.setContent {var form by remember {mutableStateOf(initial)};TrackerTheme {EntryScreen(form,listOf(Account("w","Wallet","Cash",100000,false)),listOf(Person("self","You"),Person("f","Rahul"),Person("z","Ananya")),emptyList(),listOf(Group("a","Original group",listOf("self","f")),Group("b","Other group",listOf("self","z"))),{form=it;current=it},{},{})}}
        compose.onNodeWithText("Add a friend").performScrollTo().performClick()
        compose.onNodeWithText("Use a saved group").performClick()
        compose.onNodeWithText("Other group").performScrollTo().performClick()
        compose.onAllNodesWithText("Cancel").onLast().performClick()
        assertEquals(initial,current)
    }
    @Test fun cancelingGpayTargetDoesNotChangeTheSavedSchedule() {
        var saved:Long?=null
        val snapshot=Snapshot(accounts=listOf(Account("w","Wallet","Cash",0,false),Account("g","GPay","Bank",500000,false)),walletSources=WalletSources("w","g","2026-10"))
        compose.setContent {TrackerTheme {GpaySettingsScreen(snapshot,false,{_,_->},{_,target,_,_,_->saved=target},{})}}
        compose.onNodeWithText("Enable monthly reset").performScrollTo().performClick()
        compose.onNodeWithText("Target amount").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Reset balance (₹)").performTextReplacement("999")
        compose.onAllNodesWithText("Cancel").onLast().performClick()
        compose.onNodeWithText("Save settings").performScrollTo().performClick()
        assertEquals(500000L,saved)
    }
    @Test fun transactionReportOmitsSummaryAndReversedEntries() {
        val at=java.time.LocalDate.of(2026,10,20).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val records=listOf(Record("a",Kind.EXPENSE,"Coffee",2050,2050,null,null,at,"",false),Record("b",Kind.EXPENSE,"Reversed",100,100,null,null,at,"",true))
        val review=MonthlyReview("2026-10",emptyMap(),emptyMap(),0,0,0,0,2050,2050,0,0,0,true)
        val rows=ReportCsv.rows(Snapshot(records=records),review,"Transactions")
        assertTrue(rows.any {"Coffee" in it && "20.50" in it})
        assertFalse(rows.any {"Reversed" in it || "Opening balance" in it})
    }
}
