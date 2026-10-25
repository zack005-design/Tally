package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.smartexpense.R

@Composable fun DetailScreen(id:String,s:Snapshot,model:AppViewModel,onEdit:()->Unit,onRefund:(Record)->Unit) {
    val context=LocalContext.current
    var sharing by rememberSaveable(id) {mutableStateOf(false)}
    var shareError by remember(id) {mutableStateOf<String?>(null)}
    var detail by remember(id) {mutableStateOf<RecordDetail?>(null)}
    var error by remember(id) {mutableStateOf<String?>(null)}
    var actions by rememberSaveable(id) {mutableStateOf(false)}
    var confirm by rememberSaveable(id) {mutableStateOf(false)}
    var receipt by remember(id) {mutableStateOf<com.smartexpense.data.ScannedBill?>(null)}
    var showReceipt by rememberSaveable(id) {mutableStateOf(false)}
    var removeReceipt by rememberSaveable(id) {mutableStateOf(false)}
    LaunchedEffect(id) {model.repository.receiptChanges(id).collect {receipt=it}}
    LaunchedEffect(id) {try {model.repository.details(id).collect {detail=it}} catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {error=e.message}}
    val record=detail?.record?:s.records.find {it.id==id}
    if(record==null) Page("Transaction detail") {item {EmptyLedger(if(error==null) "Loading transaction" else "Record unavailable",error?:"Getting the saved details…")}}
    else {
        val sharedRefund=record.kind==Kind.REFUND && (detail?.shares?.size?:0)>1
        val refundRecord=record.copy(personIds=detail?.shares?.keys?.toList()?:record.personIds)
        val canShare=!record.reversed && record.kind==Kind.EXPENSE && (detail?.shares?.size?:0)>1
        TransactionDetailView(record,detail,s,receipt!=null,{showReceipt=true},onShare=if(canShare) ({sharing=true}) else null,
            onEdit=if(!record.reversed && !sharedRefund && record.kind !in listOf(Kind.REPAYMENT,Kind.ADJUSTMENT,Kind.OFFSET)) ({model.edit(id,onReady=onEdit)}) else null,
            onDuplicate=if(!record.reversed && !sharedRefund && record.kind !in listOf(Kind.REPAYMENT,Kind.ADJUSTMENT,Kind.OFFSET)) ({model.edit(id,duplicate=true,onReady=onEdit)}) else null,
            onRefund=if(!record.reversed && record.kind==Kind.EXPENSE && detail!=null) ({onRefund(refundRecord)}) else null) {actions=true}
        if(sharing && canShare) EditorialBottomSheet("Share bill summary",{sharing=false;shareError=null}) {
            val summary=billSummary(requireNotNull(detail),s.people,s.debts)
            Text(summary)
            Text("Includes this bill's payments, shares and remaining debts. Private notes and account balances are excluded.",color=Tokens.muted)
            shareError?.let {Text(it,color=Tokens.negative)}
            EditorialPrimaryButton("Share summary") {
                try {
                    val send=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,summary)
                    context.startActivity(Intent.createChooser(send,"Share bill summary"));sharing=false
                } catch(_:android.content.ActivityNotFoundException) {shareError="No sharing app is available on this device."}
            }
        }
        if(showReceipt && receipt!=null) EditorialBottomSheet("Saved printed bill",{showReceipt=false}) {
            BillPreview(model.billScanner,receipt!!)
            Text(receipt!!.text,color=Tokens.muted)
            TextButton({removeReceipt=true}) {Text("Remove attachment · keep transaction")}
        }
        if(removeReceipt) EditorialConfirmSheet("Remove this bill attachment?","The saved transaction and balances stay in your ledger. Keep a local backup if you want to retain the original bill.","Remove",{removeReceipt=false}) {removeReceipt=false;showReceipt=false;model.action("Bill attachment removed") {model.repository.removeReceipt(id)}}
        if(actions) EditorialActionSheet("Transaction actions",buildList {
            if(!record.reversed) {
                if(!sharedRefund && record.kind !in listOf(Kind.REPAYMENT,Kind.ADJUSTMENT,Kind.OFFSET)) {
                    add(EditorialAction("Edit with revision") {model.edit(id,onReady=onEdit)})
                    add(EditorialAction("Duplicate as new draft") {model.edit(id,duplicate=true,onReady=onEdit)})
                }
                if(record.kind==Kind.EXPENSE && detail!=null) add(EditorialAction("Record refund") {onRefund(refundRecord)})
                if(!record.id.startsWith("gpay-reset")) add(EditorialAction("Reverse transaction") {confirm=true})
            }
        },{actions=false})
        if(confirm) EditorialConfirmSheet("Reverse this transaction?","The original record stays in history. Its account movement, spending and debts will be undone. Linked repayments or refunds must be reversed first.","Reverse",{confirm=false}) {confirm=false;model.action("Transaction reversed") {model.repository.reverse(id)}}
    }
}

