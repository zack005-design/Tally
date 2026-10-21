package com.smartexpense.data

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.smartexpense.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReportExport {
    suspend fun bytes(snapshot:Snapshot,review:MonthlyReview,pdf:Boolean,reportType:String="Combined"):ByteArray=withContext(Dispatchers.IO) {
        val rows=ReportCsv.rows(snapshot,review,reportType)
        if(!pdf) ReportCsv.encode(rows).toByteArray(Charsets.UTF_8)
        else {
            val document=PdfDocument();val output=java.io.ByteArrayOutputStream()
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=11f;color=android.graphics.Color.BLACK}
            var number=1;var page=document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create());var y=48f
            fun nextPage() {document.finishPage(page);number++;page=document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create());y=48f}
            fun line(text:String) {if(y>792) nextPage();page.canvas.drawText(text,32f,y,paint);y+=17f}
            fun wrap(text:String) {
                text.lines().forEach {paragraph->
                    var remaining=paragraph
                    while(remaining.isNotEmpty()) {
                        var count=paint.breakText(remaining,true,531f,null).coerceAtLeast(1)
                        if(count<remaining.length) {val space=remaining.lastIndexOf(' ',count-1);if(space>0) count=space+1}
                        line(remaining.take(count).trimEnd());remaining=remaining.drop(count)
                    }
                    if(paragraph.isEmpty()) line("")
                }
            }
            try {rows.forEach {wrap(it.joinToString("  |  "))};document.finishPage(page);document.writeTo(output);output.toByteArray()} finally {document.close()}
        }
    }
}
