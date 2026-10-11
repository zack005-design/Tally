package com.smartexpense.domain
import java.time.*
import org.junit.Assert.*
import org.junit.Test
class ReportsTest {
    @Test fun periodsUsePersonalSharesAndExcludeRepayments() {
        val zone=ZoneId.of("Asia/Kolkata");val date=LocalDate.of(2026,10,10)
        val time=date.atStartOfDay(zone).toInstant().toEpochMilli()
        val records=listOf(Record("meal",Kind.EXPENSE,"Meal",89000,22250,"food",null,time,"",false),Record("repayment",Kind.REPAYMENT,"Repayment",10000,0,null,null,time,"",false),Record("refund",Kind.REFUND,"Refund",1000,1000,"food",null,time,"",false))
        val report=Reports.period(records,date,date,zone)
        assertEquals(21250L,report.spending);assertEquals(0L,report.income);assertEquals(21250L,report.categories.getValue("food"))
    }
    @Test fun reversedTransactionsExcluded() { val report=Reports.period(listOf(Record("a",Kind.EXPENSE,"Lunch",10000,10000,null,null,0,"",true)),LocalDate.of(1970,1,1),LocalDate.of(1970,1,1),ZoneId.of("UTC"));assertEquals(0L,report.spending) }
}
