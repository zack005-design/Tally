package com.smartexpense.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.smartexpense.data.*
import com.smartexpense.domain.*
import com.smartexpense.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.*
import java.io.File

// Receipt bytes remain in memory across rotation, never in the saved-state Bundle.
internal class ReceiptReviewModel(initialBill:ScannedBill?):ViewModel() {
    val bill=mutableStateOf(initialBill)
    val text=mutableStateOf(initialBill?.text.orEmpty())
    val editingText=mutableStateOf(false)
    val correctedItems=mutableStateOf<List<BillItem>?>(null)
    val itemEditor=mutableStateOf(false)
    val itemIndex=mutableIntStateOf(-1)
    val itemName=mutableStateOf("")
    val itemAmount=mutableStateOf("")
    val textDraft=mutableStateOf("")
    val selectedCategory=mutableStateOf<String?>(null)
    val choosingCategory=mutableStateOf(false)
    val scanning=mutableStateOf(initialBill!=null)
    val error=mutableStateOf<String?>(null)
    val duplicate=mutableStateOf<String?>(null)
    val cameraPath=mutableStateOf<String?>(null)
    private var initialCheckStarted=false
    fun checkInitial(load:suspend (ByteArray)->String?) {
        val initial=bill.value?:return
        if(initialCheckStarted) return
        initialCheckStarted=true
        viewModelScope.launch {
            try {duplicate.value=load(initial.bytes)}
            catch(cancelled:CancellationException) {throw cancelled}
            catch(failure:Exception) {error.value=failure.message?:"Could not check this receipt";bill.value=null}
            finally {scanning.value=false}
        }
    }
}

