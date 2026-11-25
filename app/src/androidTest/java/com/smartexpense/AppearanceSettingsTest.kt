package com.smartexpense
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.data.AppIcon
import com.smartexpense.data.AppIcons
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

    private val context=ApplicationProvider.getApplicationContext<Context>()

    @After fun reset() {
        runBlocking {preferences.setThemeMode(ThemeMode.System)}
        AppIcons.select(context,AppIcon.Default)
    }

    @Test fun choosingAnAppIconSwitchesTheLauncherEntry() {
        AppIcons.select(context,AppIcon.Default)
        compose.setContent {TrackerTheme {AppearanceScreen()}}
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Lowercase t"))
        compose.onNodeWithText("Lowercase t").performClick()
        compose.waitUntil(5000) {AppIcons.current(context)==AppIcon.LetterT}
        compose.onNodeWithText("Lowercase t").assertIsSelected()
        val packages=context.packageManager
        val launchers=packages.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),0)
        assertEquals(listOf(AppIcon.LetterT.alias),launchers.map {it.activityInfo.name})
    }

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
