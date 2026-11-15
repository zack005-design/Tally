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
    /**
     * A complete shareable receipt: merchant, date and time, category, payment account, every item,
     * total, who paid and each share, what is still owed, then the scanned original when one is attached.
     * Private notes and account balances are never included.
     */
    suspend fun bytes(detail: RecordDetail, people: List<Person>, debts: List<Debt>, accounts: List<Account> = emptyList(), categories: List<Category> = emptyList(), receipt: ScannedBill? = null, scanner: BillScanner? = null): ByteArray = withContext(Dispatchers.IO) {
        val record = detail.record
        require(record.kind == Kind.EXPENSE && !record.reversed) { "Choose a saved, active expense" }
        fun name(id: String) = if (id == "self") "You" else people.find { it.id == id }?.name ?: "Participant"
        val items = ReceiptItems.from(record.notes, receipt?.text)
        val merchant = ReceiptItems.merchant(receipt?.text)
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }
        val muted = Color.rgb(96, 96, 96)
        var number = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(420, 595, number).create())
        var y = 40f
        fun finishPage() {
            paint.textSize = 9f; paint.typeface = Typeface.DEFAULT; paint.color = muted; paint.textAlign = Paint.Align.LEFT
            page.canvas.drawText("Tally receipt · Page $number", 28f, 575f, paint)
            paint.color = Color.BLACK
            document.finishPage(page)
        }
        fun room(height: Float) {
            if (y + height > 548f) {
                finishPage(); number++
                page = document.startPage(PdfDocument.PageInfo.Builder(420, 595, number).create()); y = 40f
            }
        }
        fun style(size: Float, bold: Boolean, color: Int = Color.BLACK, mono: Boolean = false) {
            paint.textSize = size; paint.color = color
            paint.typeface = when { mono && bold -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); mono -> Typeface.MONOSPACE; bold -> Typeface.DEFAULT_BOLD; else -> Typeface.DEFAULT }
        }
        fun wrap(value: String, width: Float): List<String> = value.lines().flatMap { paragraph ->
            val out = mutableListOf<String>()
            var remaining = paragraph
            do {
                var count = paint.breakText(remaining, true, width.coerceAtLeast(40f), null).coerceAtLeast(1)
                if (count < remaining.length) {
                    val space = remaining.lastIndexOf(' ', count - 1)
                    if (space > 0) count = space + 1
                }
                out += remaining.take(count).trimEnd()
                remaining = remaining.drop(count)
            } while (remaining.isNotEmpty())
            out
        }
        fun line(value: String, size: Float = 12f, bold: Boolean = false, color: Int = Color.BLACK, center: Boolean = false) {
            style(size, bold, color)
            wrap(value, 364f).forEach {
                room(size)
                paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
                page.canvas.drawText(it, if (center) 210f else 28f, y, paint)
                y += size + 7f
            }
            paint.textAlign = Paint.Align.LEFT
        }
        /** The label wraps; the amount stays right-aligned on its first line, like a printed bill. */
        fun row(label: String, amount: String, size: Float = 12f, bold: Boolean = false, color: Int = Color.BLACK) {
            style(size, bold, color, mono = true)
            if (size > 14f) y += size - 12f
            val amountWidth = paint.measureText(amount)
            wrap(label, 364f - amountWidth - 12f).forEachIndexed { index, text ->
                room(size)
                paint.textAlign = Paint.Align.LEFT
                page.canvas.drawText(text, 28f, y, paint)
                if (index == 0) {
                    paint.textAlign = Paint.Align.RIGHT
                    page.canvas.drawText(amount, 392f, y, paint)
                }
                y += size + 6f
            }
            paint.textAlign = Paint.Align.LEFT
        }
        fun divider(dashed: Boolean = true) {
            room(12f); y += 2f
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; strokeWidth = 0.8f; if (dashed) pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f) }
            page.canvas.drawLine(28f, y - 6f, 392f, y - 6f, stroke); y += 10f
        }
        try {
            val time = Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault())
            line(merchant ?: record.description, 20f, true, center = true)
            if (merchant != null && !merchant.equals(record.description, true)) line(record.description, 12f, color = muted, center = true)
            line("EXPENSE RECEIPT", 10f, color = muted, center = true)
            divider(false)
            row("Date", time.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")), 11f)
            row("Time", time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), 11f)
            categories.find { it.id == record.categoryId }?.let { row("Category", it.name, 11f) }
            detail.entries.filter { it.amount < 0 }.map { movement -> accounts.find { it.id == movement.accountId }?.name ?: "Account" }.distinct()
                .takeIf { it.isNotEmpty() }?.let { row("Paid from", it.joinToString(", "), 11f) }
            row("Reference", record.id.take(8).uppercase(), 11f, color = muted)
            divider()
            if (items.isNotEmpty()) {
                line("ITEMS", 11f, true)
                items.forEach { row(it.name, Money.format(it.amount), 12f) }
                val itemTotal = runCatching { Finance.total(items.map { it.amount }) }.getOrNull()
                if (itemTotal != null && itemTotal != record.amount) {
                    divider()
                    row("Items subtotal", Money.format(itemTotal), 11f, color = muted)
                    val difference = record.amount - itemTotal
                    row(if (difference > 0) "Taxes, charges and other" else "Discounts and adjustments", (if (difference < 0) "-" else "") + Money.format(kotlin.math.abs(difference)), 11f, color = muted)
                }
                divider()
            }
            row("TOTAL", Money.format(record.amount), 18f, true)
            divider()
            val shares = detail.shares.ifEmpty { mapOf("self" to record.amount) }
            line(if (shares.size > 1) "PAYMENTS AND SHARES" else "PAYMENT", 11f, true)
            shares.forEach { (id, share) ->
                line(name(id), 12f, true)
                row("  Paid", Money.format(detail.contributions[id] ?: if (shares.size == 1) record.amount else 0), 11f)
                if (shares.size > 1) row("  Share", Money.format(share), 11f)
            }
            if (shares.size > 1) {
                divider(); line("REMAINING ON THIS BILL", 11f, true)
                val remaining = debts.filter { it.transactionId == record.id && it.outstanding > 0 }
                if (remaining.isEmpty()) line("Settled. No outstanding amounts on this bill.", 11f)
                else remaining.forEach { row("${name(it.from)} owes ${name(it.to)}", Money.format(it.outstanding), 11f) }
            }
            divider()
            if (receipt != null && scanner != null) line("The original receipt is attached on the following pages.", 10f, color = muted, center = true)
            line("Based on records and repayments saved in Tally. Please check together before paying.", 9f, color = muted, center = true)
            line("Expense record, not a merchant tax invoice.", 9f, color = muted, center = true)
            finishPage()
            if (receipt != null && scanner != null) {
                ReceiptStorage.validate(receipt)
                val pages = scanner.pageCount(receipt.bytes, receipt.mime)
                for (index in 0 until pages) {
                    val bitmap = if (receipt.mime == "application/pdf") scanner.preview(receipt.bytes, receipt.mime, index) else scanner.receiptImage(receipt.bytes)
                    try { number = drawOriginal(document, bitmap, number + 1, 420, 595) - 1 } finally { bitmap.recycle() }
                }
            }
            ByteArrayOutputStream().use { output -> document.writeTo(output); output.toByteArray() }
        } finally { document.close() }
    }

    /** Draws the bitmap at full width across as many pages as needed; returns the next page number. */
    private fun drawOriginal(document: PdfDocument, bitmap: android.graphics.Bitmap, first: Int, width: Int, height: Int): Int {
        val margin = 28f
        val scale = (width - 2 * margin) / bitmap.width
        val sliceHeight = ((height - 2 * margin) / scale).toInt().coerceAtLeast(1)
        var number = first
        for (top in 0 until bitmap.height step sliceHeight) {
            val bottom = minOf(top + sliceHeight, bitmap.height)
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, number++).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap, android.graphics.Rect(0, top, bitmap.width, bottom), RectF(margin, margin, width - margin, margin + (bottom - top) * scale), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            document.finishPage(page)
        }
        return number
    }

    suspend fun share(context: Context, detail: RecordDetail, people: List<Person>, debts: List<Debt>, accounts: List<Account> = emptyList(), categories: List<Category> = emptyList(), receipt: ScannedBill? = null, scanner: BillScanner? = null): Intent =
        attachment(context, bytes(detail, people, debts, accounts, categories, receipt, scanner))

    /** Export the actual pixels, never the uncertain OCR interpretation. */
    suspend fun originalBytes(bill:ScannedBill,scanner:BillScanner):ByteArray=withContext(Dispatchers.IO) {
        ReceiptStorage.validate(bill)
        if(bill.mime=="application/pdf") return@withContext bill.bytes
        val bitmap=scanner.receiptImage(bill.bytes)
        try {
            val document=PdfDocument()
            try {
                drawOriginal(document,bitmap,1,595,842)
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
