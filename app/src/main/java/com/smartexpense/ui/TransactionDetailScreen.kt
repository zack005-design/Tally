package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun DetailScreen(id:String,s:Snapshot,model:AppViewModel,onEdit:()->Unit,onRefund:(Record)->Unit) {
    var detail by remember(id) {mutableStateOf<RecordDetail?>(null)}
    var error by remember(id) {mutableStateOf<String?>(null)}
    var actions by rememberSaveable(id) {mutableStateOf(false)}
    var confirm by rememberSaveable(id) {mutableStateOf(false)}
    LaunchedEffect(id) {try {model.repository.details(id).collect {detail=it}} catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {error=e.message}}
    val record=detail?.record?:s.records.find {it.id==id}
    if(record==null) Page("Transaction detail") {item {EmptyLedger(if(error==null) "Loading transaction" else "Record unavailable",error?:"Getting the saved details…")}}
    else {
        TransactionDetailView(record,detail,s) {actions=true}
        if(actions) EditorialActionSheet("Transaction actions",buildList {
            if(!record.reversed) {
                if(record.kind !in listOf(Kind.REPAYMENT,Kind.ADJUSTMENT,Kind.OFFSET)) {
                    add(EditorialAction("Edit with revision") {model.edit(id,onReady=onEdit)})
                    add(EditorialAction("Duplicate as new draft") {model.edit(id,duplicate=true,onReady=onEdit)})
                }
                if(record.kind==Kind.EXPENSE && detail?.shares?.keys==setOf("self")) add(EditorialAction("Record refund") {onRefund(record)})
                if(!record.id.startsWith("gpay-reset")) add(EditorialAction("Reverse transaction") {confirm=true})
            }
        },{actions=false})
        if(confirm) EditorialConfirmSheet("Reverse this transaction?","The original record stays in history. Its account movement, spending and debts will be undone. Linked repayments or refunds must be reversed first.","Reverse",{confirm=false}) {confirm=false;model.action("Transaction reversed") {model.repository.reverse(id)}}
    }
}

@Composable fun TransactionDetailView(record:Record,detail:RecordDetail?,snapshot:Snapshot,onActions:()->Unit) {
    fun name(id:String)=snapshot.people.find {it.id==id}?.let {personLabel(it,snapshot.people)}?:"Participant"
    val date=Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).toLocalDateTime()
    Page("Transaction detail","Saved locally · ${record.kind.name.lowercase().replaceFirstChar(Char::titlecase)}") {
        item {
            Text(record.description,style=MaterialTheme.typography.headlineMedium)
            Text(editorialMoney(record.amount),style=MaterialTheme.typography.displaySmall)
            Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy · HH:mm")),style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
        }
        item {Panel {
            snapshot.categories.find {it.id==record.categoryId}?.let {Text("Category · ${it.name}",style=MaterialTheme.typography.bodyLarge)}
            snapshot.groups.find {it.id==record.groupId}?.let {Text("Group · ${it.name}")}
            if(record.kind==Kind.EXPENSE) AmountRow("Your spending share",record.personalAmount)
            if(record.reversed) Text("Reversed · excluded from balances and spending",color=Tokens.muted)
            else Text("Recorded",style=MaterialTheme.typography.labelMedium)
            if(record.notes.isNotBlank()) {HorizontalDivider(color=Tokens.border);Text("Notes",style=MaterialTheme.typography.titleSmall);Text(record.notes)}
        }}
        if(!detail?.entries.isNullOrEmpty()) item {Panel {
            SectionHeading("Account movements")
            detail!!.entries.forEach {AmountRow(snapshot.accounts.find {a->a.id==it.accountId}?.name?:"Account",it.amount)}
        }}
        if(!detail?.shares.isNullOrEmpty()) item {Panel {
            SectionHeading("People and contributions")
            detail!!.shares.forEach {(id,share)->Text(name(id),style=MaterialTheme.typography.titleSmall);AmountRow("Share",share);AmountRow("Paid",detail.contributions[id]?:0);HorizontalDivider(color=Tokens.border)}
        }}
        val obligations=snapshot.debts.filter {it.transactionId==record.id}
        if(obligations.isNotEmpty()) item {Panel {SectionHeading("Outstanding balances");obligations.forEach {AmountRow("${name(it.from)} → ${name(it.to)}",it.outstanding)}}}
        if(!detail?.settlements.isNullOrEmpty()) item {Panel {SectionHeading(if(record.kind==Kind.OFFSET) "Debt offsets" else "Settlements");detail!!.settlements.forEach {AmountRow("${name(it.from)} ${if(record.kind==Kind.OFFSET) "↔" else "→"} ${name(it.to)}",it.amount)};if(record.kind==Kind.OFFSET) Text("No cash movement",color=Tokens.muted)}}
        if(!detail?.allocations.isNullOrEmpty()) item {Panel {SectionHeading("Payment allocation");detail!!.allocations.forEach {(debt,amount)->AmountRow("Original obligation ${debt.take(8)}",amount)}}}
        if(!record.reversed) item {EditorialPrimaryButton("Transaction actions ⋯",onClick=onActions)}
    }
}
