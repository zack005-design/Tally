package com.smartexpense.domain

import kotlin.math.absoluteValue

/** Amount matching for the Transactions search box. Text matching stays in the screen. */
object TransactionSearch {
    private val amountPattern = Regex("₹?\\s*[0-9][0-9,]*(\\.[0-9]{1,2})?")

    /** Paise for a query such as "80", "₹1,250" or "20.50"; null when the query is not an amount. */
    fun amount(query: String): Long? {
        val text = query.trim()
        if (!amountPattern.matches(text)) return null
        return runCatching { Money.parse(text.replace(" ", "")) }.getOrNull()?.takeIf { it > 0 }
    }

    /** True when the record's total or your share equals [paise], ignoring sign. */
    fun matchesAmount(record: Record, paise: Long): Boolean =
        record.amount.absoluteValue == paise || record.personalAmount.absoluteValue == paise
}
