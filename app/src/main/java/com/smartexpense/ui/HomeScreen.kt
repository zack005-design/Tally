package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun HomeScreen(s:Snapshot,onAdd:(Kind,Boolean)->Unit,onAccounts:()->Unit,onRepay:()->Unit,onDetails:(String)->Unit,onManage:(String)->Unit,onRepeat:(String)->Unit={}) {
    val active = remember(s.accounts) {s.accounts.filterNot { it.archived }}
    val sources = s.walletSources
    val spendable = remember(active,sources?.walletId,sources?.gpayId) {
        if (sources != null) active.filter { it.id == sources.walletId || it.id == sources.gpayId } else active
    }
    var visible by rememberSaveable { mutableStateOf(true) }
    val receive = remember(s.debts) {Finance.total(s.debts.filter { it.to == "self" }.map { it.outstanding })}
    val pay = remember(s.debts) {Finance.total(s.debts.filter { it.from == "self" }.map { it.outstanding })}
    val total = remember(spendable) {runCatching { Finance.total(spendable.map { it.balance }) }.getOrNull()}
    val latest = remember(s.records) {s.records.sortedByDescending { it.date }.take(3)}
    val recent = remember(s.records) {recentPersonalExpenses(s.records)}
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(horizontal = Tokens.pageInset, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { TallyWordmark() }
                        StudioSettingsButton { onManage("more") }
                    }
                    Text("Overview", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL AVAILABLE", style = MaterialTheme.typography.labelMedium, color = Tokens.muted)
                        IconButton({ visible = !visible }, Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_visibility), if (visible) "Hide balance" else "Show balance", Modifier.size(20.dp), tint = Tokens.muted)
                        }
                    }
                    Text(if (!visible) "••••••" else total?.let(::referenceMoney) ?: "Total unavailable",
                        style = MaterialTheme.typography.displaySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(if (sources != null) "Wallet + GPay" else "Across your active accounts", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                }
            }
            item {
                if (active.isEmpty()) GroupedCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Set up your accounts", style = MaterialTheme.typography.titleMedium)
                        Text("Add your starting balances to begin.", color = Tokens.muted)
                        EditorialPrimaryButton("Set up Wallet & GPay") { onManage("wallet") }
                    }
                } else GroupedCard {
                    spendable.take(2).forEachIndexed { index, account ->
                        if (index > 0) GroupedDivider()
                        GroupedRow(account.name, if(account.id==sources?.gpayId) R.drawable.ic_contactless else R.drawable.ic_wallet_outline, value = if (visible) referenceMoney(account.balance) else "••••••", onClick = onAccounts)
                    }
                    if (active.size > spendable.take(2).size) {
                        GroupedDivider()
                        GroupedRow("All accounts", R.drawable.ic_account, onClick = onAccounts)
                    }
                }
            }
            if (active.isNotEmpty()) item {
                HomeQuickActions(
                    { onManage(if (sources == null) "wallet" else "wallet/add-money") },
                    { onAdd(Kind.TRANSFER, false) }, { onManage("bill-import") }
                )
            }
            item {
                AdaptivePair(first = {
                    Surface(onClick = { onManage("activity") }, color = Tokens.background, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(referenceMoney(s.dashboard.today), style = MaterialTheme.typography.headlineMedium)
                            Text("Spent today", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                        }
                    }
                }, second = {
                    Surface(onClick = { onManage("calendar") }, color = Tokens.background, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = Tokens.secondarySurface, modifier = Modifier.size(52.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(LocalDate.now().dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, color = Tokens.accent) }
                            }
                            Column {
                                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM")), style = MaterialTheme.typography.titleSmall)
                                Text("Calendar", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                            }
                        }
                    }
                })
            }
            item { SectionHeading("Latest", "View all") { onManage("activity") } }
            item {
                GroupedCard {
                    if (latest.isEmpty()) Column(Modifier.padding(20.dp)) {
                        EmptyLedger("A fresh start", "Your transactions will appear here.", "Add expense") { onAdd(Kind.EXPENSE, false) }
                    } else latest.forEach { record ->
                        Box(Modifier.padding(horizontal = 16.dp)) { TransactionRow(record, s, containerColor = Tokens.paper) { onDetails(record.id) } }
                    }
                }
            }
            item {
                GroupedCard {
                    GroupedRow("Friends owe you", R.drawable.ic_people,
                        supporting = if (pay > 0) "You also owe ${editorialMoney(pay)}" else "View shared balances",
                        value = editorialMoney(receive)) { onManage("split") }
                    GroupedDivider()
                    GroupedRow("Record repayment", R.drawable.ic_arrow_in, onClick = onRepay)
                }
            }
            item { SectionLabel("MORE FOR YOUR MONEY") }
            item {
                GroupedCard {
                    GroupedRow("Record income", R.drawable.ic_arrow_in) { onAdd(Kind.INCOME, false) }
                    GroupedDivider()
                    GroupedRow("Money planner", R.drawable.ic_insights) { onManage("planner") }
                    GroupedDivider()
                    GroupedRow("Check balance", R.drawable.ic_account) { onManage("balance-check") }
                    GroupedDivider()
                    GroupedRow("Favourite expenses", R.drawable.ic_activity) { onManage("favourites") }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionLabel("REPEAT AN EXPENSE") }
                item { GroupedCard {
                    recent.forEachIndexed { index, record ->
                        if (index > 0) GroupedDivider()
                        GroupedRow(record.description, R.drawable.ic_compose, supporting = "Opens a draft for today", value = editorialMoney(record.amount)) { onRepeat(record.id) }
                    }
                } }
            }
            item { Text("Review spending, budgets and trends in Spending.", style = MaterialTheme.typography.bodySmall, color = Tokens.muted) }
        }
    }
}

