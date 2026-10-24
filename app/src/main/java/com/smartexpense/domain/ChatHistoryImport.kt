package com.smartexpense.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId

data class ChatHistory(val historyId: String, val coverage: String, val entries: List<ChatHistoryEntry>)
data class ChatHistoryEntry(
    val sourceId: String, val kind: String, val date: String? = null,
    val description: String? = null, val amount: String? = null, val account: String? = null,
    val category: String? = null, val notes: String? = null, val shared: Boolean = false,
    val unresolved: List<String> = emptyList(), val details: String? = null
)
data class HistoryImportRow(val source: ChatHistoryEntry, val draft: EntryDraft?, val issues: List<String>, val possibleDuplicate: Boolean, val alreadyRecorded: Boolean)
data class HistoryImportPreview(val rows: List<HistoryImportRow>) {
    fun selected(ids: Set<String>): List<EntryDraft> {
        val knownIds = rows.map { it.source.sourceId }.toSet()
        require(knownIds.containsAll(ids)) { "The selection contains unknown records" }
        return rows.filter { it.source.sourceId in ids }.map {
            requireNotNull(it.draft) { "Resolve or exclude ${it.source.sourceId}: ${it.issues.joinToString()}" }
        }
    }
    fun cashChanges(ids: Set<String>): Map<String, Long> = selected(ids).groupBy { it.accountId }.mapValues { (_, drafts) ->
        Finance.total(drafts.map { if (it.kind == Kind.EXPENSE) Math.negateExact(it.amount) else it.amount })
    }
}
data class HistoryImportResult(val imported: Int, val skipped: Int, val excluded: Int)
data class HistoryLedgerPreview(val token: String, val beforeBalances: Map<String, Long>, val afterBalances: Map<String, Long>, val imported: Int, val skipped: Int)

object ChatHistoryImport {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_ENTRIES = 10000

    fun validate(history: ChatHistory) {
        require(history.historyId.isNotBlank() && history.historyId.length <= 120) { "Invalid history ID" }
        require(history.coverage.isNotBlank() && history.coverage.length <= 2000) { "Describe the history coverage and any gaps" }
        require(history.entries.size in 1..MAX_ENTRIES) { "Choose a history with 1 to 10,000 entries" }
        require(history.entries.map { it.sourceId }.distinct().size == history.entries.size) { "Duplicate source IDs in this file" }
        history.entries.forEach { row ->
            require(row.sourceId.isNotBlank() && row.sourceId.length <= 120 && row.kind.isNotBlank() && row.kind.length <= 80) { "Invalid entry identity or kind" }
            require(listOf(row.date, row.description, row.amount, row.account, row.category, row.notes, row.details).all { it == null || it.length <= 10000 }) { "An entry field is too long" }
            require(row.unresolved.size <= 100 && row.unresolved.all { it.isNotBlank() && it.length <= 10000 }) { "Invalid unresolved details" }
        }
    }

    fun transactionId(historyId: String, sourceId: String): String = "chat-import:" + digest(listOf(historyId, sourceId))

    fun reviewToken(parts: List<String>, clock: java.time.Clock = java.time.Clock.systemDefaultZone()): String = digest(parts + listOf(clock.zone.id, LocalDate.now(clock).toString()))

    // Length prefixes avoid ambiguous source boundaries and delimiter collisions.
    fun digest(parts: List<String>): String {
        val hash = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(StandardCharsets.UTF_8)
            hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); hash.update(bytes)
        }
        val hex = "0123456789abcdef"
        return buildString(64) { hash.digest().forEach { byte -> val value = byte.toInt() and 255; append(hex[value ushr 4]); append(hex[value and 15]) } }
    }

    fun preview(history: ChatHistory, snapshot: Snapshot, accountMapping: Map<String, String>, zone: ZoneId = ZoneId.systemDefault(), categoryMapping: Map<String, String> = emptyMap()): HistoryImportPreview {
        validate(history)
        val records = snapshot.records.associateBy { it.id }
        val dates = snapshot.records.filterNot { it.reversed }.groupBy { signature(it.kind.name, java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate().toString(), it.description, it.amount) }
        val prepared = history.entries.map { entry ->
            val issues = entry.unresolved.toMutableList()
            if (entry.shared || entry.kind !in setOf("EXPENSE", "INCOME")) issues += "Use Tally's existing workflow for shared bills, refunds, repayments, transfers or adjustments"
            val date = entry.date?.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (date == null) issues += "Missing or invalid date"
            val amount = entry.amount?.takeIf { Regex("\\d+(\\.\\d{1,2})?").matches(it) }?.let { runCatching { Money.parse(it) }.getOrNull() }
            if (amount == null || amount <= 0) issues += "Enter a positive exact INR amount with at most two decimals"
            if (entry.description.isNullOrBlank()) issues += "Missing description"
            val account = entry.account?.let { accountMapping[it] }?.takeIf { id -> snapshot.accounts.any { it.id == id && !it.archived } }
            if (account == null) issues += "Map the source to an active account; missing source labels must be corrected in the file"
            val category = entry.category?.takeIf { it.isNotBlank() }
            val categoryId = category?.let { categoryMapping[it] }
            if (category != null && (categoryId == null || (categoryId.isNotEmpty() && snapshot.categories.none { it.id == categoryId && !it.archived }))) issues += "Map category $category or choose Uncategorised"
            val draft = if (issues.isEmpty()) EntryDraft(
                id = transactionId(history.historyId, entry.sourceId), kind = Kind.valueOf(entry.kind), amount = amount!!,
                description = entry.description!!.trim(), accountId = account!!, categoryId = categoryId?.ifEmpty { null },
                date = date!!.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                notes = listOfNotNull(entry.notes?.takeIf { it.isNotBlank() }, entry.details?.takeIf { it.isNotBlank() }).joinToString("\n")
            ) else null
            HistoryImportRow(entry, draft, issues.toList(), draft != null && dates.containsKey(signature(entry.kind, entry.date!!, entry.description!!, amount!!)), records.containsKey(transactionId(history.historyId, entry.sourceId)))
        }
        val repeated = prepared.filter { it.draft != null }.groupingBy { signature(it.source.kind, it.source.date!!, it.source.description!!, it.draft!!.amount) }.eachCount()
        return HistoryImportPreview(prepared.map { row -> row.copy(possibleDuplicate = row.possibleDuplicate || (row.draft != null && repeated.getValue(signature(row.source.kind, row.source.date!!, row.source.description!!, row.draft.amount)) > 1)) })
    }

    private fun signature(kind: String, date: String, description: String, amount: Long) = listOf(kind, date, description.trim().lowercase(java.util.Locale.ROOT), amount.toString())
}
