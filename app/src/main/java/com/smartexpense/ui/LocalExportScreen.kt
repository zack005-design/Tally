package com.smartexpense.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.data.ReportExport
import com.smartexpense.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.time.*

@Composable fun LocalExportScreen(model:AppViewModel) {
    val context=LocalContext.current;val state by model.state.collectAsStateWithLifecycle()
    var month by rememberSaveable {mutableStateOf(YearMonth.now().toString())}
    var format by rememberSaveable {mutableStateOf("CSV")}
    var reportType by rememberSaveable {mutableStateOf("Statement")}
    var requestedType by rememberSaveable {mutableStateOf(reportType)}
    var choosing by rememberSaveable {mutableStateOf(false)}
    var customName by rememberSaveable {mutableStateOf("")}
    var filenameDraft by rememberSaveable {mutableStateOf("")}
    var filenameEditor by rememberSaveable {mutableStateOf(false)}
    var requestedMonth by rememberSaveable {mutableStateOf(month)}
    var preview by remember {mutableStateOf<Pair<Snapshot,MonthlyReview>?>(null)}
    var previewError by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(month,state.snapshot) {
        preview=null;previewError=null
        try {preview=model.repository.exportReport(month)}
        catch(cancelled:CancellationException) {throw cancelled}
        catch(error:Exception) {previewError=error.message?:"Could not load report preview"}
    }
    fun write(uri:android.net.Uri?,pdf:Boolean) {if(uri!=null) model.action("Monthly report exported") {
        val (snapshot,review)=model.repository.exportReport(requestedMonth)
        val bytes=ReportExport.bytes(snapshot,review,pdf,requestedType)
        withContext(Dispatchers.IO) {requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use {it.write(bytes)}}
    }}
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {write(it,false)}
    val pdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {write(it,true)}
    val selectedMonth=YearMonth.parse(month)
    val selectedPreview=preview.takeIf {it?.second?.month==month}
    val rowCount=selectedPreview?.let {(snapshot,review)->ReportCsv.rows(snapshot,review,reportType).count {it.isNotEmpty()}}
    val fileBase=customName.ifBlank {"tally-$month"}
    Scaffold(containerColor=Tokens.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={Column(Modifier.padding(horizontal=Tokens.pageInset,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        EditorialPrimaryButton("Export report",!state.busy) {requestedMonth=month;requestedType=reportType;if(format=="PDF") pdf.launch("$fileBase.pdf") else csv.launch("$fileBase.csv")}
        Text("Reports cannot restore your data.",Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,color=Tokens.muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }}) {padding->Box(Modifier.fillMaxSize().padding(padding)) {Page("Export report",gap=12.dp) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Text("Month",Modifier.weight(1f));TextButton({choosing=true}) {Text("${selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))} ⌄")}}}
        item {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text("Report type",style=MaterialTheme.typography.bodyMedium);EditorialSegmentedControl(reportType,listOf("Statement" to "Statement","Transactions" to "Transactions")) {reportType=it}}}
        item {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text("File format",style=MaterialTheme.typography.bodyMedium);EditorialSegmentedControl(format,listOf("CSV" to "CSV","PDF" to "PDF")) {format=it}}}
        item {TallyCompanionCard {reportType="Transactions";format="CSV"}}
        item {Text("Preview",style=MaterialTheme.typography.bodyMedium)}
        item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth()) {Text("Tally",Modifier.weight(1f),color=Tokens.accent,style=MaterialTheme.typography.titleLarge);Text(reportType,style=MaterialTheme.typography.bodySmall)}
            Text(selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider(color=Tokens.border)
            selectedPreview?.let {(snapshot,review)->
                val optional=setOf("Repayments received","Repayments paid","Balance corrections","Scheduled adjustments")
                val rows=ReportCsv.rows(snapshot,review,reportType).drop(1).filterNot {it.firstOrNull() in optional && it.getOrNull(1)=="0.00"}
                rows.take(if(reportType=="Statement") 18 else 7).forEach {row->
                    if(row.isEmpty()) HorizontalDivider(color=Tokens.border)
                    else if(reportType=="Statement") Row(Modifier.fillMaxWidth().padding(vertical=3.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(row.first(),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                        Text(row.getOrNull(1).orEmpty().let {runCatching {referenceMoney(Money.parse(it))}.getOrDefault(it)},style=MaterialTheme.typography.bodySmall)
                    } else Text(row.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                }
            }?:Text(previewError?:"Loading report preview…",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        }}}
        item {GroupedCard {
            ReferenceDetailRow("File name",com.smartexpense.R.drawable.ic_document_outline,"$fileBase.${format.lowercase()}") {filenameDraft=fileBase;filenameEditor=true}
            GroupedDivider();ReferenceValueRow("Rows in file",if(selectedPreview==null) "—" else "$rowCount rows")
        }}

    }
    }}
    if(filenameEditor) EditorialBottomSheet("File name",{filenameEditor=false}) {
        Field("File name",filenameDraft,{filenameDraft=it})
        val valid=filenameDraft.isNotBlank() && filenameDraft.length<=80 && filenameDraft.none {it in "/\\:*?\"<>|\n\r"}
        Text("The selected file format supplies the extension.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
        EditorialPrimaryButton("Done",valid) {customName=filenameDraft.trim().removeSuffix(".csv").removeSuffix(".pdf");filenameEditor=false}
    }
    if(choosing) EditorialMonthPicker(YearMonth.parse(month),{choosing=false}) {month=it.toString();choosing=false}
}

