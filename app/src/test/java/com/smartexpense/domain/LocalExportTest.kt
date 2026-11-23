package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class LocalExportTest {
    @Test fun manualRatesRoundAtPaiseAndRejectOverflow() {
        assertEquals(8313L,ManualExchange.convert("1.00","83.125"))
        assertEquals(0L,ManualExchange.convert("0.00","83"))
        assertTrue(runCatching {ManualExchange.convert("999999999999999999999","100")}.isFailure)
        assertTrue(runCatching {ManualExchange.convert("1","0")}.isFailure)
        assertTrue(runCatching {ManualExchange.convert("1","-5")}.isFailure)
    }
    @Test fun csvEscapesNamesAndFormulaCells() {
        assertEquals("\"-500.00\"",ReportCsv.cell("-500.00"))
        assertEquals("\"Tea, cake\"",ReportCsv.cell("Tea, cake"))
        assertEquals("\"A \"\"quote\"\"\"",ReportCsv.cell("A \"quote\""))
        assertEquals("\"'=SUM(A1)\"",ReportCsv.cell("=SUM(A1)"))
    }
    @Test fun transactionRowsIncludeAccountsAndTags() {
        val at=java.time.LocalDate.of(2026,10,20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val accounts=listOf(Account("cash","Cash","Cash",0,false),Account("bank","HDFC, savings","Bank",0,false))
        val record=Record("a",Kind.EXPENSE,"Dinner",90000,30000,null,null,at,"",false,accountIds=listOf("cash","bank","gone"),tags=listOf("Goa trip","work"))
        val review=MonthlyReview("2026-10",emptyMap(),emptyMap(),0,0,0,0,30000,90000,0,0,0,true)
        val rows=ReportCsv.rows(Snapshot(accounts=accounts,records=listOf(record)),review,"Transactions")
        val header=rows.first {it.firstOrNull()=="Date"}
        assertEquals(listOf("Date","Type","Description","Total INR","Personal INR","Category","Accounts","Tags","Notes"),header)
        val row=rows.last()
        assertEquals("Cash; HDFC, savings; gone",row[header.indexOf("Accounts")])
        assertEquals("Goa trip; work",row[header.indexOf("Tags")])
        assertTrue(ReportCsv.encode(listOf(row)).contains("\"Cash; HDFC, savings; gone\""))
    }
}
