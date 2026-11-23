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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
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
    val today = LocalDate.now()
    val monthBudget = remember(s.budgets) {s.budgets.firstOrNull { it.month == YearMonth.now().toString() && it.categoryId == null }}
    val monthSpent = s.dashboard.month.spending
    // The daily limit is fixed at the start of the day so spending today does not move it.
    val dailyLimit = monthBudget?.let { budgetAllowance(it.amount, (monthSpent - s.dashboard.today).coerceAtLeast(0), today).perDay }
    val hidden = "••••••"
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(start = Tokens.pageInset, end = Tokens.pageInset, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
                        Surface(onClick = { onManage("more") }, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(48.dp).semantics { contentDescription = "Settings" }) {
                            Box(contentAlignment = Alignment.Center) { InitialAvatar(s.localSettings.name.ifBlank { "Tally" }, Modifier.size(44.dp)) }
                        }
                    }
                    Text("Summary", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                }
            }
            item {
                GroupedCard {
                    Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Available to spend", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
                            if (active.isNotEmpty()) IconButton({ visible = !visible }, Modifier.size(40.dp)) {
                                Icon(painterResource(R.drawable.ic_visibility), if (visible) "Hide balance" else "Show balance", Modifier.size(18.dp), tint = Tokens.muted)
                            }
                        }
                        Text(if (!visible) hidden else total?.let(::referenceMoney) ?: "Total unavailable", Modifier.padding(end = 10.dp),
                            style = MaterialTheme.typography.displaySmall, color = if (active.isEmpty()) Tokens.muted else Tokens.text)
                        Text(when {
                            active.isEmpty() -> "Add an account to see your balance here"
                            !visible -> spendable.take(2).joinToString(" · ") { it.name }
                            else -> spendable.take(2).joinToString(" · ") { "${it.name} ${referenceMoney(it.balance)}" }
                        }, Modifier.padding(end = 10.dp), style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.padding(end = 10.dp)) {
                            if (active.isEmpty()) EditorialPrimaryButton("Set up Wallet & GPay") { onManage("wallet") }
                            else PillRow(listOf(
                                PillAction("Expense", R.drawable.ic_add, primary = true) { onAdd(Kind.EXPENSE, false) },
                                PillAction("Scan", null) { onManage("bill-import") },
                                PillAction("Transfer", null) { onAdd(Kind.TRANSFER, false) }
                            ))
                        }
                    }
                }
            }
            if (active.isNotEmpty()) item {
                GroupedCard {
                    Surface(onClick = { onManage("calendar") }, color = Tokens.paper, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(if (dailyLimit != null && dailyLimit > 0) s.dashboard.today.toFloat() / dailyLimit else 0f, CategoryColors.food, 52.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Today", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(referenceMoney(s.dashboard.today), style = MaterialTheme.typography.headlineSmall)
                                    if (dailyLimit != null) Text("of ${referenceMoney(dailyLimit)}", Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
                                }
                                Text(when {
                                    dailyLimit == null -> "Spent today · open the calendar"
                                    s.dashboard.today <= dailyLimit -> "Daily limit · ${referenceMoney(dailyLimit - s.dashboard.today)} left"
                                    else -> "Daily limit · ${referenceMoney(s.dashboard.today - dailyLimit)} over"
                                }, style = MaterialTheme.typography.bodySmall, color = if (dailyLimit != null && s.dashboard.today > dailyLimit) Tokens.owed else Tokens.muted)
                            }
                        }
                    }
                }
            }
            if (active.isNotEmpty()) item {
                AdaptivePair(equalCards = true, first = {
                    SummaryTile({ onManage("budgets") }) {
                        Text("${today.format(DateTimeFormatter.ofPattern("MMMM"))} budget", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                        if (monthBudget != null && monthBudget.amount > 0) {
                            val used = (monthSpent * 100 / monthBudget.amount).toInt()
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("$used%", style = MaterialTheme.typography.headlineSmall)
                                Text("used", Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
                            }
                            TallyMeter(monthSpent.toFloat() / monthBudget.amount)
                        } else {
                            Text("Not set", style = MaterialTheme.typography.headlineSmall)
                            Text("Set a monthly budget", style = MaterialTheme.typography.bodySmall, color = Tokens.accent)
                        }
                    }
                }, second = {
                    SummaryTile({ onManage("split") }) {
                        Text("Friends owe", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                        Text(referenceMoney(receive), style = MaterialTheme.typography.headlineSmall)
                        Text(if (pay > 0) "You owe ${referenceMoney(pay)}" else "You owe nothing", style = MaterialTheme.typography.bodySmall, color = if (pay > 0) Tokens.owed else Tokens.muted)
                    }
                })
            }
            if (latest.isEmpty()) item {
                GroupedCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(painterResource(R.drawable.ic_tally_mark), null, Modifier.size(40.dp), tint = Tokens.muted.copy(alpha = .6f))
                        Text("Nothing recorded yet", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("Add what you spend and Tally keeps the running total for the day and month.", style = MaterialTheme.typography.bodyMedium, color = Tokens.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        PillRow(listOf(
                            PillAction("Add an expense", null, primary = true) { onAdd(Kind.EXPENSE, false) },
                            PillAction("Scan a bill", null, tonal = true) { onManage("bill-import") }
                        ))
                    }
                }
            } else {
                item { SectionHeading("Recent", "See all") { onManage("activity") } }
                item {
                    GroupedCard {
                        latest.forEach { record ->
                            Box(Modifier.padding(horizontal = 16.dp)) { TransactionRow(record, s, containerColor = Tokens.paper) { onDetails(record.id) } }
                        }
                    }
                }
            }
            if (active.isEmpty() || monthBudget == null) {
                item { SectionLabel("Set up in a minute") }
                item {
                    GroupedCard {
                        val steps = listOfNotNull(
                            if (active.isEmpty()) "Add your wallet or UPI account" to "wallet" else null,
                            if (monthBudget == null) "Set a monthly budget" to "budgets" else null,
                            "Add the home-screen widget" to "favourites"
                        )
                        steps.forEachIndexed { index, (label, destination) ->
                            if (index > 0) GroupedDivider(56.dp)
                            GroupedRow(label, R.drawable.ic_add, leading = { StepNumber(index + 1) }) { onManage(destination) }
                        }
                    }
                }
            }
            if (active.isNotEmpty()) {
                item { SectionLabel("More for your money") }
                item {
                    GroupedCard {
                        GroupedRow("Accounts", R.drawable.ic_wallet_outline, supporting = "${active.size} active", tone = ReferenceTone.Gray, onClick = onAccounts)
                        GroupedDivider(60.dp)
                        GroupedRow("Add money", R.drawable.ic_add, tone = ReferenceTone.Lavender) { onManage(if (sources == null) "wallet" else "wallet/add-money") }
                        GroupedDivider(60.dp)
                        GroupedRow("Record income", R.drawable.ic_arrow_in) { onAdd(Kind.INCOME, false) }
                        GroupedDivider(60.dp)
                        GroupedRow("Record repayment", R.drawable.ic_people, tone = ReferenceTone.Peach, onClick = onRepay)
                        GroupedDivider(60.dp)
                        GroupedRow("Money planner", R.drawable.ic_insights, tone = ReferenceTone.Lavender) { onManage("planner") }
                        GroupedDivider(60.dp)
                        GroupedRow("Check balance", R.drawable.ic_account, tone = ReferenceTone.Gray) { onManage("balance-check") }
                        GroupedDivider(60.dp)
                        GroupedRow("Favourites", R.drawable.ic_star_outline, tone = ReferenceTone.Pink) { onManage("favourites") }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionLabel("Repeat an expense") }
                item { GroupedCard {
                    recent.forEachIndexed { index, record ->
                        if (index > 0) GroupedDivider(60.dp)
                        GroupedRow(record.description, R.drawable.ic_duplicate_outline, supporting = "Opens a draft for today", value = editorialMoney(record.amount)) { onRepeat(record.id) }
                    }
                } }
            }
        }
    }
}

