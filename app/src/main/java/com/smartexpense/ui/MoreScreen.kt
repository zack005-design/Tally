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

private data class ToolGroup(val id: String, val title: String, val description: String, val icon: Int, val links: List<Pair<String, String>>)
private val toolGroups = listOf(
    ToolGroup("money", "Money & planning", "Budgets, goals and limits", R.drawable.ic_insights, listOf("wallet" to "Wallet & GPay", "income" to "Record income", "transfer" to "Transfer between accounts", "accounts" to "Accounts", "gpay-settings" to "GPay balance & resets", "insights" to "Spending insights", "budgets" to "Budgets", "planner" to "Money planner", "goals" to "Savings goals", "recurring" to "Recurring entries")),
    ToolGroup("friends", "Friends & groups", "Split expenses and settle up", R.drawable.ic_people, listOf("split" to "Shared balances", "people" to "Friends", "groups" to "Groups", "default-splits" to "Default group splits")),
    ToolGroup("history", "History & reports", "Spending, trends and exports", R.drawable.ic_clock_outline, listOf("activity" to "Transactions", "calendar" to "Spending calendar", "diary" to "Daily diary", "monthly-review" to "Monthly review", "export" to "Export monthly report")),
    ToolGroup("bills", "Bills & categories", "Manage categories and rules", R.drawable.ic_tag_outline, listOf("bill-import" to "Photo / PDF bill", "foreign-entry" to "Foreign bill to INR", "categories" to "Categories", "category-rules" to "Merchant categories")),
    ToolGroup("settings", "Settings & backup", "App preferences and data", R.drawable.ic_settings_outline, listOf("profile" to "Your profile", "app-lock" to "App lock", "reminders" to "Local reminders", "backup" to "Backup & restore"))
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
            item { SectionLabel("FAVOURITES & WIDGET") }
            item { GroupedCard {
                GroupedRow("Favourites", R.drawable.ic_star_outline, supporting = "Quick access to what you use",roomy=true,bareIcon=true) { onNavigate("favourites") }
                GroupedDivider()
                GroupedRow("Home screen widget", R.drawable.ic_widget_outline, supporting = "Quick expense shortcuts",roomy=true,bareIcon=true) { onNavigate("favourites") }
            } }
            item { Surface(shape=Tokens.field,color=Tokens.secondarySurface) {GroupedRow("Add Tally to your home screen",R.drawable.ic_phone_outline,supporting="Open expense drafts quickly. No balances or history shown.",color=Tokens.secondarySurface) {onNavigate("favourites")}} }
        } else item { GroupedCard {
            group.links.forEachIndexed { index, (route, label) ->
                if (index > 0) GroupedDivider()
                GroupedRow(label, group.icon) { onNavigate(route) }
            }
        } }
    }
    if(menu) EditorialActionSheet("Tools",toolGroups.map {entry->EditorialAction(entry.title) {menu=false;onNavigate("tools/${entry.id}")}}, {menu=false})
}
