package com.smartexpense.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetAlertsTest {
    private val month="2026-11"
    private fun report(total:Long,categories:Map<String?,Long> = emptyMap())=PeriodReport(total,0,categories,emptyMap())

    @Test fun levelUsesExactThresholds() {
        assertEquals(0,BudgetAlerts.level(79_99,100_00))
        assertEquals(80,BudgetAlerts.level(80_00,100_00))
        assertEquals(80,BudgetAlerts.level(99_99,100_00))
        assertEquals(100,BudgetAlerts.level(100_00,100_00))
        assertEquals(0,BudgetAlerts.level(5_00,0))
        assertEquals(100,BudgetAlerts.level(Long.MAX_VALUE,Long.MAX_VALUE-1))
    }

    @Test fun overallAndCategoryBudgetsAlertOncePerLevel() {
        val budgets=listOf(Budget("$month:all",month,null,1000_00),Budget("$month:food",month,"food",200_00),Budget("2026-10:all","2026-10",null,1_00))
        val (first,levels)=BudgetAlerts.evaluate(budgets,month,report(850_00,mapOf("food" to 250_00)),emptyMap())
        assertEquals(listOf(BudgetAlert("$month:all",null,80),BudgetAlert("$month:food","food",100)),first)
        val (again,_)=BudgetAlerts.evaluate(budgets,month,report(860_00,mapOf("food" to 260_00)),levels)
        assertEquals(emptyList<BudgetAlert>(),again)
        val (crossed,_)=BudgetAlerts.evaluate(budgets,month,report(1000_00,mapOf("food" to 260_00)),levels)
        assertEquals(listOf(BudgetAlert("$month:all",null,100)),crossed)
    }

    @Test fun refundsResetLevelSoACrossingAlertsAgain() {
        val budgets=listOf(Budget("$month:all",month,null,100_00))
        val (_,high)=BudgetAlerts.evaluate(budgets,month,report(90_00),emptyMap())
        val (none,low)=BudgetAlerts.evaluate(budgets,month,report(50_00),high)
        assertEquals(emptyList<BudgetAlert>(),none)
        assertEquals(emptyMap<String,Int>(),low)
        assertEquals(1,BudgetAlerts.evaluate(budgets,month,report(85_00),low).first.size)
    }
}