/** A pill-shaped button for the Summary cards. */
internal class PillAction(val label: String, val icon: Int?, val primary: Boolean = false, val tonal: Boolean = false, val onClick: () -> Unit)

/** Lays pills in a row, or stacks them full width when the screen is narrow or text is large. */
@Composable internal fun PillRow(actions: List<PillAction>) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val needed = with(density) { actions.sumOf { measurer.measure(it.label, style).size.width }.toDp() } + 44.dp * actions.size + 8.dp * (actions.size - 1)
        val stacked = density.fontScale > 1.3f || needed > maxWidth
        val pill: @Composable (PillAction, Modifier) -> Unit = { action, modifier ->
            val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Surface(onClick = lightHapticAction(action.onClick), interactionSource = interaction, shape = CircleShape,
                color = when { action.primary -> Tokens.accent; action.tonal -> Tokens.tonal; else -> Tokens.secondarySurface },
                contentColor = when { action.primary -> MaterialTheme.colorScheme.onPrimary; action.tonal -> Tokens.accent; else -> Tokens.text },
                modifier = modifier.heightIn(min = 44.dp).softPress(interaction)) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    action.icon?.let { Icon(painterResource(it), null, Modifier.size(18.dp)) }
                    Text(action.label, style = style, maxLines = 1)
                }
            }
        }
        if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions.forEach { pill(it, Modifier.fillMaxWidth()) } }
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions.forEach { pill(it, Modifier) } }
    }
}

