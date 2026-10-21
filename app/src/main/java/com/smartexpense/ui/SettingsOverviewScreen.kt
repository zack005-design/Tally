package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.LocalSettings

@Composable internal fun SettingsOverviewScreen(settings: LocalSettings, onNavigate: (String) -> Unit) {
    Page("Settings", largeTitle = true) {
        item { SectionLabel("PROFILE") }
        item { GroupedCard {
            GroupedRow(settings.name.ifBlank { "Your profile" }, R.drawable.ic_people, "Manage your profile", leading={InitialAvatar(settings.name.ifBlank {"You"})}) { onNavigate("profile") }
            GroupedDivider()
            GroupedRow("Replay walkthrough", R.drawable.ic_document_outline, "See key features again") { onNavigate("welcome") }
        } }
        item { SectionLabel("SECURITY") }
        item { GroupedCard {
            GroupedRow("App lock", R.drawable.ic_lock_outline, "Protect your data") { onNavigate("app-lock") }
            GroupedDivider()
            GroupedRow("Reminders", R.drawable.ic_calendar, "Set up helpful reminders", tone = ReferenceTone.Peach) { onNavigate("reminders") }
        } }
        item { SectionLabel("DATA") }
        item { GroupedCard {
            GroupedRow("Backup & restore", R.drawable.ic_document_outline, "Export or restore your data", tone = ReferenceTone.Lavender) { onNavigate("backup") }
        } }
        item { ReferenceNotice("Stored on this device","Your data stays on this device and is not uploaded anywhere.") }
    }
}
