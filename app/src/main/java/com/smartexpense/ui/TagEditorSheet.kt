package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.Tags

/** Edits a transaction's tags as comma-separated text, with tags used before offered as shortcuts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun TagEditorSheet(text: String, onText: (String) -> Unit, existing: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val parsed = remember(text) { runCatching { Tags.parse(text) } }
    val current = parsed.getOrNull().orEmpty()
    val error = parsed.exceptionOrNull()?.message
    EditorialSaveSheet("Tags", onDismiss, enabled = parsed.isSuccess, onSave = { parsed.getOrNull()?.let(onSave) }) {
        Field("Tags", text, onText)
        Text(
            error ?: "Separate tags with commas, for example Goa trip, work. Up to ${Tags.MAX_TAGS} tags. Leave empty to remove them.",
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) Tokens.negative else Tokens.muted
        )
        val suggestions = existing.filter { tag -> current.none { it.equals(tag, ignoreCase = true) } }.take(8)
        if (suggestions.isNotEmpty()) {
            Text("Used before", style = MaterialTheme.typography.labelMedium, color = Tokens.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { tag ->
                    AssistChip(onClick = { onText((current + tag).joinToString(", ")) }, label = { Text(tag) })
                }
            }
        }
        Text("Tags are labels only. They never change amounts, balances or budgets.", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
    }
}
