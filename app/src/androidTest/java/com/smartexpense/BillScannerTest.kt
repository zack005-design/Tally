package com.smartexpense

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.data.*
import com.smartexpense.domain.BillRecognition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BillScannerTest {
    private fun bitmap():Bitmap=Bitmap.createBitmap(1200,1000,Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.WHITE);val canvas=Canvas(this);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=64f}
        listOf("Tally Store","Tea 50.00","Cake 100.00","Total INR 150.00").forEachIndexed {index,line->canvas.drawText(line,80f,120f+index*150,paint)}
    }
    private fun file(name:String):File {val c=ApplicationProvider.getApplicationContext<Context>();return File(File(c.cacheDir,"bill-camera").apply {mkdirs()},name)}
    private suspend fun scan(file:File):ScannedBill {val c=ApplicationProvider.getApplicationContext<Context>();return BillScanner(c).read(FileProvider.getUriForFile(c,"${c.packageName}.bills",file))}
    @Test fun bundledModelReadsImageWithoutInternetPermission()=runBlocking {
        val c=ApplicationProvider.getApplicationContext<Context>()
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,c.checkSelfPermission(android.Manifest.permission.INTERNET))
        val file=file("ocr-fixture.png");val b=bitmap()
        try {file.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};val read=scan(file);assertEquals(15000L,BillRecognition.parse(read.text).total);assertTrue(read.text.contains("Tally"))}
        finally {file.delete();b.recycle()}
    }
    @Test fun scannedPdfUsesBundledOcrAndTextPdfIsReadable()=runBlocking {
        val image=bitmap()
        for(scanned in listOf(true,false)) {
            val file=file("ocr-fixture-$scanned.pdf");val document=PdfDocument()
            try {
                val page=document.startPage(PdfDocument.PageInfo.Builder(1200,1000,1).create())
                if(scanned) page.canvas.drawBitmap(image,0f,0f,null)
                else {val paint=Paint().apply {textSize=64f};listOf("Tally Store","Total INR 150.00").forEachIndexed {i,line->page.canvas.drawText(line,80f,120f+i*150,paint)}}
                document.finishPage(page);file.outputStream().use {document.writeTo(it)}
                assertEquals(15000L,BillRecognition.parse(scan(file).text).total)
            } finally {document.close();file.delete()}
        }
        image.recycle()
    }
}
