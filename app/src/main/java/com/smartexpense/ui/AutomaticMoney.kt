package com.smartexpense.ui

import com.smartexpense.domain.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class AutomaticBill(
    val shares: Map<String, Long>,
    val contributions: Map<String, Long>,
    val paidTotal: Long,
    val remaining: Long,
    val missingPayments: Set<String>,
    val net: Map<String, Long>?,
    val settlements: List<SuggestedSettlement>
)

/** Draft arithmetic only: a blank payment is provisional zero, never a saved contribution. */
fun automaticBill(state: EntryUiState): Result<AutomaticBill> = runCatching {
    require(state.kind == Kind.EXPENSE) { "Bill calculations apply to expenses" }
    val amount = Money.parse(state.amount)
    val people = if (state.shared) state.participants else listOf("self")
    val rule = if (!state.shared) SplitRule.Equal else when (state.method) {
        "Equal" -> SplitRule.Equal
        "Fixed" -> SplitRule.Fixed(people.associateWith { Money.parse(state.values[it].orEmpty()) })
        "Percentage" -> SplitRule.Percentage(people.associateWith { Money.parse(state.values[it].orEmpty()) })
        "Weighted" -> SplitRule.Weighted(people.associateWith {
            state.values[it]?.toLongOrNull() ?: throw IllegalArgumentException("Enter whole-number weights")
        })
        "Itemized" -> foodSplitRule(state.items,people,Money.parse(state.itemCharges.ifBlank {"0"}),Money.parse(state.itemDiscount.ifBlank {"0"}))
        else -> throw IllegalArgumentException("Choose a split method")
    }
    val shares = Finance.split(amount, people, rule)
    val missing = if (state.shared) people.filter { state.paid[it].isNullOrBlank() }.toSet() else emptySet()
    val contributions = if (state.shared) people.associateWith { id ->
        if (id in missing) 0L else Money.parse(state.paid.getValue(id)).also {
            require(it >= 0) { "Payments cannot be negative" }
        }
    } else mapOf("self" to amount)
    val paidTotal = Finance.total(contributions.values)
    val remaining = Math.subtractExact(amount, paidTotal)
    val net = if (missing.isEmpty() && remaining == 0L) Finance.net(shares, contributions) else null
    AutomaticBill(shares, contributions, paidTotal, remaining, missing, net, net?.let(Finance::settlements).orEmpty())
}

data class ProjectedBalance(val accountId: String, val name: String, val before: Long, val after: Long)

/** Account preview does not require unrelated draft metadata or alter the ledger. */
fun projectedBalances(state: EntryUiState, accounts: List<Account>, sources:WalletSources?=null): Result<List<ProjectedBalance>> = runCatching {
    val amount = Money.parse(state.amount)
    require(amount > 0) { "Enter a positive amount" }
    fun selected(id: String) = accounts.singleOrNull { it.id == id && !it.archived }
        ?: throw IllegalArgumentException("Choose an active account")
    val movements = when (state.kind) {
        Kind.EXPENSE -> {
            val paid = if (!state.shared) amount else if ("self" !in state.participants) 0L else {
                require(!state.paid["self"].isNullOrBlank()) { "Enter your payment to project your account balance" }
                Money.parse(state.paid.getValue("self")).also { require(it >= 0) { "Payments cannot be negative" } }
            }
            val sources=state.sourcePayments(paid).ifEmpty {mapOf(selected(state.accountId).id to paid)}
            sources.map {Movement(selected(it.key).id,Math.negateExact(it.value))}
        }
        Kind.TRANSFER -> {
            require(state.accountId != state.destinationId) { "Choose different transfer accounts" }
            listOf(Movement(selected(state.accountId).id, -amount), Movement(selected(state.destinationId).id, amount))
        }
        Kind.INCOME, Kind.REFUND -> listOf(Movement(selected(state.accountId).id, amount))
        else -> throw IllegalArgumentException("Account preview is unavailable for this transaction type")
    }
    val date=java.time.LocalDateTime.parse(state.date).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun effective(account:String,amount:Long,date:Long?):Long = if(account==sources?.gpayId && sources?.resetAt!=null && date!=null && date<sources.resetAt) 0L else amount
    val newMovements=movements.associate {it.accountId to effective(it.accountId,it.amount,date)}
    (newMovements.keys+state.originalMovements.keys).map {id->
        val account=accounts.single {it.id==id}
        val original=effective(id,state.originalMovements[id]?:0,state.originalDate)
        val after=Math.addExact(Math.subtractExact(account.balance,original),newMovements[id]?:0)
        ProjectedBalance(account.id,account.name,account.balance,after)
    }
}

