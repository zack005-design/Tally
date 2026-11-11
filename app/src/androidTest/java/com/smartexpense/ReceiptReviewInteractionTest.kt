package com.smartexpense

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartexpense.data.*
import com.smartexpense.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(Parameterized::class)
class ReceiptReviewInteractionTest(private val width:Int,private val fontScale:Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="width={0} font={1}")
        fun sizes()=listOf(arrayOf<Any>(320,2f),arrayOf<Any>(412,1f))
    }
    @get:Rule val compose=createComposeRule()
    @Composable private fun Frame(content:@Composable ()->Unit) {
        CompositionLocalProvider(LocalDensity provides Density(LocalWindowInfo.current.containerSize.width/412f,fontScale)) {
            TrackerTheme {Box(Modifier.width(width.dp).fillMaxHeight()) {content()}}
        }
    }
    private fun receipt():ScannedBill {
        val image=Bitmap.createBitmap(600,1600,Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.WHITE)
        val canvas=Canvas(image)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;textSize=32f}
        listOf("Corner Cafe","Tea 50.00","Cake 100.00","Grand Total 150.00").forEachIndexed {index,line->canvas.drawText(line,30f,80f+index*180,paint)}
        val bytes=java.io.ByteArrayOutputStream().also {image.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
        image.recycle()
        return ScannedBill(bytes,"image/png","Corner Cafe\nTea 50.00\nCake 100.00\nGrand Total 150.00")
    }

    @Test fun originalReceiptCanBeEnlargedAndResetWithoutChangingTheDraft() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val original=receipt()
        compose.setContent {Frame {BillPreview(BillScanner(context),original)}}
        compose.waitUntil(5000) {compose.onAllNodesWithText("Enlarge receipt").fetchSemanticsNodes().isNotEmpty()}
        // Compare with the actual parent control: Android applies non-linear large-font scaling.
        val parentBounds=compose.onNodeWithText("Enlarge receipt",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val parentTextHeight=parentBounds.bottom-parentBounds.top
        compose.onNodeWithText("Enlarge receipt").performClick()
        compose.onNodeWithText("Close preview",useUnmergedTree=true).assertHeightIsAtLeast(parentTextHeight)
        compose.onNodeWithText("Zoom in").performClick()
        compose.onNodeWithContentDescription("Receipt image").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Zoom 200 percent"))
        File(context.getExternalFilesDir(null),"receipt-zoom-$width.png").outputStream().use {output->
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,output)
        }
        compose.onNodeWithText("Reset zoom").performClick()
        compose.onNodeWithContentDescription("Receipt image").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Zoom 100 percent"))
        compose.onNodeWithText("Close preview").performClick()
        compose.onNodeWithText("Enlarge receipt").assertExists()
    }

    @Test fun cancelingReplacementKeepsTheOriginalReadyForTheSplitDraft()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,TrackerDatabase::class.java).build()
        val original=receipt();val repo=FinanceRepository(db);repo.initialize()
        var prepared:ScannedBill?=null
        try {
            compose.setContent {Frame {BillImportScreen(BillScanner(context),repo,com.smartexpense.domain.Snapshot(),{bill,_,_->prepared=bill},{},original)}}
            compose.waitUntil(5000) {runCatching {compose.onNodeWithText("Continue to split").assertIsEnabled()}.isSuccess}
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Replace receipt"))
            compose.onNodeWithText("Replace receipt").performClick()
            compose.onNodeWithText("Choose replacement image").assertExists()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Continue to split").assertIsEnabled().performClick()
            compose.runOnIdle {assertArrayEquals(original.bytes,prepared!!.bytes);assertEquals(original.text,prepared!!.text)}
        } finally {db.close()}
    }
}
