package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.io.File

class LocalReminderAndExportTest {
    private fun db()=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
    private class Gateway:ReminderGateway {
        var allow=true;var next:Instant?=null;var shown=0
        override fun allowed()=allow
        override fun reconcile(next:Instant?,hasDue:Boolean) {this.next=next}
        override fun show():Boolean {shown++;return true}
    }
    @Test fun reminderPermissionOffBackupAndDeliveryNeverRecordMoney()=runBlocking {
        val db=db()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(100000)
            val day=LocalDate.now();val account=repo.snapshot().walletSources!!.walletId
            repo.saveTemplate(RecurringTemplate("fixture",Kind.EXPENSE,1000,"Recurring fixture",account,day.toString(),"Monthly"))
            val gateway=Gateway();val expected=day.atTime(20,0).atZone(ZoneId.systemDefault()).toInstant()
            val coordinator=ReminderCoordinator(repo,gateway,Clock.fixed(expected.plusSeconds(60),ZoneOffset.UTC))
            coordinator.syncNotifications();assertNull(gateway.next)
            repo.saveReminderSettings(ReminderSettings(true,20,0));gateway.allow=false
            coordinator.syncNotifications();assertNull(gateway.next);assertEquals(0,gateway.shown)
            gateway.allow=true;coordinator.syncNotifications(expected.toEpochMilli());coordinator.syncNotifications(expected.toEpochMilli())
            assertEquals(1,gateway.shown);assertNotNull(gateway.next)
            assertEquals(day.toString(),repo.reminderSettings().lastNotifiedDate)
            val saved=repo.reminderSettings();val backup=BackupService(db);backup.restore(backup.export(charArrayOf()),charArrayOf())
            assertEquals(saved,repo.reminderSettings())
            repo.saveReminderSettings(saved.copy(enabled=false));coordinator.syncNotifications();assertNull(gateway.next)
            assertEquals(100000L,repo.snapshot().accounts.single {it.id==account}.balance)
            assertTrue(repo.snapshot().records.none {it.kind==Kind.EXPENSE})
        } finally {db.close()}
    }
    @Test fun reportUsesWholeMonthAndPdfCanBeRendered()=runBlocking {
        val db=db()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(100000)
            val account=repo.snapshot().walletSources!!.walletId
            repeat(7) {repo.save(EntryDraft(amount=1000,description="Tea, cake $it",accountId=account,notes="=SUM(A1)"))}
            val (snapshot,review)=repo.exportReport(YearMonth.now().toString())
            assertEquals(7000L,review.spending);assertEquals(93000L,review.closing)
            val csv=ReportExport.bytes(snapshot,review,false).toString(Charsets.UTF_8)
            assertTrue(csv.contains("Tea, cake 6"));assertTrue(csv.contains("'=SUM(A1)"));assertTrue(csv.contains("70.00"))
            val context=ApplicationProvider.getApplicationContext<Context>();val file=File(context.getExternalFilesDir(null),"local-finance-report.pdf")
            file.writeBytes(ReportExport.bytes(snapshot,review,true))
            android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use {fd->android.graphics.pdf.PdfRenderer(fd).use {renderer->
                assertTrue(renderer.pageCount>0)
                renderer.openPage(0).use {page->
                    val bitmap=android.graphics.Bitmap.createBitmap(595,842,android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(context.getExternalFilesDir(null),"local-finance-report.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                    if(android.os.Build.VERSION.SDK_INT>=35) assertTrue(page.textContents.joinToString(" ") {it.text}.contains("Personal spending"))
                }
            }}
        } finally {db.close()}
    }
}
