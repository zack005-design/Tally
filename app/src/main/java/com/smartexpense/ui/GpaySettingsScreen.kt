package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun GpaySettingsScreen(s:Snapshot,busy:Boolean,onBalance:(Long,()->Unit)->Unit,onSchedule:(Boolean,Long,Int,String,()->Unit)->Unit,onSetup:()->Unit) {
    val source=s.walletSources
    if(source==null) {Page("GPay settings","Manually track the money you have") {item {EmptyLedger("Set up your sources first","Enter Wallet and GPay balances before configuring resets.","Set up Wallet & GPay",onSetup)}};return}
    val schedule=source.schedule
    val balance=s.accounts.find {it.id==source.gpayId}?.balance?:0
    var actual by rememberSaveable {mutableStateOf(Money.input(balance))}
    var enabled by rememberSaveable {mutableStateOf(schedule.enabled)}
    var target by rememberSaveable {mutableStateOf(Money.input(schedule.target))}
    var day by rememberSaveable {mutableStateOf(schedule.day.toString())}
    var month by rememberSaveable {mutableStateOf(schedule.startMonth)}
    var monthPicker by rememberSaveable {mutableStateOf(false)}
    var saved by rememberSaveable {mutableStateOf(false)}
    Page("GPay settings","Your balance, your reset schedule") {
        item {Panel {
            SectionHeading("Manual balance")
            AmountRow("Currently recorded",balance)
            Field("Actual GPay balance (₹)",actual,{actual=it},true)
            val value=runCatching {Money.parse(actual).also {require(it>=0)}}.getOrNull()
            if(value!=null) AmountRow("Balance after saving",value)
            Text("Saving records a balance correction. Your history stays intact.",color=Tokens.muted)
            EditorialPrimaryButton("Save GPay balance",value!=null && value!=balance && !busy) {onBalance(value!!) {}}
        }}
        item {Panel {
            SectionHeading("Monthly GPay reset")
            Row(Modifier.fillMaxWidth()) {Text("Enable monthly reset",Modifier.weight(1f));Switch(enabled,{enabled=it;saved=false},enabled=!busy)}
            Text(if(enabled) "Reset to an amount you choose. Remaining money is replaced, not added." else "Off: your balance carries forward. Spending and repayments still update it.",color=Tokens.muted)
            if(enabled) {
                Field("Reset balance (₹)",target,{target=it;saved=false},true)
                Choice("Reset day",day,(1..31).map {it.toString() to "Day $it"}) {day=it;saved=false}
                OutlinedButton({monthPicker=true},Modifier.fillMaxWidth()) {Text("Starting month · ${YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy"))}")}
                Text("Days 29–31 use the last day of shorter months. Changes start with the next future date.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }
            val value=runCatching {Money.parse(target).also {require(it>=0)}}.getOrNull()
            val next=runCatching {GpaySchedule(enabled,value?:0,day.toInt(),month,firstResetMonth(day.toInt(),month,LocalDate.now())).nextDate(LocalDate.now())}.getOrNull()
            if(enabled && next!=null) Text("First reset · ${next.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}",style=MaterialTheme.typography.titleMedium)
            EditorialPrimaryButton("Save reset settings",(!enabled || value!=null) && !busy) {onSchedule(enabled,value?:schedule.target,day.toInt(),month) {saved=true}}
            if(saved) Text("Reset settings saved",style=MaterialTheme.typography.bodySmall)
        }}
    }
    if(monthPicker) EditorialMonthPicker(YearMonth.parse(month),{monthPicker=false}) {month=it.toString();monthPicker=false;saved=false}
}
