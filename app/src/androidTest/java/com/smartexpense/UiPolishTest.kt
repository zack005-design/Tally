package com.smartexpense

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.domain.*
import com.smartexpense.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UiPolishTest {
    @get:Rule val compose = createComposeRule()

    private fun contrast(a:Color,b:Color):Double {
        val x=a.luminance().toDouble();val y=b.luminance().toDouble()
        return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
    }
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null),"polish-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
    private fun sample()=Snapshot(
        accounts=listOf(Account("cash","Wallet","Cash",120000,false),Account("gpay","GPay","Bank",500000,false)),
        people=listOf(Person("self","You"),Person("f","Rahul")),
        debts=listOf(Debt("d","bill","f","self",13500,13500,0),Debt("e","bill2","self","f",5000,5000,0)))

    private fun homePreview(dark:Boolean) {
        var destination=""
        compose.setContent {TrackerTheme(darkTheme=dark) {
            androidx.compose.material3.Scaffold(bottomBar={LedgerNavigation("home",{})}) {padding->
                Box(Modifier.padding(padding)) {HomeScreen(sample(),{_,_->},{},{},{},{destination=it})}
            }
        }}
        compose.onNodeWithText("Add expense").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add money").performScrollTo().assertIsDisplayed()
        capture(if(dark) "today-dark" else "today-light")
        compose.onNodeWithText("Add money").performClick()
        assertEquals("wallet",destination)
        compose.onNodeWithContentDescription("Hide balance").performScrollTo().performClick()
        compose.onNodeWithText("₹6,200").assertDoesNotExist()
        compose.onNodeWithText("₹1,200").assertDoesNotExist()
        compose.onNodeWithText("₹5,000").assertDoesNotExist()
    }

    @Test fun todayInLightThemeKeepsActionsAndPrivacy()=homePreview(false)
    @Test fun todayInDarkThemeKeepsActionsAndPrivacy()=homePreview(true)

    @Test fun darkEntryKeepsPaymentsAndReviewReachable() {
        compose.setContent {
            var form by remember {mutableStateOf(EntryUiState(amount="330",description="Dinner",accountId="cash",shared=true,participants=listOf("self","f"),paid=mapOf("self" to "300","f" to "30")))}
            TrackerTheme(darkTheme=true) {EntryScreen(form,sample().accounts,sample().people,emptyList(),emptyList(),{form=it},{},{})}
        }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithContentDescription("You paid (₹)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rahul paid (₹)").assertIsDisplayed()
        capture("payments-dark")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Save shared bill").assertIsDisplayed()
        capture("review-dark")
    }

    @Test fun calendarInDarkThemeKeepsDayNavigation() {
        compose.setContent {TrackerTheme(darkTheme=true) {Surface(color=Tokens.background) {SpendingCalendarScreen(sample(),{})}}}
        compose.onNodeWithText("Today",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        capture("calendar-dark")
    }

    @Test fun paletteFollowsSystemNightChangesAndKeepsTextReadable() {
        var night by mutableStateOf(false)
        var background=Color.Unspecified
        var bodyContrast=0.0
        var mutedContrast=0.0
        var selectedContrast=0.0
        compose.setContent {
            val configuration=Configuration(LocalConfiguration.current).apply {
                uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if(night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                TrackerTheme {
                    background=Tokens.background
                    bodyContrast=contrast(Tokens.text,Tokens.paper)
                    mutedContrast=contrast(Tokens.muted,Tokens.inputSurface)
                    selectedContrast=contrast(MaterialTheme.colorScheme.onPrimary,Tokens.accent)
                    Surface(Modifier.fillMaxSize(),color=Tokens.background) {LedgerNavigation("home",{})}
                }
            }
        }
        compose.runOnIdle {
            assertTrue(background.luminance()>.8f)
            assertTrue(bodyContrast>=4.5);assertTrue(mutedContrast>=4.5);assertTrue(selectedContrast>=4.5)
            night=true
        }
        compose.runOnIdle {
            assertTrue("The approved light palette must remain light in Android night mode",background.luminance()>.8f)
            assertTrue(bodyContrast>=4.5);assertTrue(mutedContrast>=4.5);assertTrue(selectedContrast>=4.5)
            night=false
        }
        compose.runOnIdle {assertTrue("Returning to light mode must restore the palette",background.luminance()>.8f)}
    }

    @Test fun friendsKeepsBothDirectionsAndManagementReachable() {
        var management=""
        compose.setContent {TrackerTheme {Surface(color=Tokens.background) {
            SplitScreen(sample(),{},{management="friends"},{management="groups"},{_,_->},{},{_,_,_,_->})
        }}}
        compose.onNodeWithText("Start a shared bill").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("●  You're owed ₹135").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("●  You owe ₹50").assertIsDisplayed()
        capture("friends-light")
        compose.onNodeWithText("Settlement history").performScrollTo().performClick()
        compose.onNodeWithText("Manage friends").performScrollTo().performClick()
        assertEquals("friends",management)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Original bill obligations"))
        compose.onNodeWithText("Original bill obligations").assertExists()
    }
}
