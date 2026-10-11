package com.smartexpense

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.smartexpense.ui.*
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SecondarySurfaceTest {
    @get:Rule val compose = createComposeRule()
    private var database:TrackerDatabase? = null
    private val models = ViewModelStore()

    private fun createModel():AppViewModel {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        database=db
        val model=AppViewModel(FinanceRepository(db),BackupService(db),SavedStateHandle())
        models.put("secondary-test",model)
        return model
    }

    @After fun closeFixture() {
        compose.runOnIdle {models.clear()}
        database?.close()
    }

    @Test fun newAccountCancelReturnsToCompactListWithoutSaving() {
        lateinit var model:AppViewModel
        compose.runOnIdle {model=createModel()}
        val fixture=Snapshot(accounts=listOf(Account("fixture","Pocket cash","Cash",10000,false)))
        compose.setContent {TrackerTheme {AccountsScreen(fixture,model,{})}}
        compose.onNodeWithText("Account name").assertDoesNotExist()
        compose.onNodeWithText("New account").performClick()
        compose.onNodeWithText("Account name").performTextInput("Unsaved bank")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Account name").assertDoesNotExist()
        compose.onNodeWithText("Pocket cash").assertExists()
        assertEquals(0,runBlocking {model.repository.snapshot().accounts.size})
    }

    @Test fun friendDetailsKeepEditingSeparateAndCancelPreservesAlias() {
        lateinit var model:AppViewModel
        compose.runOnIdle {model=createModel()}
        val fixture=Snapshot(people=listOf(Person("self","You"),Person("friend","Rahul",listOf("R"))))
        compose.setContent {TrackerTheme {PeopleScreen(fixture,model,{})}}
        compose.onNodeWithText("Display name").assertDoesNotExist()
        compose.onNodeWithText("Rahul").performClick()
        compose.onNodeWithText("Original debts").assertExists()
        compose.onNodeWithText("Edit profile").performClick()
        compose.onNodeWithText("Aliases, separated by |").performTextReplacement("Discard me")
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodesWithText("R")[0].assertExists()
        compose.onNodeWithText("Edit profile").performClick()
        compose.onNode(hasSetTextAction() and hasText("R")).assertExists()
    }

    @Test fun cancelEditorEmitsOnlyDismiss() {
        var dismissed = 0
        compose.setContent { TrackerTheme {
            ManagementEditorSurface("New account", { dismissed++ }) { Text("Unsaved account") }
        } }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissed)
    }

    @Test fun repaymentReviewRequiresExplicitConfirmation() {
        var saved = 0
        var dismissed = 0
        compose.setContent { TrackerTheme {
            RepaymentReviewSurface("Rahul", "You", 5000, 15000, "Cash", "2026-10-10T12:30", "Lunch", false,
                { dismissed++ }, { saved++ })
        } }
        assertEquals(0, saved)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, saved)
        compose.onNodeWithText("Confirm repayment").performScrollTo().performClick()
        assertEquals(1, saved)
    }

    @Test fun offsetReviewDoesNotCommitOnCancel() {
        var saved = 0
        var dismissed = 0
        compose.setContent { TrackerTheme {
            OffsetReviewSurface("Rahul", 5000, "All groups", { dismissed++ }, { saved++ })
        } }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, saved)
    }
}

