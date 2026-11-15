package com.smartexpense.domain

/** A budget that has newly reached an alert level (80 or 100 percent) this month. */
data class BudgetAlert(val budgetId:String,val categoryId:String?,val level:Int)

object BudgetAlerts {
    val LEVELS=listOf(80,100)

    /** Highest alert level reached, using exact integer comparison on paise. */
    fun level(spent:Long,limit:Long):Int = if(limit<=0) 0 else LEVELS.lastOrNull {spent.toBigInteger()*100.toBigInteger()>=limit.toBigInteger()*it.toBigInteger()}?:0

    /**
     * Compares the month's spending against its budgets. [notified] holds the level last recorded per
     * budget id. Returns the alerts to show and the levels to record (levels drop again after refunds
     * or reversals so crossing a threshold a second time alerts again).
     */
    fun evaluate(budgets:List<Budget>,month:String,report:PeriodReport,notified:Map<String,Int>):Pair<List<BudgetAlert>,Map<String,Int>> {
        val current=budgets.filter {it.month==month}.associate {budget->
            val spent=budget.categoryId?.let {report.categories[it]?:0L}?:report.spending
            budget.id to level(spent,budget.amount)
        }
        val alerts=budgets.filter {it.month==month}.mapNotNull {budget->
            val now=current.getValue(budget.id)
            if(now>(notified[budget.id]?:0)) BudgetAlert(budget.id,budget.categoryId,now) else null
        }
        return alerts to current.filterValues {it>0}
    }
}
