package com.smartexpense.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.Money
import java.time.LocalDate

@Composable fun TrendChart(daily:Map<LocalDate,Long>) {
    val entries=daily.entries.sortedBy {it.key}.filter {it.value!=0L};val peak=entries.maxOfOrNull {kotlin.math.abs(it.value.toDouble())}?:1.0
    var selected by rememberSaveable(daily.keys.joinToString()) {mutableStateOf(entries.lastOrNull()?.key?.toString().orEmpty())}
    val scroll=rememberLazyListState()
    LaunchedEffect(daily) {if(entries.isNotEmpty()) scroll.scrollToItem((entries.size-4).coerceAtLeast(0))}
    LazyRow(state=scroll,horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
        items(entries,key={it.key.toString()}) {entry->
            Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(64.dp).heightIn(min=140.dp).clickable {selected=entry.key.toString()}.semantics {contentDescription="${entry.key}: net spending ${Money.format(entry.value)}";role=Role.Button}) {
                Box(Modifier.height(96.dp).fillMaxWidth(),contentAlignment=Alignment.BottomCenter) {
                    Box(Modifier.width(24.dp).height((kotlin.math.abs(entry.value.toDouble())/peak*90).toFloat().coerceAtLeast(3f).dp).background(if(entry.value<0) Tokens.negative else if(entry.key.toString()==selected) Tokens.accent else Tokens.muted,MaterialTheme.shapes.small))
                }
                Text(entry.key.dayOfMonth.toString(),style=MaterialTheme.typography.labelMedium);Text(if(entry.value>=0) "spent" else "refund",style=MaterialTheme.typography.labelSmall,color=Tokens.muted)
            }
        }
    }
    entries.find {it.key.toString()==selected}?.let {AmountRow(it.key.toString(),it.value)}
}
