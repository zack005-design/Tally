package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import com.smartexpense.R

@Composable fun WalletScreen(s:Snapshot,busy:Boolean,onSetup:(Long,String?,String?,()->Unit)->Unit,onBalance:(String,Long,Boolean,()->Unit)->Unit,onEntry:(Kind,String,String?)->Unit,onRepay:()->Unit,onAccounts:()->Unit,onSetupManual:((Long,Long,String?,String?,()->Unit)->Unit)?=null,onGpay:()->Unit={},initialAddMoney:Boolean=false,onTopupDetailed:((String,Long,Long,String,()->Unit)->Unit)?=null,onTopupProjection:(suspend (String,Long,Long)->Long)?=null) {
    var walletId by rememberSaveable {mutableStateOf("new")}
    var gpayId by rememberSaveable {mutableStateOf("new")}
    var opening by rememberSaveable {mutableStateOf("")}
    var gpayOpening by rememberSaveable {mutableStateOf("")}
    var setupDone by rememberSaveable {mutableStateOf(false)}
    var funds by rememberSaveable {mutableStateOf(initialAddMoney)}
    var fundsSource by rememberSaveable {mutableStateOf("wallet")}
    var mode by rememberSaveable {mutableStateOf("topup")}
    var amount by rememberSaveable {mutableStateOf("")}
    var destinationPicker by rememberSaveable {mutableStateOf(false)}
    var fundsDate by rememberSaveable {mutableStateOf(java.time.LocalDate.now().toString())}
    var fundsTime by rememberSaveable {mutableStateOf(java.time.LocalTime.now().withNano(0).toString())}
    var fundsNote by rememberSaveable {mutableStateOf("")}
    var noteDraft by rememberSaveable {mutableStateOf("")}
    var datePicker by rememberSaveable {mutableStateOf(false)}
    var notePicker by rememberSaveable {mutableStateOf(false)}
    val sources=s.walletSources
    Page(if(funds) "Add money" else "Wallet & GPay") {
        if(sources==null) {
            item {Panel {
                Text("Make it yours",style=MaterialTheme.typography.titleLarge)
                Text("Enter the money you have in Wallet and GPay. Monthly GPay resets are off until you enable them.",color=Tokens.muted)
                Choice("Wallet account",walletId,listOf("new" to "Create Wallet")+s.accounts.filter {!it.archived && it.id!=gpayId}.map {it.id to "${it.name} · ${editorialMoney(it.balance)}"}) {walletId=it}
                if(walletId=="new") Field("Cash in your Wallet (₹)",opening,{opening=it},true)
                Choice("GPay account",gpayId,listOf("new" to "Create GPay")+s.accounts.filter {!it.archived && it.id!=walletId}.map {it.id to "${it.name} · ${editorialMoney(it.balance)}"}) {gpayId=it}
                if(gpayId=="new") Field("Money in your GPay (₹)",gpayOpening,{gpayOpening=it},true)
                if(gpayId!="new" || walletId!="new") Text("Existing account balances and history stay intact.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
                val value=if(walletId=="new") runCatching {Money.parse(opening).also {require(it>=0)}}.getOrNull() else 0L
                val gpayValue=if(gpayId=="new") runCatching {Money.parse(gpayOpening).also {require(it>=0)}}.getOrNull() else 0L
                EditorialPrimaryButton("Start my wallet",value!=null && gpayValue!=null && !busy && !setupDone) {
                    val manual=onSetupManual
                    if(manual!=null) manual(value!!,gpayValue!!,walletId.takeUnless {it=="new"},gpayId.takeUnless {it=="new"}) {setupDone=true}
                    else onSetup(value!!,walletId.takeUnless {it=="new"},gpayId.takeUnless {it=="new"}) {setupDone=true}
                }
            }}
        } else {
            item {WalletBalanceCards(s,onManage=onAccounts)}
            item {GroupedCard {GroupedRow("Set GPay balance / monthly reset",R.drawable.ic_calendar,onClick=onGpay)}}
            item {SectionLabel("Wallet funds")}
            item {ReferenceAction("Add money",R.drawable.ic_add,enabled=!busy) {amount="";mode="topup";fundsDate=java.time.LocalDate.now().toString();fundsTime=java.time.LocalTime.now().withNano(0).toString();fundsNote="";funds=true}}
            item {GroupedCard {
                GroupedRow("Move GPay money to Wallet",R.drawable.ic_transfer) {onEntry(Kind.TRANSFER,sources.gpayId,sources.walletId)}
                GroupedDivider()
                GroupedRow("Move Wallet money to GPay",R.drawable.ic_transfer) {onEntry(Kind.TRANSFER,sources.walletId,sources.gpayId)}
            }}
            item {ReferenceInfo("Transfers move your money; they do not count as spending.")}
            item {Panel {
                SectionHeading("Money coming back")
                AmountRow("Friends owe you",Finance.total(s.debts.filter {it.to=="self"}.map {it.outstanding}))
                AmountRow("You owe friends",Finance.total(s.debts.filter {it.from=="self"}.map {it.outstanding}))
                OutlinedButton(onRepay,Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Record a friend repayment")}
                Text("Record a repayment into Wallet or GPay when the money arrives.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }}
            item {Text("Balances are tracked manually from your entries.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
            item {TextButton(onAccounts) {Text("All accounts and history ›")}}
        }
    }
    if(funds && sources!=null) EditorialBottomSheet("Add money",{if(!busy) funds=false}) {
        val sourceName=if(fundsSource=="wallet") "Wallet" else "GPay"
        val accountId=if(fundsSource=="wallet") sources.walletId else sources.gpayId
        ReferenceAmountField(if(mode=="topup") "Amount to add (₹)" else "Actual $sourceName balance (₹)",amount,{amount=it})
        GroupedCard {
            GroupedRow("Destination",if(fundsSource=="wallet") R.drawable.ic_account else R.drawable.ic_contactless,value=sourceName) {if(!busy) destinationPicker=true}
            GroupedDivider(14.dp)
            if(mode=="topup" && onTopupDetailed!=null) {
                ReferenceDetailRow("Date",R.drawable.ic_calendar,if(fundsDate==java.time.LocalDate.now().toString()) "Today" else java.time.LocalDate.parse(fundsDate).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))) {if(!busy) datePicker=true}
                GroupedDivider(14.dp);ReferenceDetailRow("Note",R.drawable.ic_document_outline,fundsNote.ifBlank {"Optional"}) {if(!busy) {noteDraft=fundsNote;notePicker=true}}
            } else ReferenceValueRow("Date","Today")
        }
        val parsed=runCatching {Money.parse(amount).also {require(if(mode=="topup") it>0 else it>=0)}}.getOrNull()
        val current=s.accounts.find {it.id==accountId}?.balance?:0L
        val requestedDate=java.time.LocalDate.parse(fundsDate).atTime(java.time.LocalTime.parse(fundsTime)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        var remoteProjection by remember {mutableStateOf<Long?>(null)}
        var projectionError by remember {mutableStateOf<String?>(null)}
        LaunchedEffect(accountId,parsed,requestedDate,mode,current) {
            remoteProjection=null;projectionError=null
            if(mode=="topup" && parsed!=null && onTopupProjection!=null) try {remoteProjection=onTopupProjection(accountId,parsed,requestedDate)}
            catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(failure:Exception) {projectionError=failure.message?:"Could not preview this top-up"}
        }
        val projected=if(mode=="topup" && onTopupProjection!=null) remoteProjection else parsed?.let {value->runCatching {if(mode=="topup") Math.addExact(current,value) else value}.getOrNull()}
        projectionError?.let {Text(it,color=Tokens.negative,style=MaterialTheme.typography.bodySmall)}
        if(projected!=null) Surface(shape=Tokens.card,color=Tokens.secondarySurface) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Projected balance ($sourceName)",style=MaterialTheme.typography.titleSmall)
            Text(referenceMoney(projected),style=MaterialTheme.typography.headlineLarge)
            Text(if(mode=="topup" && projected==current) "Current: ${referenceMoney(current)} · later monthly resets preserve their target." else if(mode=="topup") "Current: ${referenceMoney(current)}  +  ${referenceMoney(parsed!!)}" else "Current: ${referenceMoney(current)}",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        }}
        Text(if(mode=="topup") "For new money received. To move money between Wallet and GPay, use Transfer instead." else "Updates your recorded balance to match what you actually have.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        EditorialPrimaryButton(if(mode=="topup") "Add money" else "Set $sourceName balance",parsed!=null && projected!=null && fundsNote.length<=500 && !busy) {
            val detailed=onTopupDetailed
            if(mode=="topup" && detailed!=null) detailed(accountId,parsed!!,requestedDate,fundsNote) {funds=false}
            else onBalance(accountId,parsed!!,mode=="topup") {funds=false}
        }
        TextButton({mode=if(mode=="topup") "set" else "topup";amount=""},enabled=!busy) {Text(if(mode=="topup") "Correct an existing balance" else "Add new money instead")}
    }
    if(datePicker) EditorialDatePicker("Top-up date",java.time.LocalDate.parse(fundsDate),{datePicker=false}) {fundsDate=it.toString();datePicker=false}
    if(notePicker) EditorialBottomSheet("Note",{notePicker=false}) {
        Field("Note (optional)",noteDraft,{noteDraft=it})
        if(noteDraft.length>500) Text("Keep the note under 500 characters",color=Tokens.negative)
        EditorialPrimaryButton("Done",noteDraft.length<=500 && !busy) {fundsNote=noteDraft;notePicker=false}
    }
    if(destinationPicker) EditorialBottomSheet("Destination",{destinationPicker=false}) {
        listOf("wallet" to "Wallet","gpay" to "GPay").forEach {(id,name)->
            GroupedCard {GroupedRow(name,if(id=="wallet") R.drawable.ic_account else R.drawable.ic_contactless) {fundsSource=id;destinationPicker=false}}
        }
    }
}
