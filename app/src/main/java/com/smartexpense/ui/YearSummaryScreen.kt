package com.smartexpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

@Composable fun YearSummaryScreen(s: Snapshot, load: suspend (Int) -> YearSummary) {
    val currentYear = LocalDate.now().year
    var year by rememberSaveable { mutableIntStateOf(currentYear) }
    var summary by remember { mutableStateOf<YearSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Reload when the selected year or the saved records change.
    LaunchedEffect(year, s.transactionCount, s.records) {
        error = null
        try { summary = load(year) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not load this year" }
    }
    val shown = summary?.takeIf { it.year == year }
    val earliest = shown?.earliestYear ?: summary?.earliestYear
    Page("Yearly summary") {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ year -= 1 }, enabled = earliest == null || year > earliest, modifier = Modifier.semantics { contentDescription = "Previous year" }) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
                Text(year.toString(), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton({ year += 1 }, enabled = year < currentYear, modifier = Modifier.semantics { contentDescription = "Next year" }) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        when {
            error != null -> item { EmptyLedger("Could not load $year", error!!) }
            shown == null -> item { Text("Loading your records…", color = Tokens.muted) }
            !shown.hasRecords -> item { EmptyLedger("Nothing recorded in $year", "Entries you add for this year will be summarised here.") }
            else -> {
                item { YearHero(shown) }
                item { YearMonths(shown) }
                if (shown.categories.isNotEmpty()) item { YearCategories(shown, s.categories) }
                if (shown.tags.any { it.value > 0 }) item { YearTags(shown) }
                item { ReferenceInfo("Only your share of shared bills counts as spending. Refunds reduce spending. Transfers and repayments are not spending.") }
            }
        }
    }
}

@Composable private fun YearHero(summary: YearSummary) {
    val locale = LocalConfiguration.current.locales[0]
    val inProgress = summary.through.year == summary.year && summary.through != LocalDate.of(summary.year, 12, 31)
    GroupedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (inProgress) "Spent so far" else "Spent in ${summary.year}", style = MaterialTheme.typography.titleSmall, color = Tokens.muted)
            Text(referenceMoney(summary.spending), style = MaterialTheme.typography.headlineLarge)
            if (inProgress) Text("1 Jan to ${summary.through.format(DateTimeFormatter.ofPattern("d MMM"))}", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
            Text(comparison(summary), style = MaterialTheme.typography.bodyMedium)
        }
        GroupedDivider(16.dp)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Income", style = MaterialTheme.typography.bodyLarge)
            Text(referenceMoney(summary.income), style = MaterialTheme.typography.bodyLarge)
        }
        summary.busiestMonth?.let { month ->
            GroupedDivider(16.dp)
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Highest month", style = MaterialTheme.typography.bodyLarge)
                Text(Month.of(month).getDisplayName(TextStyle.FULL, locale), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun comparison(summary: YearSummary): String {
    val previous = summary.year - 1
    val period = if (summary.through.monthValue == 12 && summary.through.dayOfMonth == 31) "in $previous" else "over the same dates in $previous"
    val change = summary.changePercent ?: return "No spending recorded $period to compare with."
    return when {
        change > 0 -> "$change% more than $period (${referenceMoney(summary.previousSpending)})."
        change < 0 -> "${-change}% less than $period (${referenceMoney(summary.previousSpending)})."
        else -> "About the same as $period (${referenceMoney(summary.previousSpending)})."
    }
}

@Composable private fun YearMonths(summary: YearSummary) {
    val locale = LocalConfiguration.current.locales[0]
    val lastMonth = if (summary.through.year == summary.year) summary.through.monthValue else 12
    val highest = summary.months.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    GroupedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("By month", style = MaterialTheme.typography.titleSmall)
            summary.months.take(lastMonth).forEachIndexed { index, amount ->
                val name = Month.of(index + 1).getDisplayName(TextStyle.SHORT, locale)
                val fraction = if (amount > 0) (amount.toFloat() / highest).coerceIn(0.02f, 1f) else 0f
                Row(
                    Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${Month.of(index + 1).getDisplayName(TextStyle.FULL, locale)}: ${referenceMoney(amount)}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(name, Modifier.widthIn(min = 36.dp), style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                    Box(Modifier.weight(1f).height(10.dp).background(Tokens.secondarySurface, RoundedCornerShape(5.dp))) {
                        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (index + 1 == summary.busiestMonth) Tokens.accent else Tokens.accent.copy(alpha = .55f), RoundedCornerShape(5.dp)))
                    }
                    Text(referenceMoney(amount), Modifier.widthIn(min = 72.dp), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }
}

@Composable private fun YearTags(summary: YearSummary) {
    GroupedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("By tag", style = MaterialTheme.typography.titleSmall)
            summary.tags.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(10).forEach { (tag, amount) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("#$tag", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(referenceMoney(amount), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("A transaction with several tags counts towards each of them.", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
        }
    }
}

@Composable private fun YearCategories(summary: YearSummary, categories: List<Category>) {
    val total = summary.spending.coerceAtLeast(1L)
    GroupedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Top categories", style = MaterialTheme.typography.titleSmall)
            summary.categories.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(8).forEach { (id, amount) ->
                val name = categories.find { it.id == id }?.name ?: "Uncategorised"
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReferenceIcon(referenceSymbol(name, R.drawable.ic_basket), ReferenceTone.Lavender, 28.dp)
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${Math.round(amount * 100.0 / total)}%", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                    Text(referenceMoney(amount), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
