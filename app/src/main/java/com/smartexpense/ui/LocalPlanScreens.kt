package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.*

@Composable fun GroupDefaultsScreen(s:Snapshot,busy:Boolean,onSave:(GroupDefault)->Unit,onGroups:()->Unit) {
    var selectedGroupId by rememberSaveable {mutableStateOf(s.groups.firstOrNull()?.id.orEmpty())}
    val groupId=selectedGroupId.takeIf {id->s.groups.any {it.id==id}}?:s.groups.firstOrNull()?.id.orEmpty()
    LaunchedEffect(groupId) {selectedGroupId=groupId}
    val group=s.groups.find {it.id==groupId}
    val existing=s.groupDefaults.find {it.groupId==groupId}
    var method by rememberSaveable(groupId) {mutableStateOf(existing?.method?:"Equal")}
    var values by rememberSaveable(groupId) {mutableStateOf(HashMap(existing?.values.orEmpty()))}
    var choosingGroup by rememberSaveable {mutableStateOf(false)}
    val savePeople=group?.let {(listOf("self")+it.members).distinct()}.orEmpty()
    val saveDefault=GroupDefault(groupId,method,if(method in listOf("Percentage","Weighted")) values.filterKeys {it in savePeople} else emptyMap())
    val saveValid=group!=null && runCatching {saveDefault.validate(savePeople)}.isSuccess
    Scaffold(containerColor=Tokens.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={if(group!=null) Column(Modifier.padding(horizontal=Tokens.pageInset,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        EditorialPrimaryButton("Save default",saveValid && !busy) {onSave(saveDefault)}
        Text("Applied to new drafts only.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
    }}) {padding->Box(Modifier.fillMaxSize().padding(padding)) {Page("Default split") {
        if(group==null) item {EmptyLedger("Create a group first","Saved defaults follow your group members.","Manage groups",onGroups)}
        else {
            val people=(listOf("self")+group.members).distinct()
            fun name(id:String)=if(id=="self") "You" else s.people.find {it.id==id}?.name?:id
            val default=GroupDefault(groupId,method,if(method in listOf("Percentage","Weighted")) values.filterKeys {it in people} else emptyMap())
            val valid=runCatching {default.validate(people)}.isSuccess
            val preview=runCatching {Finance.split(90000,people,default.rule(people))}
            item {SectionLabel("GROUP")}
            item {GroupedCard {Surface(onClick={choosingGroup=true},color=Tokens.paper,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Surface(shape=CircleShape,color=Color(0xFFFFEEE3),modifier=Modifier.size(40.dp)) {Box(contentAlignment=Alignment.Center) {Text(group.name.take(1).uppercase(),color=Color(0xFFC35A26),style=MaterialTheme.typography.titleMedium)}}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(group.name,style=MaterialTheme.typography.bodyLarge)
                        Text("${people.size} people · ${people.joinToString(", ",transform=::name)}",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                    }
                    Text("›",color=Tokens.muted,style=MaterialTheme.typography.titleLarge)
                }
            }}}
            item {SectionLabel("DEFAULT SPLIT METHOD")}
            item {DefaultMethodControl(method) {method=it}}
            item {Text("The selected method will be pre-selected when adding a new expense in this group.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
            if(method in listOf("Percentage","Weighted")) item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                people.forEach {id->Field("${name(id)} ${if(method=="Percentage") "percent" else "weight"}",values[id].orEmpty(),{values=HashMap(values+(id to it))},true)}
            }}}
            if(default.requiresBillDetails) item {ReferenceInfo(if(method=="Fixed") "Enter exact amounts for each person when you add a bill. No amounts are pre-filled." else "Add and assign items when you add a bill. Items and amounts belong to that bill.")}
            else item {GroupedCard {Column(Modifier.padding(horizontal=14.dp,vertical=12.dp)) {
                Text("Preview (₹900 expense)",Modifier.padding(bottom=12.dp),color=Tokens.muted,style=MaterialTheme.typography.titleSmall)
                HorizontalDivider(color=Tokens.border)
                preview.getOrNull()?.let {shares->people.forEachIndexed {index,id->
                    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=CircleShape,color=if(index%3==1) Color(0xFFFFEEE3) else Tokens.secondarySurface,modifier=Modifier.size(40.dp)) {Box(contentAlignment=Alignment.Center) {Text(name(id).take(1).uppercase(),color=Tokens.accent,style=MaterialTheme.typography.titleMedium)}}
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(name(id),style=MaterialTheme.typography.bodyLarge)
                            Text(when(method) {"Percentage"->"${values[id].orEmpty()}%";"Weighted"->"${values[id].orEmpty()} shares";else->"1 share"},color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                            if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) Text(referenceMoney(shares.getValue(id)),style=MaterialTheme.typography.titleSmall)
                        }
                        if(androidx.compose.ui.platform.LocalDensity.current.fontScale<=1.3f) Text(referenceMoney(shares.getValue(id)),style=MaterialTheme.typography.titleSmall)
                    }
                    if(index<people.lastIndex) GroupedDivider()
                }}?:Text(preview.exceptionOrNull()?.message?:"Complete all shares to preview this split.",Modifier.padding(vertical=16.dp),color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }}}

        }
    }
    }}
    if(choosingGroup) EditorialBottomSheet("Choose group",{choosingGroup=false}) {
        GroupedCard {s.groups.forEachIndexed {index,value->
            GroupedRow(value.name,R.drawable.ic_people,supporting="${(listOf("self")+value.members).distinct().size} people") {selectedGroupId=value.id;choosingGroup=false}
            if(index<s.groups.lastIndex) GroupedDivider()
        }}
    }
}

