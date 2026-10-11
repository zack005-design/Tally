package com.smartexpense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.domain.*

/** Dedicated, keyboard-safe secondary view; closing never commits its draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ManagementEditorSurface(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    val density=LocalDensity.current
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        modifier=Modifier.fillMaxSize(),containerColor=Tokens.background,contentColor=Tokens.text,dragHandle=null) {
        CompositionLocalProvider(LocalDensity provides density) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=Tokens.pageInset,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onDismiss,Modifier.semantics {contentDescription="Back"}) {Text("←",style=MaterialTheme.typography.titleLarge)}
                if(density.fontScale<=1.3f) Text(title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.headlineMedium)
                else Spacer(Modifier.weight(1f))
                TextButton(onDismiss) {Text("Cancel")}
            }
            if(density.fontScale>1.3f) Text(title,Modifier.fillMaxWidth().padding(horizontal=Tokens.pageInset).padding(bottom=12.dp).semantics {heading()},style=MaterialTheme.typography.headlineMedium)
            HorizontalDivider(color=Tokens.border)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Tokens.pageInset),
                verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
        }
        }
    }
}

@Composable private fun ManagementRow(title:String,subtitle:String,amount:Long?=null,symbol:String?=null,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=Tokens.card,color=Tokens.surface) {
        Row(Modifier.padding(16.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(symbol==null) InitialAvatar(title) else Surface(shape=CircleShape,color=Tokens.secondarySurface,modifier=Modifier.size(48.dp)) {Box(contentAlignment=Alignment.Center) {Text(symbol,style=MaterialTheme.typography.titleLarge)}}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(subtitle,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                if(amount!=null) Text(Money.format(amount),style=MaterialTheme.typography.titleMedium)
            }
            Text("→",color=Tokens.muted)
        }
    }
}

@Composable fun AccountsScreen(s:Snapshot,model:AppViewModel,onDetails:(String)->Unit) {
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var creating by rememberSaveable {mutableStateOf(false)}
    Page("Accounts","Balances are reconstructed from recorded account movements.") {
        item {EditorialPrimaryButton("New account") {creating=true}}
        if(s.accounts.isEmpty()) item {EmptyLedger("Your accounts","Create an account to record balances and payments.")}
        s.accounts.forEach {account->item(key=account.id) {ManagementRow(account.name,"${account.type}${if(account.archived) " · archived" else ""}",account.balance) {selected=account.id}}}
    }
    if(creating) AccountEditor(null,model) {creating=false}
    s.accounts.find {it.id==selected}?.let {account->
        var editing by rememberSaveable(account.id) {mutableStateOf(false)}
        var reconciling by rememberSaveable(account.id) {mutableStateOf(false)}
        if(!editing && !reconciling) ManagementEditorSurface(account.name,{selected=null}) {
            Panel {Text("Recorded balance",color=Tokens.muted);Text(Money.format(account.balance),style=MaterialTheme.typography.displaySmall);Text("${account.type}${if(account.archived) " · archived" else ""}",color=Tokens.muted)}
            EditorialPrimaryButton("Rename account") {editing=true}
            OutlinedButton({reconciling=true},Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Reconcile balance")}
            TextButton({model.action("Account updated") {model.repository.updateAccount(account.copy(archived=!account.archived))}}) {Text(if(account.archived) "Reactivate account" else "Archive account")}
            SectionHeading("Account history")
            val history=s.records.filter {account.id in it.accountIds}.take(30)
            if(history.isEmpty()) Text("No account movements yet.",color=Tokens.muted)
            history.forEach {TransactionRow(it) {selected=null;onDetails(it.id)}}
        }
        if(editing) AccountEditor(account,model) {editing=false}
        if(reconciling) {
            var correction by rememberSaveable(account.id) {mutableStateOf("")}
            val state by model.state.collectAsStateWithLifecycle()
            ManagementEditorSurface("Reconcile balance",{reconciling=false}) {
                AmountRow("Recorded balance",account.balance)
                Text("Record the difference between the actual and recorded balances as an account correction.",color=Tokens.muted)
                Field("Actual balance (₹)",correction,{correction=it},true)
                EditorialPrimaryButton("Record balance correction",!state.busy && runCatching {Money.parse(correction)}.isSuccess) {
                    model.action("Reconciliation recorded") {model.repository.reconcile(account.id,Money.parse(correction));reconciling=false}
                }
            }
        }
    }
}

@Composable private fun AccountEditor(account:Account?,model:AppViewModel,onDismiss:()->Unit) {
    var name by rememberSaveable(account?.id) {mutableStateOf(account?.name.orEmpty())}
    var type by rememberSaveable(account?.id) {mutableStateOf(account?.type?:"Cash")}
    var opening by rememberSaveable(account?.id) {mutableStateOf("0")}
    val state by model.state.collectAsStateWithLifecycle()
    ManagementEditorSurface(if(account==null) "New account" else "Rename account",onDismiss) {
        Field("Account name",name,{name=it})
        if(account==null) {
            Choice("Type",type,listOf("Cash","Bank","Wallet","Other").map {it to it}) {type=it}
            Field("Opening balance (₹)",opening,{opening=it},true)
            Text("This is the account balance before any recorded movements.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        }
        EditorialPrimaryButton(if(account==null) "Create account" else "Save account",!state.busy && name.isNotBlank() && (account!=null || runCatching {Money.parse(opening)}.isSuccess)) {
            model.action(if(account==null) "Account created" else "Account renamed") {
                if(account==null) model.repository.createAccount(name,type,Money.parse(opening)) else model.repository.updateAccount(account.copy(name=name))
                onDismiss()
            }
        }
    }
}

@Composable fun CategoriesScreen(s:Snapshot,model:AppViewModel) {
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var creating by rememberSaveable {mutableStateOf(false)}
    Page("Categories","Archived categories remain attached to past transactions.") {
        item {EditorialPrimaryButton("New category") {creating=true}}
        s.categories.forEach {category->item(key=category.id) {ManagementRow(category.name,if(category.archived) "Archived" else "Transaction category",symbol=category.icon) {selected=category.id}}}
    }
    if(creating) CategoryEditor(null,model) {creating=false}
    s.categories.find {it.id==selected}?.let {category->CategoryEditor(category,model) {selected=null}}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CategoryEditor(category:Category?,model:AppViewModel,onDismiss:()->Unit) {
    var name by rememberSaveable(category?.id) {mutableStateOf(category?.name.orEmpty())}
    var icon by rememberSaveable(category?.id) {mutableStateOf(category?.icon?:"•")}
    var color by rememberSaveable(category?.id) {mutableStateOf(category?.color?.let {java.lang.Long.toHexString(it).takeLast(6).padStart(6,'0')}?:"716F69")}
    var custom by rememberSaveable(category?.id) {mutableStateOf(false)}
    val state by model.state.collectAsStateWithLifecycle()
    val colors=listOf("181818","716F69","98958E","D6D4CF","EAE8E3","F0EEEA")
    ManagementEditorSurface(if(category==null) "New category" else "Edit category",onDismiss) {
        Field("Category name",name,{name=it});Field("Icon or symbol",icon,{icon=it})
        Text("Category color",style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            colors.forEach {hex->Surface(onClick={color=hex},modifier=Modifier.size(48.dp).semantics {contentDescription="Color #$hex${if(color.equals(hex,true)) ", selected" else ""}"},shape=CircleShape,color=Color(0xFF000000 or hex.toLong(16)),border=BorderStroke(if(color.equals(hex,true)) 3.dp else 1.dp,Tokens.border)) {Box(contentAlignment=Alignment.Center) {if(color.equals(hex,true)) Text("✓",color=if(hex in colors.take(3)) Color.White else Tokens.text)}}}
        }
        Text("Selected: #${color.uppercase()}",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        TextButton({custom=!custom}) {Text(if(custom) "Hide custom color" else "Use custom color")}
        if(custom) Field("Custom hexadecimal color",color,{color=it.removePrefix("#")})
        EditorialPrimaryButton(if(category==null) "Create category" else "Save changes",!state.busy && name.isNotBlank() && Regex("[A-Fa-f0-9]{6}").matches(color)) {
            model.action(if(category==null) "Category created" else "Category updated") {
                val originalColor=category?.color?.let {java.lang.Long.toHexString(it).takeLast(6).padStart(6,'0')}
                val storedColor=if(category!=null && color.equals(originalColor,true)) category.color else 0xFF000000 or color.toLong(16)
                model.repository.saveCategory(name,icon,storedColor,category?.id,category?.archived?:false);onDismiss()
            }
        }
        if(category!=null) TextButton({model.action("Category updated") {model.repository.saveCategory(category.name,category.icon,category.color,category.id,!category.archived);onDismiss()}}) {Text(if(category.archived) "Reactivate category" else "Archive category")}
    }
}

@Composable fun PeopleScreen(s:Snapshot,model:AppViewModel,onDetails:(String)->Unit) {
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var creating by rememberSaveable {mutableStateOf(false)}
    Page("Friends","Names may repeat. Each person has a stable identifier.") {
        item {EditorialPrimaryButton("Add a friend") {creating=true}}
        if(s.people.none {it.id!="self"}) item {EmptyLedger("Add your first friend","Friends need no app or account.")}
        s.people.filter {it.id!="self"}.forEach {person->item(key=person.id) {
            val receivable=Finance.total(s.debts.filter {it.from==person.id && it.to=="self"}.map {it.outstanding})
            val payable=Finance.total(s.debts.filter {it.to==person.id && it.from=="self"}.map {it.outstanding})
            ManagementRow(personLabel(person,s.people),"${if(receivable>=payable) "Net receivable" else "Net payable"}",if(receivable>=payable) receivable-payable else payable-receivable) {selected=person.id}
        }}
    }
    if(creating) PersonEditor(null,model) {creating=false}
    s.people.find {it.id==selected}?.let {person->
        var editing by rememberSaveable(person.id) {mutableStateOf(false)}
        if(!editing) ManagementEditorSurface(personLabel(person,s.people),{selected=null}) {
            InitialAvatar(person.name)
            if(person.aliases.any {it.isNotBlank()}) Text(person.aliases.filter {it.isNotBlank()}.joinToString(" · "),color=Tokens.muted)
            Panel {
                AmountRow("Owes you",Finance.total(s.debts.filter {it.from==person.id && it.to=="self"}.map {it.outstanding}))
                AmountRow("You owe",Finance.total(s.debts.filter {it.to==person.id && it.from=="self"}.map {it.outstanding}))
            }
            EditorialPrimaryButton("Edit profile") {editing=true}
            SectionHeading("Original debts")
            val debts=s.debts.filter {it.from==person.id || it.to==person.id}
            if(debts.isEmpty()) Text("No shared debts yet.",color=Tokens.muted)
            debts.forEach {debt->Panel {
                Text("${s.people.find {it.id==debt.from}?.name} → ${s.people.find {it.id==debt.to}?.name}",style=MaterialTheme.typography.titleMedium)
                AmountRow("Remaining",debt.outstanding);AmountRow("Original",debt.original)
                TextButton({selected=null;onDetails(debt.transactionId)}) {Text("View original transaction")}
            }}
            SectionHeading("Transaction and settlement history")
            val history=s.records.filter {person.id in it.personIds}.take(50)
            if(history.isEmpty()) Text("No transactions yet.",color=Tokens.muted)
            history.forEach {TransactionRow(it) {selected=null;onDetails(it.id)}}
        }
        if(editing) PersonEditor(person,model) {editing=false}
    }
}

@Composable private fun PersonEditor(person:Person?,model:AppViewModel,onDismiss:()->Unit) {
    var name by rememberSaveable(person?.id) {mutableStateOf(person?.name.orEmpty())}
    var aliases by rememberSaveable(person?.id) {mutableStateOf(person?.aliases?.joinToString("|").orEmpty())}
    val state by model.state.collectAsStateWithLifecycle()
    ManagementEditorSurface(if(person==null) "Add a friend" else "Edit profile",onDismiss) {
        Field("Display name",name,{name=it});Field("Aliases, separated by |",aliases,{aliases=it})
        Text("Aliases help identify this friend when describing expenses.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        EditorialPrimaryButton(if(person==null) "Create friend" else "Save profile",name.isNotBlank() && !state.busy) {
            model.action(if(person==null) "Friend created" else "Friend updated") {
                if(person==null) model.repository.createPerson(name,aliases) else model.repository.updatePerson(person.copy(name=name,aliases=aliases.split('|')))
                onDismiss()
            }
        }
    }
}

@Composable fun GroupsScreen(s:Snapshot,model:AppViewModel) {
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var creating by rememberSaveable {mutableStateOf(false)}
    Page("Groups","Manage local groups; friends need no app or account.") {
        item {EditorialPrimaryButton("New group") {creating=true}}
        if(s.groups.isEmpty()) item {EmptyLedger("Bring a group together","Create a group with at least two members.")}
        s.groups.forEach {group->item(key=group.id) {ManagementRow(group.name,"${group.members.size} members",s.groupSpending[group.id]?:0) {selected=group.id}}}
    }
    if(creating) GroupEditor(null,s,model) {creating=false}
    s.groups.find {it.id==selected}?.let {group->
        var editing by rememberSaveable(group.id) {mutableStateOf(false)}
        if(!editing) ManagementEditorSurface(group.name,{selected=null}) {
            Panel {AmountRow("Total group bills",s.groupSpending[group.id]?:0)}
            EditorialPrimaryButton("Edit group") {editing=true}
            SectionHeading("Members")
            group.members.forEach {id->val person=s.people.find {it.id==id};Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {InitialAvatar(person?.name?:id);Text(person?.let {personLabel(it,s.people)}?:id)}}
            SectionHeading("Group spending")
            val bills=s.records.filter {it.groupId==group.id}
            if(bills.isEmpty()) Text("No group transactions yet.",color=Tokens.muted)
            bills.forEach {Panel {Text(it.description,style=MaterialTheme.typography.titleMedium);AmountRow("Recorded amount",it.amount)}}
        }
        if(editing) GroupEditor(group,s,model) {editing=false}
    }
}

@Composable private fun GroupEditor(group:Group?,s:Snapshot,model:AppViewModel,onDismiss:()->Unit) {
    var name by rememberSaveable(group?.id) {mutableStateOf(group?.name.orEmpty())}
    var selected by rememberSaveable(group?.id) {mutableStateOf(group?.members?:listOf("self"))}
    val state by model.state.collectAsStateWithLifecycle()
    ManagementEditorSurface(if(group==null) "New group" else "Edit group",onDismiss) {
        Field("Group name",name,{name=it})
        Text("Members · ${selected.size} selected",style=MaterialTheme.typography.titleMedium)
        s.people.forEach {person->
            val isMember=person.id in selected
            Surface(onClick={selected=if(isMember) selected-person.id else selected+person.id},modifier=Modifier.fillMaxWidth().semantics {this.selected=isMember},shape=Tokens.field,color=if(isMember) Tokens.secondarySurface else Tokens.surface) {
            Row(Modifier.padding(12.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Checkbox(person.id in selected,null);Text(personLabel(person,s.people),Modifier.weight(1f))
            }
        }}
        if(selected.size<2) Text("Choose at least two members.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        EditorialPrimaryButton("Save group",!state.busy && name.isNotBlank() && selected.size>=2) {model.action("Group saved") {model.repository.saveGroup(name,selected,group?.id);onDismiss()}}
    }
}
