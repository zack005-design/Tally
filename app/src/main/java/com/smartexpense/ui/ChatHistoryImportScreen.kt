package com.smartexpense.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartexpense.data.FinanceRepository
import com.smartexpense.domain.*

@Composable fun ChatHistoryImportScreen(repository: FinanceRepository, snapshot: Snapshot, onFinished: () -> Unit) {
    val model: ChatHistoryImportModel = viewModel { ChatHistoryImportModel(repository) }
    val state by model.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.applicationContext.contentResolver
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.load(resolver, it) }
    BackHandler(state.busy) { }
    val guard = LocalNavigationExitGuard.current
    DisposableEffect(guard, state.busy) {
        val owner = Any()
        if (state.busy) guard?.install(owner) { }
        onDispose { guard?.clear(owner) }
    }
    Page("Import from ChatGPT", onBack = { if (!state.busy) onFinished() }) {
        item { Text("Prepare tally-history.json with Tally Companion in the chat holding your expenses. Choose that file here to review and add records.") }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Preparing import…") }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (state.result != null) {
            val result = state.result!!
            item { Panel {
                Text("Import saved", style = MaterialTheme.typography.titleLarge)
                Text("${result.imported} added · ${result.skipped} already recorded · ${result.excluded} excluded")
                Text("Excluded entries were not imported. Your existing records were preserved.")
                EditorialPrimaryButton("Done", true, onFinished)
            } }
        } else {
            item { EditorialPrimaryButton(if (state.history == null) "Choose history file" else "Choose another file", !state.busy) { chooser.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } }
            val history = state.history
            if (history != null) {
                item { Panel {
                    Text("History coverage", style = MaterialTheme.typography.titleMedium)
                    Text(history.coverage)
                    val dates = history.entries.mapNotNull { it.date }.sorted()
                    if (dates.isNotEmpty()) Text("${dates.first()} to ${dates.last()}")
                    Text("${state.rows.size} records · ${state.rows.count { it.draft == null }} need correction or another workflow")
                    Text("Date-only entries use local noon. Historical entries affect balances; this is not a backup restore.")
                } }
                history.entries.mapNotNull { it.account }.distinct().forEach { label -> item(key = "account:$label") {
                    ImportMapping(enabled = !state.busy) { Choice("Account: $label", state.accounts[label], snapshot.accounts.filterNot { it.archived }.map { it.id to it.name }) { model.account(label, it) } }
                } }
                history.entries.mapNotNull { it.category?.takeIf(String::isNotBlank) }.distinct().forEach { label -> item(key = "category:$label") {
                    ImportMapping(enabled = !state.busy) { Choice("Category: $label", state.categories[label], listOf("" to "Uncategorised") + snapshot.categories.filterNot { it.archived }.map { it.id to it.name }) { model.category(label, it) } }
                } }
                item {
                    Text("${state.selected.size} selected · ${state.rows.size - state.selected.size} excluded")
                    TextButton(model::selectReady, enabled = !state.busy) { Text("Select ready records") }
                    TextButton(model::clearSelection, enabled = !state.busy) { Text("Clear selection") }
                }
                item { EditorialPrimaryButton("Preview balance effects", !state.busy && state.selected.isNotEmpty(), model::preview) }
                state.ledgerPreview?.let { review ->
                    item { Panel {
                        Text("Confirm this batch", style = MaterialTheme.typography.titleLarge)
                        Text("${review.imported} will be added · ${review.skipped} already recorded · ${state.rows.size - state.selected.size} excluded")
                        review.afterBalances.forEach { (id, after) ->
                            val before = review.beforeBalances[id] ?: 0
                            if (before != after) Text("${snapshot.accounts.find { it.id == id }?.name ?: id}: ${Money.format(before)} → ${Money.format(after)}")
                        }
                        if (review.beforeBalances == review.afterBalances) Text("No change to current account balances.")
                        Text("Includes historical GPay reset recalculations, when applicable.")
                        if (state.rows.any { it.source.sourceId in state.selected && it.possibleDuplicate }) Text("Possible duplicates are selected. Check that these are separate purchases.")
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(state.acknowledged, model::acknowledge, enabled = !state.busy, modifier = Modifier.semantics { contentDescription = "Confirm reviewed records, possible duplicates and excluded entries" })
                            Text("I reviewed these records, any possible duplicates, and the excluded entries.", Modifier.weight(1f).padding(top = 12.dp))
                        }
                        EditorialPrimaryButton("Confirm import", !state.busy && state.acknowledged, model::confirm)
                    } }
                }
                items(state.rows, key = { "row:${it.source.sourceId}" }) { row -> HistoryImportRowCard(row, row.source.sourceId in state.selected, !state.busy) { model.toggle(row.source.sourceId) } }
            }
        }
    }
}

@Composable private fun ImportMapping(enabled: Boolean, content: @Composable () -> Unit) {
    // Mapping controls disappear during work, avoiding stale popup callbacks while a batch is committing.
    if (enabled) content()
}

@Composable fun HistoryImportRowCard(row: HistoryImportRow, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Panel {
        Row(Modifier.fillMaxWidth()) {
            Checkbox(selected, { onToggle() }, enabled = enabled && row.draft != null, modifier = Modifier.semantics { contentDescription = "Select ${row.source.description ?: row.source.sourceId}" })
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.source.description ?: "Missing description", style = MaterialTheme.typography.titleMedium)
                Text("${row.source.date ?: "Missing date"} · ${row.source.kind} · ${row.source.amount ?: "Missing amount"} INR")
                Text(row.source.account ?: "Missing source account")
            }
        }
        if (row.alreadyRecorded) Text("This source ID already exists; Tally will verify its saved details.")
        if (row.possibleDuplicate) Text("Possible duplicate — review before selecting")
        row.issues.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        row.source.notes?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        row.source.details?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
