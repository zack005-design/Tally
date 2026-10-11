package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*

@Composable fun WalletScreen(s:Snapshot,busy:Boolean,onSetup:(Long,String?,String?,()->Unit)->Unit,onBalance:(String,Long,Boolean,()->Unit)->Unit,onEntry:(Kind,String,String?)->Unit,onRepay:()->Unit,onAccounts:()->Unit,onSetupManual:((Long,Long,String?,String?,()->Unit)->Unit)?=null,onGpay:()->Unit=onAccounts) {
    var walletId by rememberSaveable {mutableStateOf("new")}
    var gpayId by rememberSaveable {mutableStateOf("new")}
    var opening by rememberSaveable {mutableStateOf("")}
    var gpayOpening by rememberSaveable {mutableStateOf("")}
    var setupDone by rememberSaveable {mutableStateOf(false)}
    var funds by rememberSaveable {mutableStateOf(false)}
    var mode by rememberSaveable {mutableStateOf("topup")}
    var amount by rememberSaveable {mutableStateOf("")}
    val sources=s.walletSources
    Page("Wallet & GPay","Cash you have, separate from money you're owed") {
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
            item {OutlinedButton(onGpay,Modifier.fillMaxWidth()) {Text("Set GPay balance / monthly reset ›")}}
            item {Panel {
                SectionHeading("Wallet funds")
                EditorialPrimaryButton("Add cash / set Wallet balance",!busy) {amount="";mode="topup";funds=true}
                OutlinedButton({onEntry(Kind.TRANSFER,sources.gpayId,sources.walletId)},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Move GPay money to Wallet")}
                OutlinedButton({onEntry(Kind.TRANSFER,sources.walletId,sources.gpayId)},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Move Wallet money to GPay")}
                Text("Transfers move your money; they do not count as spending.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }}
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
    if(funds && sources!=null) EditorialBottomSheet("Wallet funds",{if(!busy) funds=false}) {
        EditorialSegmentedControl(mode,listOf("topup" to "Add cash","set" to "Set balance")) {mode=it}
        Field(if(mode=="topup") "Cash added (₹)" else "Actual Wallet balance (₹)",amount,{amount=it},true)
        val parsed=runCatching {Money.parse(amount).also {require(if(mode=="topup") it>0 else it>=0)}}.getOrNull()
        Text(if(mode=="topup") "Records a cash top-up. For money moved from GPay, use Transfer instead." else "Records a correction to match the cash you actually have.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        EditorialPrimaryButton(if(mode=="topup") "Add cash" else "Set Wallet balance",parsed!=null && !busy) {onBalance(sources.walletId,parsed!!,mode=="topup") {funds=false}}
        OutlinedButton({funds=false},Modifier.fillMaxWidth(),enabled=!busy) {Text("Cancel")}
    }
}