fun payAll(state: EntryUiState): Result<EntryUiState> = runCatching {
    require(state.kind == Kind.EXPENSE && state.shared && "self" in state.participants) { "Include yourself before choosing I paid all" }
    val amount = Money.parse(state.amount)
    Finance.split(amount, state.participants, SplitRule.Equal)
    state.copy(paid = state.participants.associateWith { Money.input(if (it == "self") amount else 0L) })
}

fun equalPayments(state: EntryUiState): Result<EntryUiState> = runCatching {
    require(state.kind == Kind.EXPENSE && state.shared) { "Choose a shared expense" }
    val payments = Finance.split(Money.parse(state.amount), state.participants, SplitRule.Equal)
    state.copy(paid = payments.mapValues { Money.input(it.value) })
}

fun fillRemainingShare(state: EntryUiState): Result<EntryUiState> = runCatching {
    require(state.kind == Kind.EXPENSE && state.shared && state.method in listOf("Fixed", "Percentage")) { "Choose exact or percentage shares" }
    require(state.participants.isNotEmpty() && state.participants.distinct().size == state.participants.size) { "Choose distinct participants" }
    val missing = state.participants.filter { state.values[it].isNullOrBlank() }
    require(missing.size == 1) { "Leave exactly one share empty to fill the remainder" }
    val total = if (state.method == "Percentage") 10000L else Money.parse(state.amount).also { require(it > 0) { "Enter a positive amount" } }
    val filled = state.participants.filterNot { it in missing }.map { id ->
        Money.parse(state.values.getValue(id)).also { require(it >= 0) { "Shares cannot be negative" } }
    }
    val remainder = Math.subtractExact(total, Finance.total(filled))
    require(remainder >= 0) { "Entered shares exceed the total" }
    state.copy(values = state.values + (missing.single() to Money.input(remainder)))
}

data class MoneyPlan(
    val allocations: List<Long>,
    val unallocated: Long,
    val remaining: Long,
    val days: Long,
    val dailyAllowance: Long,
    val dailyRemainder: Long
)

/** Percentages use basis points. Allocation and cash-flow what-if are separate views of available money. */
fun moneyPlan(available: Long, costs: Long, savings: Long, percentages: List<Long>, days: Long): MoneyPlan {
    require(available >= 0 && costs >= 0 && savings >= 0) { "Amounts cannot be negative" }
    require(days > 0) { "Choose an end date on or after the start date" }
    require(percentages.size == 3 && percentages.all { it in 0L..10000L }) { "Enter percentages from 0 to 100" }
    val percentTotal = Finance.total(percentages)
    require(percentTotal <= 10000L) { "Allocations cannot exceed 100%" }
    val committed = Math.addExact(costs, savings)
    require(committed <= available) { "Costs and desired savings exceed available money" }
    val remaining = Math.subtractExact(available, committed)
    // Treat unallocated money as a fourth bucket so every paise has a deterministic destination.
    val weights = percentages + (10000L - percentTotal)
    val ids = listOf("needs", "wants", "savings", "unallocated")
    val buckets = if (available == 0L) ids.associateWith { 0L }
        else Finance.split(available, ids, SplitRule.Percentage(ids.zip(weights).toMap()))
    return MoneyPlan(ids.take(3).map { buckets.getValue(it) }, buckets.getValue("unallocated"), remaining, days, remaining / days, remaining % days)
}

fun moneyPlan(available: Long, costs: Long, savings: Long, percentages: List<Long>, start: LocalDate, end: LocalDate): MoneyPlan =
    moneyPlan(available, costs, savings, percentages, Math.addExact(ChronoUnit.DAYS.between(start, end), 1L))
