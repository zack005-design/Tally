package com.smartexpense
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.data.Preferences
import com.smartexpense.domain.ThemeMode
import com.smartexpense.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class) class AppearanceSettingsTest {
    @get:Rule val compose=createComposeRule()
    private val preferences=Preferences(ApplicationProvider.getApplicationContext<Context>())

    @After fun reset()=runBlocking {preferences.setThemeMode(ThemeMode.System)}

    @Test fun choosingAThemeIsSavedAndShownAsSelected() {
        runBlocking {preferences.setThemeMode(ThemeMode.System)}
        compose.setContent {TrackerTheme {AppearanceScreen()}}
        compose.onNodeWithText("System default").assertIsSelected()
        compose.onNodeWithText("Dark").performClick()
        compose.waitUntil(5000) {runBlocking {preferences.themeMode.first()}==ThemeMode.Dark}
        compose.onNodeWithText("Dark").assertIsSelected()
        compose.onNodeWithText("System default").assertIsNotSelected()
        compose.onNodeWithText("Light").performClick()
        compose.waitUntil(5000) {runBlocking {preferences.themeMode.first()}==ThemeMode.Light}
        assertEquals(ThemeMode.Light,runBlocking {preferences.themeMode.first()})
    }

    @Test fun settingsLinksToAppearance() {
        var destination=""
        compose.setContent {TrackerTheme(darkTheme=true) {SettingsOverviewScreen(com.smartexpense.domain.LocalSettings()) {destination=it}}}
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        assertEquals("appearance",destination)
    }
}
