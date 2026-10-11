package com.smartexpense.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object Money {
    const val currency = "INR"
    fun parse(text: String): Long {
        val clean = text.trim().removePrefix("₹").replace(",", "")
        require(Regex("[+-]?\\d+(\\.\\d{1,2})?").matches(clean)) { "Enter an amount with at most two decimal places" }
        return try { BigDecimal(clean).movePointRight(2).longValueExact() }
        catch (e: ArithmeticException) { throw IllegalArgumentException("Amount is too large", e) }
    }
    fun format(paise: Long): String = "₹" + BigDecimal.valueOf(paise, 2).setScale(2).toPlainString()
    fun input(paise: Long): String = BigDecimal.valueOf(paise, 2).toPlainString()
}

sealed interface SplitRule {
    data object Equal : SplitRule
    data class Weighted(val weights: Map<String, Long>) : SplitRule
    /** Percentages in basis points: 10000 means 100 percent. */
    data class Percentage(val basisPoints: Map<String, Long>) : SplitRule
    data class Fixed(val amounts: Map<String, Long>) : SplitRule
    data class Itemized(val items: List<SplitItem>) : SplitRule
}
data class SplitItem(val amount: Long, val people: List<String>)
data class SuggestedSettlement(val from: String, val to: String, val amount: Long)
enum class Kind { EXPENSE, INCOME, TRANSFER, REPAYMENT, REFUND, ADJUSTMENT, OFFSET }
data class Movement(val accountId: String, val amount: Long)
data class LedgerEvent(val id: String, val kind: Kind, val personalAmount: Long, val movements: List<Movement>, val reversed: Boolean = false)

object Finance {
    fun total(values: Iterable<Long>): Long = values.fold(0L, Math::addExact)
    fun split(total: Long, people: List<String>, rule: SplitRule): Map<String, Long> {
        require(total > 0) { "Bill must be positive" }
        require(people.isNotEmpty() && people.distinct().size == people.size) { "Choose distinct participants" }
        return when (rule) {
            SplitRule.Equal -> weighted(total, people, people.associateWith { 1L })
            is SplitRule.Weighted -> weighted(total, people, rule.weights)
            is SplitRule.Percentage -> {
                require(total(rule.basisPoints.values) == 10000L) { "Percentages must add to 100%" }
                weighted(total, people, rule.basisPoints)
            }
            is SplitRule.Fixed -> {
                require(rule.amounts.keys == people.toSet() && rule.amounts.values.all { it >= 0 } && total(rule.amounts.values) == total) { "Fixed shares must equal the bill" }
                people.associateWith { rule.amounts.getValue(it) }
            }
            is SplitRule.Itemized -> {
                require(total(rule.items.map { it.amount }) == total) { "Item amounts must equal the bill" }
                val result = people.associateWith { 0L }.toMutableMap()
                rule.items.forEach { item ->
                    require(item.people.all { it in people }) { "Item has unknown participants" }
                    split(item.amount, item.people, SplitRule.Equal).forEach { (id, amount) -> result[id] = Math.addExact(result.getValue(id), amount) }
                }
                result
            }
        }
    }
    /** Largest remainders; ties use explicit participant order. BigInteger avoids product overflow. */
    private fun weighted(total: Long, people: List<String>, weights: Map<String, Long>): Map<String, Long> {
        require(weights.keys == people.toSet() && weights.values.all { it >= 0 } && weights.values.any { it > 0 }) { "Enter non-negative weights for every participant" }
        val denominator = weights.values.fold(BigInteger.ZERO) { acc, n -> acc + n.toBigInteger() }
        val parts = people.map { id -> total.toBigInteger().multiply(weights.getValue(id).toBigInteger()).divideAndRemainder(denominator) }
        // Quotients are non-negative and <= total (a Long); toLong is exact and available on API 26.
        val values = parts.map { it[0].toLong() }.toMutableList()
        val left = Math.subtractExact(total, total(values)).toInt()
        people.indices.sortedWith(compareByDescending<Int> { parts[it][1] }.thenBy { it }).take(left).forEach { values[it]++ }
        return people.indices.associate { people[it] to values[it] }
    }
    fun net(shares: Map<String, Long>, contributions: Map<String, Long>): Map<String, Long> {
        require(shares.keys == contributions.keys && contributions.values.all { it >= 0 } && total(shares.values) == total(contributions.values)) { "Contributions must reconcile with the bill" }
        return shares.mapValues { (id, share) -> Math.subtractExact(contributions.getValue(id), share) }
    }
    fun settlements(net: Map<String, Long>): List<SuggestedSettlement> {
        require(total(net.values) == 0L) { "Debt positions do not reconcile" }
        val creditors = net.filterValues { it > 0 }.toMutableMap()
        val result = mutableListOf<SuggestedSettlement>()
        net.filterValues { it < 0 }.forEach { (debtor, negative) ->
            var remaining = Math.negateExact(negative)
            creditors.keys.toList().forEach { creditor ->
                val amount = minOf(remaining, creditors.getValue(creditor))
                if (amount > 0) { result += SuggestedSettlement(debtor, creditor, amount); remaining -= amount; creditors[creditor] = creditors.getValue(creditor) - amount }
            }
            check(remaining == 0L)
        }
        return result
    }
    fun allocate(amount: Long, outstanding: List<Pair<String, Long>>): Map<String, Long> {
        require(amount > 0 && outstanding.map { it.first }.distinct().size == outstanding.size && outstanding.all { it.second >= 0 }) { "Invalid repayment" }
        require(amount <= total(outstanding.map { it.second })) { "Repayment exceeds outstanding debt" }
        var remaining = amount
        return buildMap { outstanding.forEach { (id, due) -> val part = minOf(remaining, due); if (part > 0) put(id, part); remaining -= part } }
    }
    fun balances(events: List<LedgerEvent>): Map<String, Long> = buildMap {
        events.filterNot { it.reversed }.flatMap { it.movements }.forEach { put(it.accountId, Math.addExact(get(it.accountId) ?: 0, it.amount)) }
    }
    fun spending(events: List<LedgerEvent>): Long = total(events.filterNot { it.reversed }.map { when(it.kind) { Kind.EXPENSE -> it.personalAmount; Kind.REFUND -> Math.negateExact(it.personalAmount); else -> 0L } })
    fun income(events: List<LedgerEvent>): Long = total(events.filter { !it.reversed && it.kind == Kind.INCOME }.map { it.personalAmount })
}
