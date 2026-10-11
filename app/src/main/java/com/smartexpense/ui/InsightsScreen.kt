package com.smartexpense.ui
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun InsightsScreen(s:Snapshot,selected:String,onMonth:(String)->Unit,onBudget:(String,String?,Long)->Unit,onPlanner:()->Unit={},onBudgetSave:((String,String?,Long,()->Unit)->Unit)?=null,busy:Boolean=false) {
    var budgetAmount by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf<String?>(null)}
    var editing by rememberSaveable {mutableStateOf(false)}
    var monthPicker by rememberSaveable {mutableStateOf(false)}
    val month=runCatching {YearMonth.parse(selected)}.getOrElse {YearMonth.now()}
    val report=s.selectedReport
    Page("Insights","Your personal shares, after refunds") {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton({onMonth(month.minusMonths(1).toString())}) {Icon(painterResource(R.drawable.ic_chevron_down),"Previous month",Modifier.rotate(90f))}
            TextButton({monthPicker=true},Modifier.weight(1f)) {Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy"))+" ⌄",style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
            IconButton({onMonth(month.plusMonths(1).toString())}) {Icon(painterResource(R.drawable.ic_chevron_down),"Next month",Modifier.rotate(-90f))}
        }}
        if(s.reportMonth!=selected) item {Text("Loading this month's report…",color=Tokens.muted)} else {
            item {AdaptivePair(equalCards=true,first={Panel {Text("Personal spending",style=MaterialTheme.typography.labelMedium,color=Tokens.muted);Text(Money.format(report.spending),style=MaterialTheme.typography.headlineSmall,color=Tokens.expense);Text("Previous: ${Money.format(s.previousPeriodSpending)}",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}},second={Panel {Text("Income",style=MaterialTheme.typography.labelMedium,color=Tokens.muted);Text(Money.format(report.income),style=MaterialTheme.typography.headlineSmall,color=Tokens.positive);val days=if(month==YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth();Text("${Money.format(report.spending/days)} average daily spend",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}})}
            item {Text("Transfers and repayments are excluded from personal spending.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
            item {Panel {
                Text("Spending by category",style=MaterialTheme.typography.titleLarge)
                if(report.categories.isEmpty()) EmptyLedger("No expenses this month","Record an expense to see your category breakdown.") else CategoryDistribution(report.categories,s.categories)
            }}
            item {Panel {
                Text("Daily spending",style=MaterialTheme.typography.titleLarge)
                if(report.daily.isEmpty()) Text("Record expenses to see your daily trend.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall) else TrendChart(report.daily)
            }}
            item {Panel {
                SectionHeading("Monthly budgets",if(editing) "Close" else "Set budget") {editing=!editing}
                val budgets=s.budgets.filter {it.month==month.toString()}
                if(budgets.isEmpty()) Text("Set an overall or category budget to track your spending pace.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                budgets.forEach {budget->
                    val used=if(budget.categoryId==null) report.spending else report.categories[budget.categoryId]?:0L
                    Text(s.categories.find {it.id==budget.categoryId}?.name?:"Overall monthly budget",style=MaterialTheme.typography.titleSmall)
                    AmountRow("Used of ${Money.format(budget.amount)}",used)
                    LinearProgressIndicator(progress={(used.toDouble()/budget.amount.toDouble()).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=if(used>budget.amount) Tokens.negative else Tokens.accent)
                    AmountRow(if(used>budget.amount) "Over budget" else "Remaining",if(used>budget.amount) used-budget.amount else budget.amount-used,if(used>budget.amount) Tokens.negative else Tokens.positive)
                }
            }}
            item {OutlinedButton(onPlanner,Modifier.fillMaxWidth()) {Text("Open money planner")}}
            item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {AmountRow("Receivables",Finance.total(s.debts.filter {it.to=="self"}.map {it.outstanding}),Tokens.positive);AmountRow("Payables",Finance.total(s.debts.filter {it.from=="self"}.map {it.outstanding}),Tokens.expense)}}
        }
    }
    if(monthPicker) EditorialMonthPicker(month,{monthPicker=false}) {onMonth(it.toString());monthPicker=false}
    if(editing) EditorialBottomSheet("Set monthly budget",{editing=false}) {
        Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy")),color=Tokens.muted)
        Choice("Budget category",category?:"all",listOf("all" to "Overall")+s.categories.filterNot {it.archived}.map {it.id to it.name}) {category=if(it=="all") null else it}
        Field("Budget amount (₹)",budgetAmount,{budgetAmount=it},true)
        val entered=runCatching {Money.parse(budgetAmount)}.getOrNull()
        val spent=if(category==null) report.spending else report.categories[category]?:0L
        if(entered!=null && entered>0) {AmountRow("Already spent",spent);runCatching {Math.subtractExact(entered,spent)}.getOrNull()?.let {AmountRow("Remaining",it)}}
        EditorialPrimaryButton("Save budget",entered!=null && entered>0 && !busy) {
            val finished={budgetAmount="";editing=false}
            if(onBudgetSave!=null) onBudgetSave(selected,category,entered!!,finished) else {onBudget(selected,category,entered!!);finished()}
        }
        OutlinedButton({editing=false},Modifier.fillMaxWidth()) {Text("Cancel")}
    }
}
@Composable private fun CategoryDistribution(amounts:Map<String?,Long>,categories:List<Category>) {
    val entries=amounts.entries.sortedByDescending {it.value}
    val positiveTotal=entries.sumOf {it.value.coerceAtLeast(0).toDouble()}
    fun color(id:String?)=Tokens.chartTones[entries.indexOfFirst {it.key==id}.coerceAtLeast(0)%Tokens.chartTones.size]
    if(positiveTotal>0) Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        var left=0f
        entries.filter {it.value>0}.forEach {entry->val width=(entry.value.toDouble()/positiveTotal*size.width).toFloat();drawRect(color(entry.key),Offset(left,0f),Size(width,size.height));left+=width}
    }
    if(entries.any {it.value<0}) Text("Chart shows positive category spending; refunds remain in the amounts below.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
    entries.forEach {entry->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(color(entry.key),CircleShape))
        Box(Modifier.weight(1f)) {AmountRow((categories.find {it.id==entry.key}?.name?:"Uncategorized")+if(entry.value<0) " · net refund" else "",entry.value)}
    }}
}


