package com.smartexpense.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.ChatHistoryJson
import com.smartexpense.data.FinanceRepository
import com.smartexpense.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ChatHistoryImportState(
    val history: ChatHistory? = null, val rows: List<HistoryImportRow> = emptyList(),
    val accounts: Map<String, String> = emptyMap(), val categories: Map<String, String> = emptyMap(),
    val selected: Set<String> = emptySet(), val busy: Boolean = false, val error: String? = null,
    val ledgerPreview: HistoryLedgerPreview? = null, val acknowledged: Boolean = false,
    val result: HistoryImportResult? = null
)

/** Holds review in memory across rotation; process death always requires a fresh file/review. */
internal class ChatHistoryImportModel(private val repository: FinanceRepository): ViewModel() {
    private val mutable = MutableStateFlow(ChatHistoryImportState())
    val state = mutable.asStateFlow()

    private fun task(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(error = failure.message ?: "Could not prepare this import", ledgerPreview = null) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    fun load(resolver: ContentResolver, uri: Uri?) {
        if (uri == null) return
        task {
            val history = withContext(Dispatchers.IO) { requireNotNull(resolver.openInputStream(uri)) { "Could not open history file" }.use(ChatHistoryJson::read) }
            val snapshot = repository.snapshot()
            val accounts = history.entries.mapNotNull { it.account }.distinct().mapNotNull { label ->
                snapshot.accounts.filter { !it.archived && it.name.equals(label, true) }.singleOrNull()?.let { label to it.id }
            }.toMap()
            val categories = history.entries.mapNotNull { it.category?.takeIf(String::isNotBlank) }.distinct().mapNotNull { label ->
                snapshot.categories.filter { !it.archived && it.name.equals(label, true) }.singleOrNull()?.let { label to it.id }
            }.toMap()
            val rows = withContext(Dispatchers.Default) { ChatHistoryImport.preview(history, snapshot, accounts, categoryMapping = categories).rows }
            mutable.value = ChatHistoryImportState(history, rows, accounts, categories, busy = true)
        }
    }

    fun account(label: String, id: String) = remap { it.copy(accounts = it.accounts + (label to id)) }
    fun category(label: String, id: String) = remap { it.copy(categories = it.categories + (label to id)) }
    private fun remap(change: (ChatHistoryImportState) -> ChatHistoryImportState) {
        if (mutable.value.busy) return
        mutable.value = change(mutable.value).copy(ledgerPreview = null, acknowledged = false)
        task {
            val current = mutable.value
            val history = current.history ?: return@task
            val snapshot = repository.snapshot()
            val rows = withContext(Dispatchers.Default) { ChatHistoryImport.preview(history, snapshot, current.accounts, categoryMapping = current.categories).rows }
            mutable.value = mutable.value.copy(rows = rows, selected = current.selected.intersect(rows.filter { it.draft != null }.map { it.source.sourceId }.toSet()))
        }
    }

    fun toggle(id: String) {
        val current = mutable.value
        if (current.busy || current.rows.none { it.source.sourceId == id && it.draft != null }) return
        mutable.value = current.copy(selected = if (id in current.selected) current.selected - id else current.selected + id, ledgerPreview = null, acknowledged = false)
    }
    fun selectReady() {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(selected = mutable.value.rows.filter { it.draft != null }.map { it.source.sourceId }.toSet(), ledgerPreview = null, acknowledged = false)
    }
    fun clearSelection() {
        if (!mutable.value.busy) mutable.value = mutable.value.copy(selected = emptySet(), ledgerPreview = null, acknowledged = false)
    }
    fun acknowledge(value: Boolean) { if (!mutable.value.busy) mutable.value = mutable.value.copy(acknowledged = value) }
    fun preview() = task {
        val current = mutable.value
        val history = current.history ?: return@task
        val review = repository.previewChatHistory(history, current.selected, current.accounts, current.categories)
        mutable.value = mutable.value.copy(ledgerPreview = review, acknowledged = false)
    }
    fun confirm() {
        val current = mutable.value
        val history = current.history ?: return
        val review = current.ledgerPreview ?: return
        if (!current.acknowledged || current.result != null) return
        task {
            val result = repository.importChatHistory(history, current.selected, current.accounts, current.categories, review.token)
            mutable.value = mutable.value.copy(result = result, ledgerPreview = null)
        }
    }
}
