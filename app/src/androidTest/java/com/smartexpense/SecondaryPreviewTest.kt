package com.smartexpense

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.*
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Native screenshots use fictional snapshots and an isolated in-memory database. */
class SecondaryPreviewTest {
    @get:Rule val compose=createComposeRule()
    private var database:TrackerDatabase?=null
    private val models=ViewModelStore()
    private val people=listOf(Person("self","You"),Person("maya","Maya",listOf("Maya from work")),Person("dev","Dev"))
    private val timestamp=LocalDateTime.of(2026,10,10,12,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val fixture=Snapshot(
        accounts=listOf(Account("bank","Everyday bank","Bank",1250000,false),Account("cash","Pocket cash","Cash",85000,false)),
        people=people,
        groups=listOf(Group("trip","Weekend trip",people.map {it.id})),
        categories=listOf(Category("food","Food",0xFF716F69,"•",false),Category("travel","Travel",0xFF98958E,"↗",false)),
        records=listOf(
            Record("dinner",Kind.EXPENSE,"Dinner together",90000,30000,"food","trip",timestamp,"Saturday dinner",false,listOf("bank"),people.map {it.id}),
            Record("payment",Kind.REPAYMENT,"Repayment",10000,0,null,null,timestamp,"Part payment",false,listOf("bank"),listOf("self","maya"))),
        debts=listOf(Debt("debt","dinner","maya","self",30000,20000,timestamp,"trip")),
        groupSpending=mapOf("trip" to 90000L),transactionCount=2)

    private fun createModel():AppViewModel {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        database=db
        return AppViewModel(FinanceRepository(db),BackupService(db),BillScanner(androidx.test.core.app.ApplicationProvider.getApplicationContext()),SavedStateHandle()).also {models.put("preview",it)}
    }
    private fun render(large:Boolean=false,content:@Composable ()->Unit) {
        compose.setContent {
            val density=LocalDensity.current
            val windowWidth=androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
            // Increasing logical density constrains modal windows as well as the page to 320dp.
            val previewDensity=if(large) windowWidth/320f else density.density
            CompositionLocalProvider(LocalDensity provides Density(previewDensity,if(large) 2f else density.fontScale)) {
                EditorialPreviewTheme(content)
            }
        }
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png")
        val screenshot=checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {"Native screenshot unavailable"}
        try {file.outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {screenshot.recycle()}
        listOf("mkdir -p /sdcard/Download/editorial-extensions","cp ${file.absolutePath} /sdcard/Download/editorial-extensions/$name.png").forEach {command->
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {
                assertEquals("",it.readBytes().toString(Charsets.UTF_8).trim())
            }
        }
    }
    @After fun closeFixture() {
        compose.runOnIdle {models.clear()}
        database?.close()
    }

    @Test fun accountsListDetailAndNewEditor() {
        lateinit var model:AppViewModel
        compose.runOnIdle {model=createModel()}
        render {AccountsScreen(fixture,model,{})}
        compose.onNodeWithText("Everyday bank").assertIsDisplayed()
        capture("accounts-list")
        compose.onNodeWithText("Everyday bank").performClick()
        compose.onNodeWithText("Account history").assertExists()
        capture("account-detail")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Add account").performClick()
        compose.onNodeWithText("Account name").assertIsDisplayed()
        capture("account-editor")
    }
    @Test fun friendDetailAndEditor() {
        lateinit var model:AppViewModel
        compose.runOnIdle {model=createModel()}
        render {PeopleScreen(fixture,model,{})}
        compose.onNodeWithText("Maya").performClick()
        compose.onNodeWithText("Edit profile").assertExists()
        capture("friend-detail")
        compose.onNodeWithText("Edit profile").performScrollTo().performClick()
        compose.onNodeWithText("Display name").assertIsDisplayed()
        capture("friend-editor")
    }
    @Test fun groupDetailAndEditor() {
        lateinit var model:AppViewModel
        compose.runOnIdle {model=createModel()}
        render {GroupsScreen(fixture,model)}
        compose.onNodeWithText("Weekend trip").performClick()
        compose.onNodeWithText("Members").assertExists()
        capture("group-detail")
        compose.onNodeWithText("Edit group").performScrollTo().performClick()
        compose.onNodeWithText("Group name").assertIsDisplayed()
        capture("group-editor")
    }
    @Test fun categoryEditor() {
        lateinit var model:AppViewModel
        compose.runOnIdle {model=createModel()}
        render {CategoriesScreen(fixture,model)}
        compose.onNodeWithText("Food").performClick()
        compose.onNodeWithText("Category color").assertIsDisplayed()
        capture("category-editor")
    }
    @Test fun repaymentReview() {
        render {RepaymentReviewSurface("Maya","You",5000,20000,"Everyday bank","2026-10-10T12:30","Part payment",false,{},{})}
        compose.onNodeWithText("Confirm repayment").performScrollTo().assertIsDisplayed()
        capture("repayment-review")
    }
    @Test fun offsetConfirmation() {
        render {OffsetReviewSurface("Maya",5000,"Weekend trip",{},{})}
        compose.onNodeWithText("Record offset").performScrollTo().assertIsDisplayed()
        capture("offset-confirmation")
    }
    @Test fun narrowLargeAccountEditorKeepsCancelAndSaveReachable() {
        lateinit var model:AppViewModel
        compose.runOnIdle {model=createModel()}
        render(large=true) {AccountsScreen(fixture,model,{})}
        compose.onNodeWithText("Add account").performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Account name",useUnmergedTree=true).assertHeightIsAtLeast(24.dp)
        capture("account-editor-320dp-2x")
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed()
        capture("account-editor-save-320dp-2x")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Account name").assertDoesNotExist()
    }
    @Test fun narrowLargeRepaymentReviewKeepsConfirmationReachable() {
        render(large=true) {RepaymentReviewSurface("Maya","You",5000,20000,"Everyday bank","2026-10-10T12:30","Part payment",false,{},{})}
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Review repayment",useUnmergedTree=true).assertHeightIsAtLeast(48.dp)
        capture("repayment-review-320dp-2x")
        compose.onNodeWithText("Confirm repayment").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("repayment-review-confirm-320dp-2x")
    }
}