@Composable private fun HomeQuickActions(onMoney: () -> Unit, onTransfer: () -> Unit, onScan: () -> Unit) {
    val actions = listOf(Triple("Add money", R.drawable.ic_arrow_in, onMoney), Triple("Transfer", R.drawable.ic_transfer, onTransfer), Triple("Scan bill", R.drawable.ic_activity, onScan))
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    if (large) GroupedCard {
        actions.forEachIndexed { index, (label, icon, action) ->
            if (index > 0) GroupedDivider()
            GroupedRow(label, icon, onClick = action)
        }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        actions.forEach { (label, icon, action) -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { CircleQuickAction(label, icon, onClick = action) } }
    }
}


@Composable internal fun StudioTransaction(record:Record,s:Snapshot,onClick:()->Unit) {
    val debts=s.debts.filter {it.transactionId==record.id && it.outstanding>0}
    Surface(shape=Tokens.card,color=Tokens.paper,border=BorderStroke(1.dp,Tokens.border.copy(alpha=.5f)),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal=16.dp,vertical=6.dp)) {
            TransactionRow(record,s,containerColor=Tokens.paper,onClick=onClick)
            if(record.personIds.any {it!="self"}) {
                Column(Modifier.padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(record.personIds.joinToString(" + ") {id->if(id=="self") "You" else s.people.find {it.id==id}?.let {personLabel(it,s.people)}?:"Friend"},style=MaterialTheme.typography.labelMedium)
                    val receive=Finance.total(debts.filter {it.to=="self"}.map {it.outstanding})
                    val pay=Finance.total(debts.filter {it.from=="self"}.map {it.outstanding})
                    if(!record.reversed && receive>0) Text("You get ${editorialMoney(receive)}",style=MaterialTheme.typography.bodySmall)
                    if(!record.reversed && pay>0) Text("You owe ${editorialMoney(pay)}",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
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
        else Row(modifier=if(equalCards) Modifier.height(IntrinsicSize.Min) else Modifier,horizontalArrangement=Arrangement.spacedBy(16.dp)) {Box(Modifier.weight(1f)) {first()};Box(Modifier.weight(1f)) {second()}}
    }
}
@Composable fun TransactionRow(record:Record,snapshot:Snapshot?=null,showPersonalAmount:Boolean=false,containerColor:androidx.compose.ui.graphics.Color=Tokens.background,onClick:()->Unit) {
    val displayAmount=if(showPersonalAmount && record.kind in listOf(Kind.EXPENSE,Kind.REFUND)) record.personalAmount else record.amount
    val amountText=(when(record.kind) {Kind.INCOME,Kind.REFUND->"+";Kind.EXPENSE->"−";else->""})+referenceMoney(displayAmount)
    val shareText=if(record.kind==Kind.EXPENSE && record.personalAmount!=record.amount) {
        if(showPersonalAmount) "Your share" else "Your share ${editorialMoney(record.personalAmount)}"
    } else null
    val category=snapshot?.categories?.find {it.id==record.categoryId}
    val icon=when {record.kind==Kind.INCOME || record.kind==Kind.REFUND->R.drawable.ic_arrow_in;record.kind==Kind.TRANSFER->R.drawable.ic_transfer;category?.icon=="food" || category?.name?.contains("food",true)==true->R.drawable.ic_food;category?.icon=="shopping" || category?.name?.contains("grocer",true)==true->R.drawable.ic_basket;category?.icon=="transport" || category?.name?.contains("transport",true)==true->R.drawable.ic_transit;else->R.drawable.ic_activity}
    val accounts=record.accountIds.mapNotNull {id->snapshot?.accounts?.find {it.id==id}?.name}.joinToString(" → ")
    val date=Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM"))
    val metadata=listOf(date,category?.name,accounts.ifBlank {null}).filterNotNull().joinToString(" · ")
    
    val large=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f || amountText.length>14
    Surface(onClick=onClick,color=containerColor,modifier=Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                ReferenceIcon(icon,if(icon==R.drawable.ic_food) ReferenceTone.Peach else if(icon==R.drawable.ic_basket) ReferenceTone.Lavender else ReferenceTone.Blue,44.dp)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(record.description,style=MaterialTheme.typography.titleSmall)
                    Text(metadata,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                    if(large) {
                        Text(amountText,style=MaterialTheme.typography.titleMedium)
                        shareText?.let {Text(it,style=MaterialTheme.typography.labelSmall,color=Tokens.muted)}
                    }
                    if(record.reversed) Text("Reversed",color=Tokens.negative,style=MaterialTheme.typography.bodySmall)
                }
                if(!large) Column(horizontalAlignment=Alignment.End,modifier=Modifier.widthIn(max=130.dp)) {
                    Text(amountText,style=MaterialTheme.typography.titleSmall)
                    shareText?.let {Text(it,style=MaterialTheme.typography.labelSmall,color=Tokens.muted)}
                }
            }
            HorizontalDivider(color=Tokens.border.copy(alpha=0.5f))
        }
    }
}












