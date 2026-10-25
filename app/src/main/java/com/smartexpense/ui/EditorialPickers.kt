package com.smartexpense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun EditorialDatePicker(title:String,value:LocalDate,onDismiss:()->Unit,onConfirm:(LocalDate)->Unit) {
    var selected by rememberSaveable(value.toString()) {mutableStateOf(value.toString())}
    var shown by rememberSaveable(value.toString()) {mutableStateOf(YearMonth.from(value).toString())}
    var chooseMonth by rememberSaveable {mutableStateOf(false)}
    val date=LocalDate.parse(selected)
    val month=YearMonth.parse(shown)
    EditorialBottomSheet(title,onDismiss) {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),style=MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton({shown=month.minusMonths(1).toString()},enabled=month.year>1900 || month.monthValue>1) {Text("‹",Modifier.semantics {contentDescription="Previous month"},style=MaterialTheme.typography.headlineSmall)}
            TextButton({chooseMonth=true},Modifier.weight(1f)) {Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy"))+" ⌄")}
            IconButton({shown=month.plusMonths(1).toString()},enabled=month.year<2100 || month.monthValue<12) {Text("›",Modifier.semantics {contentDescription="Next month"},style=MaterialTheme.typography.headlineSmall)}
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(336.dp)) {
                Row {listOf("M","T","W","T","F","S","S").forEach {Text(it,Modifier.width(48.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=Tokens.muted,style=MaterialTheme.typography.labelMedium)}}
                val start=month.atDay(1).dayOfWeek.value-1
                val cells=((start+month.lengthOfMonth()+6)/7)*7
                (0 until cells).chunked(7).forEach {week->Row {week.forEach {index->
                    val day=index-start+1
                    if(day !in 1..month.lengthOfMonth()) Spacer(Modifier.size(48.dp)) else {
                        val item=month.atDay(day);val picked=item==date
                        Surface(onClick={selected=item.toString()},modifier=Modifier.size(48.dp).semantics {contentDescription=item.format(DateTimeFormatter.ofPattern("d MMMM yyyy"));this.selected=picked;role=Role.Button},shape=CircleShape,
                            color=if(picked) Tokens.accent else Tokens.background,contentColor=if(picked) MaterialTheme.colorScheme.onPrimary else Tokens.text,
                            border=if(item==LocalDate.now() && !picked) BorderStroke(1.dp,Tokens.muted) else null) {
                            Box(contentAlignment=Alignment.Center) {Text(day.toString(),style=MaterialTheme.typography.bodyMedium)}
                        }
                    }
                }}}
            }
        }
        TextButton({selected=LocalDate.now().toString();shown=YearMonth.now().toString()}) {Text("Today")}
        EditorialPrimaryButton("Use date →",date.year in 1900..2100) {onConfirm(date)}
        OutlinedButton(onDismiss,Modifier.fillMaxWidth()) {Text("Cancel")}
    }
    if(chooseMonth) EditorialMonthPicker(month,{chooseMonth=false}) {shown=it.toString();chooseMonth=false}
}

@Composable fun EditorialMonthPicker(value:YearMonth,onDismiss:()->Unit,onConfirm:(YearMonth)->Unit) {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var year by rememberSaveable {mutableStateOf(value.year.toString())}
    var month by rememberSaveable {mutableIntStateOf(value.monthValue)}
    val valid=year.toIntOrNull()?.takeIf {it in 1900..2100}
    EditorialBottomSheet("Choose month & year",onDismiss) {
        Field("Year",year,{year=it},true)
        Text("1900–2100",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)
        (1..12).chunked(if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) 2 else 3).forEach {months->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {months.forEach {m->
                Surface(onClick={month=m},modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics {selected=month==m},shape=Tokens.field,color=if(month==m) Tokens.accent else Tokens.surface,contentColor=if(month==m) MaterialTheme.colorScheme.onPrimary else Tokens.text) {
                    Box(Modifier.padding(12.dp),contentAlignment=Alignment.Center) {Text(Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT,locale),style=MaterialTheme.typography.bodyMedium)}
                }
            }}
        }
        EditorialPrimaryButton("Use month →",valid!=null) {onConfirm(YearMonth.of(valid!!,month))}
        OutlinedButton(onDismiss,Modifier.fillMaxWidth()) {Text("Cancel")}
    }
}

