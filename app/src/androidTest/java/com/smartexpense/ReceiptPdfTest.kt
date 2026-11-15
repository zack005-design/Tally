package com.smartexpense

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.smartexpense.data.ReceiptPdf
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ReceiptPdfTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun originalPhotoBecomesPdfAndExistingPdfKeepsItsBytes() = runBlocking<Unit> {
        val bitmap=Bitmap.createBitmap(600,2400,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        android.graphics.Canvas(bitmap).drawRect(0f,2200f,600f,2400f,android.graphics.Paint().apply {color=Color.BLACK})
        val output=ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
        val photo=com.smartexpense.data.ScannedBill(output.toByteArray(),"image/png","UNRELIABLE OCR")
        val bytes=ReceiptPdf.originalBytes(photo,com.smartexpense.data.BillScanner(context))
        assertEquals("%PDF-",bytes.take(5).toByteArray().toString(Charsets.US_ASCII))
        val file=File.createTempFile("original-receipt-",".pdf",context.cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { pdf ->
                assertTrue(pdf.pageCount>1)
                pdf.openPage(pdf.pageCount-1).use { page ->
                    val rendered=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                    try {
                        rendered.eraseColor(Color.WHITE)
                        page.render(rendered,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertTrue((0 until rendered.height).any { y->Color.red(rendered.getPixel(rendered.width/2,y))<50 })
                    } finally {rendered.recycle()}
                }
            } }
            assertArrayEquals(bytes,ReceiptPdf.originalBytes(com.smartexpense.data.ScannedBill(bytes,"application/pdf",""),com.smartexpense.data.BillScanner(context)))
        } finally {file.delete()}
    }

    @SdkSuppress(minSdkVersion=35)
    @Test fun sharedPdfIsReadableAndExcludesPrivateLedgerData() = runBlocking<Unit> {
        val record = Record("bill", Kind.EXPENSE, "Dinner at Corner Cafe", 90000, 30000, null, null, 0, "PRIVATE NOTE", false)
        val detail = RecordDetail(record, emptyList(), mapOf("self" to 30000L, "f" to 60000L), mapOf("self" to 90000L), emptyList(), emptyMap())
        val intent = ReceiptPdf.share(context, detail, listOf(Person("f", "Rahul")), listOf(Debt("d", "bill", "f", "self", 60000, 20000, 0)))
        assertEquals("application/pdf", intent.type)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = requireNotNull(intent.clipData).getItemAt(0).uri
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                assertEquals(1, pdf.pageCount)
                pdf.openPage(0).use { page ->
                    val text = page.textContents.joinToString("\n") { it.text }
                    assertTrue(text, text.contains("Dinner at Corner Cafe"))
                    assertTrue(text, text.contains("Rahul"))
                    assertTrue(text, text.contains("200.00"))
                    assertFalse(text.contains("PRIVATE NOTE"))
                    val preview=Bitmap.createBitmap(page.width*2,page.height*2,Bitmap.Config.ARGB_8888)
                    try {
                        preview.eraseColor(Color.WHITE)
                        page.render(preview,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        File(context.getExternalFilesDir(null),"receipt-pdf-preview.png").outputStream().use {preview.compress(Bitmap.CompressFormat.PNG,100,it)}
                    } finally {preview.recycle()}
                }
            }
        }
    }

    @SdkSuppress(minSdkVersion=35)
    @Test fun sharedReceiptIncludesItemsDetailsAndTheOriginalScan() = runBlocking<Unit> {
        val bitmap=Bitmap.createBitmap(500,900,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        android.graphics.Canvas(bitmap).drawRect(0f,800f,500f,900f,android.graphics.Paint().apply {color=Color.BLACK})
        val output=ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
        val scan=com.smartexpense.data.ScannedBill(output.toByteArray(),"image/png","Corner Cafe\nPaneer tikka 320.00\nTotal 450.00")
        val notes="Imported printed bill · reviewed locally\nPaneer tikka: 320.00\nButter naan x2: 90.00"
        val record=Record("bill",Kind.EXPENSE,"Dinner",45000,45000,"food",null,0,notes,false)
        val detail=RecordDetail(record,listOf(Movement("hdfc",-45000)),emptyMap(),emptyMap(),emptyList(),emptyMap())
        val intent=ReceiptPdf.share(context,detail,emptyList(),emptyList(),listOf(Account("hdfc","HDFC Savings","Bank",999999,false)),listOf(Category("food","Food & dining",0,"food",false)),scan,com.smartexpense.data.BillScanner(context))
        val uri=requireNotNull(intent.clipData).getItemAt(0).uri
        context.contentResolver.openFileDescriptor(uri,"r")!!.use { descriptor -> PdfRenderer(descriptor).use { pdf ->
            assertTrue(pdf.pageCount>=2)
            val text=pdf.openPage(0).use { page ->
                val preview=Bitmap.createBitmap(page.width*2,page.height*2,Bitmap.Config.ARGB_8888)
                try {
                    preview.eraseColor(Color.WHITE)
                    page.render(preview,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(context.getExternalFilesDir(null),"receipt-full-preview.png").outputStream().use {preview.compress(Bitmap.CompressFormat.PNG,100,it)}
                } finally {preview.recycle()}
                page.textContents.joinToString("\n") { it.text }
            }
            listOf("Corner Cafe","Paneer tikka","Butter naan x2","320.00","90.00","Taxes, charges and other","40.00","450.00","Food & dining","HDFC Savings","TOTAL").forEach { assertTrue("missing $it in $text",text.contains(it)) }
            assertFalse(text.contains("9999.99"))
            assertFalse(text.contains("reviewed locally"))
            pdf.openPage(pdf.pageCount-1).use { page ->
                val rendered=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                try {
                    rendered.eraseColor(Color.WHITE)
                    page.render(rendered,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertTrue((0 until rendered.height).any { y->Color.red(rendered.getPixel(rendered.width/2,y))<50 })
                } finally {rendered.recycle()}
            }
        } }
    }

    @SdkSuppress(minSdkVersion=35)
    @Test fun longReceiptPaginatesWithoutDroppingParticipants() = runBlocking<Unit> {
        val people = (1..80).map { Person("p$it", "Participant $it with a long name that must wrap on the receipt") }
        val record = Record("long", Kind.EXPENSE, "Group dinner", 80000, 0, null, null, 0, "", false)
        val detail = RecordDetail(record, emptyList(), people.associate { it.id to 1000L }, mapOf("p1" to 80000L), emptyList(), emptyMap())
        val bytes = ReceiptPdf.bytes(detail, people, emptyList())
        val file = File.createTempFile("receipt-test-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    assertTrue(pdf.pageCount > 1)
                    val text = (0 until pdf.pageCount).joinToString("\n") { i -> pdf.openPage(i).use { page -> page.textContents.joinToString("\n") { it.text } } }
                    assertTrue(text, text.contains("Participant 80"))
                }
            }
        } finally { file.delete() }
    }
}
