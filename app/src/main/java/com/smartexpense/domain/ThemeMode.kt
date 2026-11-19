package com.smartexpense.domain

/** The user's appearance choice. [System] follows the phone's dark theme setting. */
enum class ThemeMode(val id: String, val label: String) {
    System("system", "System default"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        /** Unknown or missing values fall back to following the system. */
        fun from(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}
