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
    @Test fun faintCameraJpegKeepsPrintedAmounts()=runBlocking {
        val image=Bitmap.createBitmap(1200,1200,Bitmap.Config.ARGB_8888)
        val file=file("ocr-faint-camera.jpg")
        try {
            image.eraseColor(Color.rgb(220,220,220))
            val canvas=Canvas(image)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color=Color.rgb(216,216,216);textSize=60f
                maskFilter=BlurMaskFilter(1f,BlurMaskFilter.Blur.NORMAL)
            }
            listOf("Corner Cafe","Tea 50.00","Cake 100.00","Grand Total 150.00").forEachIndexed {i,line->canvas.drawText(line,80f,150f+i*200,paint)}
            file.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,100,it)}
            val original=file.readBytes()
            val read=scan(file)
            val bill=BillRecognition.parse(read.text)
            assertEquals(read.text,listOf(5000L,10000L),bill.items.map {it.amount})
            assertEquals(read.text,listOf("Tea","Cake"),bill.items.map {it.name})
            assertEquals(15000L,bill.total)
            assertArrayEquals(original,read.bytes)
            assertTrue(read.recognitionWarnings.isNotEmpty())
        } finally {file.delete();image.recycle()}
    }
    @Test fun faintPrintOnShadedPaperKeepsPrintedAmounts()=runBlocking {
        val image=Bitmap.createBitmap(1200,1200,Bitmap.Config.ARGB_8888)
        val file=file("ocr-shaded-camera.jpg")
        try {
            val canvas=Canvas(image)
            canvas.drawPaint(Paint().apply {shader=LinearGradient(0f,0f,0f,1200f,Color.rgb(180,180,180),Color.rgb(240,240,240),Shader.TileMode.CLAMP)})
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize=60f
                shader=LinearGradient(0f,0f,0f,1200f,Color.rgb(176,176,176),Color.rgb(236,236,236),Shader.TileMode.CLAMP)
                maskFilter=BlurMaskFilter(1f,BlurMaskFilter.Blur.NORMAL)
            }
            listOf("Corner Cafe","Tea 50.00","Cake 100.00","Grand Total 150.00").forEachIndexed {i,line->canvas.drawText(line,80f,150f+i*200,paint)}
            file.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,100,it)}
            val read=scan(file)
            val bill=BillRecognition.parse(read.text)
            assertEquals(read.text,listOf(5000L,10000L),bill.items.map {it.amount})
            assertEquals(read.text,listOf("Tea","Cake"),bill.items.map {it.name})
            assertEquals(15000L,bill.total)
        } finally {file.delete();image.recycle()}
    }
    @Test fun unreadablePhotoDoesNotInventItemsAndRequiresReview()=runBlocking {
        val image=Bitmap.createBitmap(800,800,Bitmap.Config.ARGB_8888)
        val file=file("ocr-blank-camera.jpg")
        try {
            image.eraseColor(Color.WHITE)
            file.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,100,it)}
            val read=scan(file)
            val bill=BillRecognition.parse(read.text)
            assertTrue(bill.items.isEmpty())
            assertNull(bill.total)
            assertTrue(read.recognitionWarnings.any {it.contains("could not be read reliably")})
        } finally {file.delete();image.recycle()}
    }
    @Test fun tiltedCameraColumnsKeepPricesWithTheirItems()=runBlocking {
        val image=Bitmap.createBitmap(1600,1500,Bitmap.Config.ARGB_8888)
        val file=file("ocr-tilted-camera.jpg")
        try {
            image.eraseColor(Color.WHITE)
            val canvas=Canvas(image)
            canvas.rotate(8f,800f,750f)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=48f}
            canvas.drawText("Corner Cafe",100f,180f,paint)
            listOf("Tea","Cake","Lemon Juice","Grand Total").forEachIndexed {i,name->
                canvas.drawText(name,100f,400f+i*200,paint)
                canvas.drawText(listOf("50.00","100.00","75.00","225.00")[i],1200f,400f+i*200,paint)
            }
            file.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,100,it)}
            val read=scan(file)
            val bill=BillRecognition.parse(read.text)
            assertEquals(read.text,listOf("Tea","Cake","Lemon Juice"),bill.items.map {it.name})
            assertEquals(listOf(5000L,10000L,7500L),bill.items.map {it.amount})
            assertEquals(22500L,bill.total)
        } finally {file.delete();image.recycle()}
    }
    @Test fun separatedPriceColumnKeepsEachPriceWithItsItem()=runBlocking {
        val image=Bitmap.createBitmap(1600,1600,Bitmap.Config.ARGB_8888)
        val file=file("ocr-columns.png")
        try {
            image.eraseColor(Color.WHITE)
            val canvas=Canvas(image)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=48f}
            canvas.drawText("Corner Cafe",80f,100f,paint)
            listOf("Masala Tea","Chocolate Cake","Lemon Juice").forEachIndexed {i,name->
                canvas.drawText(name,80f,300f+i*130,paint)
                canvas.drawText(listOf("50.00","100.00","75.00")[i],1200f,300f+i*130,paint)
            }
            canvas.drawText("Grand Total",80f,850f,paint)
            canvas.drawText("225.00",1200f,850f,paint)
            file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            val bill=BillRecognition.parse(scan(file).text)
            assertEquals(listOf("Masala Tea","Chocolate Cake","Lemon Juice"),bill.items.map {it.name})
            assertEquals(listOf(5000L,10000L,7500L),bill.items.map {it.amount})
            assertEquals(22500L,bill.total)
        } finally {file.delete();image.recycle()}
    }
    @Test fun longReceiptKeepsSmallPrintReadable()=runBlocking {
        val image=Bitmap.createBitmap(1200,8000,Bitmap.Config.ARGB_8888)
        val file=file("ocr-long-receipt.png")
        try {
            image.eraseColor(Color.WHITE)
            val canvas=Canvas(image)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=32f}
            canvas.drawText("Corner Cafe",50f,80f,paint)
            // Include rows straddling section boundaries; repeated purchases must appear once each.
            repeat(30) {i->canvas.drawText("Tea 50.00",50f,when(i) {6->1612f;12->3212f;else->250f+i*240},paint)}
            canvas.drawText("Grand Total 1500.00",50f,7700f,paint)
            file.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            val read=scan(file)
            val bill=BillRecognition.parse(read.text)
            assertEquals("Recognized synthetic receipt: ${read.text}",30,bill.items.size)
            assertEquals(150000L,bill.total)
        } finally {file.delete();image.recycle()}
    }
    @Test fun exifRotatedLongReceiptKeepsSmallPrintReadable()=runBlocking {
        val upright=Bitmap.createBitmap(1200,8000,Bitmap.Config.ARGB_8888)
        val file=file("ocr-long-rotated.jpg")
        try {
            upright.eraseColor(Color.WHITE)
            val canvas=Canvas(upright)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=32f}
            canvas.drawText("Corner Cafe",50f,80f,paint)
            repeat(30) {i->canvas.drawText("Tea 50.00",50f,when(i) {6->1612f;12->3212f;else->250f+i*240},paint)}
            canvas.drawText("Grand Total 1500.00",50f,7700f,paint)
            val stored=Bitmap.createBitmap(upright,0,0,upright.width,upright.height,Matrix().apply {setRotate(270f)},true)
            try {file.outputStream().use {stored.compress(Bitmap.CompressFormat.JPEG,100,it)}} finally {stored.recycle()}
            upright.recycle()
            android.media.ExifInterface(file.absolutePath).apply {
                setAttribute(android.media.ExifInterface.TAG_ORIENTATION,android.media.ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val read=scan(file)
            val bill=BillRecognition.parse(read.text)
            assertEquals("Recognized synthetic receipt: ${read.text}",30,bill.items.size)
            assertEquals(150000L,bill.total)
        } finally {file.delete();if(!upright.isRecycled) upright.recycle()}
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
