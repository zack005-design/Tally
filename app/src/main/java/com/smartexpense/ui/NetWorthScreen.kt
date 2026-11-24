package com.smartexpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import kotlinx.coroutines.CancellationException
import java.time.format.TextStyle

/** Net worth from local records: what you have, plus what friends owe you, minus what you owe. */
@Composable fun NetWorthScreen(s: Snapshot, load: suspend () -> NetWorth) {
    var worth by remember { mutableStateOf<NetWorth?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.transactionCount, s.records, s.debts, s.accounts) {
        error = null
        try { worth = load() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Could not load net worth" }
    }
    Page("Net worth") {
        val shown = worth
        when {
            error != null -> item { EmptyLedger("Could not load net worth", error!!) }
            shown == null -> item { Text("Loading your records…", color = Tokens.muted) }
            else -> {
                item {
                    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your net worth", color = Tokens.muted, style = MaterialTheme.typography.bodyMedium)
                        Text(editorialMoney(shown.net), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                            color = if (shown.net < 0) Tokens.negative else Tokens.text)
                    }
                }
                item { SectionLabel("HOW IT ADDS UP") }
                item { GroupedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AmountRow("Account balances", shown.accounts)
                    AmountRow("Friends owe you", shown.owedToYou)
                    AmountRow("You owe friends", -shown.youOwe)
                    HorizontalDivider(color = Tokens.border)
                    AmountRow("Net worth", shown.net)
                } } }
                if (shown.byAccount.isNotEmpty()) {
                    item { SectionLabel("ACCOUNTS") }
                    item { GroupedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        shown.byAccount.forEach { (account, balance) -> AmountRow(account.name + if (account.archived) " (archived)" else "", balance) }
                    } } }
                }
                item { SectionLabel("ACCOUNT TOTAL AT MONTH END") }
                item { TrendBars(shown.trend) }
                item {
                    Text("Built on your phone from your own records. Savings goals are money set aside inside your accounts, so they are not counted twice. Loans, investments and property are not tracked in Tally.",
                        color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable private fun TrendBars(points: List<NetWorthPoint>) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val largest = points.maxOfOrNull { kotlin.math.abs(it.accounts) }?.takeIf { it > 0 } ?: 1L
    GroupedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach { point ->
            val label = point.month.month.getDisplayName(TextStyle.SHORT, locale) + " " + point.month.year
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label: ${editorialMoney(point.accounts)}" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, Modifier.width(72.dp), color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).height(10.dp)) {
                    val fraction = (kotlin.math.abs(point.accounts).toDouble() / largest).toFloat().coerceIn(0.02f, 1f)
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (point.accounts < 0) Tokens.negative else Tokens.accent, RoundedCornerShape(5.dp)))
                }
                Text(referenceMoney(point.accounts), color = Tokens.text, style = MaterialTheme.typography.bodySmall)
            }
        }
    } }
}