@Composable fun EditorialTimePicker(value:LocalTime,onDismiss:()->Unit,onConfirm:(LocalTime)->Unit) {
    var hour by rememberSaveable {mutableStateOf(value.hour.toString().padStart(2,'0'))}
    var minute by rememberSaveable {mutableStateOf(value.minute.toString().padStart(2,'0'))}
    val h=hour.toIntOrNull();val m=minute.toIntOrNull()
    val valid=h!=null && h in 0..23 && m!=null && m in 0..59
    EditorialBottomSheet("Choose time",onDismiss) {
        Text("24-hour time",style=MaterialTheme.typography.bodyMedium,color=Tokens.muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {Field("Hour",hour,{hour=it.take(2)},true);Row {IconButton({hour=((h?:0)+23).rem(24).toString().padStart(2,'0')}) {Text("−",Modifier.semantics {contentDescription="Previous hour"})};IconButton({hour=((h?:0)+1).rem(24).toString().padStart(2,'0')}) {Text("+",Modifier.semantics {contentDescription="Next hour"})}}}
            Column(Modifier.weight(1f)) {Field("Minute",minute,{minute=it.take(2)},true);Row {IconButton({minute=((m?:0)+59).rem(60).toString().padStart(2,'0')}) {Text("−",Modifier.semantics {contentDescription="Previous minute"})};IconButton({minute=((m?:0)+1).rem(60).toString().padStart(2,'0')}) {Text("+",Modifier.semantics {contentDescription="Next minute"})}}}
        }
        if(!valid) Text("Enter an hour from 0–23 and a minute from 0–59.",color=Tokens.negative)
        EditorialPrimaryButton("Use time →",valid) {onConfirm(LocalTime.of(h!!,m!!))}
        OutlinedButton(onDismiss,Modifier.fillMaxWidth()) {Text("Cancel")}
    }
}

data class EditorialAction(val label:String,val onClick:()->Unit)
@Composable fun EditorialActionSheet(title:String,actions:List<EditorialAction>,onDismiss:()->Unit) {
    EditorialBottomSheet(title,onDismiss) {
        actions.forEach {action->Surface(onClick={onDismiss();action.onClick()},shape=Tokens.field,color=Tokens.surface,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {Text(action.label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge);Text("›",color=Tokens.muted)}
        }}
        OutlinedButton(onDismiss,Modifier.fillMaxWidth()) {Text("Close")}
    }
}
@Composable fun EditorialConfirmSheet(title:String,message:String,confirmLabel:String,onDismiss:()->Unit,enabled:Boolean=true,onConfirm:()->Unit) {
    EditorialBottomSheet(title,onDismiss) {
        Text(message,style=MaterialTheme.typography.bodyLarge)
        EditorialPrimaryButton(confirmLabel,enabled,onClick=onConfirm)
    }
}

@Composable fun EditorialFilterDate(label:String,value:String,onChange:(String)->Unit) {
    var open by rememberSaveable {mutableStateOf(false)}
    val date=runCatching {LocalDate.parse(value)}.getOrElse {LocalDate.now()}
    Surface(onClick={open=true},shape=Tokens.field,color=Tokens.surface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text(label,color=Tokens.muted,style=MaterialTheme.typography.labelMedium);Text(if(value.isBlank()) "Any date" else date.format(DateTimeFormatter.ofPattern("d MMM yyyy")))};Text("▦")}
    }
    if(value.isNotBlank()) TextButton({onChange("")}) {Text("Clear $label")}
    if(open) EditorialDatePicker(label,date,{open=false}) {onChange(it.toString());open=false}
}
