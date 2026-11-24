package com.smartexpense.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import android.os.Build
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartexpense.domain.BillRecognition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class ScannedBill(val bytes:ByteArray,val mime:String,val text:String,val recognitionWarnings:List<String> = emptyList())

/** Bundled Latin OCR. Sources are copied locally, bounded and never uploaded. */
class BillScanner @Inject constructor(@param:ApplicationContext private val context:Context) {
    companion object {const val MAX_BYTES=6*1024*1024;const val MAX_PAGES=5
        /** Camera apps commonly save 8 to 25 MB photos. They are read in full and stored as a compact JPEG. */
        const val MAX_PHOTO_SOURCE_BYTES=40*1024*1024
        private const val MAX_RECOGNITION_WIDTH=4096
        private const val MAX_RECOGNITION_HEIGHT=16000
        private const val MAX_RECOGNITION_PIXELS=16_777_216L
        /** Narrower images are enlarged so small print reaches the character size OCR needs. */
        private const val MIN_RECOGNITION_WIDTH=1600
        private const val MAX_UPSCALE=3.0
        private const val PREVIEW_SIDE=2400
        /** Formats ReceiptStorage keeps as-is. Any other decodable image is stored as JPEG. */
        private val STORED_IMAGE_TYPES=setOf("image/jpeg","image/png","image/webp")}
    suspend fun read(uri:Uri):ScannedBill=withContext(Dispatchers.IO) {
        val source=readSource(uri)
        require(source.isNotEmpty()) {"The selected bill is empty. If you took a photo, try again."}
        val pdf=source.take(5).toByteArray().toString(Charsets.US_ASCII)=="%PDF-"
        val warnings=mutableListOf<String>()
        try {
            if(pdf) {
                require(source.size<=MAX_BYTES) {"Choose a PDF bill smaller than 6 MB"}
                val text=pdfText(source,warnings)
                requireTextLimit(text)
                return@withContext ScannedBill(source,"application/pdf",text,warnings.distinct())
            }
            val decoded=decodeImage(source,DecodeSize.RECOGNITION)
            val text=try {uprightText(decoded.bitmap,warnings)} finally {decoded.bitmap.recycle()}
            requireTextLimit(text)
            // Keep the original file when the receipt store supports it; otherwise store an upright JPEG.
            if(source.size<=MAX_BYTES && decoded.mime in STORED_IMAGE_TYPES) ScannedBill(source,decoded.mime,text,warnings.distinct())
            else ScannedBill(compactPhoto(source),"image/jpeg",text,warnings.distinct())
        } catch(memory:OutOfMemoryError) {
            throw IllegalArgumentException("This photo is too large for this phone's memory. Take the photo at a lower resolution or crop it, then try again.")
        }
    }
    private fun requireTextLimit(text:String)=require(text.length<=ReceiptStorage.MAX_TEXT_CHARS) {"Receipt text exceeds 10,000 characters. Import a smaller section or enter the expense manually."}
    /** Copies the source once, into an exact-size buffer when the provider reports a length. */
    private suspend fun readSource(uri:Uri):ByteArray {
        val resolver=context.contentResolver
        val length=runCatching {resolver.openAssetFileDescriptor(uri,"r")?.use {it.length}}.getOrNull()?:-1L
        require(length<=MAX_PHOTO_SOURCE_BYTES) {"Choose a bill photo smaller than 40 MB"}
        return resolver.openInputStream(uri)?.use {input->
            val output=java.io.ByteArrayOutputStream(1 shl 16)
            if(length>0) {
                // Known size: read straight into one array instead of buffering and copying a large photo.
                val bytes=ByteArray(length.toInt());var offset=0
                while(offset<bytes.size) {currentCoroutineContext().ensureActive();val n=input.read(bytes,offset,bytes.size-offset);if(n<0) break;offset+=n}
                val next=if(offset==bytes.size) input.read() else -1
                if(next<0) return@use if(offset==bytes.size) bytes else bytes.copyOf(offset)
                // The provider under-reported the size; keep what was read and continue.
                output.write(bytes,0,offset);output.write(next)
            }
            val buffer=ByteArray(65536)
            while(true) {currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0) break;require(output.size()+n<=MAX_PHOTO_SOURCE_BYTES) {"Choose a bill photo smaller than 40 MB"};output.write(buffer,0,n)}
            output.toByteArray()
        }?:throw IllegalArgumentException("Cannot read this local file")
    }
    /** Stores an upright JPEG copy. Recognition has already read the source photo. */
    private suspend fun compactPhoto(source:ByteArray):ByteArray {
        var bitmap=decodeImage(source,DecodeSize.STORAGE).bitmap
        try {
            while(true) {
                for(quality in intArrayOf(90,80,70)) {
                    currentCoroutineContext().ensureActive()
                    val output=java.io.ByteArrayOutputStream()
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG,quality,output)) {"Cannot store this bill photo"}
                    if(output.size()<=MAX_BYTES) return output.toByteArray()
                }
                require(bitmap.width>200 && bitmap.height>200) {"Cannot store this bill photo"}
                val smaller=Bitmap.createScaledBitmap(bitmap,bitmap.width*3/4,bitmap.height*3/4,true)
                if(smaller!==bitmap) {bitmap.recycle();bitmap=smaller}
            }
        } finally {bitmap.recycle()}
    }
    private data class ReceiptLine(val text:String,val bounds:Rect?,val confidence:Float,val slope:Double?,val textHeight:Double)
    private suspend fun recognize(bitmap:Bitmap):Text {
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            // Do not recycle the bitmap until ML Kit completes, even after coroutine cancellation.
            return suspendCoroutine {continuation->recognizer.process(InputImage.fromBitmap(bitmap,0))
                .addOnSuccessListener {continuation.resume(it)}
                .addOnFailureListener {continuation.resumeWithException(it)}}
        } finally {recognizer.close()}
    }
    /** ML Kit groups columns into separate blocks. Rebuild printed rows before parsing prices. */
    private fun receiptText(lines:List<ReceiptLine>):String {
        if(lines.any {it.bounds==null}) return lines.joinToString("\n") {it.text}
        // Compare positions in the paper's coordinate system. On a tilted photo,
        // the right-hand price can sit below the next item's name in image coordinates.
        val slopes=lines.mapNotNull {it.slope}.sorted()
        val slope=slopes.getOrNull(slopes.size/2)?:0.0
        fun rowY(line:ReceiptLine)=line.bounds!!.exactCenterY()-slope*line.bounds.exactCenterX()
        val rows=mutableListOf<MutableList<ReceiptLine>>()
        for(line in lines.sortedWith(compareBy<ReceiptLine> {rowY(it)}.thenBy {it.bounds!!.left})) {
            val row=rows.lastOrNull()
            val anchor=row?.first()
            if(row!=null && anchor!=null && kotlin.math.abs(rowY(line)-rowY(anchor))<=minOf(line.textHeight,anchor.textHeight)*0.6) {
                row.add(line)
            } else rows.add(mutableListOf(line))
        }
        return rows.joinToString("\n") {row->row.sortedBy {it.bounds!!.left}.joinToString(" ") {it.text}}
    }
    private fun confidence(lines:List<ReceiptLine>):Double {
        val characters=lines.sumOf {it.text.length}.coerceAtLeast(1)
        return lines.sumOf {it.confidence.toDouble()*it.text.length}/characters
    }
    private fun candidateScore(lines:List<ReceiptLine>):Double {
        val bill=BillRecognition.parse(receiptText(lines))
        // Taxes/discounts can legitimately separate item sums from totals. Do not reward a
        // different OCR amount merely because it makes the arithmetic more convenient.
        return confidence(lines)*0.8+(if(bill.total!=null) 0.1 else 0.0)+(if(bill.items.isNotEmpty()) 0.1 else 0.0)
    }
    /** A weak first read is retried at the other right angles, for receipts photographed sideways
     *  or upside down without rotation metadata. The best-scoring orientation is kept. */
    private suspend fun uprightText(bitmap:Bitmap,warnings:MutableList<String>):String {
        val upright=bitmapLines(bitmap,enhanced=false)
        if(!weak(upright)) return bitmapText(bitmap,warnings,upright)
        var best=0f;var bestLines=upright;var bestScore=candidateScore(upright)
        for(degrees in floatArrayOf(90f,270f,180f)) {
            currentCoroutineContext().ensureActive()
            val turned=rotate(bitmap,degrees)
            try {
                val lines=bitmapLines(turned,enhanced=false)
                val score=candidateScore(lines)
                if(score>bestScore+0.05) {best=degrees;bestLines=lines;bestScore=score}
            } finally {turned.recycle()}
            if(best!=0f && !weak(bestLines)) break
        }
        if(best==0f) return bitmapText(bitmap,warnings,upright)
        val turned=rotate(bitmap,best)
        try {return bitmapText(turned,warnings,bestLines)} finally {turned.recycle()}
    }
    private fun weak(lines:List<ReceiptLine>):Boolean {
        if(lines.isEmpty() || confidence(lines)<0.7) return true
        val bill=BillRecognition.parse(receiptText(lines))
        return bill.total==null && bill.items.isEmpty()
    }
    private fun rotate(bitmap:Bitmap,degrees:Float):Bitmap=
        Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply {setRotate(degrees)},true)
    private suspend fun bitmapText(bitmap:Bitmap,warnings:MutableList<String>,firstRead:List<ReceiptLine>?=null):String {
        val original=firstRead?:bitmapLines(bitmap,enhanced=false)
        val originalText=receiptText(original)
        val bill=BillRecognition.parse(originalText)
        var selected=original
        if(confidence(original)<0.9 || bill.total==null || bill.items.isEmpty() || ReceiptImageEnhancement.lowContrast(bitmap)) {
            val recovered=try {bitmapLines(bitmap,enhanced=true)}
            catch(cancelled:CancellationException) {throw cancelled}
            catch(failure:MlKitException) {
                warnings+="Contrast recovery could not finish. Check the original recognition or try a clearer photo."
                emptyList()
            }
            if(candidateScore(recovered)>candidateScore(original)+0.025) {
                selected=recovered
                if(receiptText(recovered)!=originalText) warnings+="Photo contrast was enhanced. Check the recovered item names and amounts against the original."
            }
        }
        val text=receiptText(selected)
        val parsed=BillRecognition.parse(text)
        if(selected.isEmpty() || confidence(selected)<0.8 || parsed.total==null || parsed.items.isEmpty()) {
            warnings+="This photo could not be read reliably. Retake it closer and in focus, or correct the items and amounts before continuing."
        } else {
            val uncertain=selected.filter {it.confidence<0.75f}.take(3).joinToString("; ") {it.text.take(60)}
            if(uncertain.isNotBlank()) warnings+="Check unclear text against the photo: $uncertain"
        }
        return text
    }
    private suspend fun bitmapLines(bitmap:Bitmap,enhanced:Boolean):List<ReceiptLine> {
        val lines=mutableListOf<ReceiptLine>()
        // Overlap protects letters near a cut. Each line belongs to one core region by position,
        // so identical purchases on different rows are preserved rather than deduplicated by text.
        // Enhanced crops also allocate pixel buffers; keep each near two megapixels.
        val coreHeight=if(enhanced) minOf(1600,(2_000_000/bitmap.width-256).coerceAtLeast(256)) else 1600
        for(coreTop in 0 until bitmap.height step coreHeight) {
            currentCoroutineContext().ensureActive()
            val coreBottom=minOf(coreTop+coreHeight,bitmap.height)
            val cropTop=maxOf(0,coreTop-128)
            val cropBottom=minOf(bitmap.height,coreBottom+128)
            val crop=Bitmap.createBitmap(bitmap,0,cropTop,bitmap.width,cropBottom-cropTop)
            try {
                val input=if(enhanced) ReceiptImageEnhancement.enhance(crop) else crop
                try {
                    for(line in recognize(input).textBlocks.flatMap {it.lines}) {
                        if(line.text.isBlank()) continue
                        val bounds=line.boundingBox?.let {Rect(it).apply {offset(0,cropTop)}}
                        val corners=line.cornerPoints
                        val dx=if(corners!=null && corners.size==4) corners[1].x-corners[0].x else 0
                        val slope=if(dx>0) ((corners!![1].y-corners[0].y).toDouble()/dx).takeIf {kotlin.math.abs(it)<=0.36} else null
                        val textHeight=if(corners!=null && corners.size==4) kotlin.math.hypot((corners[3].x-corners[0].x).toDouble(),(corners[3].y-corners[0].y).toDouble()) else bounds?.height()?.toDouble()?:1.0
                        if(bounds==null || bounds.centerY() in coreTop until coreBottom) lines+=ReceiptLine(line.text,bounds,line.confidence,slope,textHeight)
                    }
                } finally {if(input!==crop) input.recycle()}
            } finally {if(crop!==bitmap) crop.recycle()}
        }
        return lines
    }
    private enum class DecodeSize {RECOGNITION,STORAGE,PREVIEW}
    private class DecodedImage(val bitmap:Bitmap,val mime:String)
    private fun tooLarge(width:Int,height:Int,sample:Int,size:DecodeSize):Boolean {
        val w=width/sample;val h=height/sample
        return when(size) {
            // Receipt text must keep enough pixels per character for OCR. A standard 12 MP phone photo
            // (4032 x 3024) is read at full size; larger camera modes are halved until they fit.
            DecodeSize.RECOGNITION->w>MAX_RECOGNITION_WIDTH || h>MAX_RECOGNITION_HEIGHT || w.toLong()*h>MAX_RECOGNITION_PIXELS
            // Stored copies keep recognition detail, without the enlargement applied for OCR.
            DecodeSize.STORAGE->w>MAX_RECOGNITION_WIDTH || h>MAX_RECOGNITION_HEIGHT || w.toLong()*h>MAX_RECOGNITION_PIXELS
            DecodeSize.PREVIEW->w>PREVIEW_SIDE || h>PREVIEW_SIDE
        }
    }
    /**
     * Decodes any image format this phone supports, upright and as 8-bit ARGB for OCR.
     * BitmapFactory covers JPEG, PNG, WebP, GIF, BMP and (Android 9+) HEIF. ImageDecoder adds
     * formats such as AVIF and camera DNG where the phone supports them.
     */
    private fun decodeImage(bytes:ByteArray,size:DecodeSize):DecodedImage {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        val decoded=(if(bounds.outWidth>0 && bounds.outHeight>0) factoryDecode(bytes,bounds,size) else null)
            ?:(if(Build.VERSION.SDK_INT>=28) runCatching {platformDecode(bytes,size)}.getOrElse {if(it is OutOfMemoryError) throw it else null} else null)
            ?:throw IllegalArgumentException(if(Build.VERSION.SDK_INT<28) "This phone cannot open this photo format. Set the camera to save JPEG photos, or choose a JPEG, PNG or WebP image."
                else "Cannot open this image. Choose a photo or a PDF of the bill.")
        var bitmap=decoded.bitmap
        // 10-bit HDR photos decode to half-float pixels, and hardware bitmaps cannot be read back.
        if(bitmap.config!=Bitmap.Config.ARGB_8888) bitmap=bitmap.copy(Bitmap.Config.ARGB_8888,false).also {bitmap.recycle()}?:throw IllegalArgumentException("Cannot decode this bill image")
        if(size==DecodeSize.RECOGNITION && bitmap.width<MIN_RECOGNITION_WIDTH) {
            val factor=minOf(MAX_UPSCALE,MIN_RECOGNITION_WIDTH.toDouble()/bitmap.width,
                MAX_RECOGNITION_HEIGHT.toDouble()/bitmap.height,kotlin.math.sqrt(MAX_RECOGNITION_PIXELS.toDouble()/(bitmap.width.toLong()*bitmap.height)))
            if(factor>1.05) {
                val larger=Bitmap.createScaledBitmap(bitmap,(bitmap.width*factor).toInt(),(bitmap.height*factor).toInt(),true)
                if(larger!==bitmap) {bitmap.recycle();bitmap=larger}
            }
        }
        return DecodedImage(bitmap,decoded.mime)
    }
    private fun factoryDecode(bytes:ByteArray,bounds:BitmapFactory.Options,size:DecodeSize):DecodedImage? {
        val orientation=runCatching {ExifInterface(java.io.ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
        val width=if(orientation in 5..8) bounds.outHeight else bounds.outWidth
        val height=if(orientation in 5..8) bounds.outWidth else bounds.outHeight
        var sample=1
        while(tooLarge(width,height,sample,size)) sample*=2
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888})?:return null
        val mime=bounds.outMimeType?.takeIf {it.startsWith("image/")}?:"image/unknown"
        val matrix=Matrix()
        when(orientation) {2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(270f);matrix.postScale(-1f,1f)};8->matrix.setRotate(270f)}
        if(orientation !in 2..8) return DecodedImage(bitmap,mime)
        return DecodedImage(Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also {if(it!==bitmap) bitmap.recycle()},mime)
    }
    /** ImageDecoder applies rotation metadata itself, so its size limits allow either orientation. */
    @androidx.annotation.RequiresApi(28)
    private fun platformDecode(bytes:ByteArray,size:DecodeSize):DecodedImage {
        var mime="image/unknown"
        val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) {decoder,info,_->
            mime=info.mimeType.takeIf {it.startsWith("image/")}?:mime
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            val width=info.size.width;val height=info.size.height
            var sample=1
            while(tooLarge(width,height,sample,size) || tooLarge(height,width,sample,size)) sample*=2
            if(sample>1) decoder.setTargetSampleSize(sample)
        }
        return DecodedImage(bitmap,mime)
    }
    private suspend fun pdfText(bytes:ByteArray,warnings:MutableList<String>):String {
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
                            val scale=2000.0/maxOf(page.width,page.height);val bitmap=createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                            try {bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);texts+=bitmapText(bitmap,warnings)} finally {bitmap.recycle()}
                        }
                    }
                }
                texts.joinToString("\n")
            }}
        } finally {file.delete()}
    }
    suspend fun preview(bytes:ByteArray,mime:String,pageIndex:Int=0):Bitmap=withContext(Dispatchers.IO) {
        if(mime!="application/pdf") decodeImage(bytes,DecodeSize.PREVIEW).bitmap
        else {
            val file=File.createTempFile("bill-view-",".pdf",context.cacheDir)
            try {file.writeBytes(bytes);ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {descriptor->PdfRenderer(descriptor).use {renderer->
                require(pageIndex in 0 until renderer.pageCount && renderer.pageCount<=MAX_PAGES)
                renderer.openPage(pageIndex).use {page->val scale=1600.0/maxOf(page.width,page.height);createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also {it.eraseColor(Color.WHITE);page.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)}}
            }}} finally {file.delete()}
        }
    }
    internal suspend fun receiptImage(bytes:ByteArray):Bitmap=withContext(Dispatchers.IO) {decodeImage(bytes,DecodeSize.STORAGE).bitmap}
    suspend fun pageCount(bytes:ByteArray,mime:String):Int=withContext(Dispatchers.IO) {
        if(mime!="application/pdf") 1 else {
            val file=File.createTempFile("bill-pages-",".pdf",context.cacheDir)
            try {file.writeBytes(bytes);ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {descriptor->PdfRenderer(descriptor).use {it.pageCount.also {count->require(count in 1..MAX_PAGES)}}}}
            finally {file.delete()}
        }
    }
}
