package com.smartexpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Budget limits are plans; every used amount comes from the full monthly report. */
@Composable fun BudgetsScreen(s:Snapshot,selected:String,onMonth:(String)->Unit,onBack:()->Unit,onBudgetSave:(String,String?,Long,()->Unit)->Unit,busy:Boolean=false) {
    var monthPicker by rememberSaveable {mutableStateOf(false)}
    var editing by rememberSaveable {mutableStateOf(false)}
    var category by rememberSaveable {mutableStateOf<String?>(null)}
    var amount by rememberSaveable {mutableStateOf("")}
    val month=YearMonth.parse(selected)
    val ready=s.reportMonth==selected
    val budgets=s.budgets.filter {it.month==selected}
    val overall=budgets.firstOrNull {it.categoryId==null}
    fun edit(id:String?) {
        category=id;amount=budgets.firstOrNull {it.categoryId==id}?.amount?.let(Money::input).orEmpty();editing=true
    }
    BackHandler(!editing && !monthPicker,onBack)
    Page("Budgets",largeTitle=true,actions={TextButton({monthPicker=true}) {Text(month.format(DateTimeFormatter.ofPattern("MMMM"))+" ⌄")}}) {
        if(!ready) item {Text("Loading this month's report…",color=Tokens.muted)} else {
            item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                val large=LocalDensity.current.fontScale>1.3f || referenceMoney(s.selectedReport.spending).length>14 || (overall?.let {referenceMoney(it.amount).length}?:0)>14
                if(large) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(referenceMoney(s.selectedReport.spending),style=MaterialTheme.typography.headlineMedium)
                    Text(overall?.let {"spent of ${referenceMoney(it.amount)}"}?:"spent this month",color=Tokens.muted,style=MaterialTheme.typography.bodyLarge)
                } else Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(referenceMoney(s.selectedReport.spending),Modifier.weight(1f,false),style=MaterialTheme.typography.headlineMedium)
                    Text(overall?.let {"spent of ${referenceMoney(it.amount)}"}?:"spent this month",color=Tokens.muted,style=MaterialTheme.typography.bodyLarge)
                }
                overall?.let {BudgetProgress(s.selectedReport.spending,it.amount)}
                if(overall==null) Text("No overall limit set. Category budgets are shown separately.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }}}
            budgets.filter {it.categoryId!=null}.forEach {budget->item(key=budget.id) {
                val value=s.categories.find {it.id==budget.categoryId}
                val used=s.selectedReport.categories[budget.categoryId]?:0L
                val icon=when {value?.icon=="food" || value?.name?.contains("food",true)==true->R.drawable.ic_food;value?.icon=="shopping" || value?.name?.contains("shopping",true)==true->R.drawable.ic_basket;value?.icon=="transport" || value?.name?.contains("travel",true)==true->R.drawable.ic_transit;value?.icon=="home"->R.drawable.ic_home;else->R.drawable.ic_activity}
                Surface(onClick={edit(budget.categoryId)},enabled=!busy,color=Tokens.paper,shape=Tokens.card,modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.Top) {
                        ReferenceIcon(icon,when(icon) {R.drawable.ic_food->ReferenceTone.Peach;R.drawable.ic_basket->ReferenceTone.Pink;else->ReferenceTone.Lavender},46.dp)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            Text(value?.name?:"Unavailable category",style=MaterialTheme.typography.titleMedium)
                            Text("${referenceMoney(used)} of ${referenceMoney(budget.amount)}",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
                            BudgetProgress(used,budget.amount)
                        }
                        Text("›",color=Tokens.muted,style=MaterialTheme.typography.titleLarge)
                    }
                }
            }}
            if(budgets.isEmpty()) item {Text("Set an overall or category budget to track your spending pace.",color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)}
            item {ReferenceAction("Edit budgets",R.drawable.ic_compose,!busy) {edit(null)}}
            item {ReferenceInfo("Transfers and repayments are excluded from budgets. Spending uses your share, minus refunds.")}
        }
        item {TextButton(onBack,Modifier.fillMaxWidth()) {Text("Back")}}
    }
    if(monthPicker) EditorialMonthPicker(month,{monthPicker=false}) {onMonth(it.toString());monthPicker=false}
    if(editing) EditorialBottomSheet("Set monthly budget",{if(!busy) editing=false}) {
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),color=Tokens.muted)
        Choice("Budget category",category?:"all",listOf("all" to "Overall")+s.categories.filterNot {it.archived}.map {it.id to it.name}) {id->
            category=id.takeUnless {it=="all"};amount=budgets.firstOrNull {it.categoryId==category}?.amount?.let(Money::input).orEmpty()
        }
        Field("Budget amount (₹)",amount,{amount=it},true)
        val value=runCatching {Money.parse(amount).also {require(it>0)}}.getOrNull()
        if(ready) {
            val used=if(category==null) s.selectedReport.spending else s.selectedReport.categories[category]?:0L
            AmountRow("Already spent",used)
            if(value!=null) runCatching {Math.subtractExact(value,used)}.getOrNull()?.let {AmountRow("Remaining",it)}
        } else Text("Loading this month's report…",color=Tokens.muted)
        EditorialPrimaryButton("Save budget",ready && value!=null && !busy) {onBudgetSave(selected,category,value!!) {editing=false}}
    }
}

@Composable private fun BudgetProgress(used:Long,limit:Long) {
    val ratio=if(limit>0) used.toDouble()/limit.toDouble() else 0.0
    val percent=(ratio*100).roundToInt()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        ReferenceProgress(ratio.toFloat(),Modifier.weight(1f),overLimit=used>limit)
        Text("$percent%",color=if(used>limit) Tokens.negative else Tokens.muted,style=MaterialTheme.typography.bodySmall)
    }
}
