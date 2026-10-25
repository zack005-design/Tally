package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.data.SharedRefundContext
import com.smartexpense.domain.*
import kotlinx.coroutines.CancellationException

@Composable internal fun SharedRefundRoute(originalId:String,snapshot:Snapshot,model:AppViewModel,onBack:()->Unit) {
    var context by remember(originalId) {mutableStateOf<SharedRefundContext?>(null)}
    var error by remember(originalId) {mutableStateOf<String?>(null)}
    LaunchedEffect(originalId) {
        try {context=model.repository.sharedRefundContext(originalId)}
        catch(e:CancellationException) {throw e}
        catch(e:Exception) {error=e.message?:"Could not load this bill"}
    }
    val ui by model.state.collectAsStateWithLifecycle()
    context?.let {value->SharedRefundEditor(value,snapshot,ui.busy,{draft->model.action("Shared refund saved") {model.repository.saveSharedRefund(draft);onBack()}},onBack)}
        ?:Page("Shared refund",onBack=onBack) {item {Text(error?:"Loading original bill…")}}
}

@Composable fun SharedRefundEditor(context:SharedRefundContext,snapshot:Snapshot,busy:Boolean,onSave:(SharedRefundDraft)->Unit,onBack:()->Unit) {
    val original=context.original
    val requestId=rememberSaveable(original.id) {java.util.UUID.randomUUID().toString()}
    val date=rememberSaveable(original.id) {System.currentTimeMillis()}
    var amount by rememberSaveable(original.id) {mutableStateOf("")}
    var account by rememberSaveable(original.id) {mutableStateOf(snapshot.accounts.firstOrNull {!it.archived}?.id.orEmpty())}
    var shares by rememberSaveable(original.id) {mutableStateOf<Map<String,String>>(emptyMap())}
    var received by rememberSaveable(original.id) {mutableStateOf<Map<String,String>>(emptyMap())}
    var note by rememberSaveable(original.id) {mutableStateOf("")}
    var reviewing by rememberSaveable(original.id) {mutableStateOf(false)}
    fun name(id:String)=if(id=="self") "You" else snapshot.people.find {it.id==id}?.name?:"Participant"
    val people=context.remaining.keys.sorted()
    val preview=runCatching {
        val value=Money.parse(amount)
        val distribution=people.associateWith {Money.parse(shares[it].orEmpty())}
        val recipients=people.associateWith {Money.parse(received[it].orEmpty())}
        SharedRefund.positions(value,context.remaining,distribution,recipients)
        require((recipients["self"]?:0)==0L || snapshot.accounts.any {it.id==account && !it.archived}) {"Choose an active destination account"}
        snapshot.accounts.find {it.id==account}?.let {Math.addExact(it.balance,recipients["self"]?:0)}
        SharedRefundDraft(requestId,original.id,value,"Refund: ${original.description}",account,distribution,recipients,date,note)
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        TextButton(onBack,enabled=!busy) {Text("Back")}
        Text("Shared refund",style=MaterialTheme.typography.headlineMedium)
        Text(original.description,style=MaterialTheme.typography.titleMedium)
        Text("Remaining refundable: ${Money.format(Finance.total(context.remaining.values))}")
        if(!reviewing) {
            Field("Refund total (₹)",amount,{text->
                amount=text
                val split=runCatching {SharedRefund.proportional(Money.parse(text),context.remaining)}.getOrNull()
                shares=split?.mapValues {Money.input(it.value)}.orEmpty()
                val recipient=if("self" in people) "self" else people.first()
                received=people.associateWith {if(it==recipient) text else "0"}
            },true)
            Text("Refunded shares",style=MaterialTheme.typography.titleMedium)
            Text("Suggested from remaining original shares. Edit for an item-specific refund.")
            people.forEach {person->Field("${name(person)} · refunded share (₹)",shares[person].orEmpty(),{shares=shares+(person to it)},true)}
            Text("Who received the cash?",style=MaterialTheme.typography.titleMedium)
            people.forEach {person->Field("${name(person)} · cash received (₹)",received[person].orEmpty(),{received=received+(person to it)},true)}
            Choice("Your destination account",account,snapshot.accounts.filterNot {it.archived}.map {it.id to it.name}) {account=it}
            Field("Note (optional)",note,{note=it})
            if(amount.isNotBlank() && preview.isFailure) Text(preview.exceptionOrNull()?.message?:"Check the distribution",color=Tokens.negative)
            EditorialPrimaryButton("Review refund",preview.isSuccess && !busy) {reviewing=true}
        } else if(preview.isFailure) {
            Text(preview.exceptionOrNull()?.message?:"Review the refund again",color=Tokens.negative)
            TextButton({reviewing=false}) {Text("Edit distribution")}
        } else preview.getOrNull()?.let {draft->
            Panel {
                AmountRow("Total merchant refund",draft.amount)
                AmountRow("Your spending reduction",draft.shares["self"]?:0)
                AmountRow("Cash received by you",draft.received["self"]?:0)
                snapshot.accounts.find {it.id==account}?.let {a->AmountRow("${a.name} after refund",Math.addExact(a.balance,draft.received["self"]?:0))}
                people.forEach {person->AmountRow("${name(person)} · refunded share",draft.shares.getValue(person));AmountRow("${name(person)} · received cash",draft.received.getValue(person))}
                val positions=SharedRefund.positions(draft.amount,context.remaining,draft.shares,draft.received)
                Finance.settlements(positions).forEach {AmountRow("Refund balance: ${name(it.from)} → ${name(it.to)}",it.amount)}
                Text("The original bill and repayments stay in history. Refund balances can offset existing opposite balances through the settlement tools.")
            }
            EditorialPrimaryButton("Save refund",!busy) {onSave(draft)}
            TextButton({reviewing=false},enabled=!busy) {Text("Edit distribution")}
        }
        Spacer(Modifier.height(24.dp))
    }
}