@Composable fun BillImportScreen(scanner:BillScanner,repository:FinanceRepository,s:Snapshot,onPrepared:(ScannedBill,BillSuggestion,String?)->Unit,onDetails:(String)->Unit,initialBill:ScannedBill?=null) {
    val context=LocalContext.current
    val review:ReceiptReviewModel=viewModel {ReceiptReviewModel(initialBill)}
    val scope=review.viewModelScope
    var bill by review.bill
    var text by review.text
    var editingText by review.editingText
    var correctedItems by review.correctedItems
    var itemEditor by review.itemEditor
    var itemIndex by review.itemIndex
    var itemName by review.itemName
    var itemAmount by review.itemAmount
    var textDraft by review.textDraft
    var selectedCategory by review.selectedCategory
    var choosingCategory by review.choosingCategory
    var scanning by review.scanning
    var error by review.error
    var duplicate by review.duplicate
    var cameraPath by review.cameraPath
    fun suggestion():BillSuggestion=BillRecognition.parse(text).let {parsed->
        parsed.copy(items=correctedItems?:parsed.items,warnings=(parsed.warnings+bill?.recognitionWarnings.orEmpty()).distinct())
    }
    LaunchedEffect(review) {review.checkInitial {repository.savedReceiptId(it)}}
    fun import(uri:Uri,cameraFile:File?=null) {
        scanning=true;error=null;duplicate=null;bill=null;correctedItems=null
        scope.launch {
            try {val read=scanner.read(uri);duplicate=repository.savedReceiptId(read.bytes);bill=read;text=read.text}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {error=e.message?:"Could not read the bill. Try a sharper image or enter it manually."}
            finally {scanning=false;cameraFile?.delete();if(cameraFile!=null) cameraPath=null}
        }
    }
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->uri?.let {import(it)}}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {success->
        cameraPath?.let {path->val file=File(path);if(success) import(FileProvider.getUriForFile(context,"${context.packageName}.bills",file),file) else {file.delete();cameraPath=null}}
    }
    Scaffold(containerColor=Tokens.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        bill?.let {read->Box(Modifier.fillMaxWidth().padding(horizontal=Tokens.pageInset,vertical=12.dp)) {
            val suggestion=suggestion()
            val category=if(selectedCategory!=null) selectedCategory?.ifEmpty {null} else s.categoryRules.firstOrNull {it.merchant.equals(suggestion.merchant,true)}?.categoryId
            EditorialPrimaryButton("Continue to split",duplicate==null && !scanning) {onPrepared(read.copy(text=text),suggestion,category)}
        }}
    }) {padding->Box(Modifier.fillMaxSize().padding(padding)) {
    Page(if(bill==null) "Import receipt" else "Review receipt",gap=12.dp) {
        if(bill==null) item {Column(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
            ReferenceIcon(R.drawable.ic_document_outline,ReferenceTone.Blue,88.dp)
            Text("Bring your bill",style=MaterialTheme.typography.headlineMedium)
            Text("Extract items and amounts from your receipt. Everything is processed on this device.",color=Tokens.muted,textAlign=TextAlign.Center)
        }}
        if(bill==null) item {GroupedCard {
            GroupedRow("Take photo",R.drawable.ic_camera_outline,supporting="Use your camera",onClick={
                if(!scanning) {
                try {
                    val directory=File(context.cacheDir,"bill-camera").apply {mkdirs()}
                    directory.listFiles()?.filter {it.lastModified()<System.currentTimeMillis()-86400000}?.forEach {it.delete()}
                    val file=File.createTempFile("capture-",".jpg",directory);cameraPath=file.absolutePath
                    camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.bills",file))
                } catch(e:Exception) {error="Camera unavailable. Choose a saved bill image instead."}
                }
            })
            GroupedDivider()
            GroupedRow("Choose image",R.drawable.ic_photo_outline,supporting="JPEG, PNG or WebP",tone=ReferenceTone.Lavender,onClick={if(!scanning) filePicker.launch(arrayOf("image/jpeg","image/png","image/webp"))})
            GroupedDivider()
            GroupedRow("Choose PDF",R.drawable.ic_document_outline,supporting="Up to 5 pages · 6 MB",tone=ReferenceTone.Peach,onClick={if(!scanning) filePicker.launch(arrayOf("application/pdf"))})
        }}
        if(bill==null) item {ReferenceInfo("Processed on this device · Up to 5 pages · 6 MB. Latin-script recognition; check every suggestion.")}
        if(scanning || error!=null) item {Panel {
            if(scanning) {LinearProgressIndicator(Modifier.fillMaxWidth());Text("Reading the bill locally…")}
            error?.let {Text(it,color=Tokens.muted)}
        }}
        bill?.let {read->
            item {BillPreview(scanner,read,showHeading=false)}
            item {ReferenceInfo("Check scanned values. Please verify the items and amounts.",warning=true)}
            read.recognitionWarnings.forEach {warning->item {ReferenceInfo(warning,warning=true)}}
            val recognized=suggestion()
            item {Text("Items",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)}
            item {GroupedCard {
                recognized.items.forEachIndexed {index,row->
                    if(index>0) GroupedDivider(14.dp)
                    Surface(onClick={itemIndex=index;itemName=row.name;itemAmount=Money.input(row.amount);itemEditor=true},color=Tokens.paper) {
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(row.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                            Text(referenceMoney(row.amount),style=MaterialTheme.typography.bodyLarge)
                            Text("›",color=Tokens.muted)
                        }
                    }
                }
                GroupedDivider(14.dp)
                TextButton({itemIndex=-1;itemName="";itemAmount="";itemEditor=true},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Add item")}
                recognized.total?.let {GroupedDivider(14.dp);ReferenceValueRow("Total",referenceMoney(it))}
            }}
            if(correctedItems!=null && recognized.total!=null && runCatching {Finance.total(recognized.items.map {it.amount})}.getOrNull()!=recognized.total) item {ReferenceInfo("Items differ from the printed total. Check the total, taxes and discounts before saving.",warning=true)}
            duplicate?.let {id->item {Panel {Text("This bill is already attached to a transaction. Its amount will not be recorded again.");EditorialPrimaryButton("Open saved transaction") {onDetails(id)}}}}
            item {ReferenceAction("Edit scanned text",R.drawable.ic_compose) {textDraft=text;editingText=true}}
            val suggestion=BillRecognition.parse(text)
            val category=if(selectedCategory!=null) selectedCategory?.ifEmpty {null} else s.categoryRules.firstOrNull {it.merchant.equals(suggestion.merchant,true)}?.categoryId
            item {GroupedCard {GroupedRow("Category",R.drawable.ic_food,value=s.categories.find {it.id==category}?.name?:"Uncategorised",tone=ReferenceTone.Peach) {choosingCategory=true}}}
            suggestion.warnings.forEach {warning->item {Text(warning,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}}
            item {TextButton({bill=null;text="";duplicate=null;selectedCategory=null;correctedItems=null}) {Text("Discard imported bill")}}

        }
    }
    }}
    if(itemEditor) EditorialBottomSheet(if(itemIndex<0) "Add receipt item" else "Edit receipt item",{itemEditor=false}) {
        Field("Item name",itemName,{itemName=it})
        Field("Item amount (₹)",itemAmount,{itemAmount=it},true)
        val value=runCatching {Money.parse(itemAmount).also {require(it>0)}}.getOrNull()
        EditorialPrimaryButton("Save item",itemName.isNotBlank() && value!=null) {
            val items=suggestion().items.toMutableList()
            val corrected=BillItem(itemName.trim(),value!!)
            if(itemIndex in items.indices) items[itemIndex]=corrected else items.add(corrected)
            correctedItems=items.toList();itemEditor=false
        }
    }
    if(editingText) EditorialBottomSheet("Edit scanned text",{editingText=false}) {
        Text("Check each item and amount against the original receipt. Changing this text rebuilds the item list.",color=Tokens.muted)
        TextField(textDraft,{textDraft=it.take(10000)},Modifier.fillMaxWidth(),label={Text("Edit recognition")},minLines=5,maxLines=12)
        EditorialPrimaryButton("Done") {text=textDraft;correctedItems=null;editingText=false}
    }
    if(choosingCategory) EditorialBottomSheet("Category",{choosingCategory=false}) {
        Choice("Category",selectedCategory?:"",listOf("" to "Uncategorised")+s.categories.filterNot {it.archived}.map {it.id to it.name}) {selectedCategory=it;choosingCategory=false}
    }

}

@Composable fun BillPreview(scanner:BillScanner,bill:ScannedBill,showHeading:Boolean=true) {
    var page by remember {mutableIntStateOf(0)}
    var pages by remember {mutableIntStateOf(1)}
    var bitmap by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(bill,page) {error=null;bitmap=null;try {pages=scanner.pageCount(bill.bytes,bill.mime);bitmap=scanner.preview(bill.bytes,bill.mime,page)} catch(e:CancellationException) {throw e} catch(e:Exception) {error="This page is unavailable"}}
    Panel {
        if(showHeading) SectionHeading("Original bill${if(bill.mime=="application/pdf") " · page ${page+1}" else ""}")
        bitmap?.let {Image(it.asImageBitmap(),"Original bill for checking recognition",Modifier.fillMaxWidth().heightIn(max=240.dp))}
        error?.let {Text(it,color=Tokens.muted)}
        if(bill.mime=="application/pdf") Row {
            TextButton({page--},enabled=page>0) {Text("Previous page")}
            TextButton({page++},enabled=page<pages-1 && error==null) {Text("Next page")}
        }
    }
}

@Composable fun CategoryRulesScreen(s:Snapshot,busy:Boolean,onSave:(CategoryRule)->Unit,onDelete:(String)->Unit) {
    var editor by rememberSaveable {mutableStateOf(false)}
    var editingId by rememberSaveable {mutableStateOf<String?>(null)}
    var merchant by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf(s.categories.firstOrNull {!it.archived}?.id.orEmpty())}
    fun newRule() {
        editingId=null;merchant="";category=s.categories.firstOrNull {!it.archived}?.id.orEmpty();editor=true
    }
    Page("Merchant rules",actions={TextButton({newRule()},enabled=!busy) {Text("+",color=Tokens.accent,style=MaterialTheme.typography.headlineMedium)}}) {
        if(s.categoryRules.isEmpty()) item {EmptyLedger("Organise merchant categories","Save a merchant rule to suggest a category for future entries.")}
        else item {GroupedCard {s.categoryRules.forEachIndexed {index,rule->
            if(index>0) GroupedDivider()
            val name=s.categories.find {it.id==rule.categoryId}?.name?:"Category"
            val icon=when(name.lowercase()) {"travel","transport"->R.drawable.ic_transit;"groceries","shopping"->R.drawable.ic_basket;"food","food & drinks"->R.drawable.ic_food;else->R.drawable.ic_document_outline}
            GroupedRow(rule.merchant,if(icon==R.drawable.ic_food) R.drawable.ic_shop_outline else icon,supporting=name,tone=if(index%2==0) ReferenceTone.Lavender else ReferenceTone.Peach) {
                editingId=rule.id;merchant=rule.merchant;category=rule.categoryId;editor=true
            }
        }}}
        item {ReferenceAction("New merchant rule",R.drawable.ic_add,enabled=!busy) {newRule()}}
    }
    if(editor) EditorialBottomSheet(if(editingId==null) "Add merchant rule" else "Edit merchant rule",{if(!busy) editor=false}) {
        val selectedCategory=s.categories.find {it.id==category}?.name.orEmpty().lowercase()
        val selectedIcon=when(selectedCategory) {"travel","transport"->R.drawable.ic_transit;"groceries","shopping"->R.drawable.ic_basket;"food","food & drinks"->R.drawable.ic_food;else->R.drawable.ic_document_outline}
        Field("Merchant name as printed",merchant,{merchant=it})
        Text("Matches the full merchant name, ignoring case.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        Text("Category",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
        GroupedCard {Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ReferenceIcon(selectedIcon,if(selectedCategory in listOf("food","food & drinks","shopping")) ReferenceTone.Pink else ReferenceTone.Blue)
            Column(Modifier.weight(1f)) {Choice("Suggested category",category,s.categories.filterNot {it.archived}.map {it.id to it.name}) {category=it}}
        }}
        EditorialPrimaryButton("Save merchant rule",merchant.trim().isNotEmpty() && merchant.trim().length<=100 && category.isNotBlank() && !busy) {
            val existing=s.categoryRules.find {it.merchant.equals(merchant.trim(),true)}
            val id=editingId?:existing?.id?:java.util.UUID.randomUUID().toString()
            onSave(CategoryRule(id,merchant,category));editingId=id
        }
        Text("Suggests categories for future entries. You can change the category before saving an expense.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        editingId?.let {id->TextButton({onDelete("category:$id");editor=false},enabled=!busy) {Text("Remove rule",color=Tokens.negative)}}
    }
}