@Composable fun RecurringScreen(s:Snapshot,busy:Boolean,onSave:(RecurringTemplate,()->Unit)->Unit,onDelete:(String)->Unit,onRecord:(RecurringTemplate,LocalDate)->Unit) {
    var editing by rememberSaveable {mutableStateOf(false)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var details by rememberSaveable {mutableStateOf<String?>(null)}
    Page("Recurring entries") {
        item {Text("Upcoming",style=MaterialTheme.typography.titleSmall)}
        if(s.templates.isEmpty()) item {EmptyLedger("Build a routine","Save expenses or income you record regularly.")}
        val recorded=s.records.map {it.id}.toSet()
        if(s.templates.isNotEmpty()) item {GroupedCard {s.templates.forEachIndexed {index,template->
            val due=template.occurrences(LocalDate.now()).filter {template.entryId(it) !in recorded}
            GroupedRow(template.description,referenceSymbol(template.description,R.drawable.ic_calendar),supporting=due.lastOrNull()?.let {"Due $it"}?:"${template.frequency} · from ${template.firstDate}",value=referenceMoney(template.amount),tone=ReferenceTone.Lavender) {details=template.id}
            if(index<s.templates.lastIndex) GroupedDivider()
        }}}
        item {ReferenceAction("Add template",R.drawable.ic_add,!busy) {selected=null;editing=true}}
        item {ReferenceInfo("Review and save each occurrence. Recurring entries open drafts; they never record money automatically.")}
    }
    s.templates.find {it.id==details}?.let {template->
        EditorialBottomSheet(template.description,{details=null}) {
            AmountRow("${template.kind.name.lowercase()} · ${template.frequency}",template.amount)
            Text("${s.accounts.find {it.id==template.accountId}?.name?:"Unavailable account"} · from ${template.firstDate}",color=Tokens.muted)
            val recorded=s.records.map {it.id}.toSet()
            val due=template.occurrences(LocalDate.now()).filter {template.entryId(it) !in recorded}
            Text("${due.size} unrecorded dates",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            // Keep long histories usable; older pending dates follow as newer ones are recorded.
            due.takeLast(10).reversed().forEach {date->OutlinedButton({onRecord(template,date)},Modifier.fillMaxWidth(),enabled=!busy && s.accounts.any {it.id==template.accountId && !it.archived}) {Text("Review & record · $date")}}
            TextButton({details=null;selected=template.id;editing=true},enabled=!busy) {Text("Edit template")}
            TextButton({onDelete("template:${template.id}");details=null},enabled=!busy) {Text("Delete template · keep saved transactions")}
            ReferenceInfo("Due entries become drafts. Your balance changes only when you save a transaction.")
        }
    }
    if(editing) {
        val existing=s.templates.find {it.id==selected}
        var id by rememberSaveable(selected) {mutableStateOf(existing?.id?:java.util.UUID.randomUUID().toString())}
        var name by rememberSaveable(selected) {mutableStateOf(existing?.description.orEmpty())}
        var amount by rememberSaveable(selected) {mutableStateOf(existing?.amount?.let(Money::input).orEmpty())}
        var kind by rememberSaveable(selected) {mutableStateOf(existing?.kind?.name?:Kind.EXPENSE.name)}
        var frequency by rememberSaveable(selected) {mutableStateOf(existing?.frequency?:"Monthly")}
        var first by rememberSaveable(selected) {mutableStateOf(existing?.firstDate?:LocalDate.now().toString())}
        var account by rememberSaveable(selected) {mutableStateOf(existing?.accountId?:s.walletSources?.walletId?:s.accounts.firstOrNull {!it.archived}?.id.orEmpty())}
        var datePicker by rememberSaveable {mutableStateOf(false)}
        EditorialBottomSheet("Recurring template",{if(!busy) editing=false}) {
            Field("Description",name,{name=it})
            Field("Amount (₹)",amount,{amount=it},true)
            Choice("Entry type",kind,listOf("EXPENSE" to "Expense","INCOME" to "Income")) {kind=it}
            Choice("Account",account,s.accounts.filterNot {it.archived}.map {it.id to it.name}) {account=it}
            Choice("Repeat",frequency,listOf("Daily","Weekly","Monthly").map {it to it}) {frequency=it}
            OutlinedButton({datePicker=true},Modifier.fillMaxWidth()) {Text("First date · $first")}
            Text("Due entries become drafts. Your balance changes only when you save a transaction.",color=Tokens.muted)
            val template=runCatching {RecurringTemplate(id,Kind.valueOf(kind),Money.parse(amount),name.trim(),account,first,frequency)}.getOrNull()
            EditorialPrimaryButton("Save recurring template",template!=null && !busy) {onSave(template!!) {editing=false}}
        }
        if(datePicker) EditorialDatePicker("First recurring date",LocalDate.parse(first),{datePicker=false}) {first=it.toString();datePicker=false}
    }
}

@Composable fun GoalsScreen(s:Snapshot,busy:Boolean,onSave:(String,Long,String?,()->Unit)->Unit,onAllocate:(String,Long,()->Unit)->Unit,onDelete:(String)->Unit) {
    var editor by rememberSaveable {mutableStateOf(false)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var allocating by rememberSaveable {mutableStateOf<String?>(null)}
    var details by rememberSaveable {mutableStateOf<String?>(null)}
    Page("Savings goals") {
        if(s.goals.isEmpty()) item {EmptyLedger("Give your savings a purpose","Set a target and track planned allocations.")}
        s.goals.forEach {goal->item(key=goal.id) {GroupedCard {
            Row(Modifier.fillMaxWidth().padding(start=14.dp,end=6.dp,top=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                ReferenceIcon(referenceSymbol(goal.name,R.drawable.ic_account),ReferenceTone.Lavender,48.dp)
                Column(Modifier.weight(1f)) {
                    TextButton({if(goal.allocated<goal.target) allocating=goal.id else details=goal.id},enabled=!busy,contentPadding=PaddingValues(0.dp)) {Text(goal.name,color=Tokens.text,style=MaterialTheme.typography.titleSmall)}
                    Text("${referenceMoney(goal.allocated)} of ${referenceMoney(goal.target)}",color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)
                }
                IconButton({details=goal.id},Modifier.semantics {contentDescription="Goal details · ${goal.name}"}) {Icon(painterResource(R.drawable.ic_more),null,tint=Tokens.muted)}
            }
            Row(Modifier.fillMaxWidth().padding(start=64.dp,end=16.dp,bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                ReferenceProgress((goal.allocated.toDouble()/goal.target).toFloat(),Modifier.weight(1f))
                Text("${(goal.allocated.toDouble()/goal.target*100).toInt()}%",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }
        }}}
        item {ReferenceAction("Add goal",R.drawable.ic_add,!busy) {selected=null;editor=true}}
        item {ReferenceInfo("Allocations are a plan. They do not move money or prove cash is saved. Record real transfers in Wallet & GPay.")}
    }
    s.goals.find {it.id==details}?.let {goal->
        EditorialBottomSheet(goal.name,{details=null}) {
            var historyLimit by rememberSaveable(goal.id) {mutableStateOf(10)}
            SectionHeading(goal.name)
            AmountRow("Planned allocations",goal.allocated);AmountRow("Target",goal.target)
            ReferenceProgress((goal.allocated.toDouble()/goal.target).toFloat(),Modifier.fillMaxWidth())
            EditorialPrimaryButton("Add planned allocation",!busy && goal.allocated<goal.target) {details=null;allocating=goal.id}
            goal.contributions.takeLast(historyLimit).reversed().forEach {AmountRow(it.date,it.amount)}
            if(goal.contributions.size>historyLimit) TextButton({historyLimit+=10}) {Text("Show older allocations")}
            TextButton({details=null;selected=goal.id;editor=true},enabled=!busy) {Text("Edit goal")}
            TextButton({onDelete("goal:${goal.id}");details=null},enabled=!busy) {Text("Delete goal · keep account balances")}
        }
    }
    if(editor) {
        val existing=s.goals.find {it.id==selected}
        var name by rememberSaveable(selected) {mutableStateOf(existing?.name.orEmpty())}
        var target by rememberSaveable(selected) {mutableStateOf(existing?.target?.let(Money::input).orEmpty())}
        EditorialBottomSheet("Savings goal",{if(!busy) editor=false}) {
            Field("Goal name",name,{name=it});Field("Target (₹)",target,{target=it},true)
            val value=runCatching {Money.parse(target).also {require(it>0 && it>=(existing?.allocated?:0))}}.getOrNull()
            EditorialPrimaryButton("Save goal",name.trim().isNotEmpty() && value!=null && !busy) {onSave(name,value!!,selected) {editor=false}}
        }
    }
    s.goals.find {it.id==allocating}?.let {goal->
        var amount by rememberSaveable(goal.id) {mutableStateOf("")}
        EditorialBottomSheet("Plan allocation",{if(!busy) allocating=null}) {
            GroupedCard {ReferenceValueRow(goal.name,"${referenceMoney(goal.allocated)} of ${referenceMoney(goal.target)}")}
            Field("Planned amount (₹)",amount,{amount=it},true)
            Text("This changes goal progress only. It does not deduct money from Wallet or GPay.",color=Tokens.muted)
            val value=runCatching {Money.parse(amount).also {require(it>0 && it<=goal.target-goal.allocated)}}.getOrNull()
            EditorialPrimaryButton("Save planned allocation",value!=null && !busy) {onAllocate(goal.id,value!!) {allocating=null}}
        }
    }
}

@Composable private fun DefaultMethodControl(selectedMethod:String,onSelect:(String)->Unit) {
    val options=listOf("Equal" to "Equal","Percentage" to "Percent","Weighted" to "Shares","Fixed" to "Exact","Itemized" to "Itemized")
    if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) EditorialSegmentedControl(selectedMethod,options,onSelect)
    else Row(Modifier.fillMaxWidth().background(Tokens.paper,Tokens.field).padding(4.dp)) {
        options.forEach {(id,label)->Surface(onClick={onSelect(id)},modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics {selected=id==selectedMethod;role=androidx.compose.ui.semantics.Role.Tab},shape=Tokens.field,color=if(id==selectedMethod) Tokens.accent else Tokens.secondarySurface,contentColor=if(id==selectedMethod) Color.White else Tokens.muted) {
            Box(Modifier.padding(horizontal=2.dp,vertical=8.dp),contentAlignment=Alignment.Center) {Text(label,style=MaterialTheme.typography.labelSmall)}
        }}
    }
}
