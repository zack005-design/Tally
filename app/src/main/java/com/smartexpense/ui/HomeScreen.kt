package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun HomeScreen(s:Snapshot,onAdd:(Kind,Boolean)->Unit,onAccounts:()->Unit,onRepay:()->Unit,onDetails:(String)->Unit,onManage:(String)->Unit) {
    val month=YearMonth.now()
    val active=s.accounts.filterNot {it.archived}
    val budget=s.budgets.find {it.month==month.toString() && it.categoryId==null}
    var menu by rememberSaveable {mutableStateOf(false)}
    var visible by rememberSaveable {mutableStateOf(true)}
    Page("Overview",month.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(),actions={
        Box {
            FilledTonalIconButton({menu=true}) {Icon(painterResource(R.drawable.ic_more),"Manage tracker")}
        }
    },onSubtitle={onManage("insights")}) {
        if(s.localSettings.name.isNotBlank()) item {Text("Hello, ${s.localSettings.name}",style=MaterialTheme.typography.titleMedium)}
        item {
            if(active.isEmpty()) Spacer(Modifier.height(0.dp))
            else Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("AVAILABLE BALANCE",style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val balanceSize=if(maxWidth<320.dp) 32.sp else 40.sp
                    val large=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
                    val spendable=if(s.walletSources!=null) active.filter {it.id in listOf(s.walletSources.walletId,s.walletSources.gpayId)} else active
                    val amount=if(visible) editorialMoney(Finance.total(spendable.map {it.balance})) else "••••••"
                    if(large) Column {
                        Text(amount,style=MaterialTheme.typography.headlineMedium)
                        TextButton({visible=!visible}) {Text(if(visible) "Hide balance" else "Show balance")}
                    } else Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(amount,Modifier.weight(1f),style=MaterialTheme.typography.displaySmall.copy(fontSize=balanceSize))
                        FilledTonalIconButton({visible=!visible}) {Icon(painterResource(R.drawable.ic_visibility),if(visible) "Hide balance" else "Show balance")}
                    }
                }
                TextButton(onAccounts,contentPadding=PaddingValues(0.dp)) {Icon(painterResource(R.drawable.ic_account),null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("${active.size} active accounts  ›",style=MaterialTheme.typography.bodySmall)}
            }
        }
        item {WalletBalanceCards(s,visible,{onManage("wallet")})}
        item {HorizontalDivider(color=Tokens.border);Spacer(Modifier.height(16.dp));TransactionTypes(Kind.EXPENSE,false,onAdd)}
        item {OutlinedButton({onManage("calendar")},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Open spending calendar ›")}}
        item {OutlinedButton({onManage("diary")},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Daily spending diary ›")}}
        item {OutlinedButton({onManage("monthly-review")},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Monthly money review ›")}}
        if(s.debts.any {it.outstanding>0}) item {AdaptivePair(first={FinancialMetric("TO RECEIVE",Finance.total(s.debts.filter {it.to=="self"}.map {it.outstanding}),"Money owed to you",onRepay)},second={FinancialMetric("TO PAY",Finance.total(s.debts.filter {it.from=="self"}.map {it.outstanding}),"Outstanding",onRepay)})}
        if(active.isNotEmpty() && (s.records.isNotEmpty() || budget!=null)) item {Panel {
            SectionHeading("Monthly spending","›") {onManage("insights")}
            Text(editorialMoney(s.dashboard.month.spending),style=MaterialTheme.typography.headlineMedium)
            if(budget!=null && budget.amount>0) {
                Text("of ${editorialMoney(budget.amount)} budget",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                val fraction=s.dashboard.month.spending.toDouble()/budget.amount
                LinearProgressIndicator(progress={fraction.toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(6.dp),color=Tokens.accent,trackColor=Tokens.border)
                Text("${"%.1f".format(fraction*100)}% used · ${editorialMoney(budget.amount-s.dashboard.month.spending)} remaining",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            } else Text("Your share this month · Set a budget in Insights",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        }}
        item {SectionHeading("Recent activity","View all ›") {onManage("activity")}}
        if(s.records.isEmpty()) item {EmptyLedger("A fresh start","Expenses, income and shared bills will appear here.","Add an expense") {onAdd(Kind.EXPENSE,false)}}
        s.records.take(5).forEach {r->item(key=r.id) {TransactionRow(r,s) {onDetails(r.id)}}}
    }
    if(menu) EditorialActionSheet("Your tracker",listOf("profile" to "Your profile","wallet" to "Wallet & GPay","gpay-settings" to "GPay balance & resets","diary" to "Daily diary","monthly-review" to "Monthly review","calendar" to "Spending calendar","accounts" to "Accounts","people" to "Friends","groups" to "Groups","default-splits" to "Default group splits","recurring" to "Recurring entries","goals" to "Savings goals","categories" to "Categories","planner" to "Money planner","backup" to "Backup & restore").map {(route,label)->EditorialAction(label) {if(route=="accounts") onAccounts() else onManage(route)}},{menu=false})
}
@Composable fun FinancialMetric(label:String,amount:Long,description:String,onClick:()->Unit) {
    Surface(onClick=onClick,color=Tokens.background) {
        Row(Modifier.padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=androidx.compose.foundation.shape.CircleShape,color=Tokens.secondarySurface,modifier=Modifier.size(32.dp)) {Box(contentAlignment=Alignment.Center) {Icon(painterResource(if(label=="TO RECEIVE") R.drawable.ic_arrow_in else R.drawable.ic_arrow_out),null,Modifier.size(20.dp))}}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(label,style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
                Text(editorialMoney(amount),style=MaterialTheme.typography.titleLarge)
                Text(description,style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
            }
        }
    }
}
@Composable fun AdaptivePair(equalCards:Boolean=false,first:@Composable ()->Unit,second:@Composable ()->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<280.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {first();second()}
        else Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {Box(Modifier.weight(1f)) {first()};Box(Modifier.weight(1f)) {second()}}
    }
}
@Composable fun TransactionRow(record:Record,snapshot:Snapshot?=null,showPersonalAmount:Boolean=false,onClick:()->Unit) {
    val displayAmount=if(showPersonalAmount && record.kind==Kind.EXPENSE) record.personalAmount else record.amount
    val category=snapshot?.categories?.find {it.id==record.categoryId}
    val icon=when {record.kind==Kind.INCOME || record.kind==Kind.REFUND->R.drawable.ic_arrow_in;record.kind==Kind.TRANSFER->R.drawable.ic_transfer;category?.icon=="food" || category?.name?.contains("food",true)==true->R.drawable.ic_food;category?.icon=="shopping" || category?.name?.contains("grocer",true)==true->R.drawable.ic_basket;category?.icon=="transport" || category?.name?.contains("transport",true)==true->R.drawable.ic_transit;else->R.drawable.ic_activity}
    val accounts=record.accountIds.mapNotNull {id->snapshot?.accounts?.find {it.id==id}?.name}.joinToString(" → ")
    val date=Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM"))
    val metadata=listOf(date,category?.name,accounts.ifBlank {null}).filterNotNull().joinToString(" · ")
    
                    val large=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
    Surface(onClick=onClick,color=Tokens.background,modifier=Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(shape=androidx.compose.foundation.shape.CircleShape,color=Tokens.secondarySurface,modifier=Modifier.size(44.dp)) {Box(contentAlignment=Alignment.Center) {Icon(painterResource(icon),null,Modifier.size(22.dp))}}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(record.description,style=MaterialTheme.typography.titleSmall)
                    Text(metadata,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                    if(large) Text(editorialMoney(displayAmount),style=MaterialTheme.typography.titleMedium)
                    if(record.reversed) Text("Reversed",color=Tokens.negative,style=MaterialTheme.typography.bodySmall)
                }
                if(!large) Column(horizontalAlignment=Alignment.End,modifier=Modifier.widthIn(max=130.dp)) {
                    Text((if(record.kind==Kind.INCOME || record.kind==Kind.REFUND) "+" else "")+editorialMoney(displayAmount),style=MaterialTheme.typography.titleSmall)
                    if(record.kind==Kind.EXPENSE && record.personalAmount!=record.amount) Text(if(showPersonalAmount) "Your share" else "Your share ${editorialMoney(record.personalAmount)}",style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
                }
            }
            HorizontalDivider(color=Tokens.border.copy(alpha=0.5f))
        }
    }
}






