package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import com.smartexpense.R
import androidx.compose.ui.Alignment

@Composable fun BalanceCheckScreen(s:Snapshot,busy:Boolean,onDetails:(String)->Unit,onAdd:(String)->Unit,onAdjust:(String,Long,Long,String,()->Unit)->Unit) {
    var accountId by rememberSaveable {mutableStateOf("")}
    var actual by rememberSaveable {mutableStateOf("")}
    var reason by rememberSaveable {mutableStateOf("")}
    var accountPicker by rememberSaveable {mutableStateOf(false)}
    var reasonPicker by rememberSaveable {mutableStateOf(false)}
    var reasonDraft by rememberSaveable {mutableStateOf("")}
    var previewBalance by rememberSaveable {mutableStateOf<Long?>(null)}
    val account=s.accounts.find {it.id==accountId && !it.archived}
    val parsed=runCatching {Money.parse(actual)}
    val difference=runCatching {Math.subtractExact(parsed.getOrThrow(),requireNotNull(account).balance)}
    Page("Balance check",largeTitle=true) {
        item {GroupedCard {ReferenceDetailRow("Account",if(accountId==s.walletSources?.gpayId) R.drawable.ic_contactless else R.drawable.ic_wallet_outline,account?.name?:"Choose account") {if(!busy) accountPicker=true}}}
        if(account!=null) {
            item {GroupedCard {
                ReferenceValueRow("Recorded balance",referenceMoney(account.balance))
                Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)) {Field("Actual balance (₹)",actual,{actual=it;previewBalance=null},true)}
                if(actual.isNotBlank() && difference.isFailure) Text(parsed.exceptionOrNull()?.message?:"These amounts are too far apart",color=Tokens.negative)
                difference.getOrNull()?.let {delta->
                    HorizontalDivider(Modifier.padding(horizontal=16.dp),color=Tokens.border)
                    ReferenceValueRow("Difference · actual minus recorded",referenceMoney(delta),if(delta<0) Tokens.negative else Tokens.text)
                }
            }}
            difference.getOrNull()?.let {delta->
                if(delta!=0L) item {GroupedCard {ReferenceDetailRow("Reason",R.drawable.ic_document_outline,reason.ifBlank {"Add reason"}) {if(!busy) {reasonDraft=reason;reasonPicker=true}}}}
                else item {ReferenceInfo("Balances match. Nothing needs to be recorded.")}
            }
            item {Text("Recent account activity",style=MaterialTheme.typography.titleMedium)}
            val records=s.records.filter {account.id in it.accountIds}.sortedByDescending {it.date}.take(20)
            if(records.isEmpty()) item {Text("No entries in this account yet.")}
            if(records.isNotEmpty()) item {GroupedCard {records.forEachIndexed {index,r->if(index>0) GroupedDivider();HistoryTransactionRow(r,s) {onDetails(r.id)}}}}
            if(difference.getOrNull()?.let {it!=0L}==true) item {EditorialPrimaryButton("Review adjustment",!busy && reason.isNotBlank() && reason.length<=500) {previewBalance=account.balance}}
            item {ReferenceInfo("History is preserved. This will be recorded as an adjustment, not an edit. Check for missed or incorrect entries first.")}
            item {OutlinedButton({onAdd(account.id)},Modifier.fillMaxWidth(),enabled=!busy) {Text("Add missing expense")}}
        }
    }
    if(accountPicker) EditorialBottomSheet("Account",{accountPicker=false}) {s.accounts.filterNot {it.archived}.forEach {choice->GroupedCard {GroupedRow(choice.name,R.drawable.ic_wallet_outline,value=referenceMoney(choice.balance)) {accountId=choice.id;actual="";reason="";previewBalance=null;accountPicker=false}}}}
    if(reasonPicker) EditorialBottomSheet("Reason",{reasonPicker=false}) {
        Field("Reason for adjustment",reasonDraft,{reasonDraft=it})
        EditorialPrimaryButton("Done",reasonDraft.isNotBlank() && reasonDraft.length<=500 && !busy) {reason=reasonDraft;previewBalance=null;reasonPicker=false}
    }
    val expected=previewBalance
    if(expected!=null && account!=null && parsed.isSuccess) {
        val stale=expected!=account.balance
        EditorialConfirmSheet("Record balance adjustment?",if(stale) "The recorded balance changed. Close this preview and check again." else "${account.name}: ${Money.format(expected)} → ${Money.format(parsed.getOrThrow())}\nReason: $reason\nThis is a balance adjustment, not spending or income.","Record adjustment",{previewBalance=null},enabled=!busy && !stale) {
            onAdjust(account.id,parsed.getOrThrow(),expected,reason) {previewBalance=null;actual="";reason=""}
        }
    }
}
