package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test

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
}
