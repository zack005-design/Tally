package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test fun systemFollowsThePhoneAndExplicitChoicesOverrideIt() {
        assertTrue(ThemeMode.System.isDark(systemDark = true))
        assertFalse(ThemeMode.System.isDark(systemDark = false))
        assertFalse(ThemeMode.Light.isDark(systemDark = true))
        assertTrue(ThemeMode.Dark.isDark(systemDark = false))
    }
    @Test fun storedValuesRoundTripAndUnknownValuesFollowTheSystem() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.from(it.id)) }
        assertEquals(ThemeMode.System, ThemeMode.from(null))
        assertEquals(ThemeMode.System, ThemeMode.from("sepia"))
    }
}
