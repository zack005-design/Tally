package com.smartexpense.data

import android.graphics.Bitmap
import androidx.core.graphics.get
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Removes slowly varying paper shadows and strengthens faint ink without changing source pixels. */
internal object ReceiptImageEnhancement {
    fun lowContrast(source:Bitmap):Boolean {
        var darkest=255
        var lightest=0
        for(y in 0 until source.height step maxOf(1,source.height/64)) {
            for(x in 0 until source.width step maxOf(1,source.width/64)) {
                val value=luminance(source[x,y])
                darkest=minOf(darkest,value);lightest=maxOf(lightest,value)
            }
        }
        return lightest-darkest<64
    }

    suspend fun enhance(source:Bitmap):Bitmap {
        val width=source.width
        val height=source.height
        val pixels=IntArray(width*height)
        source.getPixels(pixels,0,width,0,0,width,height)
        val gray=ByteArray(pixels.size) {luminance(pixels[it]).toByte()}
        val radius=24
        val columns=IntArray(width)
        for(y in 0..minOf(radius,height-1)) for(x in 0 until width) columns[x]+=gray[y*width+x].toInt() and 255
        for(y in 0 until height) {
            if(y%32==0) currentCoroutineContext().ensureActive()
            val rowCount=minOf(height-1,y+radius)-maxOf(0,y-radius)+1
            var sum=0
            for(x in 0..minOf(radius,width-1)) sum+=columns[x]
            for(x in 0 until width) {
                val count=rowCount*(minOf(width-1,x+radius)-maxOf(0,x-radius)+1)
                val paper=(sum+count/2)/count
                val ink=gray[y*width+x].toInt() and 255
                val value=255-((paper-ink).coerceAtLeast(0)*32).coerceAtMost(255)
                pixels[y*width+x]=(255 shl 24) or (value shl 16) or (value shl 8) or value
                if(x-radius>=0) sum-=columns[x-radius]
                if(x+radius+1<width) sum+=columns[x+radius+1]
            }
            for(x in 0 until width) {
                if(y-radius>=0) columns[x]-=gray[(y-radius)*width+x].toInt() and 255
                if(y+radius+1<height) columns[x]+=gray[(y+radius+1)*width+x].toInt() and 255
            }
        }
        currentCoroutineContext().ensureActive()
        return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
    }

    private fun luminance(pixel:Int):Int {
        val gray=(((pixel ushr 16 and 255)*77)+((pixel ushr 8 and 255)*150)+((pixel and 255)*29)) ushr 8
        return 255-(255-gray)*(pixel ushr 24)/255
    }
}
