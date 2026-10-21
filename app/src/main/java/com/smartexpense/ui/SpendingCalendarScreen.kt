package com.smartexpense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun SpendingCalendarScreen(s:Snapshot,onDetails:(String)->Unit,today:LocalDate=LocalDate.now(),onDiary:((LocalDate)->Unit)?=null) {
    var monthText by rememberSaveable {mutableStateOf(YearMonth.from(today).toString())}
    var selectedText by rememberSaveable {mutableStateOf(today.toString())}
    var view by rememberSaveable {mutableStateOf("Month")}
    val month=YearMonth.parse(monthText);val selected=LocalDate.parse(selectedText)
    val calendar=remember(s.records,month) {spendingCalendar(s.records,month)}
    fun move(delta:Long) {
        val date=if(view=="Week") selected.plusWeeks(delta) else month.plusMonths(delta).let {it.atDay(minOf(selected.dayOfMonth,it.lengthOfMonth()))}
        monthText=YearMonth.from(date).toString();selectedText=date.toString()
    }
    val gridCalendar=remember(s.records,month,view) {
        if(view!="Week") calendar else {
            val adjacent=listOf(month.minusMonths(1),month,month.plusMonths(1)).map {spendingCalendar(s.records,it)}
            calendar.copy(days=adjacent.flatMap {it.days.entries}.associate {it.key to it.value},dailySpending=adjacent.flatMap {it.dailySpending.entries}.associate {it.key to it.value})
        }
    }
    val entries=calendar.days[selected].orEmpty().sortedByDescending {it.date}
    val activeDays=calendar.days.size
    val shared=entries.filter {it.kind==Kind.EXPENSE && it.personIds.any {id->id!="self"}}
    Page("Calendar") {
        item {EditorialSegmentedControl(view,listOf("Month" to "Month","Week" to "Week")) {view=it}}
        item {Column {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                CalendarArrow("Previous calendar ${if(view=="Week") "week" else "month"}",true) {move(-1)}
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),style=MaterialTheme.typography.titleMedium)}
                CalendarArrow("Next calendar ${if(view=="Week") "week" else "month"}",false) {move(1)}
            }
            CalendarGrid(month,selected,today,gridCalendar,view=="Week") {selectedText=it.toString();monthText=YearMonth.from(it).toString()}
        }}
        item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleMedium)
                if(onDiary!=null) TextButton({onDiary(selected)}) {Text("Diary")}
            }
            Text("Total spent",color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)
            Text(referenceMoney(calendar.dailySpending[selected]?:0),style=MaterialTheme.typography.headlineMedium)
            entries.forEachIndexed {index,record->
                if(index>0) HorizontalDivider(color=Tokens.border.copy(alpha=.5f))
                CalendarTransaction(record,s) {onDetails(record.id)}
            }
            if(entries.isEmpty()) Text("Nothing recorded for this day.",color=Tokens.muted)
        }}}
        if(shared.isNotEmpty()) item {CalendarPaper {
            Text("Shared spending",style=MaterialTheme.typography.labelMedium,color=Tokens.muted)
            AmountRow("Shared bills · full totals",Finance.total(shared.map {it.amount}))
            AmountRow("Your share of shared bills",Finance.total(shared.map {it.personalAmount}))
        }}
        item {TextButton({monthText=YearMonth.from(today).toString();selectedText=today.toString()}) {Text("Today")}}
        item {Text("${month.format(DateTimeFormatter.ofPattern("MMMM"))}: ${referenceMoney(calendar.spending)} · $activeDays active ${if(activeDays==1) "day" else "days"}",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
        item {Text("Totals use your share, minus refunds. Transfers and repayments stay outside spending.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun CalendarPaper(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(24.dp),color=Tokens.paper,shadowElevation=1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable private fun CalendarArrow(label:String,previous:Boolean,onClick:()->Unit) {
    IconButton(onClick,Modifier.semantics {contentDescription=label}) {
        Icon(painterResource(R.drawable.ic_chevron_down),null,Modifier.size(18.dp).rotate(if(previous) 90f else -90f),tint=Tokens.accent)
    }
}

@Composable private fun CalendarTransaction(record:Record,snapshot:Snapshot,onClick:()->Unit) {
    HistoryTransactionRow(record,snapshot,onClick)
}
@Composable private fun CalendarGrid(month:YearMonth,selected:LocalDate,today:LocalDate,calendar:SpendingCalendar,weekOnly:Boolean,onSelect:(LocalDate)->Unit) {
    val scale=LocalDensity.current.fontScale
    val offset=month.atDay(1).dayOfWeek.value-1
    val cells=(List<LocalDate?>(offset) {null}+(1..month.lengthOfMonth()).map {month.atDay(it)}).toMutableList()
    while(cells.size%7!=0) cells.add(null)
    val weeks=if(weekOnly) listOf((0..6).map {selected.minusDays((selected.dayOfWeek.value-1).toLong()).plusDays(it.toLong())}) else cells.chunked(7)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEach {label->
            Box(Modifier.weight(1f).height(if(scale>1.3f) 40.dp else 32.dp),contentAlignment=Alignment.Center) {Text(if(scale>1.3f) label.take(1) else label,color=Tokens.muted,style=MaterialTheme.typography.labelSmall)}
        }}
        weeks.forEach {week->Row(Modifier.fillMaxWidth()) {
            week.forEach {date->if(date==null) Spacer(Modifier.weight(1f).height(if(scale>1.3f) 64.dp else 54.dp)) else {
                val count=calendar.days[date]?.size?:0;val amount=calendar.dailySpending[date]?:0L
                Column(Modifier.weight(1f).height(if(scale>1.3f) 64.dp else 54.dp).clickable(role=Role.Button) {onSelect(date)}.semantics(mergeDescendants=true) {
                    contentDescription="${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}, spending ${Money.format(amount)}, $count entries"
                    this.selected=date==selected
                },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Surface(shape=CircleShape,color=if(date==selected) Tokens.accent else Color.Transparent,border=if(date==today && date!=selected) BorderStroke(1.dp,Tokens.muted) else null) {
                        Box(Modifier.size(if(scale>1.3f) 40.dp else 32.dp),contentAlignment=Alignment.Center) {
                            Text(date.dayOfMonth.toString(),color=if(date==selected) MaterialTheme.colorScheme.onPrimary else if(YearMonth.from(date)!=month) Tokens.muted else Tokens.text,style=MaterialTheme.typography.bodyMedium.copy(fontSize=14.sp,lineHeight=18.sp,fontWeight=if(date==selected || date==today) FontWeight.SemiBold else FontWeight.Normal))
                        }
                    }
                    Box(Modifier.height(8.dp),contentAlignment=Alignment.Center) {if(count>0) Box(Modifier.size(4.dp).background(Tokens.accent,CircleShape))}
                }
            }}
        }}
    }
}
