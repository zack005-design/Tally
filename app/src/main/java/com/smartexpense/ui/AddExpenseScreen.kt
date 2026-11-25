package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*

/** Adds one keypad press to an exact rupee amount: at most nine whole digits and two decimals. */
internal fun keypadAmount(current: String, key: String): String = when {
    key == "⌫" -> current.dropLast(1)
    key == "." -> if ('.' in current) current else current.ifEmpty { "0" } + "."
    '.' in current && current.substringAfter('.').length >= 2 -> current
    '.' !in current && current.length >= 9 && current != "0" -> current
    current == "0" -> key
    else -> current + key
}

/** Indian digit grouping for an amount that may still be mid-entry. */
internal fun keypadDisplay(raw: String): String {
    val whole = raw.substringBefore('.').ifEmpty { "0" }
    if (whole.any { !it.isDigit() }) return "₹$raw"
    val head = whole.dropLast(3)
    val grouped = if (head.isEmpty()) whole else head.reversed().chunked(2).joinToString(",").reversed() + "," + whole.takeLast(3)
    return "₹$grouped" + if ('.' in raw) "." + raw.substringAfter('.') else ""
}

/** Amount first, with Save on the same page. Everything below the keypad is optional. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AddExpenseScreen(
    s: EntryUiState, accounts: List<Account>, categories: List<Category>, walletSources: WalletSources?,
    onChange: (EntryUiState) -> Unit, onSave: () -> Unit, onBack: () -> Unit, onDescribe: () -> Unit, onImportBill: () -> Unit
) {
    val activeCategories = categories.filterNot { it.archived }
    val categoryUnavailable = s.categoryId != null && activeCategories.none { it.id == s.categoryId }
    var picker by rememberSaveable(s.id) { mutableStateOf<String?>(null) }
    var details by rememberSaveable(s.id) { mutableStateOf(false) }
    val valid = s.canReview(accounts, categories) && s.description.isNotBlank()
    val projections = projectedBalances(s, accounts, walletSources).getOrNull().orEmpty()
    Scaffold(modifier = Modifier.imePadding(), containerColor = Tokens.background, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        Box(Modifier.padding(horizontal = Tokens.pageInset, vertical = 10.dp)) { EditorialPrimaryButton("Save expense", valid, onSave) }
    }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) {
        Page(if (s.replaces != null) "Correct expense" else "Add expense", onBack = onBack) {
            if (s.replaces == null) item {
                EditorialSegmentedControl("EXPENSE", listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer")) { onChange(s.copy(kind = Kind.valueOf(it))) }
            }
            if (s.parserNotice.isNotBlank()) item { ReferenceInfo(s.parserNotice) }
            item {
                Text(keypadDisplay(s.amount), Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { contentDescription = "Amount ${keypadDisplay(s.amount)}" },
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(48f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(56f, androidx.compose.ui.unit.TextUnitType.Sp)), textAlign = TextAlign.Center, color = if (s.amount.isBlank()) Tokens.muted else Tokens.text)
            }
            item { Field("Description", s.description, { onChange(s.copy(description = it)) }) }
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddChip(if (categoryUnavailable) "Choose category" else activeCategories.find { it.id == s.categoryId }?.name ?: "Uncategorised", R.drawable.ic_tag_outline) { picker = "category" }
                    AddChip(accounts.find { it.id == s.accountId && !it.archived }?.name ?: "Choose account", R.drawable.ic_wallet_outline) { picker = "account" }
                }
            }
            projections.forEach { p -> item(key = "after-${p.accountId}") {
                if (p.after < 0) ReferenceInfo("${p.name} would be ${referenceMoney(p.after)}. Choose another account, or save anyway if Tally is behind.", warning = true)
                else Text("${p.name} will be ${referenceMoney(p.after)}", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = Tokens.muted, textAlign = TextAlign.Center)
            } }
            if (categoryUnavailable) item { Text("This category is no longer available. Choose an active category or Uncategorised before saving.", color = Tokens.negative) }
            if (accounts.none { !it.archived }) item { Text("Create an account from Summary → Accounts before saving.", color = Tokens.muted) }
            item { NumberPad { onChange(s.copy(amount = keypadAmount(s.amount, it))) } }
            item { GroupedCard {
                if (s.replaces == null) {
                    GroupedRow("Split with friends", R.drawable.ic_people) { onChange(s.copy(shared = true)) }
                    GroupedDivider()
                    GroupedRow("Describe it instead", R.drawable.ic_compose, onClick = onDescribe)
                    GroupedDivider()
                    GroupedRow("Use a bill photo / PDF", R.drawable.ic_camera_outline, onClick = onImportBill)
                    GroupedDivider()
                }
                GroupedRow(if (details) "Hide details" else "Date, payment and notes", R.drawable.ic_calendar, tone = ReferenceTone.Gray) { details = !details }
            } }
            if (details || s.secondAccountId.isNotBlank()) item { Panel {
                PaymentSourcePicker(s, accounts, onChange, walletSources)
                EditorialDateRow(s.date) { onChange(s.copy(date = it)) }
                Field("Notes (optional)", s.notes, { onChange(s.copy(notes = it)) })
            } }
            if (s.replaces != null) item { ReferenceInfo("This correction keeps the original record in history.") }
        }
    } }
    picker?.let { kind ->
        EditorialBottomSheet(if (kind == "category") "Category" else "Pay from", { picker = null }) {
            GroupedCard {
                if (kind == "category") (listOf<Category?>(null) + activeCategories).forEachIndexed { index, category ->
                    if (index > 0) GroupedDivider()
                    GroupedRow(category?.name ?: "Uncategorised", R.drawable.ic_tag_outline, value = if (s.categoryId == category?.id) "Selected" else null, showChevron = false) {
                        onChange(s.copy(categoryId = category?.id)); picker = null
                    }
                } else accounts.filterNot { it.archived }.forEachIndexed { index, account ->
                    if (index > 0) GroupedDivider()
                    GroupedRow(account.name, R.drawable.ic_wallet_outline, value = referenceMoney(account.balance), showChevron = false) {
                        onChange(s.copy(accountId = account.id, secondAccountId = if (s.secondAccountId == account.id) "" else s.secondAccountId, secondPayment = if (s.secondAccountId == account.id) "" else s.secondPayment)); picker = null
                    }
                }
            }
        }
    }
}

@Composable private fun AddChip(label: String, icon: Int, onClick: () -> Unit) {
    Surface(onClick = lightHapticAction(onClick), shape = Tokens.pill, color = Tokens.paper, modifier = Modifier.heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = Tokens.accent)
            Text(label, style = MaterialTheme.typography.titleSmall)
            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(16.dp), tint = Tokens.muted)
        }
    }
}

@Composable private fun NumberPad(onKey: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Surface(onClick = lightHapticAction { onKey(key) }, shape = Tokens.field, color = Tokens.background,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).semantics { contentDescription = when (key) { "⌫" -> "Delete digit"; "." -> "Decimal point"; else -> "Key $key" } }) {
                        Box(contentAlignment = Alignment.Center) { Text(key, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)) }
                    }
                }
            }
        }
    }
}
