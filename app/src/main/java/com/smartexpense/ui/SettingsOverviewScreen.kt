package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.LocalSettings

/** The avatar's destination: preferences, money setup and data, grouped like the system Settings app. */
@Composable internal fun SettingsOverviewScreen(settings: LocalSettings, onNavigate: (String) -> Unit) {
    Page("Settings", largeTitle = true) {
        item { GroupedCard {
            GroupedRow(settings.name.ifBlank { "Your profile" }, R.drawable.ic_people, "Name, photo and starting balances", leading={InitialAvatar(settings.name.ifBlank {"You"})}, roomy = true) { onNavigate("profile") }
        } }
        item { SectionLabel("GENERAL") }
        item { GroupedCard {
            GroupedRow("Appearance", R.drawable.ic_visibility, tone = ReferenceTone.Lavender) { onNavigate("appearance") }
            GroupedDivider()
            GroupedRow("App lock", R.drawable.ic_lock_outline, tone = ReferenceTone.Gray) { onNavigate("app-lock") }
            GroupedDivider()
            GroupedRow("Reminders", R.drawable.ic_bell_outline, tone = ReferenceTone.Peach) { onNavigate("reminders") }
        } }
        item { SectionLabel("MONEY") }
        item { GroupedCard {
            GroupedRow("Accounts", R.drawable.ic_account) { onNavigate("accounts") }
            GroupedDivider()
            GroupedRow("Wallet & GPay", R.drawable.ic_wallet_outline) { onNavigate("wallet") }
            GroupedDivider()
            GroupedRow("GPay monthly reset", R.drawable.ic_transfer) { onNavigate("gpay-settings") }
            GroupedDivider()
            GroupedRow("Categories", R.drawable.ic_tag_outline, tone = ReferenceTone.Pink) { onNavigate("categories") }
            GroupedDivider()
            GroupedRow("Merchant rules", R.drawable.ic_shop_outline, tone = ReferenceTone.Pink) { onNavigate("category-rules") }
            GroupedDivider()
            GroupedRow("Default group splits", R.drawable.ic_people, tone = ReferenceTone.Peach) { onNavigate("default-splits") }
        } }
        item { SectionLabel("TOOLS") }
        item { GroupedCard {
            GroupedRow("Favourites & widget", R.drawable.ic_star_outline, tone = ReferenceTone.Peach) { onNavigate("favourites") }
            GroupedDivider()
            GroupedRow("Calculator", R.drawable.ic_compose, tone = ReferenceTone.Gray) { onNavigate("calculator") }
            GroupedDivider()
            GroupedRow("Foreign bill to INR", R.drawable.ic_plane_outline, tone = ReferenceTone.Lavender) { onNavigate("foreign-entry") }
        } }
        item { SectionLabel("DATA") }
        item { GroupedCard {
            GroupedRow("Backup & restore", R.drawable.ic_document_outline, tone = ReferenceTone.Lavender) { onNavigate("backup") }
            GroupedDivider()
            GroupedRow("Export report", R.drawable.ic_upload_outline, tone = ReferenceTone.Lavender) { onNavigate("export") }
            GroupedDivider()
            GroupedRow("Import from ChatGPT", R.drawable.ic_duplicate_outline, tone = ReferenceTone.Lavender) { onNavigate("history-import") }
        } }
        item { GroupedCard {
            GroupedRow("Replay walkthrough", R.drawable.ic_info, tone = ReferenceTone.Gray) { onNavigate("welcome") }
        } }
        item { ReferenceNotice("Stored on this device","Your data stays on this device and is not uploaded anywhere.") }
    }
}
