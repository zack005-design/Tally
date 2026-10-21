package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Compact single-operation forms share the existing exact-money draft and projection rules. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ReferenceRecordScreen(
    s:EntryUiState,accounts:List<Account>,categories:List<Category>,records:List<Record>,
    onChange:(EntryUiState)->Unit,onSave:()->Unit,onBack:()->Unit,walletSources:WalletSources?,
    hasBill:Boolean,onDiscardBill:()->Unit,onExchange:()->Unit
) {
    val transfer=s.kind==Kind.TRANSFER
    val refund=s.kind==Kind.REFUND
    val active=accounts.filterNot {it.archived}
    val originalExpenses=records.filter {it.kind==Kind.EXPENSE && !it.reversed && it.personIds==listOf("self")}
    val original=originalExpenses.find {it.id==s.refundOf}
    val knownOriginal=records.find {it.id==s.refundOf}
    val projections=projectedBalances(s,accounts,walletSources)
    // History may be paged; the repository still validates an original not present in this list.
    val originalUnavailable=refund && (s.refundOf==null || knownOriginal!=null && original==null)
    val valid=s.canReview(accounts,categories) && !originalUnavailable
    val title=when {transfer->"Transfer money";refund->"Record refund";else->"Record income"}
    val save=when {transfer->"Record transfer";refund->"Save refund";else->"Save income"}
    var picker by rememberSaveable(s.id) {mutableStateOf<String?>(null)}
    var editing by rememberSaveable(s.id) {mutableStateOf<String?>(null)}
    fun accountName(id:String)=active.find {it.id==id}?.name?:"Choose account"
    Scaffold(containerColor=Tokens.background,modifier=Modifier.imePadding(),contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        Box(Modifier.fillMaxWidth().padding(horizontal=Tokens.pageInset,vertical=12.dp)) {EditorialPrimaryButton(save,valid,onSave)}
    }) {padding->Box(Modifier.fillMaxSize().padding(padding)) {
        Page(if(s.replaces!=null) "Correct ${if(transfer) "transfer" else if(refund) "refund" else "income"}" else title,onBack=onBack) {
            if(hasBill) item {Panel {
                Text("Printed bill attached · check the details before saving",color=Tokens.muted)
                TextButton(onExchange) {Text("Enter a manual exchange rate")}
                TextButton(onDiscardBill) {Text("Discard bill attachment")}
            }}
            if(s.parserNotice.isNotBlank()) item {ReferenceInfo(s.parserNotice)}
            when {
                transfer->{
                    item {TransferAccountCard("From",accountName(s.accountId),active.find {it.id==s.accountId}?.balance,ReferenceTone.Peach) {picker="source"}}
                    item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {FilledTonalIconButton(onClick={onChange(s.copy(accountId=s.destinationId,destinationId=s.accountId))},modifier=Modifier.size(48.dp).semantics {contentDescription="Swap accounts"}) {Text("⇅",color=Tokens.accent,style=MaterialTheme.typography.headlineSmall)}}}
                    item {TransferAccountCard("To",accountName(s.destinationId),active.find {it.id==s.destinationId}?.balance,ReferenceTone.Lavender) {picker="destination"}}
                    item {RecordAmountField("Amount (₹)",s.amount) {onChange(s.copy(amount=it))}}
                }
                refund->{
                    item {GroupedCard {GroupedRow(original?.description?:"Original expense",R.drawable.ic_food,supporting=original?.let {referenceMoney(it.amount)+" · "+Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}?:"Choose a personal expense",tone=ReferenceTone.Peach,onClick={picker="expense"})}}
                    item {Panel {RecordAmountField("Refund amount (₹)",s.amount) {onChange(s.copy(amount=it))}}}
                }
                else->{
                    item {Column(Modifier.fillMaxWidth().padding(top=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        ReferenceIcon(R.drawable.ic_account,ReferenceTone.Blue,76.dp)
                        Text("Income amount",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                        RecordAmountField("Amount (₹)",s.amount,centered=true) {onChange(s.copy(amount=it))}
                    }}
                }
            }
            item {GroupedCard {
                if(!refund) {GroupedRow("Description",R.drawable.ic_compose,value=s.description.ifBlank {"Add description"},tone=ReferenceTone.Gray,onClick={editing="Description"});GroupedDivider()}
                if(!transfer) {GroupedRow("Destination account",R.drawable.ic_account,value=accountName(s.accountId),onClick={picker="source"});GroupedDivider()}
                EditorialDateRow(s.date) {onChange(s.copy(date=it))}
                if(transfer) {GroupedDivider();GroupedRow("Note",R.drawable.ic_activity,value=s.notes.ifBlank {"Optional"},tone=ReferenceTone.Gray,onClick={editing="Note"})}
            }}
            if(refund) item {Panel {
                Text("Reason",style=MaterialTheme.typography.labelMedium,color=Tokens.muted)
                OutlinedTextField(s.notes,{onChange(s.copy(notes=it))},Modifier.fillMaxWidth(),placeholder={Text("Reason for refund (optional)")},minLines=2,shape=Tokens.field)
                TextButton({editing="Description"}) {Text("Description · ${s.description.ifBlank {"Add description"}}")}
            }}
            if(!transfer && !refund) item {TextButton({editing="Note"},Modifier.heightIn(min=48.dp)) {Text(if(s.notes.isBlank()) "Add a note (optional)" else "Note · ${s.notes}")}}
            projections.getOrNull()?.takeIf {it.isNotEmpty()}?.let {values->item {Surface(color=Tokens.secondarySurface,shape=Tokens.card) {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(if(transfer) "After transfer" else if(refund) "Projected ${accountName(s.accountId)} balance" else "Preview after saving",style=MaterialTheme.typography.labelMedium,color=Tokens.muted)
                GroupedCard {values.forEachIndexed {index,p->
                    if(transfer) GroupedRow(p.name,R.drawable.ic_account,supporting="${referenceMoney(p.before)} → ${referenceMoney(p.after)}",value=referenceMoney(p.after),tone=if(p.accountId==s.accountId) ReferenceTone.Peach else ReferenceTone.Lavender,showChevron=false,onClick={})
                    else Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("${p.name} balance",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(referenceMoney(p.before),style=MaterialTheme.typography.headlineSmall)
                            Text("→",style=MaterialTheme.typography.headlineSmall,color=Tokens.muted)
                            Text(referenceMoney(p.after),style=MaterialTheme.typography.headlineSmall,color=if(p.after<0) Tokens.negative else Tokens.accent)
                        }
                    }
                    if(index<values.lastIndex) GroupedDivider()
                }}
                values.filter {it.after<0}.forEach {Text("This exceeds your recorded ${it.name} balance. Check the amount or update your balance.",color=Tokens.negative,style=MaterialTheme.typography.bodySmall)}
            }}}}
            if(transfer) item {ReferenceInfo("Does not count as spending. This only moves money between your accounts.")}
            if(originalUnavailable) item {ReferenceInfo("Choose an available personal expense. Shared or reversed expenses cannot receive a refund here.",warning=true)}
            if(active.isEmpty()) item {ReferenceInfo("Create an account from Overview → Accounts before saving.",warning=true)}
            if(s.amount.isNotBlank() && projections.isFailure) item {ReferenceInfo(projections.exceptionOrNull()?.message?:"Check the amount and accounts.",warning=true)}
            if(s.description.isBlank()) item {ReferenceInfo("Add a description before saving.")}
            if(s.replaces!=null) item {ReferenceInfo("This correction keeps the original record in history. The preview includes reversal of its original account movements.")}
        }
    }}
    picker?.let {kind->EditorialBottomSheet(if(kind=="expense") "Original expense" else if(kind=="destination") "Destination account" else if(transfer) "Source account" else "Destination account",{picker=null}) {
        if(kind=="expense") {
            if(originalExpenses.isEmpty()) Text("No available personal expenses.",color=Tokens.muted)
            originalExpenses.forEach {record->GroupedCard {GroupedRow(record.description,R.drawable.ic_food,value=referenceMoney(record.amount),tone=ReferenceTone.Peach,onClick={onChange(s.copy(refundOf=record.id,categoryId=record.categoryId));picker=null})}}
        } else {
            val choices=active.filter {kind!="destination" || it.id!=s.accountId}
            if(choices.isEmpty()) Text("No available accounts. Create an account first.",color=Tokens.muted)
            choices.forEach {account->GroupedCard {GroupedRow(account.name,R.drawable.ic_account,value=referenceMoney(account.balance),onClick={
                onChange(if(kind=="destination") s.copy(destinationId=account.id) else s.copy(accountId=account.id,destinationId=if(transfer && s.destinationId==account.id) "" else s.destinationId,secondAccountId=if(s.secondAccountId==account.id) "" else s.secondAccountId));picker=null
            })}}
        }
    }}
    editing?.let {field->EditorialBottomSheet(field,{editing=null}) {
        if(field=="Description") Field("Description",s.description,{onChange(s.copy(description=it))}) else Field("Note (optional)",s.notes,{onChange(s.copy(notes=it))})
        EditorialPrimaryButton("Done") {editing=null}
    }}
}

@Composable private fun TransferAccountCard(label:String,name:String,balance:Long?,tone:ReferenceTone,onClick:()->Unit) {
    GroupedCard {GroupedRow(name,R.drawable.ic_account,supporting=label,value=balance?.let(::referenceMoney),tone=tone,onClick=onClick)}
}

@Composable private fun RecordAmountField(label:String,value:String,centered:Boolean=false,onChange:(String)->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value,onChange,Modifier.fillMaxWidth().semantics {contentDescription=label},visualTransformation=RupeeMoneyTransformation,singleLine=true,shape=Tokens.field,
            textStyle=(if(centered) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium).copy(textAlign=if(centered) TextAlign.Center else TextAlign.Start),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label=if(centered) null else ({Text(label)}),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Tokens.paper,focusedContainerColor=Tokens.paper,unfocusedBorderColor=Tokens.border,focusedBorderColor=Tokens.accent))
    }
}