@Composable private fun SummaryTile(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = lightHapticAction(onClick), interactionSource = interaction, shape = Tokens.card, color = Tokens.paper,
        modifier = Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 112.dp).softPress(interaction)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable private fun StepNumber(number: Int) {
    Box(Modifier.size(28.dp).border(1.dp, Tokens.border, CircleShape), contentAlignment = Alignment.Center) {
        Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = Tokens.muted)
    }
}

/** Circular progress used for the daily limit. Values over 1 show a full ring. */
@Composable internal fun ProgressRing(progress: Float, color: Color, size: androidx.compose.ui.unit.Dp) {
    val track = Tokens.secondarySurface
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * .14f
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Tally-mark meter: a row of thin bars, filled from the left. The app's signature progress bar. */
@Composable internal fun TallyMeter(progress: Float, color: Color = Tokens.accent, bars: Int = 28) {
    val empty = Tokens.secondarySurface
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val gap = this.size.width / bars
        val width = (gap * .45f).coerceAtLeast(1f)
        val filled = (progress.coerceIn(0f, 1f) * bars).toInt()
        for (i in 0 until bars) drawLine(if (i < filled) color else empty,
            androidx.compose.ui.geometry.Offset(i * gap + width / 2, 0f), androidx.compose.ui.geometry.Offset(i * gap + width / 2, this.size.height), width)
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
    val amountColor=if(record.kind==Kind.INCOME || record.kind==Kind.REFUND) Tokens.positive else Tokens.text
    val shareText=if(record.kind==Kind.EXPENSE && record.personalAmount!=record.amount) {
        if(showPersonalAmount) "Your share" else "Your share ${editorialMoney(record.personalAmount)}"
    } else null
    val category=snapshot?.categories?.find {it.id==record.categoryId}
    val (icon,iconColor)=recordVisual(record,category)
    val accounts=record.accountIds.mapNotNull {id->snapshot?.accounts?.find {it.id==id}?.name}.joinToString(" → ")
    val date=Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM"))
    val metadata=listOf(date,category?.name,accounts.ifBlank {null}).filterNotNull().joinToString(" · ")
    
    val large=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f || amountText.length>14
    Surface(onClick=onClick,color=containerColor,modifier=Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                SolidIcon(icon,iconColor,36.dp)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(record.description,style=MaterialTheme.typography.titleSmall)
                    Text(metadata,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                    if(large) {
                        Text(amountText,style=MaterialTheme.typography.titleMedium,color=amountColor)
                        shareText?.let {Text(it,style=MaterialTheme.typography.labelSmall,color=Tokens.muted)}
                    }
                    if(record.reversed) Text("Reversed",color=Tokens.negative,style=MaterialTheme.typography.bodySmall)
                }
                if(!large) Column(horizontalAlignment=Alignment.End,modifier=Modifier.widthIn(max=130.dp)) {
                    Text(amountText,style=MaterialTheme.typography.titleSmall,color=amountColor)
                    shareText?.let {Text(it,style=MaterialTheme.typography.labelSmall,color=Tokens.muted)}
                }
            }
            HorizontalDivider(Modifier.padding(start=48.dp),color=Tokens.border.copy(alpha=0.65f))
        }
    }
}













/** Icon and solid square colour for a transaction, chosen from its kind and category name. */
@Composable internal fun recordVisual(record:Record,category:Category?):Pair<Int,Color> {
    val name=(category?.name.orEmpty()+" "+category?.icon.orEmpty()).lowercase()
    return when {
        record.kind==Kind.INCOME || record.kind==Kind.REFUND->R.drawable.ic_arrow_in to CategoryColors.moneyIn
        record.kind==Kind.TRANSFER->R.drawable.ic_transfer to CategoryColors.gray
        record.personIds.any {it!="self"}->R.drawable.ic_people to CategoryColors.brand
        "food" in name || "dining" in name || "restaurant" in name->R.drawable.ic_food to CategoryColors.food
        "grocer" in name->R.drawable.ic_basket to CategoryColors.groceries
        "transport" in name || "travel" in name || "fuel" in name->R.drawable.ic_transit to CategoryColors.transport
        "bill" in name || "subscription" in name || "rent" in name->R.drawable.ic_document_outline to CategoryColors.bills
        "shop" in name->R.drawable.ic_shop_outline to CategoryColors.shopping
        "entertain" in name->R.drawable.ic_star_outline to CategoryColors.shopping
        "health" in name->R.drawable.ic_gym_outline to CategoryColors.brand
        "education" in name->R.drawable.ic_laptop_outline to CategoryColors.transport
        else->R.drawable.ic_activity to CategoryColors.gray
    }
}
