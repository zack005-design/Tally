package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MoneyPlannerScreen(snapshot: Snapshot, onBack: () -> Unit = {}) {
    var source by rememberSaveable { mutableStateOf("available") }
    var available by rememberSaveable { mutableStateOf("") }
    var costs by rememberSaveable { mutableStateOf("0") }
    var savings by rememberSaveable { mutableStateOf("0") }
    var needsPercent by rememberSaveable { mutableStateOf("50") }
    var wantsPercent by rememberSaveable { mutableStateOf("30") }
    var savingsPercent by rememberSaveable { mutableStateOf("20") }
    var startDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var endDate by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(29).toString()) }
    var choosingDate by rememberSaveable { mutableStateOf<String?>(null) }
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    val accountMoney = runCatching { Finance.total(snapshot.accounts.filterNot { it.archived }.map { it.balance }) }
    val planResult = runCatching {
        moneyPlan(Money.parse(available), Money.parse(costs), Money.parse(savings),
            listOf(needsPercent, wantsPercent, savingsPercent).map(Money::parse), LocalDate.parse(startDate), LocalDate.parse(endDate))
    }
    Page("Money planner", "Explore a what-if plan", onBack = onBack) {
        item {
            Panel {
                Text("Preview only", style = MaterialTheme.typography.titleLarge)
                Text("This planner never saves income, transactions or budgets. Enter one available amount or expected income; saved income is not added again.", color = Tokens.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Panel {
                SectionHeading("Money to plan")
                EditorialSegmentedControl(source, listOf("available" to "Available money", "income" to "Expected income")) { source = it }
                Field(if (source == "income") "Expected income (₹)" else "Available money (₹)", available, { available = it }, true)
                if (source == "available") {
                    accountMoney.getOrNull()?.let { total ->
                        AmountRow("Active account balances", total)
                        OutlinedButton({ available = Money.input(total) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = total >= 0) { Text("Use account balances") }
                    } ?: Text("Account balances are too large to combine", color = Tokens.negative, style = MaterialTheme.typography.bodySmall)
                }
                Field("Committed costs (₹)", costs, { costs = it }, true)
                Field("Desired savings (₹)", savings, { savings = it }, true)
            }
        }
        item {
            Panel {
                SectionHeading("Allocation preview")
                Text("Edit the percentages to allocate your entered amount. Costs and desired savings set the daily allowance separately.", color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                Field("Needs (%)", needsPercent, { needsPercent = it }, true)
                Field("Wants (%)", wantsPercent, { wantsPercent = it }, true)
                Field("Savings (%)", savingsPercent, { savingsPercent = it }, true)
                planResult.getOrNull()?.let { plan ->
                    listOf("Needs", "Wants", "Savings allocation").zip(plan.allocations).forEach { (label, amount) -> AmountRow(label, amount) }
                    AmountRow("Unallocated", plan.unallocated)
                }
            }
        }
        item {
            Panel {
                SectionHeading("Daily allowance")
                OutlinedButton({ choosingDate = "start" }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("From ${LocalDate.parse(startDate).format(formatter)}") }
                OutlinedButton({ choosingDate = "end" }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Through ${LocalDate.parse(endDate).format(formatter)}") }
                planResult.getOrNull()?.let { plan ->
                    AmountRow("After costs and desired savings", plan.remaining)
                    Text("${plan.days} days · both selected dates included", color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                    AmountRow("Daily allowance", plan.dailyAllowance)
                    if (plan.dailyRemainder > 0) AmountRow("Paise left after equal daily amounts", plan.dailyRemainder)
                    Text("The savings allocation above is a scenario, not an additional deduction from this balance.", color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (available.isNotBlank() && planResult.isFailure) item {
            Text(planResult.exceptionOrNull()?.message ?: "Check the planner values", color = Tokens.negative, style = MaterialTheme.typography.bodyMedium)
        }
        if (available.isBlank()) item { Text("Enter an amount to calculate your preview.", color = Tokens.muted, style = MaterialTheme.typography.bodyMedium) }
    }
    choosingDate?.let { target ->
        EditorialDatePicker(if (target == "start") "Plan start date" else "Plan end date", LocalDate.parse(if (target == "start") startDate else endDate),
            onDismiss = { choosingDate = null }, onConfirm = { date ->
                if (target == "start") startDate = date.toString() else endDate = date.toString()
                choosingDate = null
            })
    }
}
