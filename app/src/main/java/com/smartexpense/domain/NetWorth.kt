package com.smartexpense.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One dated account movement from the ledger (a transaction's entry). */
data class DatedMovement(val accountId: String, val amount: Long, val date: Long, val reversed: Boolean)

data class NetWorthPoint(val month: YearMonth, val accounts: Long)
data class NetWorth(
    val accounts: Long, val owedToYou: Long, val youOwe: Long,
    val byAccount: List<Pair<Account, Long>>, val trend: List<NetWorthPoint>
) {
    val net: Long get() = Finance.total(listOf(accounts, owedToYou, -youOwe))
}

/**
 * What you have minus what you owe, from local records only: account balances, plus money friends
 * owe you, minus money you owe them. Savings goals are money set aside inside these accounts, so they
 * are not added again. The trend rebuilds each month-end account total from the dated ledger entries.
 */
object NetWorths {
    fun build(accounts: List<Account>, movements: List<DatedMovement>, debts: List<Debt>, today: LocalDate,
              months: Int = 6, zone: ZoneId = ZoneId.systemDefault()): NetWorth {
        val live = movements.filterNot { it.reversed }
        val total = Finance.total(accounts.map { it.balance })
        val trend = (months - 1 downTo 0).map { back ->
            val month = YearMonth.from(today).minusMonths(back.toLong())
            val end = minOf(month.atEndOfMonth(), today).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            NetWorthPoint(month, Finance.total(live.filter { it.date < end }.map { it.amount }))
        }
        return NetWorth(total,
            Finance.total(debts.filter { it.to == "self" }.map { it.outstanding }),
            Finance.total(debts.filter { it.from == "self" }.map { it.outstanding }),
            accounts.filter { !it.archived || it.balance != 0L }.map { it to it.balance }.sortedByDescending { it.second },
            trend)
    }
}
