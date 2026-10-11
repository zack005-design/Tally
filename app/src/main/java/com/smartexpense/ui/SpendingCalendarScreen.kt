package com.smartexpense.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
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
        if(onDiary!=null) item {OutlinedButton({onDiary(selected)},Modifier.fillMaxWidth()) {Text("Open this day's diary ›")}}
        item {Column(Modifier.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Your spending",color=Tokens.muted,style=MaterialTheme.typography.labelMedium)
            Text(editorialMoney(calendar.spending),style=MaterialTheme.typography.headlineLarge)
            Text("${month.format(DateTimeFormatter.ofPattern("MMMM"))} · $activeDays active ${if(activeDays==1) "day" else "days"} · your share",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            SpendingRhythm(month,calendar)
        }}
        item {CalendarPaper {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                CalendarArrow("Previous calendar ${if(view=="Week") "week" else "month"}",true) {move(-1)}
                CalendarArrow("Next calendar ${if(view=="Week") "week" else "month"}",false) {move(1)}
            }
            CalendarViewControls(view,{view=it}) {monthText=YearMonth.from(today).toString();selectedText=today.toString()}
            CalendarGrid(month,selected,today,gridCalendar,view=="Week") {selectedText=it.toString();monthText=YearMonth.from(it).toString()}
            Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(4.dp).background(Tokens.muted,CircleShape))
                Text("Recorded activity",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                if(LocalDensity.current.fontScale<=1.3f) {
                    Spacer(Modifier.weight(1f))
                    Text("Less",color=Tokens.muted,style=MaterialTheme.typography.labelSmall)
                    listOf(.25f,.55f,1f).forEach {alpha->Box(Modifier.size(4.dp).background(Tokens.text.copy(alpha=alpha),CircleShape))}
                    Text("More",color=Tokens.muted,style=MaterialTheme.typography.labelSmall)
                }
            }
        }}
        item {Column(Modifier.padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics {heading()})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("${entries.size} ${if(entries.size==1) "entry" else "entries"}",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                Text(editorialMoney(calendar.dailySpending[selected]?:0),style=MaterialTheme.typography.titleMedium)
            }
        }}
        if(shared.isNotEmpty()) item {CalendarPaper {
            Text("Shared spending",style=MaterialTheme.typography.labelMedium,color=Tokens.muted)
            AmountRow("Shared bills · full totals",Finance.total(shared.map {it.amount}))
            AmountRow("Your share of shared bills",Finance.total(shared.map {it.personalAmount}))
        }}
        if(entries.isEmpty()) item {CalendarPaper {
            Icon(painterResource(R.drawable.ic_calendar),null,Modifier.size(28.dp),tint=Tokens.muted)
            Text("A quiet day",style=MaterialTheme.typography.titleMedium)
            Text("Nothing recorded. Select another date to explore your spending.",color=Tokens.muted,style=MaterialTheme.typography.bodyMedium)
        }}
        entries.forEach {r->item(key=r.id) {CalendarPaper {
            CalendarTransaction(r,s) {onDetails(r.id)}
            if(shared.isNotEmpty()) {
                if(r in shared) Text("${r.personIds.size} people · ${editorialMoney(r.amount)} full bill",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }
        }}}
        item {Text("Totals use your share, minus refunds. Transfers and repayments stay outside spending.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun CalendarPaper(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(24.dp),color=Color.White,shadowElevation=1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable private fun CalendarViewControls(view:String,onView:(String)->Unit,onToday:()->Unit) {
    val segmented:@Composable ()->Unit={
        Surface(shape=RoundedCornerShape(12.dp),color=Tokens.secondarySurface) {
            Row(Modifier.padding(3.dp)) {listOf("Month","Week").forEach {label->
                Surface(onClick={onView(label)},shape=RoundedCornerShape(10.dp),color=if(view==label) Color.White else Color.Transparent,shadowElevation=if(view==label) 1.dp else 0.dp,modifier=Modifier.semantics {selected=view==label}) {
                    Box(Modifier.heightIn(min=48.dp).padding(horizontal=14.dp),contentAlignment=Alignment.Center) {Text(label,style=MaterialTheme.typography.labelMedium)}
                }
            }}
        }
    }
    if(LocalDensity.current.fontScale>1.3f) Column(Modifier.fillMaxWidth()) {
        segmented()
        TextButton(onToday,Modifier.align(Alignment.End)) {Text("Today")}
    } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        segmented();TextButton(onToday) {Text("Today")}
    }
}

@Composable private fun CalendarArrow(label:String,previous:Boolean,onClick:()->Unit) {
    IconButton(onClick,Modifier.semantics {contentDescription=label}) {
        Icon(painterResource(R.drawable.ic_chevron_down),null,Modifier.size(18.dp).rotate(if(previous) 90f else -90f),tint=Tokens.text)
    }
}

@Composable private fun CalendarTransaction(record:Record,snapshot:Snapshot,onClick:()->Unit) {
    val amount=if(record.kind==Kind.EXPENSE) record.personalAmount else record.amount
    val amountText=(if(record.kind in listOf(Kind.INCOME,Kind.REFUND) && amount>0) "+" else "")+editorialMoney(amount)
    val large=LocalDensity.current.fontScale>1.3f || amountText.length>14
    val accounts=record.accountIds.mapNotNull {id->snapshot.accounts.find {it.id==id}?.name}.joinToString(" → ")
    val time=Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    val label=when(record.kind) {Kind.EXPENSE->"Your share";Kind.REFUND->"Refund";Kind.TRANSFER->"Transfer";Kind.REPAYMENT->"Repayment";else->record.kind.name.lowercase().replaceFirstChar(Char::uppercase)}
    Surface(onClick=onClick,color=Color.White,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=RoundedCornerShape(12.dp),color=Tokens.surface,modifier=Modifier.size(40.dp)) {
                Box(contentAlignment=Alignment.Center) {Icon(painterResource(R.drawable.ic_activity),null,Modifier.size(20.dp))}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(record.description,style=MaterialTheme.typography.titleSmall)
                Text(listOf(time,accounts).filter(String::isNotBlank).joinToString(" · "),color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                if(large) {Text(amountText,style=MaterialTheme.typography.titleMedium);Text(label,color=Tokens.muted,style=MaterialTheme.typography.labelSmall)}
            }
            if(!large) Column(horizontalAlignment=Alignment.End) {
                Text(amountText,style=MaterialTheme.typography.titleSmall)
                Text(label,color=Tokens.muted,style=MaterialTheme.typography.labelSmall)
            }
            Icon(painterResource(R.drawable.ic_chevron_down),null,Modifier.size(14.dp).rotate(-90f),tint=Tokens.muted)
        }
    }
}