@Composable fun ManualExchangeScreen(onDraft:(String,String,String,String,Long)->Unit) {
    var currency by rememberSaveable {mutableStateOf("USD")}
    var amount by rememberSaveable {mutableStateOf("")}
    var rate by rememberSaveable {mutableStateOf("")}
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var datePicker by rememberSaveable {mutableStateOf(false)}
    var currencyPicker by rememberSaveable {mutableStateOf(false)}
    val conversion=runCatching {ManualExchange.convert(amount,rate)}
    Page("Foreign bill") {
        item {Panel {
            Text("Foreign amount",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {ForeignDecimalField("Original amount",amount,{amount=it})}
                FilledTonalButton({currencyPicker=true},shape=Tokens.field) {Text(currency+" ⌄")}
            }
        }}
        item {Panel {
            Text("Exchange rate (1 $currency = ? INR)",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
            ForeignDecimalField("INR per one $currency",rate,{rate=it})
        }}
        item {GroupedCard {GroupedRow("Rate date",com.smartexpense.R.drawable.ic_calendar,value=LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM yyyy"))) {datePicker=true}}}
        item {ReferenceInfo("Enter your own exchange rate. This app does not provide live exchange rates.")}
        conversion.getOrNull()?.let {converted->item {Surface(shape=Tokens.card,color=MaterialTheme.colorScheme.tertiaryContainer) {Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Converted amount",style=MaterialTheme.typography.bodyMedium)
            Text(referenceMoney(converted),style=MaterialTheme.typography.displaySmall)
        }}}}
        item {EditorialPrimaryButton("Use in expense",conversion.getOrNull()?.let {it>0}==true && Regex("[A-Z]{3}").matches(currency) && currency!="INR") {onDraft(currency,amount.trim(),rate.trim(),date,conversion.getOrThrow())}}
    }
    if(currencyPicker) EditorialBottomSheet("Currency",{currencyPicker=false}) {
        Field("Original currency code",currency,{currency=it.uppercase().take(3)})
        EditorialPrimaryButton("Done",Regex("[A-Z]{3}").matches(currency) && currency!="INR") {currencyPicker=false}
    }
    if(datePicker) EditorialDatePicker("Exchange rate date",LocalDate.parse(date),{datePicker=false}) {date=it.toString();datePicker=false}
}

@Composable private fun ForeignDecimalField(label:String,value:String,onChange:(String)->Unit) {
    OutlinedTextField(value,onChange,Modifier.fillMaxWidth().then(Modifier.semantics {contentDescription=label}),singleLine=true,shape=Tokens.field,textStyle=MaterialTheme.typography.headlineMedium,
        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Tokens.paper,focusedContainerColor=Tokens.paper,unfocusedBorderColor=Tokens.border,focusedBorderColor=Tokens.accent))
}
