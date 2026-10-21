package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import com.smartexpense.R
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun GpaySettingsScreen(s:Snapshot,busy:Boolean,onBalance:(Long,()->Unit)->Unit,onSchedule:(Boolean,Long,Int,String,()->Unit)->Unit,onSetup:()->Unit) {
    val source=s.walletSources
    if(source==null) {Page("GPay reset schedule") {item {EmptyLedger("Set up your sources first","Enter Wallet and GPay balances before configuring resets.","Set up Wallet & GPay",onSetup)}};return}
    val schedule=source.schedule
    val balance=s.accounts.find {it.id==source.gpayId}?.balance?:0
    var actual by rememberSaveable {mutableStateOf(Money.input(balance))}
    var enabled by rememberSaveable {mutableStateOf(schedule.enabled)}
    var target by rememberSaveable {mutableStateOf(Money.input(schedule.target))}
    var day by rememberSaveable {mutableStateOf(schedule.day.toString())}
    var month by rememberSaveable {mutableStateOf(schedule.startMonth)}
    var editor by rememberSaveable {mutableStateOf<String?>(null)}
    var draftTarget by rememberSaveable {mutableStateOf(target)}
    var draftDay by rememberSaveable {mutableStateOf(day)}
    var saved by rememberSaveable {mutableStateOf(false)}
    val value=runCatching {Money.parse(target).also {require(it>=0)}}.getOrNull()
    val next=runCatching {GpaySchedule(enabled,value?:0,day.toInt(),month,firstResetMonth(day.toInt(),month,LocalDate.now())).nextDate(LocalDate.now())}.getOrNull()
    Page("GPay reset schedule") {
        item {Surface(color=Tokens.secondarySurface,shape=Tokens.card) {Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Amount currently",style=MaterialTheme.typography.bodyMedium)
            Text(referenceMoney(balance),style=MaterialTheme.typography.headlineMedium)
        }}}
        item {SectionLabel("CORRECT BALANCE")}
        item {GroupedCard {GroupedRow("Correct balance",R.drawable.ic_compose,supporting="Adjust if this is not accurate",tone=ReferenceTone.Peach) {actual=Money.input(balance);editor="balance"}}}
        item {SectionLabel("MONTHLY RESET")}
        item {GroupedCard {
            ReferenceToggleRow("Enable monthly reset",enabled,{enabled=it;saved=false},!busy)
            if(enabled) {
                GroupedDivider();GroupedRow("Target amount",R.drawable.ic_add,value=value?.let(::referenceMoney)?:"Enter amount") {draftTarget=target;editor="target"}
                GroupedDivider();GroupedRow("Reset day",R.drawable.ic_calendar,value=day) {draftDay=day;editor="day"}
                GroupedDivider();GroupedRow("Starting month",R.drawable.ic_calendar,value=YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy"))) {editor="month"}
            }
        }}
        if(enabled && next!=null) item {Surface(color=Tokens.secondarySurface,shape=Tokens.card) {Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            ReferenceIcon(R.drawable.ic_calendar)
            Column {Text("Next reset",color=Tokens.muted,style=MaterialTheme.typography.bodySmall);Text(next.format(DateTimeFormatter.ofPattern("d MMM yyyy")),style=MaterialTheme.typography.titleMedium)}
        }}}
        item {EditorialPrimaryButton("Save settings",(!enabled || value!=null) && !busy) {onSchedule(enabled,value?:schedule.target,day.toInt(),month) {saved=true}}}
        if(saved) item {Text("Reset settings saved",color=Tokens.muted)}
        item {ReferenceInfo("Local ledger adjustment only. No connection to Google Pay. Short months use their last day.")}
    }
    when(editor) {
        "balance"->EditorialBottomSheet("Correct balance",{if(!busy) editor=null}) {
            ReferenceAmountField("Actual GPay balance (₹)",actual,{actual=it})
            val corrected=runCatching {Money.parse(actual).also {require(it>=0)}}.getOrNull()
            Text("Saving records a balance correction. Your history stays intact.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            EditorialPrimaryButton("Save GPay balance",corrected!=null && corrected!=balance && !busy) {onBalance(corrected!!) {editor=null}}
        }
        "target"->EditorialBottomSheet("Target amount",{editor=null}) {
            ReferenceAmountField("Reset balance (₹)",draftTarget,{draftTarget=it})
            val draftValue=runCatching {Money.parse(draftTarget).also {require(it>=0)}}.getOrNull()
            EditorialPrimaryButton("Done",draftValue!=null) {target=draftTarget;saved=false;editor=null}
        }
        "day"->EditorialBottomSheet("Reset day",{editor=null}) {
            Choice("Reset day",draftDay,(1..31).map {it.toString() to "Day $it"}) {draftDay=it}
            Text("Days 29–31 use the last day of shorter months.",color=Tokens.muted)
            EditorialPrimaryButton("Done") {day=draftDay;saved=false;editor=null}
        }
        "month"->EditorialMonthPicker(YearMonth.parse(month),{editor=null}) {month=it.toString();editor=null;saved=false}
    }
}
