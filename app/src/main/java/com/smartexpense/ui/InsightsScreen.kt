package com.smartexpense.ui
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

@Composable fun InsightsScreen(s:Snapshot,selected:String,onMonth:(String)->Unit,onBudget:(String,String?,Long)->Unit,onPlanner:()->Unit={},onBudgetSave:((String,String?,Long,()->Unit)->Unit)?=null,busy:Boolean=false,onTransactions:()->Unit={},onPeriodReport:(suspend (LocalDate,LocalDate)->PeriodReport)?=null) {
    var budgetAmount by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf<String?>(null)}
    var editing by rememberSaveable {mutableStateOf(false)}
    var monthPicker by rememberSaveable {mutableStateOf(false)}
    var budgetsOpen by rememberSaveable {mutableStateOf(false)}
    var details by rememberSaveable {mutableStateOf(false)}
    val month=runCatching {YearMonth.parse(selected)}.getOrElse {YearMonth.now()}
    var mode by rememberSaveable {mutableStateOf("Month")}
    var weekAnchor by rememberSaveable(selected) {mutableStateOf((if(month==YearMonth.now()) LocalDate.now() else month.atEndOfMonth()).with(DayOfWeek.MONDAY).toString())}
    val period=if(onPeriodReport==null) "Month" else mode
    val start=when(period) {"Week"->LocalDate.parse(weekAnchor);"Year"->month.atDay(1).withDayOfYear(1);else->month.atDay(1)}
    val end=when(period) {"Week"->start.plusDays(6);"Year"->start.plusYears(1).minusDays(1);else->month.atEndOfMonth()}
    var loadedReport by remember {mutableStateOf<PeriodReport?>(null)}
    var loadedRange by remember {mutableStateOf("")}
    var reportError by remember {mutableStateOf<String?>(null)}
    val rangeKey="$period:$start:$end"
    LaunchedEffect(rangeKey,s.records,s.selectedReport,s.reportMonth,onPeriodReport) {
        loadedReport=null;loadedRange="";reportError=null
        if(period!="Month" && onPeriodReport!=null) {
            try {loadedReport=onPeriodReport(start,end);loadedRange=rangeKey}
            catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {reportError=error.message?:"Could not load this period's report";loadedRange=rangeKey}
        }
    }
    val ready=if(period=="Month") s.reportMonth==selected else loadedRange==rangeKey && loadedReport!=null
    val visibleError=reportError.takeIf {loadedRange==rangeKey}
    val report=if(period=="Month") s.selectedReport else loadedReport?:PeriodReport(0,0,emptyMap(),emptyMap())
    fun movePeriod(delta:Long) {
        when(period) {"Week"->weekAnchor=LocalDate.parse(weekAnchor).plusWeeks(delta).toString();"Year"->onMonth(month.plusYears(delta).toString());else->onMonth(month.plusMonths(delta).toString())}
    }
    if(budgetsOpen) {
        BudgetsScreen(s,selected,onMonth,{budgetsOpen=false},{value,id,amount,done->
            if(onBudgetSave!=null) onBudgetSave(value,id,amount,done) else {onBudget(value,id,amount);done()}
        },busy)
        return
    }
    Page("Spending",largeTitle=true) {
        if(onPeriodReport!=null) item {EditorialSegmentedControl(period,listOf("Week" to "Week","Month" to "Month","Year" to "Year")) {mode=it}}
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton({movePeriod(-1)}) {Icon(painterResource(R.drawable.ic_chevron_down),"Previous ${period.lowercase()}",Modifier.rotate(90f))}
            TextButton({monthPicker=true},Modifier.weight(1f)) {Text(when(period) {"Week"->"${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}";"Year"->month.year.toString();else->month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))},style=MaterialTheme.typography.bodyLarge,color=Tokens.text)}
            IconButton({movePeriod(1)}) {Icon(painterResource(R.drawable.ic_chevron_down),"Next ${period.lowercase()}",Modifier.rotate(-90f))}
        }}
        if(!ready) item {Text(visibleError?:if(period=="Month") "Loading this month's report…" else "Loading this ${period.lowercase()}'s report…",color=if(visibleError==null) Tokens.muted else Tokens.negative)} else {
            item {Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(referenceMoney(report.spending),style=MaterialTheme.typography.displayMedium)
                Text("Total spent",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
            }}
            item {TrendChart(report.daily,month,start,end)}
            item {Text("By category",style=MaterialTheme.typography.titleMedium)}
            item {if(report.categories.isEmpty()) EmptyLedger("No expenses this ${period.lowercase()}","Record an expense to see your category breakdown.") else CategoryDistribution(report.categories,s.categories)}
            item {TextButton(onTransactions,Modifier.fillMaxWidth()) {Text("View transactions →")}}
            item {ReferenceAction("Monthly budgets",R.drawable.ic_insights) {budgetsOpen=true}}
            item {TextButton({editing=true},Modifier.fillMaxWidth()) {Text("Set budget")}}
            item {TextButton({details=!details},Modifier.fillMaxWidth()) {Text(if(details) "Hide spending details" else "Spending details")}}
            if(details) {
                item {Text("Monthly details · ${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}",style=MaterialTheme.typography.titleMedium)}
                if(s.reportMonth==selected) item {EverydayInsights(s)}
                if(s.reportMonth==selected) item {Panel {
                    AmountRow("Previous month's spending",s.previousPeriodSpending)
                    AmountRow("Monthly income",s.selectedReport.income)
                    val days=if(month==YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth()
                    AmountRow("Monthly average daily spend",s.selectedReport.spending/days)
                    AmountRow("Receivables",Finance.total(s.debts.filter {it.to=="self"}.map {it.outstanding}),Tokens.positive)
                    AmountRow("Payables",Finance.total(s.debts.filter {it.from=="self"}.map {it.outstanding}),Tokens.expense)
                }}
                item {OutlinedButton(onPlanner,Modifier.fillMaxWidth()) {Text("Open money planner")}}
                item {ReferenceInfo("Your personal shares, after refunds. Transfers and repayments are excluded from personal spending.")}
            }
        }
    }
    if(monthPicker) EditorialMonthPicker(month,{monthPicker=false}) {
        weekAnchor=(if(it==YearMonth.now()) LocalDate.now() else it.atEndOfMonth()).with(DayOfWeek.MONDAY).toString()
        onMonth(it.toString());monthPicker=false
    }
    if(editing) EditorialBottomSheet("Set monthly budget",{if(!busy) editing=false}) {
        Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy")),color=Tokens.muted)
        Choice("Budget category",category?:"all",listOf("all" to "Overall")+s.categories.filterNot {it.archived}.map {it.id to it.name}) {category=if(it=="all") null else it}
        Field("Budget amount (₹)",budgetAmount,{budgetAmount=it},true)
        val entered=runCatching {Money.parse(budgetAmount)}.getOrNull()
        val spent=if(category==null) s.selectedReport.spending else s.selectedReport.categories[category]?:0L
        val ready=s.reportMonth==selected
        if(ready && entered!=null && entered>0) {AmountRow("Already spent",spent);runCatching {Math.subtractExact(entered,spent)}.getOrNull()?.let {AmountRow("Remaining",it)}}
        if(!ready) Text("Loading this month's report…",color=Tokens.muted)
        EditorialPrimaryButton("Save budget",ready && entered!=null && entered>0 && !busy) {
            val finished={budgetAmount="";editing=false}
            if(onBudgetSave!=null) onBudgetSave(selected,category,entered!!,finished) else {onBudget(selected,category,entered!!);finished()}
        }
    }
}
@Composable private fun CategoryDistribution(amounts:Map<String?,Long>,categories:List<Category>) {
    val entries=amounts.entries.sortedByDescending {it.value}
    val positiveTotal=entries.sumOf {it.value.coerceAtLeast(0).toDouble()}
    val tones=Tokens.chartTones
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(positiveTotal>0) Canvas(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(7.dp))) {
            var left=0f
            entries.forEachIndexed {index,entry->if(entry.value>0) {
                val width=(entry.value.toDouble()/positiveTotal*size.width).toFloat()
                drawRect(tones[index%tones.size],Offset(left,0f),Size(width,size.height));left+=width
            }}
        }
        if(entries.any {it.value<0}) Text("Distribution shows positive spending; net refunds remain in the amounts below.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
        GroupedCard {entries.forEachIndexed {index,entry->
            val category=categories.find {it.id==entry.key}
            val icon=when(category?.icon) {"food"->R.drawable.ic_food;"shopping"->R.drawable.ic_basket;"transport"->R.drawable.ic_transit;else->R.drawable.ic_activity}
            val percent=if(positiveTotal>0 && entry.value>=0) "${kotlin.math.round(entry.value/positiveTotal*100).toInt()}%" else if(entry.value<0) "Net refund" else "0%"
            val large=LocalDensity.current.fontScale>1.3f || referenceMoney(entry.value).length>14
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ReferenceIcon(icon,when(index%3) {0->ReferenceTone.Peach;1->ReferenceTone.Lavender;else->ReferenceTone.Blue})
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(category?.name?:"Uncategorised",style=MaterialTheme.typography.bodyLarge)
                    if(large) {Text(referenceMoney(entry.value),style=MaterialTheme.typography.titleSmall);Text(percent,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
                }
                if(!large) Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(3.dp)) {Text(referenceMoney(entry.value),style=MaterialTheme.typography.titleSmall);Text(percent,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
            }
            if(index<entries.lastIndex) GroupedDivider()
        }}
    }
}
