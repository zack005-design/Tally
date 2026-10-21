package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.smartexpense.R
import com.smartexpense.domain.*

@Composable fun AdvancedEntryScreen(s:EntryUiState,accounts:List<Account>,people:List<Person>,categories:List<Category>,groups:List<Group>,onChange:(EntryUiState)->Unit,onParse:()->Unit,onSave:()->Unit,records:List<Record> = emptyList(),onFriends:()->Unit={},onBack:()->Unit={},walletSources:WalletSources?=null,hasBill:Boolean=false,onDiscardBill:()->Unit={},onExchange:()->Unit={}) {
    if(s.kind in listOf(Kind.INCOME,Kind.REFUND,Kind.TRANSFER)) {
        ReferenceRecordScreen(s,accounts,categories,records,onChange,onSave,onBack,walletSources,hasBill,onDiscardBill,onExchange)
        return
    }
    val preview=s.preview()
    var mode by rememberSaveable(s.id) {mutableStateOf(if(s.amount.isNotBlank() || s.shared || s.replaces!=null) "Manual" else "Describe")}
    var reviewing by rememberSaveable(s.id) {mutableStateOf(s.amount.isNotBlank())}
    var extraMethods by rememberSaveable {mutableStateOf(false)}
    var entryMenu by rememberSaveable {mutableStateOf(false)}
    var detailsExpanded by rememberSaveable {mutableStateOf(false)}
    var addPeople by rememberSaveable {mutableStateOf(false)}
    val labels=people.associate {it.id to personLabel(it,people)}
    val splitting=s.shared && s.kind==Kind.EXPENSE && mode=="Manual"
    val valid=s.canReview(accounts,categories)
    Scaffold(modifier=Modifier.imePadding(),containerColor=Tokens.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        if(mode=="Describe" || (!reviewing && !splitting)) Box(Modifier.padding(horizontal=Tokens.pageInset,vertical=10.dp)) {
            if(mode=="Describe") EditorialPrimaryButton("Continue") {onParse();mode="Manual";reviewing=true}
            else EditorialPrimaryButton("Review Transaction →",valid) {reviewing=true}
        }
    }) {padding->Box(Modifier.fillMaxSize().padding(padding)) {
    Page(if(splitting) "Split Bill" else if(s.replaces!=null) "Correct transaction" else if(s.kind==Kind.REFUND) "Record refund" else if(s.kind==Kind.INCOME) "Record income" else if(mode=="Describe") "Describe entry" else "Add Transaction",onBack=onBack,actions={IconButton({entryMenu=true}) {Icon(painterResource(R.drawable.ic_more),"Entry options",tint=Tokens.accent)}}) {
        if(hasBill) item {Panel {Text("Printed bill attached · check the total, date and items before Save",color=Tokens.muted);TextButton(onExchange) {Text("Enter a manual exchange rate")};TextButton(onDiscardBill) {Text("Discard bill attachment")}}}
        if(!splitting && mode!="Describe") item {EntryMode(mode) {mode=it}}
        if(mode=="Describe") {
            item {Surface(shape=Tokens.card,color=Tokens.paper) {
                OutlinedTextField(s.text,{onChange(s.copy(text=it))},Modifier.fillMaxWidth(),placeholder={Text("Describe your transaction…\n\ne.g. Dinner with friends ₹890. I paid ₹500, Rahul paid ₹290, Kevin paid ₹100 and Arjun paid nothing. Split equally.",style=MaterialTheme.typography.bodyMedium)},minLines=8,shape=Tokens.card,
                    colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Tokens.paper,focusedContainerColor=Tokens.paper,unfocusedBorderColor=Tokens.border,focusedBorderColor=Tokens.accent))
            }}
            item {DescribeLivePreview(s.text,people,accounts.find {it.id==s.accountId}?.name,accounts,walletSources)}
            item {ReferenceInfo("Review before saving. You can edit any field.")}
            if(detailsExpanded) {
            item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("Dinner split" to "Dinner bill is 890. I paid 500, Rahul paid 290, Kevin paid 100, Arjun paid 0. Split equally", "Grocery expense" to "Spent 1450 on groceries", "Transfer" to "Transfer 500 from bank to cash").forEach {(name,text)->SuggestionChip({onChange(s.copy(text=text))},label={Text(name,style=MaterialTheme.typography.labelSmall)},shape=Tokens.field)}
            }}
            item {Text("Transaction type",style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.height(12.dp));TransactionTypes(s.kind,s.shared) {kind,shared->onChange(s.copy(kind=kind,shared=shared))}}
            item {Panel {
                Text("Recent suggestions",style=MaterialTheme.typography.titleSmall)
                val suggestions=records.filter {it.kind in listOf(Kind.EXPENSE,Kind.INCOME)}.distinctBy {it.description}.take(3)
                if(suggestions.isEmpty()) {
                    Text("Reusable offline templates",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
                    listOf("Lunch" to "Spent 250 on lunch","Grocery shopping" to "Spent 1450 on groceries","Metro recharge" to "Spent 500 on transport").forEach {(title,text)->SuggestionRow(title,"Tap to review") {onChange(s.copy(text=text))}}
                } else suggestions.forEach {r->SuggestionRow(r.description,Money.format(r.amount)) {onChange(s.reuse(r));mode="Manual";reviewing=true}}
            }}
            if(s.parserNotice.isNotBlank()) item {Text(s.parserNotice,color=Tokens.negative,style=MaterialTheme.typography.bodySmall)}

            }
        } else {
            if(s.parserNotice.isNotBlank()) item {Text(s.parserNotice,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
            if(!splitting) item {Text("Transaction type",style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.height(12.dp));TransactionTypes(s.kind,s.shared) {kind,shared->onChange(s.copy(kind=kind,shared=shared,refundOf=if(kind==Kind.REFUND) s.refundOf else null))}}
            if(splitting) item {Panel {
                androidx.compose.foundation.text.BasicTextField(s.description,{onChange(s.copy(description=it))},Modifier.fillMaxWidth().padding(vertical=8.dp),textStyle=MaterialTheme.typography.titleSmall.copy(color=Tokens.text),decorationBox={inner->Row(verticalAlignment=Alignment.CenterVertically) {Icon(painterResource(R.drawable.ic_food),null,Modifier.size(32.dp));Spacer(Modifier.width(12.dp));Box(Modifier.weight(1f)) {if(s.description.isBlank()) Text("Expense name",color=Tokens.muted);inner()}}})
                HorizontalDivider(color=Tokens.border)
                Text("Total amount (₹)",style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
                androidx.compose.foundation.text.BasicTextField(s.amount,{onChange(s.copy(amount=it))},Modifier.fillMaxWidth().padding(vertical=4.dp),textStyle=MaterialTheme.typography.headlineSmall.copy(color=Tokens.text),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),decorationBox={inner->Row {Text("₹");Box(Modifier.weight(1f)) {inner()};Text("INR",style=MaterialTheme.typography.bodySmall)}})
            }} else {
                item {Surface(shape=Tokens.card,color=Tokens.surface) {Column(Modifier.padding(12.dp)) {
                    Text("Amount (₹)",style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
                    TextField(s.amount,{onChange(s.copy(amount=it))},Modifier.fillMaxWidth(),placeholder={Text("0.00",color=Tokens.muted)},leadingIcon={Text("₹",style=MaterialTheme.typography.headlineSmall)},trailingIcon={Text("INR",Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall)},shape=Tokens.field,textStyle=MaterialTheme.typography.headlineLarge,singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),colors=TextFieldDefaults.colors(unfocusedContainerColor=Tokens.surface,focusedContainerColor=Tokens.surface,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,focusedIndicatorColor=Tokens.accent))
                }}}
            }
            if(splitting) {
                if(groups.isNotEmpty()) item {Choice("Use a saved group",s.groupId,listOf("none" to "Choose friends individually")+groups.map {it.id to it.name}) {id->onChange(if(id=="none") s.copy(groupId=null) else s.withGroup(groups.single {it.id==id}))}}
                item {Text("Split method",style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.height(8.dp));EditorialSegmentedControl(s.method,listOf("Equal" to "Equal","Fixed" to "Exact","Percentage" to "Percentage")) {onChange(s.copy(method=it))}}
                item {FilterChip(s.method=="Itemized",{onChange(s.copy(method="Itemized"))},label={Text("Split by food / items")},modifier=Modifier.heightIn(min=48.dp))}
                item {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    SectionHeading("People (${s.participants.size})","Add person") {addPeople=!addPeople}
                    s.participants.forEach {id->key(id) {ParticipantRow(labels[id]?:"Participant",s.paid[id].orEmpty(),{onChange(s.copy(paid=s.paid+(id to it)))},id!="self") {onChange(s.copy(participants=s.participants-id))}}}
                    if(addPeople) {
                        ParticipantChoices(people,s.participants) {id,checked->onChange(s.copy(participants=if(checked) (s.participants+id).distinct() else s.participants-id,paid=if(checked) s.paid+(id to "") else s.paid))}
                        TextButton(onFriends) {Text("Manage friends")}
                    }
                    if(s.method in listOf("Fixed","Percentage","Weighted")) s.participants.forEach {id->Field("${labels[id]?:"Participant"} ${if(s.method=="Percentage") "percent" else if(s.method=="Weighted") "weight" else "share (₹)"}",s.values[id].orEmpty(),{onChange(s.copy(values=s.values+(id to it)))},true)}

                    if(extraMethods) {
                        ChipChoices(s.method,listOf("Weighted" to "Weighted","Itemized" to "Itemized")) {onChange(s.copy(method=it))}
                    }
                    if(s.method=="Itemized") ItemizedEditor(s,people,onChange)
                }}
            }

            item {Panel {PaymentSourcePicker(s,accounts,onChange,walletSources)}}
            if(!splitting || detailsExpanded || s.accountId.isBlank()) item {Surface(shape=Tokens.card,color=Tokens.background,shadowElevation=1.dp) {Column {
                if(s.kind in listOf(Kind.EXPENSE,Kind.REFUND)) {
                    if(s.categoryId!=null && categories.none {it.id==s.categoryId && !it.archived}) Text("Choose an active category or Uncategorised before saving.",color=Tokens.negative)
                    Choice("Category",s.categoryId?:"",listOf("" to "Uncategorised")+categories.filterNot {it.archived}.map {it.id to it.name}) {onChange(s.copy(categoryId=it.ifEmpty {null}))}
                }
                if(s.kind==Kind.TRANSFER) Choice("Destination account",s.destinationId,accounts.filter {!it.archived && it.id!=s.accountId}.map {it.id to it.name}) {onChange(s.copy(destinationId=it))}
                if(s.kind==Kind.REFUND) Choice("Original expense",s.refundOf,records.filter {it.kind==Kind.EXPENSE && !it.reversed && it.personIds==listOf("self")}.map {it.id to it.description}) {id->onChange(s.copy(refundOf=id,categoryId=records.first {it.id==id}.categoryId))}
                if(!splitting) Field("Description",s.description,{onChange(s.copy(description=it))})
                EditorialDateRow(s.date) {onChange(s.copy(date=it))}
                Field("Notes (optional)",s.notes,{onChange(s.copy(notes=it))})
                if(s.kind==Kind.EXPENSE && !splitting) ReferenceToggleRow("Shared expense",s.shared,{onChange(s.copy(shared=it))})
            }}}
            if(accounts.none {!it.archived}) item {Text("Create an account from Home → Accounts before saving.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
            if(reviewing || splitting) item {TransactionReviewPanel(s,accounts,valid,onSave)}
            item {AutoCalculationPanel(s,accounts,onChange,people,walletSources)}

        }
    }
    }}
    if(entryMenu) EditorialActionSheet("Transaction options",buildList {
        if(mode=="Describe") add(EditorialAction("Use manual entry") {mode="Manual"})
        if(splitting) {add(EditorialAction("Make personal expense") {onChange(s.copy(shared=false));reviewing=false});add(EditorialAction("Describe this transaction") {mode="Describe"})}
        add(EditorialAction(if(mode=="Describe") "More entry options" else "Account, date & notes") {detailsExpanded=!detailsExpanded})
        add(EditorialAction("Advanced split options") {extraMethods=!extraMethods})
    },{entryMenu=false})
}
@Composable private fun SuggestionRow(title:String,subtitle:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_food),null,Modifier.size(32.dp));Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.titleSmall);Text(subtitle,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
        IconButton(onClick) {Icon(painterResource(R.drawable.ic_add),"Use $title template")}
    }
}
@Composable fun ParticipantRow(name:String,paid:String,onPaid:(String)->Unit,removable:Boolean,onRemove:()->Unit) {
    var editing by rememberSaveable {mutableStateOf(false)}
    Surface(shape=Tokens.field,color=Tokens.surface,modifier=Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(horizontal=8.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
                InitialAvatar(name,Modifier.size(36.dp));Spacer(Modifier.width(12.dp));Text(name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                TextButton({editing=!editing}) {Text(if(paid.isBlank()) "Enter paid" else "₹$paid",style=MaterialTheme.typography.labelMedium)}
            }
            androidx.compose.animation.AnimatedVisibility(editing) {Column(Modifier.padding(8.dp)) {Field("$name paid (₹)",paid,onPaid,true);if(removable) TextButton(onRemove) {Text("Remove participant")}}}
        }
    }
}
@Composable fun EditorialDateRow(value:String,onChange:(String)->Unit) {
    val date=runCatching {java.time.LocalDateTime.parse(value)}.getOrElse {java.time.LocalDateTime.now()}
    var open by rememberSaveable {mutableStateOf(false)}
    var dateOpen by rememberSaveable {mutableStateOf(false)}
    var timeOpen by rememberSaveable {mutableStateOf(false)}
    Surface(onClick={open=true},color=Tokens.inputSurface,shape=Tokens.field) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_calendar),null,Modifier.size(22.dp));Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {Text("Date",style=MaterialTheme.typography.labelMedium,color=Tokens.muted);Text(date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")),style=MaterialTheme.typography.bodyLarge)}
            Text("›")
        }
    }
    if(open) EditorialBottomSheet("Date & time",{open=false}) {
        TextButton({open=false;dateOpen=true}) {Text("Change date")}
        TextButton({open=false;timeOpen=true}) {Text("Change time")}
    }
    if(dateOpen) EditorialDatePicker("Choose date",date.toLocalDate(),{dateOpen=false}) {onChange(java.time.LocalDateTime.of(it,date.toLocalTime()).toString());dateOpen=false}
    if(timeOpen) EditorialTimePicker(date.toLocalTime(),{timeOpen=false}) {onChange(java.time.LocalDateTime.of(date.toLocalDate(),it).toString());timeOpen=false}
}
@Composable fun TransactionReviewPanel(s:EntryUiState,accounts:List<Account>,valid:Boolean,onSave:()->Unit) {
    val review=s.reviewAmounts().getOrNull()
    Surface(shape=Tokens.card,color=Tokens.surface,contentColor=Tokens.text) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text("Review",style=MaterialTheme.typography.titleLarge)
        Text(s.preview().getOrElse {it.message?:"Complete the form to preview"},color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        if(review!=null) {
            AmountRow("Total bill",review.total)
            if(s.shared && s.kind==Kind.EXPENSE) {
                AmountRow("Your payment",-review.payment)
                AmountRow(if(s.method=="Equal" && "self" in s.participants) "Your share (1/${s.participants.size})" else "Your share",review.share)
                Surface(shape=Tokens.field,color=Tokens.secondarySurface) {Box(Modifier.padding(12.dp)) {AmountRow(when {review.net>0->"You will receive";review.net<0->"You owe";else->"No balance due"},kotlin.math.abs(review.net))}}
            }
            s.accountImpact().getOrNull()?.filter {it.accountId.isNotBlank()}?.forEach {movement->AmountRow("Account impact (${accounts.find {it.id==movement.accountId}?.name?:"Account"})",movement.amount)}
        }
        EditorialPrimaryButton("Save transaction",valid,onSave)
    }}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ParticipantChoices(people:List<Person>,selected:List<String>,onSelect:(String,Boolean)->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {people.forEach {person->FilterChip(person.id in selected,{onSelect(person.id,person.id !in selected)},label={Text(personLabel(person,people),style=MaterialTheme.typography.labelMedium)},border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Tokens.inputSurface,selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,selectedLabelColor=Tokens.accent))}}
}
@Composable private fun ItemizedEditor(s:EntryUiState,people:List<Person>,onChange:(EntryUiState)->Unit) {
    var amount by rememberSaveable {mutableStateOf("")}
    var selected by rememberSaveable {mutableStateOf(listOf<String>())}
    val eligible=selected.filter {it in s.participants}
    LaunchedEffect(s.participants) {selected=selected.filter {it in s.participants}}
    val lines=s.items.lines().filter(String::isNotBlank)
    if(lines.any {line->line.split('=').getOrElse(1) {""}.split('+').any {it.trim() !in s.participants}}) Text("An item includes someone removed from this bill. Reselect that person or remove the item.",color=Tokens.negative,style=MaterialTheme.typography.bodySmall)
    lines.forEachIndexed {index,line->
        val parts=line.split('=')
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(s.billItemNames.size==lines.size) s.billItemNames[index] else "Item ${index+1}",style=MaterialTheme.typography.titleSmall)
                Text(parts.first().trim()+" ₹",style=MaterialTheme.typography.titleSmall)
                Text(parts.getOrElse(1) {""}.split('+').joinToString(", ") {id->people.find {it.id==id.trim()}?.let {personLabel(it,people)}?:"Unknown participant"},style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            }
            TextButton({onChange(s.copy(items=lines.filterIndexed {i,_->i!=index}.joinToString("\n"),billItemNames=if(s.billItemNames.size==lines.size) s.billItemNames.filterIndexed {i,_->i!=index} else emptyList()))}) {Text("Remove")}
        }
    }
    Field("Item amount (₹)",amount,{amount=it},true)
    ParticipantChoices(people.filter {it.id in s.participants},eligible) {id,checked->selected=if(checked) selected+id else selected-id}
    OutlinedButton({onChange(s.copy(items=(lines+"$amount = ${eligible.joinToString(" + ")}").joinToString("\n"),billItemNames=if(s.billItemNames.isNotEmpty() && s.billItemNames.size==lines.size) s.billItemNames+"Item ${lines.size+1}" else emptyList()));amount="";selected=emptyList()},enabled=eligible.isNotEmpty() && runCatching {Money.parse(amount)>0}.getOrDefault(false)) {Text("Add item")}
    Field("Shared taxes / charges (₹)",s.itemCharges,{onChange(s.copy(itemCharges=it))},true)
    Field("Bill discount (₹)",s.itemDiscount,{onChange(s.copy(itemDiscount=it))},true)
    Text("Charges follow each person's item subtotal; discounts follow their subtotal plus charges.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
    val itemTotal=runCatching {Math.subtractExact(Math.addExact(Finance.total(lines.map {Money.parse(it.substringBefore('='))}),Money.parse(s.itemCharges.ifBlank {"0"})),Money.parse(s.itemDiscount.ifBlank {"0"}))}.getOrNull()
    if(itemTotal!=null && itemTotal>0) {
        AmountRow("Items + charges − discount",itemTotal)
        OutlinedButton({onChange(s.copy(amount=Money.input(itemTotal)))},Modifier.fillMaxWidth()) {Text("Use calculated bill total")}
    }
}
