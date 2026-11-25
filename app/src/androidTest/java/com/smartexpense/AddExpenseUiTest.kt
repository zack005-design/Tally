package com.smartexpense

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AddExpenseUiTest {
    @get:Rule val compose=createComposeRule()
    private val accounts=listOf(Account("w","Wallet","Cash",120000,false),Account("g","GPay","Bank",500000,false))
    private val categories=listOf(Category("food","Food & drinks",0,"food",false))
    private var current=EntryUiState()
    private var saved:EntryDraft?=null

    private fun start(initial:EntryUiState,scale:Float=1f,width:Int?=null,dark:Boolean=false) {
        current=initial
        compose.setContent {
            var form by remember {mutableStateOf(initial)}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                TrackerTheme(darkTheme=dark) {Box(if(width!=null) Modifier.width(width.dp).fillMaxHeight() else Modifier) {
                    EntryScreen(form,accounts,emptyList(),categories,emptyList(),{form=it;current=it},{},{saved=form.draft()})
                }}
            }
        }
    }
    private fun scrollTo(matcher:SemanticsMatcher)=compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
    private fun press(key:String) {scrollTo(hasContentDescription(key));compose.onNodeWithContentDescription(key).performClick()}
    private fun capture(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"$name.png")
        file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        listOf("mkdir -p /sdcard/Download/tally-add","cp ${file.absolutePath} /sdcard/Download/tally-add/$name.png").forEach {command->
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {it.readBytes()}
        }
    }

    @Test fun keypadAmountSavesFromTheFirstPage() {
        start(EntryUiState(description="Coffee",accountId="g",categoryId="food"))
        compose.onNodeWithText("Save expense").assertIsNotEnabled()
        listOf("Key 1","Key 2","Key 0","Decimal point","Key 5").forEach(::press)
        assertEquals("120.5",current.amount)
        scrollTo(hasText("GPay will be ₹4,879.50"))
        capture("add")
        compose.onNodeWithText("Save expense").assertIsEnabled().performClick()
        assertEquals(12050L,saved!!.amount)
        assertEquals("g",saved!!.accountId)
    }
    @Test fun goingNegativeWarnsWithoutBlockingSave() {
        start(EntryUiState(amount="1500",description="Shoes",accountId="w"))
        scrollTo(hasText("Wallet would be",substring=true))
        compose.onNodeWithText("Save expense").assertIsEnabled()
        capture("add-low-balance")
    }
    @Test fun missingDescriptionOrAccountBlocksSave() {
        start(EntryUiState(amount="50",accountId="w"))
        compose.onNodeWithText("Save expense").assertIsNotEnabled()
    }
    @Test fun switchingTypesKeepsTheAmountInBothDirections() {
        start(EntryUiState(amount="50",description="Pay",accountId="w"))
        compose.onNodeWithText("Income").performClick()
        assertEquals(Kind.INCOME,current.kind)
        compose.onNodeWithText("Save income").assertExists()
        compose.onNodeWithText("Expense").performClick()
        compose.onNodeWithText("Save expense").assertExists()
        assertEquals("50",current.amount)
    }
    @Test fun chipsChangeCategoryAndAccount() {
        start(EntryUiState(amount="50",description="Lunch",accountId="w"))
        scrollTo(hasText("Uncategorised"));compose.onNodeWithText("Uncategorised").performClick()
        compose.onNodeWithText("Food & drinks").performClick()
        compose.runOnIdle {assertEquals("food",current.categoryId)}
        scrollTo(hasText("Wallet"));compose.onNodeWithText("Wallet").performClick()
        compose.onNodeWithText("GPay").performClick()
        compose.runOnIdle {assertEquals("g",current.accountId)}
    }
    @Test fun narrowLargeTextKeepsKeypadAndSaveReachable() {
        start(EntryUiState(amount="50",description="Lunch",accountId="w"),scale=2f,width=320)
        press("Key 9")
        assertEquals("509",current.amount)
        compose.onNodeWithText("Save expense").assertIsDisplayed().assertIsEnabled()
        capture("add-320dp-2x")
    }
    @Test fun darkThemeKeepsSaveVisible() {
        start(EntryUiState(amount="120",description="Coffee",accountId="g",categoryId="food"),dark=true)
        compose.onNodeWithText("Save expense").assertIsDisplayed()
        capture("add-dark")
    }
}
