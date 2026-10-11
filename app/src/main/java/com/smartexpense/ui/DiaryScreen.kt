package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun DiaryScreen(s:Snapshot,busy:Boolean,onSave:(String,String,()->Unit)->Unit,onAdd:(LocalDate,Boolean)->Unit,onDetails:(String)->Unit,initialDate:String=LocalDate.now().toString()) {
    var dateText by rememberSaveable {mutableStateOf(initialDate)}
    var picker by rememberSaveable {mutableStateOf(false)}
    val date=LocalDate.parse(dateText)
    var note by rememberSaveable(dateText) {mutableStateOf(s.diary[dateText].orEmpty())}
    var saved by rememberSaveable(dateText) {mutableStateOf(false)}
    var discard by rememberSaveable {mutableStateOf(false)}
    val dirty=note!=s.diary[dateText].orEmpty()
    val day=spendingCalendar(s.records,YearMonth.from(date))
    Page("Daily diary","Your spending and a little room to reflect") {
        item {Panel {
            OutlinedButton({if(dirty) discard=true else picker=true},Modifier.fillMaxWidth()) {Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")))}
            AmountRow("Your spending this day",day.dailySpending[date]?:0)
            TextField(note,{if(it.length<=10000) {note=it;saved=false}},Modifier.fillMaxWidth(),label={Text("Daily note")},minLines=5,shape=Tokens.field,textStyle=MaterialTheme.typography.bodyLarge,
                colors=TextFieldDefaults.colors(unfocusedContainerColor=Tokens.inputSurface,focusedContainerColor=Tokens.inputSurface,unfocusedIndicatorColor=Color.Transparent,focusedIndicatorColor=Tokens.accent))
            Text("A note doesn't move money. Add expenses below to update your balances.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            EditorialPrimaryButton("Save daily note",dirty && !busy) {onSave(dateText,note) {saved=true}}
            if(saved) Text("Daily note saved",style=MaterialTheme.typography.bodySmall)
            if(s.diary[dateText].orEmpty().isNotEmpty()) TextButton({onSave(dateText,"") {note="";saved=true}},enabled=!busy) {Text("Delete saved note")}
        }}
        item {Panel {
            SectionHeading("Record spending")
            EditorialPrimaryButton("Add expense for this day",s.accounts.any {!it.archived} && !busy) {onAdd(date,false)}
            OutlinedButton({onAdd(date,true)},Modifier.fillMaxWidth(),enabled=s.accounts.any {!it.archived} && !busy) {Text("Add a shared bill for this day")}
            if(s.accounts.none {!it.archived}) Text("Set up Wallet & GPay from Home first.",color=Tokens.muted)
        }}
        val entries=day.days[date].orEmpty().sortedByDescending {it.date}
        if(entries.isEmpty()) item {EmptyLedger("No expenses yet","Your saved entries for this day will appear here.")}
        entries.forEach {record->item(key=record.id) {TransactionRow(record,s,showPersonalAmount=true) {onDetails(record.id)}}}
    }
    if(discard) EditorialBottomSheet("Unsaved note",{discard=false}) {
        Text("Save this note before choosing another date, or discard your changes.")
        EditorialPrimaryButton("Save and choose date",!busy) {onSave(dateText,note) {discard=false;picker=true}}
        OutlinedButton({note=s.diary[dateText].orEmpty();discard=false;picker=true},Modifier.fillMaxWidth()) {Text("Discard changes")}
        TextButton({discard=false}) {Text("Keep editing")}
    }
    if(picker) EditorialDatePicker("Diary date",date,{picker=false}) {dateText=it.toString();picker=false}
}
