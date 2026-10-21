package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import com.smartexpense.R

fun walletPaymentAccounts(s:Snapshot):List<Account> = s.accounts.sortedBy {when(it.id) {s.walletSources?.walletId->0;s.walletSources?.gpayId->1;else->2}}.map {account->
    val role=when(account.id) {s.walletSources?.walletId->"Wallet";s.walletSources?.gpayId->"GPay";else->null}
    if(role==null || role==account.name) account else account.copy(name="$role (${account.name})")
}

@Composable fun WalletBalanceCards(s:Snapshot,visible:Boolean=true,onManage:()->Unit,today:LocalDate=LocalDate.now()) {
    val sources=s.walletSources
    if(sources==null) {Panel {
        SectionHeading("Your personal wallet")
        Text("Enter your Wallet and GPay balances. Track spending locally.",color=Tokens.muted)
        EditorialPrimaryButton("Set up Wallet & GPay",onClick=onManage)
    };return}
    val wallet=s.accounts.find {it.id==sources.walletId};val gpay=s.accounts.find {it.id==sources.gpayId}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        GroupedCard {
            GroupedRow("Wallet",R.drawable.ic_wallet_outline,value=if(visible && wallet!=null) referenceMoney(wallet.balance) else "••••••",tone=ReferenceTone.Peach,onClick=onManage)
            GroupedDivider()
            GroupedRow("GPay",R.drawable.ic_contactless,value=if(visible && gpay!=null) referenceMoney(gpay.balance) else "••••••",tone=ReferenceTone.Lavender,onClick=onManage)
        }
        val next=sources.schedule.nextDate(today)
        if(next!=null) Panel {
            SectionHeading("Until your next refill")
            if(visible && gpay!=null) Text("${Money.format(gpay.balance.coerceAtLeast(0)/java.time.temporal.ChronoUnit.DAYS.between(today,next).coerceAtLeast(1))} / day",style=MaterialTheme.typography.headlineSmall)
            else Text("Balance hidden",color=Tokens.muted)
            Text("GPay resets to ${Money.format(sources.schedule.target)} on ${next.format(DateTimeFormatter.ofPattern("d MMM"))} · no carryover",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            Text("Daily pace uses your recorded GPay balance. Money friends owe you is excluded.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
        }
    }
}

@Composable private fun SourceCard(title:String,account:Account?,visible:Boolean,subtitle:String,onClick:()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=Tokens.card,color=Tokens.surface,shadowElevation=1.dp) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(if(visible && account!=null) editorialMoney(account.balance) else "••••••",style=MaterialTheme.typography.headlineSmall)
            Text(subtitle,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            Text("Manage ›",style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable fun PaymentSourcePicker(s:EntryUiState,accounts:List<Account>,onChange:(EntryUiState)->Unit,sources:WalletSources?=null) {
    val active=accounts.filterNot {it.archived}
    val primary=active.take(2)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(s.kind in listOf(Kind.INCOME,Kind.REFUND)) "Receive into" else "Pay from",style=MaterialTheme.typography.titleSmall)
        primary.forEach {account->
            FilterChip(selected=s.accountId==account.id,onClick={onChange(s.copy(accountId=account.id,secondAccountId=if(s.secondAccountId==account.id) "" else s.secondAccountId))},label={Text("${account.name} · ${editorialMoney(account.balance)}")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp))
        }
        if(active.size>2 || s.accountId !in primary.map {it.id}) Choice("Payment source",s.accountId,active.map {it.id to "${it.name} · ${editorialMoney(it.balance)}"}) {onChange(s.copy(accountId=it,secondAccountId=if(s.secondAccountId==it) "" else s.secondAccountId))}
        if(s.kind==Kind.EXPENSE && active.size>1) {
            if(s.secondAccountId.isBlank()) TextButton({onChange(s.copy(secondAccountId=active.firstOrNull {it.id!=s.accountId}?.id.orEmpty(),secondPayment="0"))}) {Text("Pay from two sources")}
            else {
                Choice("Second payment source",s.secondAccountId,active.filter {it.id!=s.accountId}.map {it.id to it.name}) {onChange(s.copy(secondAccountId=it))}
                Field("Amount from second source (₹)",s.secondPayment,{onChange(s.copy(secondPayment=it))},true)
                Text("The rest of your payment comes from the first source.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                TextButton({onChange(s.copy(secondAccountId="",secondPayment=""))}) {Text("Use one source")}
            }
        }
        projectedBalances(s,accounts,sources).getOrNull()?.forEach {p->
            Text("${p.name}: ${editorialMoney(p.before)} → ${editorialMoney(p.after)}",style=MaterialTheme.typography.bodySmall,color=if(p.after<0) Tokens.negative else Tokens.muted)
            if(p.after<0) Text("This exceeds your recorded ${p.name} balance. Check the payment or update your balance.",color=Tokens.negative,style=MaterialTheme.typography.bodySmall)
        }
    }
}
