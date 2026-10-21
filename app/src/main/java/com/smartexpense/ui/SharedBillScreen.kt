package com.smartexpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Five reference stages share the original draft and save contract. Payments are never inferred. */
@Composable internal fun SharedBillScreen(s:EntryUiState,accounts:List<Account>,people:List<Person>,categories:List<Category>,groups:List<Group>,onChange:(EntryUiState)->Unit,onSave:()->Unit,onBack:()->Unit,onFriends:()->Unit,walletSources:WalletSources?,onImportBill:()->Unit) {
    var step by rememberSaveable(s.id) {mutableIntStateOf(0)}
    var editor by rememberSaveable(s.id) {mutableStateOf<String?>(null)}
    var sheetDraft by remember(s.id) {mutableStateOf<EntryUiState?>(null)}
    var itemIndex by rememberSaveable(s.id) {mutableIntStateOf(-1)}
    var itemName by rememberSaveable(s.id) {mutableStateOf("")}
    var itemAmount by rememberSaveable(s.id) {mutableStateOf("")}
    var itemPeople by rememberSaveable(s.id) {mutableStateOf(emptyList<String>())}
    fun name(id:String)=if(id=="self") "You" else people.find {it.id==id}?.let {personLabel(it,people)}?:"Friend"
    fun back() {if(step==4 && s.method!="Itemized") step=2 else if(step>0) step-- else onBack()}
    BackHandler(step>0) {back()}
    val total=runCatching {Money.parse(s.amount).also {require(it>0)}}.getOrNull()
    val basicValid=total!=null && s.description.isNotBlank() && s.participants.size>1
    val paid=runCatching {Finance.total(s.participants.map {id->Money.parse(s.paid[id].orEmpty()).also {require(it>=0)}})}.getOrNull()
    val paymentsValid=basicValid && paid==total
    val bill=automaticBill(s).getOrNull()
    val titles=listOf("New shared bill","Who paid?","Split method","Itemized split","Review split")
    val headings=listOf("Create a shared bill","Enter who paid","Choose a split method","Add and assign items","Review and save")
    val subtitles=listOf("Add the basic details to get started.","Add how much each person actually paid for this bill.","Decide how to split ${total?.let(::referenceMoney)?:"the bill"} among ${s.participants.size} people.","Break down the bill and assign who shared each item.","Check the final split and settlements before saving.")
    Scaffold(containerColor=Tokens.background,modifier=Modifier.imePadding(),contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={Column(Modifier.padding(horizontal=Tokens.pageInset,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        EditorialPrimaryButton(when(step) {3->"Review";4->"Save shared bill";else->"Continue"},when(step) {0->basicValid;1->paymentsValid;2->s.method=="Itemized" || bill?.net!=null;else->s.canReview(accounts,categories)}) {
            when(step) {0->step=1;1->step=2;2->step=if(s.method=="Itemized") 3 else 4;3->step=4;4->onSave()}
        }
        if(step==4) Text("Nothing saved yet.",Modifier.padding(top=8.dp),color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
    }}) {padding->Box(Modifier.fillMaxSize().padding(padding)) {key(step) {
        Page(titles[step],gap=12.dp,onBack={back()},actions={Text("${step+1} of 5",color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)}) {
            item {SharedProgress(step)}
            item {Column(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(headings[step],style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)
                Text(subtitles[step],color=Tokens.muted,textAlign=TextAlign.Center)
            }}
            when(step) {
                0->{
                    item {Panel {
                        Text("Title",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
                        OutlinedTextField(s.description,{onChange(s.copy(description=it))},Modifier.fillMaxWidth(),singleLine=true,shape=Tokens.field,textStyle=MaterialTheme.typography.bodyLarge,colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Tokens.border,focusedBorderColor=Tokens.accent))
                        Text("Total amount",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
                        ReferenceAmountField("Total amount",s.amount,{onChange(s.copy(amount=it))},compact=true)
                    }}
                    item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("Participants",style=MaterialTheme.typography.titleMedium)
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(20.dp)) {items(s.participants.size) {i->val id=s.participants[i];Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {InitialAvatar(name(id),Modifier.size(52.dp));Text(name(id),style=MaterialTheme.typography.bodySmall)}}}
                    };GroupedDivider();GroupedRow("Add a friend",R.drawable.ic_add) {sheetDraft=s;editor="people"}}}
                    item {GroupedCard {GroupedRow("Category",R.drawable.ic_food,value=categories.find {it.id==s.categoryId}?.name?:"Uncategorised",tone=ReferenceTone.Peach) {editor="category"}}}
                    item {GroupedCard {GroupedRow("Date",R.drawable.ic_calendar,value=LocalDateTime.parse(s.date).format(DateTimeFormatter.ofPattern("d MMM yyyy"))) {editor="date"}}}
                    item {TextButton(onImportBill) {Text("Use a bill photo / PDF")}}
                }
                1->{
                    item {GroupedCard {
                        Column(Modifier.padding(16.dp)) {Text("Total bill amount",color=Tokens.muted);Text(total?.let(::referenceMoney)?:"—",style=MaterialTheme.typography.headlineSmall)}
                        s.participants.forEach {id->GroupedDivider();Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            InitialAvatar(name(id))
                            Column(Modifier.weight(1f)) {Text(name(id));if(id=="self") Text("From ${accounts.find {it.id==s.accountId}?.name?:"Choose account"}",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
                            Box(Modifier.widthIn(max=128.dp)) {ReferenceAmountField("${name(id)} paid (₹)",s.paid[id].orEmpty(),{onChange(s.copy(paid=s.paid+(id to it)))},compact=true)}
                        }}
                    }}
                    item {ReferenceNotice(if(paymentsValid) "${total?.let(::referenceMoney)} of ${total?.let(::referenceMoney)} accounted for" else if(paid==null) "Enter actual payments" else "${referenceMoney(paid)} of ${total?.let(::referenceMoney)} accounted for",if(paymentsValid) "The total matches the bill amount." else "Enter every person's actual payment, including zero.",success=paymentsValid)}
                    item {ReferenceNotice("Payments and shares are separate.","Who paid is not the same as who owes what.")}
                    item {GroupedCard {GroupedRow("Payment account",R.drawable.ic_account,value=accounts.find {it.id==s.accountId}?.name?:"Choose") {sheetDraft=s;editor="account"}}}
                }
                2->{
                    item {GroupedCard {
                        listOf(Triple("Equal","Equal split","Everyone pays the same amount."),Triple("Fixed","Exact amounts","Set a specific amount for each person."),Triple("Percentage","Percentages","Split by percentage."),Triple("Weighted","Shares","Split by custom shares, e.g. 2:1:1."),Triple("Itemized","By item","Assign people to specific items.")).forEachIndexed {i,method->
                            if(i>0) GroupedDivider()
                            Row(Modifier.fillMaxWidth().selectable(s.method==method.first,role=Role.RadioButton,onClick={onChange(s.copy(method=method.first))}).background(if(s.method==method.first) Tokens.secondarySurface else Color.Transparent).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                ReferenceSelectionMark(s.method==method.first)
                                Column(Modifier.weight(1f)) {Text(method.second,style=MaterialTheme.typography.titleMedium);Text(method.third,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
                                Icon(painterResource(R.drawable.ic_chevron_down),null,Modifier.size(14.dp).rotate(-90f),tint=Tokens.muted)
                            }
                        }
                    }}
                    if(s.method in listOf("Fixed","Percentage","Weighted")) item {Panel {s.participants.forEach {id->Field("${name(id)} ${if(s.method=="Percentage") "percent" else if(s.method=="Weighted") "weight" else "share (₹)"}",s.values[id].orEmpty(),{onChange(s.copy(values=s.values+(id to it)))},true)}}}
                    if(s.method!="Itemized") item {GroupedCard {
                        Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(if(s.method=="Equal") "Equal share preview" else "Share preview",color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)
                            if(s.method=="Equal" && bill?.shares?.values?.distinct()?.size==1) Text("${referenceMoney(bill.shares.values.first())} each",style=MaterialTheme.typography.bodyMedium)
                        }
                        bill?.shares?.forEach {(id,share)->GroupedDivider();GroupedRow(name(id),R.drawable.ic_people,value=referenceMoney(share),leading={InitialAvatar(name(id))},showChevron=false) {}}
                        if(bill==null) Text(automaticBill(s).exceptionOrNull()?.message?:"Check the shares",Modifier.padding(16.dp),color=Tokens.negative)
                    }}
                }
                3->{
                    val lines=s.items.lines().filter(String::isNotBlank)
                    item {GroupedCard {
                        lines.forEachIndexed {i,line->
                            if(i>0) GroupedDivider()
                            SharedItemRow(s.billItemNames.getOrNull(i)?:"Item ${i+1}",runCatching {referenceMoney(Money.parse(line.substringBefore('=')))}.getOrDefault("Check amount"),line.substringAfter('=',"").split('+').map {name(it.trim())}) {
                                itemIndex=i;itemName=s.billItemNames.getOrNull(i)?:"Item ${i+1}";itemAmount=line.substringBefore('=').trim();itemPeople=line.substringAfter('=',"").split('+').map {it.trim()}.filter {it in s.participants};editor="item"
                            }
                        }
                        GroupedDivider();GroupedRow("Add item",R.drawable.ic_add) {itemIndex=-1;itemName="";itemAmount="";itemPeople=emptyList();editor="item"}
                    }}
                    item {GroupedCard {
                        ReferenceValueRow("Items subtotal",runCatching {referenceMoney(Finance.total(lines.map {Money.parse(it.substringBefore('='))}))}.getOrDefault("—"))
                        GroupedDivider();GroupedRow("Tax",R.drawable.ic_add,value=runCatching {referenceMoney(Money.parse(s.itemCharges.ifBlank {"0"}))}.getOrDefault("—")) {sheetDraft=s;editor="tax"}
                        GroupedDivider();GroupedRow("Discount",R.drawable.ic_compose,value=runCatching {referenceMoney(Money.parse(s.itemDiscount.ifBlank {"0"}))}.getOrDefault("—")) {sheetDraft=s;editor="discount"}
                    }}
                    item {ReferenceInfo("Assign every item to at least one person so the total can be split correctly.")}
                    if(bill==null) item {Text(automaticBill(s).exceptionOrNull()?.message?:"Check your item amounts",color=Tokens.negative)}
                }
                4->{
                    item {GroupedCard {GroupedRow(s.description,R.drawable.ic_food,supporting="${s.participants.size} people · ${LocalDateTime.parse(s.date).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",value=total?.let(::referenceMoney),tone=ReferenceTone.Peach,showChevron=false) {step=0}}}
                    item {GroupedCard {
                        Text("Your summary",Modifier.padding(16.dp),style=MaterialTheme.typography.titleMedium)
                        s.reviewAmounts().getOrNull()?.let {review->
                            GroupedDivider();ReferenceValueRow("You paid",referenceMoney(review.payment))
                            GroupedDivider();ReferenceValueRow("Your share",referenceMoney(review.share))
                            GroupedDivider(14.dp);Surface(color=Tokens.secondarySurface) {ReferenceValueRow(if(review.net>=0) "You receive" else "You owe",referenceMoney(kotlin.math.abs(review.net)),Tokens.accent)}
                        }
                    }}
                    item {GroupedCard {
                        Text("Settlements",Modifier.padding(16.dp),style=MaterialTheme.typography.titleMedium)
                        bill?.settlements?.forEach {settlement->GroupedDivider();GroupedRow("${if(settlement.from=="self") "You owe" else "${name(settlement.from)} owes"} ${name(settlement.to)}",R.drawable.ic_people,value=referenceMoney(settlement.amount),leading={InitialAvatar(name(settlement.from))},showChevron=false) {}}
                        if(bill?.settlements?.isEmpty()==true) Text("All settled · nobody owes anything",Modifier.padding(16.dp))
                    }}
                    item {ReferenceNotice("All amounts reconcile","Total paid (${total?.let(::referenceMoney)}) equals total shared (${bill?.shares?.values?.let {referenceMoney(Finance.total(it))}}).",success=true)}
                    item {GroupedCard {GroupedRow("Payment account",R.drawable.ic_account,value=accounts.find {it.id==s.accountId}?.name?:"Choose") {editor="account"};GroupedDivider();GroupedRow("Private notes",R.drawable.ic_document_outline,supporting=s.notes.ifBlank {"Optional"}) {sheetDraft=s;editor="notes"}}}
                    if(!s.canReview(accounts,categories)) item {Text(s.preview().exceptionOrNull()?.message?:"Choose an active account and category before saving.",color=Tokens.negative)}
                }
            }
        }
    }}}
    when(editor) {
        "people"->EditorialBottomSheet("Participants",{editor=null;sheetDraft=null}) {
            val draft=sheetDraft?:s
            if(groups.isNotEmpty()) Choice("Use a saved group",draft.groupId,listOf("none" to "Choose friends individually")+groups.map {it.id to it.name}) {id->sheetDraft=if(id=="none") draft.copy(groupId=null) else draft.withGroup(groups.single {it.id==id})}
            people.filter {it.id!="self"}.forEach {person->ReferenceToggleRow(personLabel(person,people),person.id in draft.participants,{checked->sheetDraft=draft.copy(groupId=null,participants=if(checked) (draft.participants+person.id).distinct() else draft.participants-person.id)})}
            TextButton(onFriends) {Text("Create or manage friends")}
            EditorialPrimaryButton("Done") {sheetDraft?.let(onChange);editor=null;sheetDraft=null}
        }
        "category"->EditorialBottomSheet("Category",{editor=null}) {Choice("Category",s.categoryId?:"",listOf("" to "Uncategorised")+categories.filterNot {it.archived}.map {it.id to it.name}) {onChange(s.copy(categoryId=it.ifEmpty {null}));editor=null}}
        "date"->EditorialDatePicker("Bill date",LocalDateTime.parse(s.date).toLocalDate(),{editor=null}) {onChange(s.copy(date=LocalDateTime.of(it,LocalDateTime.parse(s.date).toLocalTime()).toString()));editor=null}
        "account"->EditorialBottomSheet("Payment account",{editor=null;sheetDraft=null}) {PaymentSourcePicker(sheetDraft?:s,accounts,{sheetDraft=it},walletSources);EditorialPrimaryButton("Done") {sheetDraft?.let(onChange);editor=null;sheetDraft=null}}
        "notes"->EditorialBottomSheet("Private notes",{editor=null;sheetDraft=null}) {val draft=sheetDraft?:s;Field("Notes (optional)",draft.notes,{sheetDraft=draft.copy(notes=it)});EditorialPrimaryButton("Done") {sheetDraft?.let(onChange);editor=null;sheetDraft=null}}
        "tax","discount"->EditorialBottomSheet(if(editor=="tax") "Tax" else "Discount",{editor=null;sheetDraft=null}) {
            val tax=editor=="tax";val draft=sheetDraft?:s
            ReferenceAmountField(if(tax) "Shared taxes / charges (₹)" else "Bill discount (₹)",if(tax) draft.itemCharges else draft.itemDiscount,{sheetDraft=if(tax) draft.copy(itemCharges=it) else draft.copy(itemDiscount=it)})
            EditorialPrimaryButton("Done") {sheetDraft?.let(onChange);editor=null;sheetDraft=null}
        }
        "item"->EditorialBottomSheet(if(itemIndex<0) "Add item" else "Edit item",{editor=null}) {
            Field("Item name",itemName,{itemName=it})
            ReferenceAmountField("Item amount (₹)",itemAmount,{itemAmount=it})
            s.participants.forEach {id->ReferenceToggleRow(name(id),id in itemPeople,{checked->itemPeople=if(checked) (itemPeople+id).distinct() else itemPeople-id})}
            EditorialPrimaryButton("Save item",itemPeople.isNotEmpty() && runCatching {Money.parse(itemAmount)>0}.getOrDefault(false)) {
                val lines=s.items.lines().filter(String::isNotBlank).toMutableList()
                val names=lines.indices.map {s.billItemNames.getOrNull(it)?:"Item ${it+1}"}.toMutableList()
                val line="$itemAmount = ${itemPeople.joinToString(" + ")}"
                if(itemIndex<0) {lines.add(line);names.add(itemName.ifBlank {"Item ${lines.size}"})} else {lines[itemIndex]=line;names[itemIndex]=itemName.ifBlank {"Item ${itemIndex+1}"}}
                onChange(s.copy(items=lines.joinToString("\n"),billItemNames=names));editor=null
            }
            if(itemIndex>=0) TextButton({onChange(s.copy(items=s.items.lines().filter(String::isNotBlank).filterIndexed {i,_->i!=itemIndex}.joinToString("\n"),billItemNames=s.billItemNames.filterIndexed {i,_->i!=itemIndex}));editor=null}) {Text("Remove item",color=Tokens.negative)}
        }
    }
}

@Composable private fun SharedProgress(step:Int) {
    val accent=Tokens.accent;val muted=Tokens.border;val paper=Tokens.background
    Canvas(Modifier.fillMaxWidth().height(18.dp).padding(horizontal=12.dp)) {
        val y=size.height/2;val gap=size.width/4
        drawLine(muted,Offset(0f,y),Offset(size.width,y),3.dp.toPx())
        repeat(5) {i->
            val x=i*gap
            drawCircle(paper,8.dp.toPx(),Offset(x,y))
            if(i==step && step>=3) drawCircle(accent,8.dp.toPx(),Offset(x,y))
            else if(i==step) drawLine(accent,Offset((x-12.dp.toPx()).coerceAtLeast(0f),y),Offset((x+12.dp.toPx()).coerceAtMost(size.width),y),9.dp.toPx(),StrokeCap.Round)
            else drawCircle(muted,5.dp.toPx(),Offset(x,y))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SharedItemRow(title:String,amount:String,owners:List<String>,onClick:()->Unit) {
    Surface(onClick=onClick,color=Tokens.paper,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
            ReferenceIcon(referenceSymbol(title),ReferenceTone.Peach,46.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    Text(amount,style=MaterialTheme.typography.titleMedium)
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    (if(owners.size>=3) listOf("Shared") else owners).forEach {owner->Surface(shape=Tokens.field,color=when {owner=="You" || owner=="Shared"->Tokens.secondarySurface;owner.startsWith("R")->Color(0xFFFFEEE3);else->MaterialTheme.colorScheme.tertiaryContainer}) {
                        Text(owner,Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.bodySmall,color=if(owner=="You") Tokens.accent else MaterialTheme.colorScheme.onTertiaryContainer)
                    }}
                    Surface(shape=androidx.compose.foundation.shape.CircleShape,color=Tokens.paper,border=androidx.compose.foundation.BorderStroke(1.dp,Tokens.border)) {
                        Box(Modifier.size(30.dp),contentAlignment=Alignment.Center) {Text("+",color=Tokens.accent,style=MaterialTheme.typography.titleMedium)}
                    }
                }
            }
        }
    }
}
