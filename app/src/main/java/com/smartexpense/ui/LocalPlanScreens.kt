package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.smartexpense.domain.*
import java.time.*

@Composable fun GroupDefaultsScreen(s:Snapshot,busy:Boolean,onSave:(GroupDefault)->Unit,onGroups:()->Unit) {
    var groupId by rememberSaveable {mutableStateOf(s.groups.firstOrNull()?.id.orEmpty())}
    val group=s.groups.find {it.id==groupId}
    val existing=s.groupDefaults.find {it.groupId==groupId}
    var method by rememberSaveable(groupId) {mutableStateOf(existing?.method?:"Equal")}
    var values by rememberSaveable(groupId) {mutableStateOf(HashMap(existing?.values.orEmpty()))}
    Page("Default group splits","Start each shared bill with the shares you prefer") {
        if(group==null) item {EmptyLedger("Create a group first","Saved defaults follow your group members.","Manage groups",onGroups)}
        else item {Panel {
            Choice("Group",groupId,s.groups.map {it.id to it.name}) {groupId=it}
            Choice("Default split",method,listOf("Equal" to "Equal","Percentage" to "Percentage","Weighted" to "Weighted")) {method=it}
            val people=(listOf("self")+group.members).distinct()
            if(method!="Equal") people.forEach {id->Field("${s.people.find {it.id==id}?.name?:id} ${if(method=="Percentage") "percent" else "weight"}",values[id].orEmpty(),{values=HashMap(values+(id to it))},true)}
            val default=GroupDefault(groupId,method,if(method=="Equal") emptyMap() else values.filterKeys {it in people})
            val validation=runCatching {default.rule(people)}
            if(validation.isFailure && method!="Equal") Text(validation.exceptionOrNull()?.message?:"Complete all shares",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            Text("Defaults fill a draft. Review payment amounts and shares before saving each bill. Changing group membership clears the old default.",color=Tokens.muted)
            EditorialPrimaryButton("Save group default",validation.isSuccess && !busy) {onSave(default)}
        }}
    }
}

@Composable fun RecurringScreen(s:Snapshot,busy:Boolean,onSave:(RecurringTemplate,()->Unit)->Unit,onDelete:(String)->Unit,onRecord:(RecurringTemplate,LocalDate)->Unit) {
    var editing by rememberSaveable {mutableStateOf(false)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    Page("Recurring entries","Reminders to record money, never automatic spending") {
        item {EditorialPrimaryButton("New recurring template",!busy) {selected=null;editing=true}}
        if(s.templates.isEmpty()) item {EmptyLedger("Build a routine","Save expenses or income you record regularly.")}
        val recorded=s.records.map {it.id}.toSet()
        s.templates.forEach {template->item(key=template.id) {Panel {
            SectionHeading(template.description)
            AmountRow("${template.kind.name.lowercase()} · ${template.frequency}",template.amount)
            Text("${s.accounts.find {it.id==template.accountId}?.name?:"Unavailable account"} · from ${template.firstDate}",color=Tokens.muted)
            val due=template.occurrences(LocalDate.now()).filter {template.entryId(it) !in recorded}
            Text("${due.size} unrecorded dates in the latest 400 occurrences",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            // Keep long histories usable; older pending dates follow as newer ones are recorded.
            due.takeLast(10).reversed().forEach {date->OutlinedButton({onRecord(template,date)},Modifier.fillMaxWidth(),enabled=!busy && s.accounts.any {it.id==template.accountId && !it.archived}) {Text("Review & record · $date")}}
            TextButton({selected=template.id;editing=true},enabled=!busy) {Text("Edit template")}
            TextButton({onDelete("template:${template.id}")},enabled=!busy) {Text("Delete template · keep saved transactions")}
        }}}
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
    Page("Savings goals","Plan the money you want to set aside") {
        item {Text("Goal allocations track plans. They do not move money or prove cash is saved. Record real transfers in Wallet & GPay.",color=Tokens.muted)}
        item {EditorialPrimaryButton("New savings goal",!busy) {selected=null;editor=true}}
        if(s.goals.isEmpty()) item {EmptyLedger("Give your savings a purpose","Set a target and track planned allocations.")}
        s.goals.forEach {goal->item(key=goal.id) {Panel {
            SectionHeading(goal.name)
            AmountRow("Planned allocations",goal.allocated);AmountRow("Target",goal.target)
            LinearProgressIndicator(progress={(goal.allocated.toDouble()/goal.target).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=Tokens.accent,trackColor=Tokens.border)
            EditorialPrimaryButton("Add planned allocation",!busy && goal.allocated<goal.target) {allocating=goal.id}
            goal.contributions.takeLast(10).reversed().forEach {AmountRow(it.date,it.amount)}
            TextButton({selected=goal.id;editor=true},enabled=!busy) {Text("Edit goal")}
            TextButton({onDelete("goal:${goal.id}")},enabled=!busy) {Text("Delete goal · keep account balances")}
        }}}
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
        EditorialBottomSheet("Allocate toward ${goal.name}",{if(!busy) allocating=null}) {
            Field("Planned amount (₹)",amount,{amount=it},true)
            Text("This changes goal progress only. It does not deduct money from Wallet or GPay.",color=Tokens.muted)
            val value=runCatching {Money.parse(amount).also {require(it>0 && it<=goal.target-goal.allocated)}}.getOrNull()
            EditorialPrimaryButton("Save planned allocation",value!=null && !busy) {onAllocate(goal.id,value!!) {allocating=null}}
        }
    }
}
