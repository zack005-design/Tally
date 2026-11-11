package com.smartexpense.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartexpense.data.BillScanner
import com.smartexpense.data.ScannedBill
import kotlinx.coroutines.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt

@Composable fun BillPreview(scanner:BillScanner,bill:ScannedBill,showHeading:Boolean=true) {
    var page by remember(bill.bytes) {mutableIntStateOf(0)}
    var pages by remember(bill.bytes) {mutableIntStateOf(1)}
    var bitmap by remember(bill.bytes) {mutableStateOf<Bitmap?>(null)}
    var error by remember(bill.bytes) {mutableStateOf<String?>(null)}
    var enlarged by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(bill.bytes,bill.mime,page) {
        error=null;bitmap=null
        try {pages=scanner.pageCount(bill.bytes,bill.mime);bitmap=scanner.preview(bill.bytes,bill.mime,page)}
        catch(e:CancellationException) {throw e}
        catch(e:Exception) {error="This page is unavailable"}
    }
    val navigation:@Composable ()->Unit = {
        if(bill.mime=="application/pdf") Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton({page--},enabled=page>0) {Text("Previous page")}
            TextButton({page++},enabled=page<pages-1 && error==null && bitmap!=null) {Text("Next page")}
        }
    }
    Panel {
        if(showHeading) SectionHeading("Original bill${if(bill.mime=="application/pdf") " · page ${page+1}" else ""}")
        bitmap?.let {image->
            Image(image.asImageBitmap(),"Original bill for checking recognition",Modifier.fillMaxWidth().heightIn(max=240.dp))
            TextButton({enlarged=true}) {Text("Enlarge receipt")}
        }
        error?.let {Text(it,color=Tokens.muted)}
        navigation()
    }
    val parentDensity=LocalDensity.current
    if(enlarged) Dialog({enlarged=false},DialogProperties(usePlatformDefaultWidth=false)) {
        CompositionLocalProvider(LocalDensity provides parentDensity) {
        Surface(Modifier.fillMaxSize(),color=Tokens.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton({enlarged=false}) {Text("Close preview")}
                Text(if(bill.mime=="application/pdf") "Receipt · page ${page+1} of $pages" else "Original receipt",style=MaterialTheme.typography.titleMedium)
                val image=bitmap
                if(image!=null) ZoomableReceipt(image,Modifier.weight(1f)) else Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Text(error?:"Loading receipt…")
                }
                navigation()
            }
        }
        }
    }
}

@Composable private fun ZoomableReceipt(bitmap:Bitmap,modifier:Modifier) {
    var zoom by remember(bitmap) {mutableFloatStateOf(1f)}
    var pan by remember(bitmap) {mutableStateOf(Offset.Zero)}
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    fun bounded(offset:Offset,scale:Float):Offset {
        val fit=min(viewport.width.toFloat()/bitmap.width,viewport.height.toFloat()/bitmap.height)
        val x=((bitmap.width*fit*scale-viewport.width)/2).coerceAtLeast(0f)
        val y=((bitmap.height*fit*scale-viewport.height)/2).coerceAtLeast(0f)
        return Offset(offset.x.coerceIn(-x,x),offset.y.coerceIn(-y,y))
    }
    fun setZoom(value:Float) {zoom=value.coerceIn(1f,8f);pan=bounded(pan,zoom)}
    val gestures=rememberTransformableState {centroid,factor,translation,_->
        val next=(zoom*factor).coerceIn(1f,8f)
        val focus=if(centroid.isSpecified) centroid-Offset(viewport.width/2f,viewport.height/2f) else Offset.Zero
        pan=bounded((pan-focus)*(next/zoom)+focus+translation,next)
        zoom=next
    }
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged {viewport=it;pan=bounded(pan,zoom)}.transformable(gestures)) {
            Image(bitmap.asImageBitmap(),"Receipt image",Modifier.fillMaxSize().graphicsLayer {scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y}.semantics {
                stateDescription="Zoom ${(zoom*100).roundToInt()} percent"
                customActions=listOf(
                    CustomAccessibilityAction("Move left") {pan=bounded(pan+Offset(100f,0f),zoom);true},
                    CustomAccessibilityAction("Move right") {pan=bounded(pan-Offset(100f,0f),zoom);true},
                    CustomAccessibilityAction("Move up") {pan=bounded(pan+Offset(0f,100f),zoom);true},
                    CustomAccessibilityAction("Move down") {pan=bounded(pan-Offset(0f,100f),zoom);true}
                )
            })
        }
        Text("Pinch to zoom and drag to inspect the receipt.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton({setZoom(zoom*2)},enabled=zoom<8f) {Text("Zoom in")}
            TextButton({setZoom(zoom/2)},enabled=zoom>1f) {Text("Zoom out")}
            TextButton({zoom=1f;pan=Offset.Zero}) {Text("Reset zoom")}
        }
    }
}
