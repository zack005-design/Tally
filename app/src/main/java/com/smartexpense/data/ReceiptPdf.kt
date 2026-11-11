package com.smartexpense.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.smartexpense.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** A human-readable record of a saved bill, never a merchant tax invoice or a backup. */
object ReceiptPdf {
    suspend fun bytes(detail: RecordDetail, people: List<Person>, debts: List<Debt>): ByteArray = withContext(Dispatchers.IO) {
        val record = detail.record
        require(record.kind == Kind.EXPENSE && !record.reversed) { "Choose a saved, active expense" }
        fun name(id: String) = if (id == "self") "You" else people.find { it.id == id }?.name ?: "Participant"
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }
        var number = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(420, 595, number).create())
        var y = 40f
        fun finishPage() {
            paint.textSize = 9f; paint.typeface = Typeface.DEFAULT
            page.canvas.drawText("Tally receipt · Page $number", 28f, 575f, paint)
            document.finishPage(page)
        }
        fun line(value: String, size: Float = 12f, bold: Boolean = false) {
            value.lines().forEach { paragraph ->
                var remaining = paragraph
                do {
                    if (y + size > 548f) {
                        finishPage(); number++
                        page = document.startPage(PdfDocument.PageInfo.Builder(420, 595, number).create()); y = 40f
                    }
                    paint.textSize = size; paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    var count = paint.breakText(remaining, true, 364f, null).coerceAtLeast(1)
                    if (count < remaining.length) {
                        val space = remaining.lastIndexOf(' ', count - 1)
                        if (space > 0) count = space + 1
                    }
                    page.canvas.drawText(remaining.take(count).trimEnd(), 28f, y, paint)
                    y += size + 7f
                    remaining = remaining.drop(count)
                } while (remaining.isNotEmpty())
            }
        }
        fun divider() { line("________________________________________", 12f); y += 4f }
        try {
            line("TALLY", 23f, true)
            line("EXPENSE RECEIPT", 10f)
            divider()
            line(record.description, 18f, true)
            line(Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).toLocalDate().toString())
            line("Receipt reference: ${record.id}", 9f)
            divider()
            line("TOTAL  ${Money.format(record.amount)}", 21f, true)
            divider()
            line("PAYMENTS AND SHARES", 12f, true)
            detail.shares.ifEmpty { mapOf("self" to record.amount) }.forEach { (id, share) ->
                line(name(id), 13f, true)
                line("Paid ${Money.format(detail.contributions[id] ?: 0)}    Share ${Money.format(share)}")
            }
            if (detail.shares.size > 1) {
                divider(); line("REMAINING ON THIS BILL", 12f, true)
                val remaining = debts.filter { it.transactionId == record.id && it.outstanding > 0 }
                if (remaining.isEmpty()) line("No outstanding amounts on this bill")
                else remaining.forEach { line("${name(it.from)} owes ${name(it.to)}: ${Money.format(it.outstanding)}") }
            }
            divider()
            line("Based on records and repayments saved in Tally. Please check together before paying.", 10f)
            line("Expense record, not a merchant tax invoice.", 9f)
            finishPage()
            ByteArrayOutputStream().use { output -> document.writeTo(output); output.toByteArray() }
        } finally { document.close() }
    }

    suspend fun share(context: Context, detail: RecordDetail, people: List<Person>, debts: List<Debt>): Intent =
        attachment(context, bytes(detail, people, debts))

    /** Export the actual pixels, never the uncertain OCR interpretation. */
    suspend fun originalBytes(bill:ScannedBill,scanner:BillScanner):ByteArray=withContext(Dispatchers.IO) {
        ReceiptStorage.validate(bill)
        if(bill.mime=="application/pdf") return@withContext bill.bytes
        val bitmap=scanner.receiptImage(bill.bytes)
        try {
            val document=PdfDocument()
            try {
                val scale=539f/bitmap.width
                val sliceHeight=(786f/scale).toInt().coerceAtLeast(1)
                var number=1
                for(top in 0 until bitmap.height step sliceHeight) {
                    val bottom=minOf(top+sliceHeight,bitmap.height)
                    val page=document.startPage(PdfDocument.PageInfo.Builder(595,842,number++).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(bitmap,android.graphics.Rect(0,top,bitmap.width,bottom),RectF(28f,28f,567f,28f+(bottom-top)*scale),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(page)
                }
                ByteArrayOutputStream().use {output->document.writeTo(output);output.toByteArray()}
            } finally {document.close()}
        } finally {bitmap.recycle()}
    }

    suspend fun shareOriginal(context:Context,bill:ScannedBill,scanner:BillScanner):Intent=
        attachment(context,originalBytes(bill,scanner))

    private suspend fun attachment(context: Context, bytes: ByteArray): Intent = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "receipt-share").apply { check(mkdirs() || isDirectory) }
        // Keep files long enough for receiving apps to read an earlier share.
        directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86400000L }?.forEach { it.delete() }
        val file = File.createTempFile("Tally-receipt-", ".pdf", directory)
        try { file.writeBytes(bytes) } catch (failure: Exception) { file.delete(); throw failure }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.bills", file)
        Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .apply { clipData = ClipData.newUri(context.contentResolver, "Receipt PDF", uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
