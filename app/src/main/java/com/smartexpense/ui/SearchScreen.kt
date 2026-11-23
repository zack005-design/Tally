package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Records matching a search, newest first. Matches descriptions, notes, categories, accounts, people and tags. */
internal fun searchRecords(s: Snapshot, query: String, month: YearMonth?, zone: ZoneId): List<Record> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val categoryNames = s.categories.associate { it.id to it.name }
    val accountNames = s.accounts.associate { it.id to it.name }
    val personNames = s.people.associate { it.id to it.name }
    val tag = q.removePrefix("#")
    return s.records.filter { r ->
        (month == null || YearMonth.from(Instant.ofEpochMilli(r.date).atZone(zone)) == month) &&
            (r.description.contains(q, true) || r.notes.contains(q, true) ||
                categoryNames[r.categoryId].orEmpty().contains(q, true) ||
                r.accountIds.any { accountNames[it].orEmpty().contains(q, true) } ||
                r.personIds.any { personNames[it].orEmpty().contains(q, true) } ||
                (tag.isNotEmpty() && r.tags.any { it.contains(tag, true) }))
    }.sortedByDescending { it.date }
}

/** Search opened from the tab bar: results above, the field and close button at the bottom, within thumb reach. */
@Composable fun SearchScreen(s: Snapshot, onDetails: (String) -> Unit, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var thisMonth by rememberSaveable { mutableStateOf(true) }
    val zone = remember { ZoneId.systemDefault() }
    val results = remember(s.records, s.categories, s.accounts, s.people, query, thisMonth, zone) { searchRecords(s, query, if (thisMonth) YearMonth.now(zone) else null, zone) }
    val spent = remember(results) { Finance.total(results.filter { it.kind == Kind.EXPENSE && !it.reversed }.map { it.amount }) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = Tokens.pageInset, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Search", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() }) }
                item {
                    ChipChoices(if (thisMonth) "month" else "all", listOf("month" to "This month", "all" to "All time")) { thisMonth = it == "month" }
                }
                if (query.isBlank()) item {
                    Text("Search descriptions, notes, categories, accounts, people and #tags.", style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
                } else {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (results.size == 1) "1 result" else "${results.size} results", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            if (spent > 0) Text("−${referenceMoney(spent)} in total", style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
                        }
                    }
                    item {
                        GroupedCard {
                            if (results.isEmpty()) Text("No matches. Try another word, or search all time.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
                            else results.take(200).forEach { record ->
                                Box(Modifier.padding(horizontal = 16.dp)) { TransactionRow(record, s, containerColor = Tokens.paper) { onDetails(record.id) } }
                            }
                        }
                    }
                    if (results.size > 200) item { Text("Showing the newest 200. Add a word to narrow the search.", style = MaterialTheme.typography.bodySmall, color = Tokens.muted) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(query, { query = it }, Modifier.weight(1f).heightIn(min = 52.dp).focusRequester(focus),
                    placeholder = { Text("Search", style = MaterialTheme.typography.bodyLarge) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge, shape = CircleShape,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp), tint = Tokens.muted) },
                    trailingIcon = if (query.isEmpty()) null else ({
                        IconButton({ query = "" }) { Icon(painterResource(R.drawable.ic_close), "Clear search", Modifier.size(18.dp), tint = Tokens.muted) }
                    }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Tokens.paper, unfocusedContainerColor = Tokens.paper,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Tokens.accent))
                Surface(onClick = onClose, shape = CircleShape, color = Tokens.paper, contentColor = Tokens.text, shadowElevation = 2.dp, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_close), "Close search", Modifier.size(20.dp)) }
                }
            }
        }
    }
}
