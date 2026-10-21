package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*

@Composable
fun AutoCalculationPanel(state: EntryUiState, accounts: List<Account>, onChange: (EntryUiState) -> Unit, people: List<Person> = emptyList(),sources:WalletSources?=null) {
    if (state.amount.isBlank()) return
    val labels = people.associate { it.id to personLabel(it, people) }
    fun name(id: String): String = if (id == "self") "You" else labels[id] ?: "Participant ${state.participants.indexOf(id) + 1}"
    var shortcutError by rememberSaveable(state.id, state.amount, state.method, state.values, state.paid) { mutableStateOf<String?>(null) }
    var detailsExpanded by rememberSaveable(state.id) { mutableStateOf(false) }
    fun apply(result: Result<EntryUiState>) {
        result.fold(onSuccess = { shortcutError = null; onChange(it) }, onFailure = { shortcutError = it.message ?: "Check the amounts" })
    }
    if (state.kind == Kind.EXPENSE && state.shared) {
        Panel {
            SectionHeading("Live bill calculation")
            Text("Draft preview · nothing is saved", color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
            val result = automaticBill(state)
            val bill = result.getOrNull()
            if (bill == null) Text(result.exceptionOrNull()?.message ?: "Complete the split values", color = Tokens.negative, style = MaterialTheme.typography.bodySmall)
            else {
                if (bill.net == null || detailsExpanded) {
                    bill.shares.forEach { (id, share) -> CalculationAmount("${name(id)} · share", share) }
                    HorizontalDivider(color = Tokens.border)
                    CalculationAmount("Entered payments", bill.paidTotal)
                }
                if (bill.remaining < 0) CalculationAmount("Overpaid", Math.negateExact(bill.remaining), error = true)
                else if (bill.net == null) CalculationAmount("Unassigned payment", bill.remaining)
                else Text("All payments reconcile with the bill", color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                if (bill.missingPayments.isNotEmpty()) {
                    Text("Missing payments are provisional zero: ${bill.missingPayments.joinToString { name(it) }}. Enter 0 explicitly for anyone who paid nothing.", color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                }
                if (bill.net == null) Text("Receive / owe and settlement suggestions appear when all payments are entered and match the bill.", color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
                else {
                    bill.net.forEach { (id, net) ->
                        CalculationAmount("${name(id)} · ${when { net > 0 -> "receives"; net < 0 -> "owes"; else -> "settled" }}", if (net < 0) Math.negateExact(net) else net)
                    }
                    if (bill.settlements.isNotEmpty()) {
                        Text("Suggested settlements", style = MaterialTheme.typography.titleSmall)
                        bill.settlements.forEach { CalculationAmount("${name(it.from)} → ${name(it.to)}", it.amount) }
                    }
                }
                if (bill.net != null) TextButton({ detailsExpanded = !detailsExpanded }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (detailsExpanded) "Hide calculation details" else "Show calculation details")
                }
            }
            // These explicit actions assign payments; the preview above never writes provisional zeroes.
            if (state.method in listOf("Fixed", "Percentage")) {
                OutlinedButton({ apply(fillRemainingShare(state)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (state.method == "Percentage") "Fill remaining percentage" else "Fill remaining exact share") }
            }
            shortcutError?.let { Text(it, color = Tokens.negative, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (state.accountId.isNotBlank()) {
        Panel {
            SectionHeading("Projected account balance")
            Text("After this draft · before saving", color = Tokens.muted, style = MaterialTheme.typography.bodySmall)
            val result = projectedBalances(state, accounts,sources)
            result.getOrNull()?.forEach { account ->
                Text(account.name, style = MaterialTheme.typography.titleSmall)
                AmountRow("Current balance", account.before)
                AmountRow("After transaction", account.after)
            } ?: Text(result.exceptionOrNull()?.message ?: "Complete the account details", color = Tokens.negative, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CalculationAmount(label: String, amount: Long, error: Boolean = false) {
    Text("$label: ${editorialMoney(amount)}", color = if (error) Tokens.negative else Tokens.text, style = MaterialTheme.typography.bodyMedium)
}
