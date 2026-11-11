package com.smartexpense

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ReceiptCompletenessTest {
    @Test fun oversizedPdfTextIsRejectedInsteadOfSilentlyLosingItsTail()=runBlocking {
        assumeTrue(Build.VERSION.SDK_INT>=35)
        val context=ApplicationProvider.getApplicationContext<Context>()
        val directory=File(context.cacheDir,"bill-camera").apply {mkdirs()}
        val file=File.createTempFile("long-text-",".pdf",directory)
        try {
            val pdf=PdfDocument()
            try {
                repeat(4) {index->
                    val page=pdf.startPage(PdfDocument.PageInfo.Builder(612,792,index+1).create())
                    val paint=Paint().apply {textSize=7f}
                    repeat(60) {row->page.canvas.drawText("Long receipt item with detailed description number $row 10.00",10f,10f+row*12,paint)}
                    pdf.finishPage(page)
                }
                file.outputStream().use(pdf::writeTo)
            } finally {pdf.close()}
            try {
                BillScanner(context).read(FileProvider.getUriForFile(context,"${context.packageName}.bills",file))
                fail("An oversized receipt must not return a truncated successful scan")
            } catch(expected:IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("10,000"))
                assertTrue(expected.message.orEmpty().contains("smaller"))
            }
        } finally {file.delete()}
    }

    @Test fun storageAcceptsCompleteTextAtTheLimitAndRejectsMore() {
        val bill=ScannedBill(byteArrayOf(-119,80,78,71,13,10,26,10),"image/png","x".repeat(10000))
        ReceiptStorage.validate(bill)
        try {ReceiptStorage.validate(bill.copy(text=bill.text+"x"));fail("Over-limit text must not be saved")}
        catch(_:IllegalArgumentException) {}
    }
}
