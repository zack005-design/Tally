package com.smartexpense

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.data.ScannedBill
import com.smartexpense.domain.BillItem
import com.smartexpense.ui.ReceiptReviewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReceiptReplacementTest {
    private val main=InstrumentationRegistry.getInstrumentation()
    private fun bill(label:String)=ScannedBill(label.toByteArray(),"image/png","$label\nTotal 50.00")

    @Test fun failedReplacementKeepsTheOriginalAndItsCorrections()=runBlocking {
        val original=bill("Original");val store=ViewModelStore()
        lateinit var model:ReceiptReviewModel;lateinit var operation:Job
        main.runOnMainSync {
            model=ReceiptReviewModel(original);store.put("receipt",model)
            model.correctedItems.value=listOf(BillItem("Corrected tea",5000))
            model.selectedCategory.value="food"
            operation=model.replaceBill({throw IllegalArgumentException("Unreadable replacement")},{null})
        }
        try {
            withTimeout(5000) {operation.join()}
            assertSame(original,model.bill.value)
            assertEquals(original.text,model.text.value)
            assertEquals(listOf(BillItem("Corrected tea",5000)),model.correctedItems.value)
            assertEquals("food",model.selectedCategory.value)
            assertEquals("Unreadable replacement",model.error.value)
            assertFalse(model.scanning.value)
        } finally {main.runOnMainSync {store.clear()}}
    }

    @Test fun replacementCommitsBytesTextAndDuplicateStateTogether()=runBlocking {
        val original=bill("Original");val replacement=bill("Replacement");val store=ViewModelStore()
        val ready=CompletableDeferred<ScannedBill>()
        lateinit var model:ReceiptReviewModel;lateinit var operation:Job
        main.runOnMainSync {
            model=ReceiptReviewModel(original);store.put("receipt",model)
            model.correctedItems.value=listOf(BillItem("Old edit",5000));model.selectedCategory.value="old-category"
            model.editingText.value=true;model.textDraft.value="Old receipt text"
            model.itemEditor.value=true;model.itemIndex.intValue=0;model.itemName.value="Old item";model.itemAmount.value="50"
            model.choosingCategory.value=true
            operation=model.replaceBill({ready.await()},{"existing-transaction"})
        }
        try {
            assertSame(original,model.bill.value)
            ready.complete(replacement)
            withTimeout(5000) {operation.join()}
            assertSame(replacement,model.bill.value)
            assertEquals(replacement.text,model.text.value)
            assertNull(model.correctedItems.value)
            assertNull(model.selectedCategory.value)
            assertEquals("existing-transaction",model.duplicate.value)
            assertFalse("The old text editor must not overwrite the replacement",model.editingText.value)
            assertEquals("",model.textDraft.value)
            assertFalse(model.itemEditor.value)
            assertEquals("",model.itemName.value)
            assertEquals("",model.itemAmount.value)
            assertFalse(model.choosingCategory.value)
        } finally {main.runOnMainSync {store.clear()}}
    }
}
