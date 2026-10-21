package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class EverydayMoneyTest {
    private fun record(id:String,date:Long=1,kind:Kind=Kind.EXPENSE,reversed:Boolean=false,people:List<String> = listOf("self")) = Record(id,kind,id,100,100,null,null,date,"",reversed,personIds=people)
    @Test fun recentExpensesExcludeUnsafeKindsAndKeepNewestThree() {
        val input=listOf(record("old",1),record("a",2),record("b",3),record("c",4),record("shared",5,people=listOf("self","friend")),record("reversed",6,reversed=true),record("refund",7,Kind.REFUND),record("transfer",8,Kind.TRANSFER),record("repay",9,Kind.REPAYMENT))
        assertEquals(listOf("c","b","a"),recentPersonalExpenses(input).map {it.id})
    }
}
