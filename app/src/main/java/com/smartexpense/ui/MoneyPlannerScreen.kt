package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.smartexpense.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MoneyPlannerScreen(snapshot: Snapshot, onBack: () -> Unit = {}) {
    val total=runCatching {Finance.total(snapshot.accounts.filterNot {it.archived}.map {it.balance})}.getOrDefault(0L)
    var available by rememberSaveable {mutableStateOf(Money.input(total.coerceAtLeast(0)))}
    var costs by rememberSaveable {mutableStateOf("0")}
    var savings by rememberSaveable {mutableStateOf("0")}
    var extra by rememberSaveable {mutableFloatStateOf(0f)}
    var startDate by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var endDate by rememberSaveable {mutableStateOf(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString())}
    var editor by rememberSaveable {mutableStateOf<String?>(null)}
    val days=ChronoUnit.DAYS.between(LocalDate.parse(startDate),LocalDate.parse(endDate))+1
    val scenario=extra.toLong()*100L
    val planResult=runCatching {moneyPlan(Money.parse(available),Math.addExact(Money.parse(costs),scenario),Money.parse(savings),listOf(5000L,3000L,2000L),LocalDate.parse(startDate),LocalDate.parse(endDate))}
    val formatter=DateTimeFormatter.ofPattern("d MMM")
    Page("Money planner",onBack=onBack) {
        item {Surface(onClick={editor="available"},color=Tokens.paper,shape=Tokens.card,modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Text("Available to plan",color=Tokens.muted);Text("ⓘ",color=Tokens.muted)}
            Text(runCatching {referenceMoney(Money.parse(available))}.getOrDefault("₹0"),style=MaterialTheme.typography.headlineLarge)
        }}}
        item {GroupedCard {Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.End) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Days to plan",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                FilledTonalIconButton({if(days>1) endDate=LocalDate.parse(endDate).minusDays(1).toString()},enabled=days>1,shape=Tokens.field) {Text("−",style=MaterialTheme.typography.headlineSmall)}
                Text("$days",Modifier.padding(horizontal=14.dp),style=MaterialTheme.typography.titleMedium)
                FilledTonalIconButton({endDate=LocalDate.parse(endDate).plusDays(1).toString()},shape=Tokens.field) {Text("+",style=MaterialTheme.typography.headlineSmall)}
            }
            TextButton({editor="end"}) {Text("Until ${LocalDate.parse(endDate).format(formatter)}",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
        }}}
        item {GroupedCard {
            GroupedRow("Planned bills",R.drawable.ic_calendar,value=runCatching {referenceMoney(Money.parse(costs))}.getOrDefault("—")) {editor="costs"}
            GroupedDivider();GroupedRow("Reserved savings",R.drawable.ic_piggy_outline,value=runCatching {referenceMoney(Money.parse(savings))}.getOrDefault("—"),tone=ReferenceTone.Pink) {editor="savings"}
        }}
        item {Surface(color=Tokens.secondarySurface,shape=Tokens.card) {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("You can plan about",style=MaterialTheme.typography.bodyLarge)
            Text(planResult.getOrNull()?.let {"${referenceMoney(it.dailyAllowance)} / day"}?:"Check your amounts",style=MaterialTheme.typography.headlineLarge)
            if(planResult.isFailure) Text(planResult.exceptionOrNull()?.message?:"Check your amounts",color=Tokens.negative,style=MaterialTheme.typography.bodySmall)
        }}}
        item {Surface(color=Tokens.secondarySurface,shape=Tokens.card) {Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("What if?",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(top=12.dp)) {Text("Extra expense",Modifier.weight(1f));Text(referenceMoney(scenario),color=Tokens.accent)}
            val track=Tokens.border;val active=Tokens.accent
            Slider(extra,{extra=it},valueRange=0f..5000f,
                thumb={Surface(Modifier.size(24.dp),shape=androidx.compose.foundation.shape.CircleShape,color=Tokens.accent) {}},
                track={slider->androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    drawLine(track,androidx.compose.ui.geometry.Offset(0f,size.height/2),androidx.compose.ui.geometry.Offset(size.width,size.height/2),size.height,cap=androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(active,androidx.compose.ui.geometry.Offset(0f,size.height/2),androidx.compose.ui.geometry.Offset(size.width*slider.value/5000f,size.height/2),size.height,cap=androidx.compose.ui.graphics.StrokeCap.Round)
                }})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {listOf("₹0","₹1,000","₹2,000","₹5,000").forEach {Text(it,color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}}
        }}}
        item {ReferenceInfo("Planning only. Balances do not change.")}
    }
    when(editor) {
        "available"->EditorialBottomSheet("Available to plan",{editor=null}) {
            ReferenceAmountField("Available money (₹)",available,{available=it})
            TextButton({available=Money.input(total.coerceAtLeast(0))}) {Text("Use account balances · ${referenceMoney(total)}")}
            Text("Enter the money you want to plan, including expected income only once.",color=Tokens.muted)
            EditorialPrimaryButton("Done") {editor=null}
        }
        "costs","savings"->EditorialBottomSheet(if(editor=="costs") "Planned bills" else "Reserved savings",{editor=null}) {
            val isCosts=editor=="costs"
            ReferenceAmountField(if(isCosts) "Committed costs (₹)" else "Desired savings (₹)",if(isCosts) costs else savings,{if(isCosts) costs=it else savings=it})
            EditorialPrimaryButton("Done") {editor=null}
        }
        "end"->EditorialDatePicker("Plan end date",LocalDate.parse(endDate),{editor=null}) {endDate=it.toString();editor=null}
    }
}
