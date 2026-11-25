package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Forecast, alerts, recurring payments, budget suggestions and duplicates, all computed on the phone. */
@Composable fun SmartInsightsScreen(s: Snapshot, busy: Boolean, onUseBudget: (String, String, Long) -> Unit) {
    val today = LocalDate.now()
    val report = remember(s.records, s.budgets, s.categories, today) { SmartInsights.build(s.records, s.budgets, s.categories, today) }
    val dates = remember { DateTimeFormatter.ofPattern("d MMM") }
    Page("Smart insights", subtitle = "Worked out on your phone from your own entries") {
        item { SectionLabel("THIS MONTH") }
        item { InsightCard {
            val forecast = report.forecast
            if (forecast == null) Text("Keep recording for two weeks to see a month-end forecast.", color = Tokens.muted)
            else {
                AmountRow("Spent so far", forecast.spentSoFar)
                AmountRow("Likely by month end", forecast.projected)
                Text("Based on your average of ${editorialMoney(forecast.dailyPace)} a day over the last 28 days, for the ${forecast.daysLeft} days left.",
                    color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                forecast.budget?.let { budget ->
                    val over = forecast.projected - budget
                    Text(if (over > 0) "At this pace you would go ${editorialMoney(over)} over your ${editorialMoney(budget)} monthly budget."
                        else "At this pace you would stay within your ${editorialMoney(budget)} monthly budget.",
                        color = if (over > 0) Tokens.negative else Tokens.positive, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } }
        item { SectionLabel("WORTH A LOOK") }
        item { InsightCard {
            if (report.alerts.isEmpty()) Text("Nothing unusual in your recent spending.", color = Tokens.muted)
            report.alerts.forEach { alert ->
                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                Text(alert.detail, color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
            }
            report.duplicates.forEach { d ->
                Text("Possible duplicate: ${d.description}", style = MaterialTheme.typography.titleSmall)
                Text("${d.count} entries of ${editorialMoney(d.amount)} on ${d.date.format(dates)}. Delete one if it was saved twice.",
                    color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { SectionLabel("REPEATING PAYMENTS") }
        item { InsightCard {
            if (report.recurring.isEmpty()) Text("No repeating payments found yet. Tally looks for the same description at a steady interval and amount, at least three times.",
                color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
            else {
                AmountRow("About per month", Finance.total(report.recurring.map { it.monthlyCost }))
                report.recurring.forEach { r ->
                    Text(r.description, style = MaterialTheme.typography.titleSmall)
                    Text("${editorialMoney(r.typicalAmount)} every ${interval(r.everyDays)} · next around ${r.nextDate.format(dates)} · seen ${r.occurrences} times",
                        color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } }
        item { SectionLabel("SUGGESTED BUDGETS") }
        item { InsightCard {
            if (report.budgets.isEmpty()) Text("No suggestions: categories need spending in at least two of the last three months and no budget yet this month.",
                color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
            report.budgets.forEach { b ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${b.categoryName}: ${editorialMoney(b.amount)}", style = MaterialTheme.typography.titleSmall)
                        Text("Your typical month, from the last ${b.basedOnMonths} months with spending", color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton({ onUseBudget(YearMonth.now().toString(), b.categoryId, b.amount) }, enabled = !busy) { Text("Use") }
                }
            }
        } }
        item { Text("These are statistics over your saved entries, not financial advice. Nothing is changed unless you tap Use.",
            Modifier.semantics { heading() }.padding(top = 4.dp), color = Tokens.muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun InsightCard(content: @Composable ColumnScope.() -> Unit) =
    GroupedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }

private fun interval(days: Int) = when (days) {
    in 6..8 -> "week"; in 13..16 -> "2 weeks"; in 27..33 -> "month"; in 85..95 -> "3 months"; in 355..370 -> "year"; else -> "$days days"
}
