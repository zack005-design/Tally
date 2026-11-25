package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.R

private data class ToolLink(val route: String, val label: String, val icon: Int)
private data class ToolSection(val label: String, val tone: ReferenceTone, val links: List<ToolLink>)
private data class ToolGroup(val id: String, val title: String, val description: String, val icon: Int, val sections: List<ToolSection>)

// Each group page is split into short, labelled sections with a distinct icon per row,
// instead of one long card where every row repeats the group's icon.
private val toolGroups = listOf(
    ToolGroup("money", "Money & planning", "Budgets, goals and limits", R.drawable.ic_insights, listOf(
        ToolSection("Record", ReferenceTone.Blue, listOf(
            ToolLink("income", "Record income", R.drawable.ic_arrow_in),
            ToolLink("transfer", "Transfer between accounts", R.drawable.ic_transfer),
            ToolLink("calculator", "Calculate", R.drawable.ic_tally_mark))),
        ToolSection("Accounts", ReferenceTone.Wallet, listOf(
            ToolLink("accounts", "Accounts", R.drawable.ic_account),
            ToolLink("wallet", "Wallet & GPay", R.drawable.ic_wallet_outline),
            ToolLink("gpay-settings", "GPay balance & resets", R.drawable.ic_contactless),
            ToolLink("net-worth", "Net worth", R.drawable.ic_tab_summary))),
        ToolSection("Plan", ReferenceTone.Lavender, listOf(
            ToolLink("insights", "Spending insights", R.drawable.ic_insights),
            ToolLink("budgets", "Budgets", R.drawable.ic_target_outline),
            ToolLink("goals", "Savings goals", R.drawable.ic_piggy_outline),
            ToolLink("planner", "Money planner", R.drawable.ic_calendar),
            ToolLink("recurring", "Recurring entries", R.drawable.ic_duplicate_outline))))),
    ToolGroup("friends", "Friends & groups", "Split expenses and settle up", R.drawable.ic_people, listOf(
        ToolSection("Balances", ReferenceTone.Blue, listOf(
            ToolLink("split", "Shared balances", R.drawable.ic_transfer))),
        ToolSection("People", ReferenceTone.Lavender, listOf(
            ToolLink("people", "Friends", R.drawable.ic_people),
            ToolLink("groups", "Groups", R.drawable.ic_people),
            ToolLink("default-splits", "Default group splits", R.drawable.ic_settings_outline))))),
    ToolGroup("history", "History & reports", "Spending, trends and exports", R.drawable.ic_clock_outline, listOf(
        ToolSection("Browse", ReferenceTone.Blue, listOf(
            ToolLink("activity", "Activity", R.drawable.ic_activity),
            ToolLink("calendar", "Spending calendar", R.drawable.ic_calendar),
            ToolLink("diary", "Daily diary", R.drawable.ic_pencil_outline))),
        ToolSection("Review", ReferenceTone.Lavender, listOf(
            ToolLink("monthly-review", "Monthly review", R.drawable.ic_check_outline),
            ToolLink("year-summary", "Yearly summary", R.drawable.ic_insights))),
        ToolSection("Export & import", ReferenceTone.Peach, listOf(
            ToolLink("export", "Export monthly report", R.drawable.ic_upload_outline),
            ToolLink("history-import", "Import from ChatGPT", R.drawable.ic_document_outline))))),
    ToolGroup("bills", "Bills & categories", "Manage categories and rules", R.drawable.ic_tag_outline, listOf(
        ToolSection("Add a bill", ReferenceTone.Peach, listOf(
            ToolLink("bill-import", "Photo / PDF bill", R.drawable.ic_photo_outline),
            ToolLink("foreign-entry", "Foreign bill to INR", R.drawable.ic_plane_outline))),
        ToolSection("Organise", ReferenceTone.Lavender, listOf(
            ToolLink("categories", "Categories", R.drawable.ic_tag_outline),
            ToolLink("category-rules", "Merchant categories", R.drawable.ic_shop_outline))))),
    ToolGroup("settings", "Settings & backup", "App preferences and data", R.drawable.ic_settings_outline, listOf(
        ToolSection("You", ReferenceTone.Blue, listOf(
            ToolLink("profile", "Your profile", R.drawable.ic_people),
            ToolLink("appearance", "Appearance", R.drawable.ic_visibility))),
        ToolSection("Security & reminders", ReferenceTone.Peach, listOf(
            ToolLink("app-lock", "App lock", R.drawable.ic_lock_outline),
            ToolLink("reminders", "Local reminders", R.drawable.ic_bell_outline))),
        ToolSection("Data", ReferenceTone.Lavender, listOf(
            ToolLink("backup", "Backup & restore", R.drawable.ic_shield_outline)))))
)

@Composable fun MoreScreen(section: String? = null, onNavigate: (String) -> Unit) {
    val group = toolGroups.find { it.id == section }
    var menu by remember {mutableStateOf(false)}
    Page(group?.title ?: "Tools", largeTitle = group == null, actions={if(group==null) IconButton({menu=true},Modifier.semantics {contentDescription="Tools menu"}) {Icon(painterResource(R.drawable.ic_more),null,tint=Tokens.accent)}}) {
        if (group == null) {
            item { SectionLabel("MANAGE") }
            item { GroupedCard {
                toolGroups.forEachIndexed { index, entry ->
                    if (index > 0) GroupedDivider()
                    GroupedRow(entry.title, entry.icon, supporting = entry.description, roomy=true, bareIcon=true) { onNavigate("tools/${entry.id}") }
                }
            } }
            item { SectionLabel("SHORTCUTS") }
            item { GroupedCard {
                GroupedRow("Favourites & widget", R.drawable.ic_star_outline, supporting = "Quick entries and a home-screen widget",roomy=true,bareIcon=true) { onNavigate("favourites") }
            } }
        } else group.sections.forEach { section ->
            item { SectionLabel(section.label.uppercase()) }
            item { GroupedCard {
                section.links.forEachIndexed { index, link ->
                    if (index > 0) GroupedDivider()
                    GroupedRow(link.label, link.icon, tone = section.tone) { onNavigate(link.route) }
                }
            } }
        }
    }
    if(menu) EditorialActionSheet("Tools",toolGroups.map {entry->EditorialAction(entry.title) {menu=false;onNavigate("tools/${entry.id}")}}, {menu=false})
}