@Composable fun TransactionDetailView(record:Record,detail:RecordDetail?,snapshot:Snapshot,hasReceipt:Boolean=false,onReceipt:()->Unit={},onShare:(()->Unit)?=null,onEdit:(()->Unit)?=null,onDuplicate:(()->Unit)?=null,onRefund:(()->Unit)?=null,onActions:()->Unit) {
    fun name(id:String)=if(id=="self") "You" else snapshot.people.find {it.id==id}?.let {personLabel(it,snapshot.people)}?:"Participant"
    var expanded by rememberSaveable(record.id) {mutableStateOf(false)}
    val date=Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).toLocalDateTime()
    Page("Transaction",actions={if(!record.reversed) IconButton(onActions,Modifier.semantics {contentDescription="Transaction actions"}) {Text("⋮",color=Tokens.accent,style=MaterialTheme.typography.titleLarge)}}) {
        item {Column(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            ReferenceIcon(if(record.kind==Kind.EXPENSE) R.drawable.ic_food else R.drawable.ic_account,if(record.kind==Kind.EXPENSE) ReferenceTone.Peach else ReferenceTone.Blue,68.dp)
            Text(record.description,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics {heading()})
            Text(referenceMoney(record.amount),style=MaterialTheme.typography.headlineLarge)
            Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")),color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            if(record.reversed) Text("Reversed · excluded from balances and spending",color=Tokens.muted)
        }}
        item {GroupedCard {
            ReferenceDetailRow("Date",R.drawable.ic_calendar,date.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")))
            GroupedDivider(14.dp)
            ReferenceDetailRow("Account",R.drawable.ic_wallet_outline,record.accountIds.mapNotNull {id->snapshot.accounts.find {it.id==id}?.name}.joinToString().ifBlank {"No cash movement"})
            snapshot.categories.find {it.id==record.categoryId}?.let {GroupedDivider(14.dp);ReferenceDetailRow("Category",R.drawable.ic_food,it.name)}
            GroupedDivider(14.dp);ReferenceDetailRow("Description",R.drawable.ic_document_outline,record.description)
        }}
        if(hasReceipt) item {Surface(color=Tokens.secondarySurface,shape=Tokens.card) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Attachment",style=MaterialTheme.typography.labelMedium,color=Tokens.muted)
            GroupedCard {GroupedRow("Original printed bill",R.drawable.ic_document_outline,supporting="Saved receipt · on this device",onClick=onReceipt)}
        }}}
        if(record.notes.isNotBlank()) item {GroupedCard {Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_document_outline),null,Modifier.size(20.dp),tint=Tokens.muted)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text("Private notes",style=MaterialTheme.typography.bodySmall,color=Tokens.muted);Text(record.notes,style=MaterialTheme.typography.bodyMedium)}
        }}}
        if((detail?.shares?.size?:0)>1) item {Panel {
            SectionHeading(if(record.kind==Kind.REFUND) "Refund shares and cash received" else "Payments and shares")
            detail!!.shares.forEach {(id,share)->
                Text(name(id),style=MaterialTheme.typography.titleMedium)
                AdaptivePair(first={AmountRow(if(record.kind==Kind.REFUND) "Received" else "Paid",detail.contributions[id]?:0)},second={AmountRow(if(record.kind==Kind.REFUND) "Refunded share" else "Share",share)})
                HorizontalDivider(color=Tokens.border.copy(alpha=.5f))
            }
            if(record.kind==Kind.EXPENSE) AmountRow("Your spending share",record.personalAmount)
            if(record.kind==Kind.REFUND && (detail?.shares?.size?:0)>1) {AmountRow("Your refunded share",record.personalAmount);AmountRow("Cash received by you",Finance.total(detail?.entries.orEmpty().map {it.amount}));Text("Correct a shared refund by reversing it and recording a new distribution.")}
        }}
        if(onShare!=null) item {OutlinedButton(onShare,Modifier.fillMaxWidth()) {Text("Share bill summary")}}
        val outstanding=snapshot.debts.filter {it.transactionId==record.id && it.outstanding>0}
        if(!record.reversed && outstanding.isNotEmpty()) outstanding.forEach {debt->item(key=debt.id) {SettlementCard("${if(debt.from=="self") "You owe" else "${name(debt.from)} owes"} ${name(debt.to)}",debt.outstanding,"Remaining on this bill")}}
        if(!record.reversed && (onEdit!=null || onDuplicate!=null || onRefund!=null)) item {
            val options=listOfNotNull(onEdit?.let {Triple("Edit",R.drawable.ic_compose,it)},onDuplicate?.let {Triple("Duplicate",R.drawable.ic_duplicate_outline,it)},onRefund?.let {Triple("Record refund",R.drawable.ic_refund_outline,it)})
            if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {options.forEach {option->TransactionAction(option.first,option.second,Modifier.fillMaxWidth(),option.third)}}
            else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {options.forEach {option->TransactionAction(option.first,option.second,Modifier.weight(1f),option.third)}}
        }
        item {TextButton({expanded=!expanded},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(if(expanded) "Hide transaction details" else "Transaction details")}}
        if(expanded) {
        item {StudioCard {
            snapshot.categories.find {it.id==record.categoryId}?.let {Text("Category · ${it.name}",style=MaterialTheme.typography.bodyLarge)}
            snapshot.groups.find {it.id==record.groupId}?.let {Text("Group · ${it.name}")}
            if(record.notes.isNotBlank()) {HorizontalDivider(color=Tokens.border);Text("Notes",style=MaterialTheme.typography.titleSmall);Text(record.notes)}
        }}
        if(!detail?.entries.isNullOrEmpty()) item {Panel {
            SectionHeading("Account movements")
            detail!!.entries.forEach {AmountRow(snapshot.accounts.find {a->a.id==it.accountId}?.name?:"Account",it.amount)}
        }}
        val obligations=snapshot.debts.filter {it.transactionId==record.id}
        if(obligations.isNotEmpty()) item {Panel {SectionHeading("Outstanding balances");obligations.forEach {AmountRow("${name(it.from)} → ${name(it.to)}",it.outstanding)}}}
        if(!detail?.settlements.isNullOrEmpty()) item {Panel {SectionHeading(if(record.kind==Kind.OFFSET) "Debt offsets" else "Settlements");detail!!.settlements.forEach {AmountRow("${name(it.from)} ${if(record.kind==Kind.OFFSET) "↔" else "→"} ${name(it.to)}",it.amount)};if(record.kind==Kind.OFFSET) Text("No cash movement",color=Tokens.muted)}}
        if(!detail?.allocations.isNullOrEmpty()) item {Panel {SectionHeading("Payment allocation");detail!!.allocations.forEach {(debt,amount)->AmountRow("Original obligation ${debt.take(8)}",amount)}}}
        }
        if(!record.reversed && onEdit==null && onDuplicate==null && onRefund==null) item {EditorialPrimaryButton("Transaction actions ⋯",onClick=onActions)}
    }
}

@Composable private fun TransactionAction(label:String,icon:Int,modifier:Modifier,onClick:()->Unit) {
    OutlinedButton(onClick,modifier.heightIn(min=76.dp),shape=Tokens.card,contentPadding=PaddingValues(10.dp),border=androidx.compose.foundation.BorderStroke(.7.dp,Tokens.border)) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(icon),null,Modifier.size(24.dp))
            Text(label,style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
        }
    }
}
