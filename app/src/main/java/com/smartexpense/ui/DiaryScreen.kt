package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun DiaryScreen(s:Snapshot,busy:Boolean,onSave:(String,String,()->Unit)->Unit,onAdd:(LocalDate,Boolean)->Unit,onDetails:(String)->Unit,initialDate:String=LocalDate.now().toString()) {
    var dateText by rememberSaveable {mutableStateOf(initialDate)}
    var picker by rememberSaveable {mutableStateOf(false)}
    val date=LocalDate.parse(dateText)
    var note by rememberSaveable(dateText) {mutableStateOf(s.diary[dateText].orEmpty())}
    var saved by rememberSaveable(dateText) {mutableStateOf(false)}
    var pendingExit by remember {mutableStateOf<(() -> Unit)?>(null)}
    val dirty=note!=s.diary[dateText].orEmpty()
    val navigationGuard=LocalNavigationExitGuard.current
    val back=LocalEditorialBack.current
    val owner=remember {Any()}
    fun requestExit(exit:()->Unit) {if(dirty) pendingExit=exit else exit()}
    SideEffect {navigationGuard?.install(owner,::requestExit)}
    DisposableEffect(navigationGuard) {onDispose {navigationGuard?.clear(owner)}}
    BackHandler(dirty && back!=null) {requestExit {back?.invoke()}}
    val day=spendingCalendar(s.records,YearMonth.from(date))
    Page("Daily diary",onBack=back?.let {{requestExit(it)}},actions={if(back!=null) TextButton({requestExit(back)},enabled=!busy) {Text("Done")}}) {
        item {Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton({requestExit {picker=true}},contentPadding=PaddingValues(0.dp)) {Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),color=Tokens.text,style=MaterialTheme.typography.titleMedium)}
            Text("You spent ${referenceMoney(day.dailySpending[date]?:0)}",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
        }}
        item {GroupedCard {
            TextField(note,{if(it.length<=10000) {note=it;saved=false}},Modifier.fillMaxWidth().heightIn(min=360.dp),label={Text("Daily note")},minLines=12,shape=Tokens.field,textStyle=MaterialTheme.typography.bodyLarge,
                colors=TextFieldDefaults.colors(unfocusedContainerColor=Tokens.paper,focusedContainerColor=Tokens.paper,unfocusedIndicatorColor=Color.Transparent,focusedIndicatorColor=Color.Transparent))
        }}
        item {ReferenceInfo("Notes do not change balances. They are just for your reference.")}
        item {Column {
            EditorialPrimaryButton("Save daily note",dirty && !busy) {onSave(dateText,note) {saved=true}}
            if(saved) Text("Daily note saved",style=MaterialTheme.typography.bodySmall)
            if(s.diary[dateText].orEmpty().isNotEmpty()) TextButton({onSave(dateText,"") {note="";saved=true}},enabled=!busy) {Text("Delete saved note")}
        }}
        item {
            EditorialPrimaryButton("Add expense for this day",s.accounts.any {!it.archived} && !busy) {requestExit {onAdd(date,false)}}
        }
        item {
            OutlinedButton({requestExit {onAdd(date,true)}},Modifier.fillMaxWidth(),enabled=s.accounts.any {!it.archived} && !busy) {Text("Add a shared bill for this day")}
        }
        if(s.accounts.none {!it.archived}) item {Text("Set up Wallet & GPay from Home first.",color=Tokens.muted)}
        val entries=day.days[date].orEmpty().sortedByDescending {it.date}
        if(entries.isEmpty()) item {EmptyLedger("No expenses yet","Your saved entries for this day will appear here.")}
        entries.forEach {record->item(key=record.id) {TransactionRow(record,s,showPersonalAmount=true) {requestExit {onDetails(record.id)}}}}
    }
    if(pendingExit!=null) EditorialBottomSheet("Unsaved note",{if(!busy) pendingExit=null}) {
        Text("Save this note before continuing, or discard your changes.")
        EditorialPrimaryButton("Save and continue",!busy) {val exit=pendingExit;onSave(dateText,note) {pendingExit=null;exit?.invoke()}}
        OutlinedButton({val exit=pendingExit;note=s.diary[dateText].orEmpty();pendingExit=null;exit?.invoke()},Modifier.fillMaxWidth(),enabled=!busy) {Text("Discard changes")}
        TextButton({pendingExit=null},enabled=!busy) {Text("Keep editing")}
    }
    if(picker) EditorialDatePicker("Diary date",date,{picker=false}) {dateText=it.toString();picker=false}
}