@Composable private fun SpendingRhythm(month:YearMonth,calendar:SpendingCalendar) {
    val values=(1..month.lengthOfMonth()).map {calendar.dailySpending[month.atDay(it)]?:0}
    val refunds=values.any {it<0}
    val max=values.maxOf {kotlin.math.abs(it.toDouble())}.coerceAtLeast(1.0)
    val ink=Tokens.text;val faint=Tokens.border
    Canvas(Modifier.fillMaxWidth().height(if(refunds) 48.dp else 36.dp).padding(top=4.dp).semantics {contentDescription="Daily spending rhythm for ${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}; spending above baseline, refunds below"}) {
        val slot=size.width/values.size
        val baseline=if(refunds) size.height/2 else size.height
        if(refunds) drawLine(faint,Offset(0f,baseline),Offset(size.width,baseline),1.dp.toPx(),StrokeCap.Round)
        values.forEachIndexed {i,value->
            val height=if(value==0L) 2.dp.toPx() else (baseline*kotlin.math.abs(value.toDouble())/max).toFloat().coerceAtLeast(3.dp.toPx())
            drawRoundRect(if(value==0L) faint else ink,Offset(i*slot+slot*.2f,if(value<0) baseline else baseline-height),androidx.compose.ui.geometry.Size(slot*.6f,height),CornerRadius(2.dp.toPx()))
        }
    }
    if(refunds) Text("Refunds below baseline",color=Tokens.muted,style=MaterialTheme.typography.labelSmall)
}

@Composable private fun CalendarGrid(month:YearMonth,selected:LocalDate,today:LocalDate,calendar:SpendingCalendar,weekOnly:Boolean,onSelect:(LocalDate)->Unit) {
    val scale=LocalDensity.current.fontScale
    val offset=month.atDay(1).dayOfWeek.value-1
    val cells=(List<LocalDate?>(offset) {null}+(1..month.lengthOfMonth()).map {month.atDay(it)}).toMutableList()
    while(cells.size%7!=0) cells.add(null)
    val weeks=if(weekOnly) listOf((0..6).map {selected.minusDays((selected.dayOfWeek.value-1).toLong()).plusDays(it.toLong())}) else cells.chunked(7)
    val max=calendar.dailySpending.values.maxOrNull()?.coerceAtLeast(1)?:1
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
                            Text(date.dayOfMonth.toString(),color=if(date==selected) Color.White else if(YearMonth.from(date)!=month) Tokens.muted else Tokens.text,style=MaterialTheme.typography.bodyMedium.copy(fontSize=14.sp,lineHeight=18.sp,fontWeight=if(date==selected || date==today) FontWeight.SemiBold else FontWeight.Normal))
                        }
                    }
                    Box(Modifier.height(8.dp),contentAlignment=Alignment.Center) {if(count>0) Box(Modifier.size(4.dp).background(Tokens.text.copy(alpha=if(amount<=0) .25f else (.3f+.7f*amount.toFloat()/max).coerceIn(.3f,1f)),CircleShape))}
                }
            }}
        }}
    }
}
