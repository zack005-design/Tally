package com.smartexpense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartexpense.data.*
import com.smartexpense.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptRepositoryTest {
    @Test fun multiChunkEncryptedReceiptRoundTripsAndDamagedRestoreIsAtomic()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(100000)
            val random=kotlin.random.Random(42)
            val pixels=IntArray(1024*1024) {0xff000000.toInt() or random.nextInt(0x1000000)}
            val bitmap=android.graphics.Bitmap.createBitmap(pixels,1024,1024,android.graphics.Bitmap.Config.ARGB_8888)
            val output=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
            val bill=ScannedBill(output.toByteArray(),"image/png","Fixture original bill")
            assertTrue(bill.bytes.size>ReceiptStorage.CHUNK_BYTES)
            val draft=EntryDraft(amount=1000,description="Large receipt fixture",accountId=repo.snapshot().walletSources!!.walletId)
            repo.saveWithBill(draft,bill)
            val backup=BackupService(db);val encrypted=backup.export("fixture password".toCharArray())
            backup.restore(encrypted,"fixture password".toCharArray())
            assertArrayEquals(bill.bytes,repo.receipt(draft.id)!!.bytes)
            val before=repo.snapshot().accounts
            val damaged=org.json.JSONObject(backup.export(charArrayOf()).toString(Charsets.UTF_8))
            val chunks=damaged.getJSONArray("receiptChunks");chunks.remove(chunks.length()-1)
            assertTrue(runCatching {backup.restore(damaged.toString().toByteArray(),charArrayOf())}.isFailure)
            assertEquals(before,repo.snapshot().accounts);assertArrayEquals(bill.bytes,repo.receipt(draft.id)!!.bytes)
        } finally {db.close()}
    }
    @Test fun receiptAndLedgerCommitAndRestoreTogether()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),TrackerDatabase::class.java).build()
        try {
            val repo=FinanceRepository(db);repo.initialize();repo.configureWallet(100000)
            val bitmap=android.graphics.Bitmap.createBitmap(500,500,android.graphics.Bitmap.Config.ARGB_8888)
            val output=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle()
            val bill=ScannedBill(output.toByteArray(),"image/png","Store\nTotal 100.00")
            val draft=EntryDraft(amount=10000,description="Store",accountId=repo.snapshot().walletSources!!.walletId)
            repo.saveWithBill(draft,bill)
            assertArrayEquals(bill.bytes,repo.receipt(draft.id)!!.bytes)
            assertTrue(runCatching {repo.saveWithBill(draft.copy(id="duplicate"),bill)}.isFailure)
            assertNull(db.dao().transaction("duplicate"))
            val backup=BackupService(db);backup.restore(backup.export(charArrayOf()),charArrayOf())
            assertArrayEquals(bill.bytes,repo.receipt(draft.id)!!.bytes)
            repo.removeReceipt(draft.id)
            assertNull(repo.receipt(draft.id));assertEquals(90000L,repo.snapshot().accounts.single {it.id==draft.accountId}.balance)
        } finally {db.close()}
    }
}
