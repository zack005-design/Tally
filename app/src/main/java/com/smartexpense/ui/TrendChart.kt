package com.smartexpense.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private fun trendAxis(paise:Double):String {
    val rupees=paise/100.0
    val magnitude=kotlin.math.abs(rupees)
    val (divisor,suffix)=when {magnitude>=1_000_000_000->1_000_000_000.0 to "B";magnitude>=1_000_000->1_000_000.0 to "M";magnitude>=1_000->1_000.0 to "K";else->1.0 to ""}
    return String.format(Locale.ROOT,"%.1f",rupees/divisor).removeSuffix(".0")+suffix
}

/** Actual daily net spending for the complete report range, including zero days. */
@Composable fun TrendChart(daily:Map<LocalDate,Long>,month:YearMonth?=null,startDate:LocalDate?=null,endDate:LocalDate?=null) {
    val selectedMonth=month?:daily.keys.minOrNull()?.let(YearMonth::from)?:YearMonth.now()
    val from=startDate?:selectedMonth.atDay(1)
    val through=endDate?:selectedMonth.atEndOfMonth()
    val days=generateSequence(from) {it.plusDays(1)}.takeWhile {!it.isAfter(through)}.toList()
    if(days.isEmpty()) return
    val values=days.map {daily[it]?:0L}
    val maximum=values.maxOf {it.toDouble()}.coerceAtLeast(0.0)
    val minimum=values.minOf {it.toDouble()}.coerceAtMost(0.0)
    val upper=if(maximum==minimum) 100.0 else maximum
    val range=(upper-minimum).coerceAtLeast(1.0)
    var breakdown by rememberSaveable {mutableStateOf(false)}
    val line=Tokens.accent
    val grid=Tokens.border
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.height(170.dp).widthIn(min=38.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Text(trendAxis(upper),style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
                Text(trendAxis((upper+minimum)/2),style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
                Text(trendAxis(minimum),style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
            }
            Canvas(Modifier.weight(1f).height(170.dp).semantics {
                contentDescription="Daily net spending from $from through $through. Highest ${referenceMoney(maximum.toLong())}; lowest ${referenceMoney(minimum.toLong())}. Open Daily breakdown for every date and amount."
            }) {
                fun y(value:Double)=((upper-value)/range*size.height).toFloat()
                val baseline=y(0.0)
                listOf(0f,.5f,1f).forEach {fraction->drawLine(grid,Offset(0f,size.height*fraction),Offset(size.width,size.height*fraction),1.dp.toPx())}
                if(minimum<0 && maximum>0) drawLine(grid,Offset(0f,baseline),Offset(size.width,baseline),1.dp.toPx())
                val points=values.mapIndexed {index,value->Offset(index*size.width/(values.size-1).coerceAtLeast(1),y(value.toDouble()))}
                val path=Path().apply {
                    moveTo(points.first().x,points.first().y)
                    points.zipWithNext().forEach {(a,b)->val center=(a.x+b.x)/2; cubicTo(center,a.y,center,b.y,b.x,b.y)}
                }
                val area=Path().apply {addPath(path);lineTo(size.width,baseline);lineTo(0f,baseline);close()}
                drawPath(area,Brush.verticalGradient(listOf(Color(0xFFB9ACFF).copy(alpha=.55f),Color(0xFFE8E4FF).copy(alpha=.15f))))
                drawPath(path,line,style=Stroke(width=2.dp.toPx(),cap=StrokeCap.Round))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start=46.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            val indices=if(days.size>40) listOf(0,days.lastIndex/4,days.lastIndex/2,days.lastIndex*3/4,days.lastIndex) else if(days.size<=7) days.indices.toList() else listOf(0,7,14,21,days.lastIndex)
            indices.distinct().forEach {index->
                val date=days[index]
                Text(if(days.size>40) date.format(java.time.format.DateTimeFormatter.ofPattern("MMM")) else date.dayOfMonth.toString(),style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
            }
        }
        TextButton({breakdown=!breakdown},Modifier.fillMaxWidth()) {Text(if(breakdown) "Hide daily breakdown" else "Daily breakdown")}
        if(breakdown) GroupedCard {days.forEach {date->ReferenceValueRow(date.toString(),referenceMoney(daily[date]?:0L))}}
    }
}
