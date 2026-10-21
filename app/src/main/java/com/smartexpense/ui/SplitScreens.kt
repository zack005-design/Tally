package com.smartexpense.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import java.time.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SplitScreen(s:Snapshot,onAdd:()->Unit,onFriends:()->Unit,onGroups:()->Unit,onRepay:(Debt?,String?)->Unit,onDetails:(String)->Unit,onOffset:(String,String,Long,String?)->Unit,onRepeatGroup:(Group)->Unit={},onSettings:()->Unit={}) {
    var tools by rememberSaveable {mutableStateOf(false)}
    var search by rememberSaveable {mutableStateOf("")}
    var group by rememberSaveable {mutableStateOf<String?>(null)}
    var offsetPerson by rememberSaveable {mutableStateOf<String?>(null)}
    var offsetAmount by rememberSaveable {mutableStateOf(0L)}
    var offsetGroup by rememberSaveable {mutableStateOf<String?>(null)}
    var selectedFriend by rememberSaveable {mutableStateOf<String?>(null)}
    val debts=s.debts.filter {group==null || it.groupId==group}
    fun name(id:String)=s.people.find {it.id==id}?.let {personLabel(it,s.people)}?:"Participant"
    val net=mutableMapOf<String,Long>()
    debts.forEach {debt->net[debt.from]=Math.subtractExact(net[debt.from]?:0,debt.outstanding);net[debt.to]=Math.addExact(net[debt.to]?:0,debt.outstanding)}
    val suggestions=DebtRouting.suggestions(debts)
    val owed=Finance.total(debts.filter {it.to=="self"}.map {it.outstanding})
    val owing=Finance.total(debts.filter {it.from=="self"}.map {it.outstanding})
    selectedFriend?.let {personId->
        val person=s.people.find {it.id==personId}
        if(person!=null) {
            BackHandler {selectedFriend=null}
            val receive=Finance.total(debts.filter {it.from==personId && it.to=="self"}.map {it.outstanding})
            val pay=Finance.total(debts.filter {it.from=="self" && it.to==personId}.map {it.outstanding})
            Page(name(personId),onBack={selectedFriend=null},actions={TextButton(onFriends) {Text("Edit")}}) {
                item {Column(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    InitialAvatar(name(personId),Modifier.size(84.dp))
                    Text(name(personId),style=MaterialTheme.typography.headlineMedium)
                    Text((if(receive>pay) "+" else if(pay>receive) "−" else "")+referenceMoney(kotlin.math.abs(receive-pay)),style=MaterialTheme.typography.displaySmall,color=Tokens.accent)
                    Text(when {receive>pay->name(personId)+" owes you";pay>receive->"You owe "+name(personId);receive>0->"Net zero · original debts remain";else->"You're settled up"},color=Tokens.muted)
                }}
                item {SectionHeading("History")}
                s.records.filter {record->record.personIds.contains(personId)}.take(30).forEach {record->item(key=record.id) {GroupedCard {GroupedRow(record.description,com.smartexpense.R.drawable.ic_food,supporting=Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).toLocalDate().toString(),value=referenceMoney(record.amount),tone=ReferenceTone.Peach,onClick={onDetails(record.id)})}}}
                item {EditorialPrimaryButton("Record repayment") {onRepay(null,group)}}
                item {SectionHeading("More options")}
                item {GroupedCard {
                    GroupedRow("Edit friend",com.smartexpense.R.drawable.ic_compose,onClick=onFriends)
                    if(minOf(receive,pay)>0) {GroupedDivider();GroupedRow("Mutual offset",com.smartexpense.R.drawable.ic_transfer,supporting="Cancel matching debts",tone=ReferenceTone.Lavender,onClick={offsetPerson=personId;offsetAmount=minOf(receive,pay);offsetGroup=group})}
                }}
            }
            offsetPerson?.let {id->OffsetReviewSurface(name(id),offsetAmount,s.groups.find {it.id==offsetGroup}?.name?:"All groups",receive,pay,{offsetPerson=null}) {onOffset(id,"self",offsetAmount,offsetGroup);offsetPerson=null}}
            return
        }
    }
    Page("Friends",largeTitle=true,actions={TextButton(onFriends) {Text("Add",color=Tokens.accent,style=MaterialTheme.typography.bodyLarge)}}) {
        item {TextField(search,{search=it},Modifier.fillMaxWidth(),placeholder={Text("Search people",color=Tokens.muted)},leadingIcon={Text("⌕",style=MaterialTheme.typography.headlineSmall,color=Tokens.muted)},singleLine=true,shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),colors=TextFieldDefaults.colors(unfocusedContainerColor=Tokens.inputSurface,focusedContainerColor=Tokens.inputSurface,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,focusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent))}
        item {Column(Modifier.fillMaxWidth().padding(top=12.dp,bottom=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(referenceMoney(kotlin.math.abs(owed-owing)),style=MaterialTheme.typography.displayMedium)
            Text(if(owed>=owing) "Net amount owed to you" else "Net amount you owe",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("●  You're owed ${referenceMoney(owed)}",style=MaterialTheme.typography.bodySmall,color=Tokens.accent)
                Text("●  You owe ${referenceMoney(owing)}",style=MaterialTheme.typography.bodySmall,color=Tokens.expense)
            }
        }}
        if(group!=null) item {Choice("Group",group?:"all",listOf("all" to "All groups")+s.groups.map {it.id to it.name}) {group=if(it=="all") null else it}}
        item {Text("BALANCES",style=MaterialTheme.typography.labelSmall,color=Tokens.muted)}
        val friends=s.people.filter {it.id!="self" && (search.isBlank() || personLabel(it,s.people).contains(search,true) || it.aliases.any {alias->alias.contains(search,true)})}
        if(friends.isNotEmpty()) item {GroupedCard {
            friends.forEachIndexed {index,person->
                val receive=Finance.total(debts.filter {it.from==person.id && it.to=="self"}.map {it.outstanding})
                val pay=Finance.total(debts.filter {it.from=="self" && it.to==person.id}.map {it.outstanding})
                val balance=Math.subtractExact(receive,pay)
                val balanceLabel=(if(balance>0) "+" else if(balance<0) "−" else "")+referenceMoney(kotlin.math.abs(balance))
                val stacked=LocalDensity.current.fontScale>1.3f || balanceLabel.length>12
                Surface(onClick={selectedFriend=person.id},color=Tokens.paper,modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(shape=androidx.compose.foundation.shape.CircleShape,color=Tokens.border.copy(alpha=.65f)) {Box(Modifier.size(44.dp),contentAlignment=Alignment.Center) {Text(name(person.id).take(1).uppercase(),style=MaterialTheme.typography.titleLarge)}}
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(name(person.id),style=MaterialTheme.typography.bodyLarge)
                            Text(when {balance>0->"Owes you";balance<0->"You owe";receive>0 || pay>0->"Net zero · original debts remain";else->"Settled up"},color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                            if(stacked) Text(balanceLabel,color=if(balance>0) Tokens.accent else Tokens.text,style=MaterialTheme.typography.titleMedium)
                        }
                        if(!stacked) Text(balanceLabel,color=if(balance>0) Tokens.accent else Tokens.text,style=MaterialTheme.typography.titleMedium)
                        Text("›",color=Tokens.muted,style=MaterialTheme.typography.titleLarge)
                    }
                }
                if(index<friends.lastIndex) GroupedDivider()
            }
        }} else item {EmptyLedger(if(search.isBlank()) "Add your first friend" else "No people found",if(search.isBlank()) "Shared bills and balances appear here." else "Try another name or alias.")}
        item {GroupedCard {
            GroupedRow("Start a shared bill",com.smartexpense.R.drawable.ic_people,onClick=onAdd)
            GroupedDivider()
            GroupedRow("Settlement history",com.smartexpense.R.drawable.ic_activity,onClick={tools=!tools})
        }}
        if(tools) item {GroupedCard {
            GroupedRow("Record repayment",com.smartexpense.R.drawable.ic_transfer,onClick={onRepay(null,group)})
            GroupedDivider();GroupedRow("Manage friends",com.smartexpense.R.drawable.ic_people,onClick=onFriends)
            GroupedDivider();GroupedRow("Manage groups",com.smartexpense.R.drawable.ic_people,onClick=onGroups)
            GroupedDivider();GroupedRow("Settings",com.smartexpense.R.drawable.ic_more,onClick=onSettings)
        }}
        if(tools && s.groups.isNotEmpty()) item {Panel {
            SectionHeading("Your usual groups")
            val recent=s.records.firstOrNull {it.kind==Kind.EXPENSE && !it.reversed && it.groupId!=null}?.groupId
            s.groups.find {it.id==recent}?.let {last->EditorialPrimaryButton("Same group again · ${last.name}") {onRepeatGroup(last)}}
            s.groups.forEach {g->OutlinedButton({onRepeatGroup(g)},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("New bill · ${g.name}")}}
        }}
        if(tools && group==null) item {Choice("Group","all",listOf("all" to "All groups")+s.groups.map {it.id to it.name},icon=com.smartexpense.R.drawable.ic_people) {group=if(it=="all") null else it}}
        if(!tools && debts.any {it.outstanding>0 && it.from!="self" && it.to!="self"}) item {Text("Other participants’ balances are available in settlement tools.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
        if(tools) {
        item {SectionHeading("People · net positions")}
        if(net.isEmpty()) item {EmptyLedger("Start a shared bill","Add friends and record a bill to calculate everyone's share.","Manage friends",onFriends)}
        net.forEach {(id,amount)->item(key="position-$id") {
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                InitialAvatar(name(id))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(name(id),style=MaterialTheme.typography.titleMedium)
                    Text(if(amount==0L) {if(debts.any {it.outstanding>0 && (it.from==id || it.to==id)}) "Net zero · original debts remain" else "Settled across selected bills"} else "${if(amount>0) "Overall owed" else "Overall owes"} · selected bills",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
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
    }
    offsetPerson?.let {person->OffsetReviewSurface(name(person),offsetAmount,s.groups.find {it.id==offsetGroup}?.name?:"All groups",Finance.total(debts.filter {it.from==person && it.to=="self"}.map {it.outstanding}),Finance.total(debts.filter {it.from=="self" && it.to==person}.map {it.outstanding}),{offsetPerson=null}) {onOffset(person,"self",offsetAmount,offsetGroup);offsetPerson=null}}
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
    var editing by rememberSaveable {mutableStateOf<String?>(null)}
    var draftFrom by rememberSaveable {mutableStateOf(from)}
    var draftTo by rememberSaveable {mutableStateOf(to)}
    var draftNote by rememberSaveable {mutableStateOf(note)}
    Page("Record repayment") {
        if(from.isNotBlank() && to.isNotBlank()) item {GroupedCard {GroupedRow(s.people.find {it.id==if(to=="self") from else to}?.let {personLabel(it,s.people)}?:"Selected participant",com.smartexpense.R.drawable.ic_people,supporting="Outstanding "+referenceMoney(due),tone=ReferenceTone.Lavender,leading={InitialAvatar(s.people.find {it.id==if(to=="self") from else to}?.name?:"Friend")}) {draftFrom=from;draftTo=to;editing="people"}}}
        else item {GroupedCard {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Choice("Paid by",from,s.people.map {it.id to personLabel(it,s.people)}) {from=it;if(to==it) to=""}
            Choice("Paid to",to,s.people.filter {it.id!=from}.map {it.id to personLabel(it,s.people)}) {to=it}
        }}}
        item {Panel {ReferenceAmountField("Amount (₹)",amount,{amount=it})}}
        item {GroupedCard {
            if(needsAccount) {GroupedRow(if(to=="self") "Received into" else "Paid from",com.smartexpense.R.drawable.ic_account,value=s.accounts.find {it.id==account}?.name?:"Choose account") {editing="account"};GroupedDivider()}
            EditorialDateRow(date) {date=it}
            GroupedDivider();GroupedRow("Note (optional)",com.smartexpense.R.drawable.ic_document_outline,supporting=note.ifBlank {"e.g. Paid via UPI"}) {draftNote=note;editing="note"}
        }}
        if(parsed!=null && parsed in 1..due) item {Surface(color=Tokens.secondarySurface,shape=Tokens.card) {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Preview",style=MaterialTheme.typography.bodyMedium)
            GroupedCard {
                ReferenceValueRow("Remaining after repayment",referenceMoney(due-parsed))
                s.accounts.find {it.id==account}?.takeIf {needsAccount}?.let {selected->
                    runCatching {Math.addExact(selected.balance,if(to=="self") parsed else Math.negateExact(parsed))}.getOrNull()?.let {after->GroupedDivider();ReferenceValueRow(selected.name+" after this",referenceMoney(after))}
                }
            }
        }}}
        item {EditorialPrimaryButton("Review repayment",valid&&!appState.busy) {reviewing=true}}
        item {ReferenceInfo("Repayments do not count as income. Review the actual payment before recording it.")}
        if(groupId!=null) item {Text("Group: "+(s.groups.find {it.id==groupId}?.name?:"Selected group"),color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
    }
    when(editing) {
        "people"->EditorialBottomSheet("Payment participants",{editing=null}) {
            Choice("Paid by",draftFrom,s.people.map {it.id to personLabel(it,s.people)}) {draftFrom=it;if(draftTo==it) draftTo=""}
            Choice("Paid to",draftTo,s.people.filter {it.id!=draftFrom}.map {it.id to personLabel(it,s.people)}) {draftTo=it}
            EditorialPrimaryButton("Done",draftFrom.isNotBlank() && draftTo.isNotBlank()) {from=draftFrom;to=draftTo;editing=null}
        }
        "account"->EditorialBottomSheet("Payment account",{editing=null}) {
            Choice(if(to=="self") "Receive repayment into" else "Pay repayment from",account,s.accounts.filterNot {it.archived}.map {it.id to it.name}) {account=it;editing=null}
        }
        "note"->EditorialBottomSheet("Note",{editing=null}) {Field("Note",draftNote,{draftNote=it});EditorialPrimaryButton("Done") {note=draftNote;editing=null}}
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

@Composable private fun SettlementReviewSheet(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    EditorialBottomSheet(title,onDismiss,content)
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

@Composable fun OffsetReviewSurface(personName:String,amount:Long,groupName:String,onDismiss:()->Unit,onConfirm:()->Unit) =
    OffsetReviewSurface(personName,amount,groupName,amount,amount,onDismiss,onConfirm)

@Composable fun OffsetReviewSurface(personName:String,amount:Long,groupName:String,owedToYou:Long,owedByYou:Long,onDismiss:()->Unit,onConfirm:()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Tokens.background) {
            Page("Mutual offset",onBack=onDismiss,actions={TextButton(onDismiss) {Text("Cancel")}}) {
                item {Column(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    ReferenceIcon(com.smartexpense.R.drawable.ic_transfer,ReferenceTone.Lavender,96.dp)
                    Text("Cancel matching debts\nwithout moving cash",style=MaterialTheme.typography.headlineSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    Text("If you and $personName owe each other, you can offset the amounts and only keep the net balance.",color=Tokens.muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }}
                item {GroupedCard {
                    ReferenceValueRow("You are owed by $personName",referenceMoney(owedToYou))
                    GroupedDivider();ReferenceValueRow("You owe $personName",referenceMoney(owedByYou))
                    GroupedDivider();ReferenceValueRow("Offset amount",referenceMoney(amount))
                }}
                item {Surface(color=Tokens.secondarySurface,shape=Tokens.card) {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Preview",style=MaterialTheme.typography.bodyMedium)
                    GroupedCard {ReferenceValueRow(if(owedToYou>=owedByYou) "$personName owes you (after offset)" else "You owe $personName (after offset)",referenceMoney(kotlin.math.abs(owedToYou-owedByYou)),Tokens.accent)}
                }}}
                item {EditorialPrimaryButton("Record offset",amount>0 && amount<=minOf(owedToYou,owedByYou),onConfirm)}
                item {ReferenceInfo("No cash moves. The original bills and this offset remain in settlement history. $groupName.")}
            }
        }
    }
}
