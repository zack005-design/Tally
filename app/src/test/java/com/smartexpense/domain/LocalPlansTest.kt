package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class LocalPlansTest {
    @org.junit.Test fun olderUnrecordedDatesRemainReachable() {
        val today=java.time.LocalDate.parse("2026-10-12")
        val template=RecurringTemplate("daily",Kind.EXPENSE,100,"Daily","w",today.minusDays(401).toString(),"Daily")
        assertEquals(402,template.occurrences(today).size)
        assertEquals(today.minusDays(401),template.occurrences(today).first())
    }
    @Test fun monthlyOccurrencesKeepOriginalDayAfterFebruary() {
        val template=RecurringTemplate("rent",Kind.EXPENSE,100000,"Rent","cash","2028-01-31","Monthly")
        assertEquals(listOf(LocalDate.of(2028,1,31),LocalDate.of(2028,2,29),LocalDate.of(2028,3,31)),template.occurrences(LocalDate.of(2028,3,31)))
    }
    @Test fun futureTemplateHasNoDueEntriesAndOccurrenceIdIsStable() {
        val template=RecurringTemplate("salary",Kind.INCOME,500000,"Salary","bank","2027-01-01","Weekly")
        assertTrue(template.occurrences(LocalDate.of(2026,12,31)).isEmpty())
        assertEquals("recurring:salary:2027-01-01",template.entryId(LocalDate.of(2027,1,1)))
    }
    @Test fun percentageDefaultMustMatchGroupAndSumToHundred() {
        val rule=GroupDefault("group","Percentage",mapOf("self" to "55","friend" to "45"))
        assertEquals(mapOf("self" to 5500L,"friend" to 4500L),Finance.split(10000,listOf("self","friend"),rule.rule(listOf("self","friend"))))
        try {rule.rule(listOf("self","other"));fail("Membership change needs a new default")} catch(_:IllegalArgumentException) {}
    }
}
