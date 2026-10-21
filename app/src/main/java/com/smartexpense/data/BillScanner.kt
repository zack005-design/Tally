package com.smartexpense.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class ScannedBill(val bytes:ByteArray,val mime:String,val text:String)

/** Bundled Latin OCR. Sources are copied locally, bounded and never uploaded. */
class BillScanner @Inject constructor(@ApplicationContext private val context:Context) {
    companion object {const val MAX_BYTES=6*1024*1024;const val MAX_PAGES=5}
    suspend fun read(uri:Uri):ScannedBill=withContext(Dispatchers.IO) {
        val bytes=context.contentResolver.openInputStream(uri)?.use {input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {ensureActive();val n=input.read(buffer);if(n<0) break;require(output.size()+n<=MAX_BYTES) {"Choose a bill smaller than 6 MB"};output.write(buffer,0,n)}
            output.toByteArray()
        }?:throw IllegalArgumentException("Cannot read this local file")
        require(bytes.isNotEmpty()) {"The selected bill is empty"}
        val pdf=bytes.take(5).toByteArray().toString(Charsets.US_ASCII)=="%PDF-"
        val mime=if(pdf) "application/pdf" else BitmapFactory.Options().apply {inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.size,this)}.outMimeType.orEmpty()
        require(mime in listOf("application/pdf","image/jpeg","image/png","image/webp")) {"Choose a JPEG, PNG, WebP or PDF bill"}
        val text=if(pdf) pdfText(bytes) else imageText(bytes)
        ScannedBill(bytes,mime,text.take(10000))
    }
    private suspend fun recognize(bitmap:Bitmap):String {
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            // Do not recycle the bitmap until ML Kit completes, even after coroutine cancellation.
            return suspendCoroutine {continuation->recognizer.process(InputImage.fromBitmap(bitmap,0))
                .addOnSuccessListener {continuation.resume(it.text)}
                .addOnFailureListener {continuation.resumeWithException(it)}}
        } finally {recognizer.close()}
    }
    private fun decode(bytes:ByteArray):Bitmap {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0) {"Choose a readable bill image or PDF"}
        var sample=1;while(bounds.outWidth/sample>2400 || bounds.outHeight/sample>2400) sample*=2
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample})?:throw IllegalArgumentException("Cannot decode this bill image")
        val orientation=runCatching {android.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
        val matrix=Matrix()
        when(orientation) {2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(270f);matrix.postScale(-1f,1f)};8->matrix.setRotate(270f)}
        if(orientation !in 2..8) return bitmap
        return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also {if(it!==bitmap) bitmap.recycle()}
    }
    private suspend fun imageText(bytes:ByteArray):String {val bitmap=decode(bytes);try {return recognize(bitmap)} finally {bitmap.recycle()}}
    private suspend fun pdfText(bytes:ByteArray):String {
        val file=File.createTempFile("bill-",".pdf",context.cacheDir)
        try {
            file.writeBytes(bytes)
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {descriptor->PdfRenderer(descriptor).use {renderer->
                require(renderer.pageCount in 1..MAX_PAGES) {"Choose a bill with 1 to 5 PDF pages"}
                val texts=mutableListOf<String>()
                for(index in 0 until renderer.pageCount) {
                    currentCoroutineContext().ensureActive()
                    renderer.openPage(index).use {page->
                        val embedded=if(Build.VERSION.SDK_INT>=35) page.textContents.joinToString("\n") {it.text} else ""
                        if(embedded.isNotBlank()) texts+=embedded
                        else {
                            val scale=2000.0/maxOf(page.width,page.height);val bitmap=Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                            try {bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);texts+=recognize(bitmap)} finally {bitmap.recycle()}
                        }
                    }
                }
                texts.joinToString("\n")
            }}
        } finally {file.delete()}
    }
    suspend fun preview(bytes:ByteArray,mime:String,pageIndex:Int=0):Bitmap=withContext(Dispatchers.IO) {
        if(mime!="application/pdf") decode(bytes)
        else {
            val file=File.createTempFile("bill-view-",".pdf",context.cacheDir)
            try {file.writeBytes(bytes);ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {descriptor->PdfRenderer(descriptor).use {renderer->
                require(pageIndex in 0 until renderer.pageCount && renderer.pageCount<=MAX_PAGES)
                renderer.openPage(pageIndex).use {page->val scale=1600.0/maxOf(page.width,page.height);Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also {it.eraseColor(Color.WHITE);page.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)}}
            }}} finally {file.delete()}
        }
    }
    suspend fun pageCount(bytes:ByteArray,mime:String):Int=withContext(Dispatchers.IO) {
        if(mime!="application/pdf") 1 else {
            val file=File.createTempFile("bill-pages-",".pdf",context.cacheDir)
            try {file.writeBytes(bytes);ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {descriptor->PdfRenderer(descriptor).use {it.pageCount.also {count->require(count in 1..MAX_PAGES)}}}}
            finally {file.delete()}
        }
    }
}
