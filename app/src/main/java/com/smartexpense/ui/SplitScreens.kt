package com.smartexpense.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import java.time.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun SplitScreen(s:Snapshot,onAdd:()->Unit,onFriends:()->Unit,onGroups:()->Unit,onRepay:(Debt?,String?)->Unit,onDetails:(String)->Unit,onOffset:(String,String,Long,String?)->Unit,onRepeatGroup:(Group)->Unit={}) {
    var group by rememberSaveable {mutableStateOf<String?>(null)}
    var offsetPerson by rememberSaveable {mutableStateOf<String?>(null)}
    var offsetAmount by rememberSaveable {mutableStateOf(0L)}
    var offsetGroup by rememberSaveable {mutableStateOf<String?>(null)}
    val debts=s.debts.filter {group==null || it.groupId==group}
    fun name(id:String)=s.people.find {it.id==id}?.let {personLabel(it,s.people)}?:"Participant"
    val net=mutableMapOf<String,Long>()
    debts.forEach {debt->net[debt.from]=Math.subtractExact(net[debt.from]?:0,debt.outstanding);net[debt.to]=Math.addExact(net[debt.to]?:0,debt.outstanding)}
    val suggestions=DebtRouting.suggestions(debts)
    val owed=Finance.total(debts.filter {it.to=="self"}.map {it.outstanding})
    val owing=Finance.total(debts.filter {it.from=="self"}.map {it.outstanding})
    Page("Split & settle","Shared bills and recorded repayments") {
        item {AdaptivePair(first={TextButton(onFriends,Modifier.fillMaxWidth()) {Text("Manage friends")}},second={TextButton(onGroups,Modifier.fillMaxWidth()) {Text("Manage groups")}})}
        item {Panel {
            Text(if(owed>=owing) "Net receivable" else "Net payable",color=Tokens.muted,style=MaterialTheme.typography.labelMedium)
            Text(Money.format(if(owed>=owing) owed-owing else owing-owed),style=MaterialTheme.typography.displaySmall,color=if(owed>=owing) Tokens.positive else Tokens.expense)
            HorizontalDivider(color=Tokens.border)
            AmountRow("You're owed",owed,Tokens.positive)
            AmountRow("You owe",owing,Tokens.expense)
            Text("Owed money is separate from available account cash.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
        }}
        item {AdaptivePair(first={Button(onAdd,Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Add shared expense")}},second={OutlinedButton({onRepay(null,group)},Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Record repayment")}})}
        if(s.groups.isNotEmpty()) item {Panel {
            SectionHeading("Your usual groups")
            val recent=s.records.firstOrNull {it.kind==Kind.EXPENSE && !it.reversed && it.groupId!=null}?.groupId
            s.groups.find {it.id==recent}?.let {last->EditorialPrimaryButton("Same group again · ${last.name}") {onRepeatGroup(last)}}
            s.groups.forEach {g->OutlinedButton({onRepeatGroup(g)},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("New bill · ${g.name}")}}
        }}
        item {Choice("Group",group?:"all",listOf("all" to "All groups")+s.groups.map {it.id to it.name}) {group=if(it=="all") null else it}}
        val friends=s.people.filter {it.id!="self" && debts.any {d->d.outstanding>0 && (d.from==it.id || d.to==it.id)}}
        if(friends.isNotEmpty()) item {Panel {
            SectionHeading("Between you and friends")
            friends.forEach {person->
                val receive=Finance.total(debts.filter {it.from==person.id && it.to=="self"}.map {it.outstanding})
                val pay=Finance.total(debts.filter {it.from=="self" && it.to==person.id}.map {it.outstanding})
                if(receive>0) AmountRow("${name(person.id)} owes you",receive)
                if(pay>0) AmountRow("You owe ${name(person.id)}",pay)
            }
            Text("Original debts stay separate until a repayment or an explicit offset is recorded.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
        }}
        item {SectionHeading("People · net positions")}
        if(net.isEmpty()) item {EmptyLedger("Start a shared bill","Add friends and record a bill to calculate everyone's share.","Manage friends",onFriends)}
        net.forEach {(id,amount)->item(key="position-$id") {
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                InitialAvatar(name(id))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(name(id),style=MaterialTheme.typography.titleMedium)
                    Text(if(amount==0L) {if(debts.any {it.outstanding>0 && (it.from==id || it.to==id)}) "Net zero · original debts remain" else "Settled across selected bills"} else "${if(amount>0) "Net receivable" else "Net payable"} · selected bills",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
                    Text(Money.format(if(amount>=0) amount else -amount),style=MaterialTheme.typography.titleMedium,color=if(amount>=0) Tokens.positive else Tokens.expense)
                }
            }
            HorizontalDivider(color=Tokens.border)
        }}
        if(suggestions.isNotEmpty()) item {Panel {
            Text("Suggested settlements",style=MaterialTheme.typography.titleMedium)
            suggestions.forEach {Text("${name(it.from)} pays ${name(it.to)} ${Money.format(it.amount)}",style=MaterialTheme.typography.bodyMedium)}
            Text("Suggestions don't move money. Record a real repayment to update the original debts.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        }}
        s.people.filter {it.id!="self"}.forEach {person->
            val inward=Finance.total(debts.filter {it.from==person.id && it.to=="self"}.map {it.outstanding})
            val outward=Finance.total(debts.filter {it.from=="self" && it.to==person.id}.map {it.outstanding})
            val offset=minOf(inward,outward)
            if(offset>0) item {Panel {Text("Mutual offset · ${personLabel(person,s.people)}",style=MaterialTheme.typography.titleMedium);Text("Cancel equal opposite debts without moving cash.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall);OutlinedButton({offsetPerson=person.id;offsetAmount=offset;offsetGroup=group}) {Text("Record offset ${Money.format(offset)}")}}}
        }
        item {SectionHeading("Original bill obligations")}
        debts.groupBy {it.transactionId}.forEach {(transaction,billDebts)->item(key="bill-$transaction") {Panel {
            val original=Finance.total(billDebts.map {it.original})
            val outstanding=Finance.total(billDebts.map {it.outstanding})
            SectionHeading(s.records.find {it.id==transaction}?.description?:"Shared bill","View bill") {onDetails(transaction)}
            if(original>0) LinearProgressIndicator(progress={((original-outstanding).toDouble()/original.toDouble()).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=Tokens.accent,trackColor=Tokens.border)
            Text("${Money.format(outstanding)} outstanding of ${Money.format(original)} original debt",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            billDebts.forEach {debt->
                Text("${name(debt.from)} owes ${name(debt.to)}",style=MaterialTheme.typography.titleSmall)
                AmountRow("Outstanding",debt.outstanding,if(debt.outstanding>0) Tokens.expense else Tokens.positive)
                if(debt.outstanding>0) TextButton({onRepay(debt,group)}) {Text("Record payment")}
            }
        }}}
        item {SectionHeading("Settlement history")}
        val history=s.records.filter {it.kind in listOf(Kind.REPAYMENT,Kind.OFFSET) && (group==null || it.groupId==group)}.take(30)
        if(history.isEmpty()) item {Text("Recorded repayments and offsets appear here.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
        history.forEach {record->item(key="settlement-${record.id}") {TransactionRow(record,s) {onDetails(record.id)}}}
    }
    offsetPerson?.let {person->OffsetReviewSurface(name(person),offsetAmount,s.groups.find {it.id==offsetGroup}?.name?:"All groups",{offsetPerson=null}) {onOffset(person,"self",offsetAmount,offsetGroup);offsetPerson=null}}
}
@Composable fun RepaymentScreen(s:Snapshot,initialDebt:Debt?,model:AppViewModel,parsed:ParsedDraft?=null,groupId:String?=null,onSaved:()->Unit) {
    val appState by model.state.collectAsStateWithLifecycle()
    var from by rememberSaveable(initialDebt?.id) {mutableStateOf(initialDebt?.from?:parsed?.personId.orEmpty())};var to by rememberSaveable(initialDebt?.id) {mutableStateOf(initialDebt?.to?:if(parsed!=null) "self" else "")}
    var amount by rememberSaveable {mutableStateOf(parsed?.amount?.let(Money::input).orEmpty())};var account by rememberSaveable {mutableStateOf("")};var note by rememberSaveable {mutableStateOf(parsed?.description.orEmpty())};var date by rememberSaveable {mutableStateOf(LocalDateTime.now().withNano(0).toString())}
    val eligible=s.debts.filter {it.outstanding>0 && (initialDebt==null || it.id==initialDebt.id) && (groupId==null || it.groupId==groupId)}
    val due=DebtRouting.capacity(from,to,eligible);val parsed=runCatching {Money.parse(amount)}.getOrNull()
    val needsAccount=from=="self" || to=="self"
    val valid=parsed!=null && parsed>0 && parsed<=due && (!needsAccount || account.isNotBlank()) && runCatching {LocalDateTime.parse(date)}.isSuccess
    val requestId=rememberSaveable {java.util.UUID.randomUUID().toString()}
    var reviewing by rememberSaveable {mutableStateOf(false)}
    Page("Record repayment","Repayments update cash and outstanding debts; they do not create income.") {
        item {Panel {
            Choice("Paid by",from,s.people.map {it.id to personLabel(it,s.people)}) {from=it;if(to==it) to=""}
            Choice("Paid to",to,s.people.filter {it.id!=from}.map {it.id to personLabel(it,s.people)}) {to=it}
            AmountRow("Outstanding in this direction",due)
            Text("Payment allocations retain the original debts. Valid indirect settlements reduce each debt along the path without moving an intermediary's cash.",color=Tokens.muted)
            if(groupId!=null) Text("Group: ${s.groups.find {it.id==groupId}?.name}",color=Tokens.muted)
            Field("Amount (₹)",amount,{amount=it},true)
            if(needsAccount) Choice(if(to=="self") "Receive repayment into" else "Pay repayment from",account,s.accounts.filterNot {it.archived}.map {it.id to "${it.name} · ${editorialMoney(it.balance)}"}) {account=it}
            DateTimeField(date) {date=it};Field("Note",note,{note=it})
            if(parsed!=null && parsed in 1..due) AmountRow("Remaining after repayment",due-parsed)
            EditorialPrimaryButton("Review repayment",valid&&!appState.busy) {reviewing=true}
        }}
    }
    if(reviewing && parsed!=null) RepaymentReviewSurface(
        s.people.find {it.id==from}?.let {personLabel(it,s.people)}?:"Choose payer",
        s.people.find {it.id==to}?.let {personLabel(it,s.people)}?:"Choose recipient",
        parsed,due,s.accounts.find {it.id==account}?.name?.takeIf {needsAccount},date,note,appState.busy || !valid,
        {reviewing=false}, {
            model.action("Repayment saved") {
                model.repository.recordRepayment(from,to,parsed,account.ifBlank {null},note,initialDebt?.id,
                    LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),requestId=requestId,groupId=groupId)
                reviewing=false;onSaved()
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettlementReviewSheet(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    val density=LocalDensity.current
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Tokens.background,contentColor=Tokens.text,dragHandle=null) {
        CompositionLocalProvider(LocalDensity provides density) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(Tokens.pageInset),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(density.fontScale>1.3f) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {TextButton(onDismiss) {Text("Cancel")}}
                Text(title,Modifier.fillMaxWidth().semantics {heading()},style=MaterialTheme.typography.headlineMedium)
            } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.headlineMedium)
                TextButton(onDismiss) {Text("Cancel")}
            }
            content()
        }
        }
    }
}

/** Opening or dismissing the review never records a payment. */
@Composable fun RepaymentReviewSurface(fromName:String,toName:String,amount:Long,due:Long,accountName:String?,date:String,note:String,busy:Boolean,onDismiss:()->Unit,onConfirm:()->Unit) {
    SettlementReviewSheet("Review repayment",onDismiss) {
        Text(Money.format(amount),style=MaterialTheme.typography.displaySmall)
        Panel {
            Text("$fromName pays $toName",style=MaterialTheme.typography.titleLarge)
            if(accountName!=null) Text("Payment account · $accountName",color=Tokens.muted)
            Text(runCatching {LocalDateTime.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))}.getOrDefault(date),color=Tokens.muted)
            if(note.isNotBlank()) Text(note)
            HorizontalDivider(color=Tokens.border)
            AmountRow("Outstanding before",due)
            AmountRow("Remaining after repayment",due-amount)
        }
        Text("Confirm after the money has changed hands. This updates cash and the original debts without creating income.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        EditorialPrimaryButton("Confirm repayment",!busy,onConfirm)
    }
}

@Composable fun OffsetReviewSurface(personName:String,amount:Long,groupName:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    SettlementReviewSheet("Review mutual offset",onDismiss) {
        Text(Money.format(amount),style=MaterialTheme.typography.displaySmall)
        Panel {
            Text("You and $personName",style=MaterialTheme.typography.titleLarge)
            Text(groupName,color=Tokens.muted)
            Text("Reduce debts in each direction by ${Money.format(amount)}. No cash moves between accounts.",color=Tokens.muted)
        }
        Text("The original bills and this offset remain in settlement history.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        EditorialPrimaryButton("Confirm offset",onClick=onConfirm)
    }
}

