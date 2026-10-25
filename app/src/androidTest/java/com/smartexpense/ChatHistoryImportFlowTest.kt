package com.smartexpense

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ChatHistoryImportFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun scrollTo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
        return compose.onNode(matcher)
    }
    @Test fun pickerCancellationThenReviewedBatchImportsOnlyConfirmedRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TrackerDatabase::class.java).build()
        val file = File.createTempFile("synthetic-history-", ".json", context.cacheDir)
        try {
            val repo = FinanceRepository(db); repo.initialize(); val cash = repo.createAccount("Cash", "Cash", 100000)
            val before = repo.snapshot()
            file.writeText("""{"format":"tally-chat-history","formatVersion":1,"currency":"INR","historyId":"flow-synthetic","coverage":"20 October 2026; synthetic test only","entries":[{"sourceId":"one","kind":"EXPENSE","date":"2026-10-20","description":"Lunch","amount":"250.00","account":"Cash"},{"sourceId":"two","kind":"REPAYMENT","description":"Unresolved repayment","amount":"50.00","unresolved":["Use repayment workflow"]}]}""")
            var launches = 0
            val registry = object: ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    launches++
                    dispatchResult<Uri?>(requestCode, if (launches == 1) null else Uri.fromFile(file))
                }
            }
            val owner = object: ActivityResultRegistryOwner { override val activityResultRegistry = registry }
            compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { TrackerTheme { ChatHistoryImportScreen(repo, before) {} } } }
            scrollTo(hasText("Choose history file")).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Choose history file").assertIsDisplayed()
            assertEquals(before, repo.snapshot())
            compose.onNodeWithText("Choose history file").performClick()
            compose.waitUntil(20000) { compose.onAllNodesWithText("Choose another file").fetchSemanticsNodes().isNotEmpty() }
            scrollTo(hasText("Select ready records")).performClick()
            scrollTo(hasText("Preview balance effects")).performClick()
            compose.waitUntil(20000) { compose.onAllNodesWithText("Preparing import…").fetchSemanticsNodes().isEmpty() }
            scrollTo(hasText("Confirm this batch")).assertIsDisplayed()
            assertEquals(before, repo.snapshot())
            scrollTo(hasText("1 will be added · 0 already recorded · 1 excluded")).assertIsDisplayed()
            scrollTo(hasContentDescription("Confirm reviewed records, possible duplicates and excluded entries")).performClick()
            scrollTo(hasText("Confirm import")).assertIsEnabled().performClick()
            compose.waitUntil(20000) { compose.onAllNodesWithText("Import saved").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1 added · 0 already recorded · 1 excluded").assertExists()
            assertEquals(75000L, repo.snapshot().accounts.single { it.id == cash }.balance)
            assertEquals(1, repo.snapshot().records.count {it.description == "Lunch"})
            assertFalse(repo.snapshot().records.any {it.description == "Unresolved repayment"})
        } finally { file.delete(); db.close() }
    }
}
