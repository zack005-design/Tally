package com.smartexpense.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun ActivityScreen(s:Snapshot,onDetails:(String)->Unit,onCalendar:()->Unit={},onSettings:()->Unit={}) {
    var query by rememberSaveable {mutableStateOf("")}
    var advanced by rememberSaveable {mutableStateOf(false)}
    var monthText by rememberSaveable {mutableStateOf("")}
    var monthPicker by rememberSaveable {mutableStateOf(false)}
    var filters by rememberSaveable {mutableStateOf(false)}
    var kind by rememberSaveable {mutableStateOf("all")}
    var category by rememberSaveable {mutableStateOf("all")}
    var account by rememberSaveable {mutableStateOf("all")}
    var person by rememberSaveable {mutableStateOf("all")}
    var group by rememberSaveable {mutableStateOf("all")}
    var start by rememberSaveable {mutableStateOf("")}
    var end by rememberSaveable {mutableStateOf("")}
    var minimum by rememberSaveable {mutableStateOf("")}
    var maximum by rememberSaveable {mutableStateOf("")}
    var previousFilters by rememberSaveable {mutableStateOf(listOf<String>())}
    fun dismissFilters() {
        if(previousFilters.size>=9) {kind=previousFilters[0];category=previousFilters[1];account=previousFilters[2];person=previousFilters[3];group=previousFilters[4];start=previousFilters[5];end=previousFilters[6];minimum=previousFilters[7];maximum=previousFilters[8];if(previousFilters.size>9) monthText=previousFilters[9]}
        filters=false
    }
    fun resetFilters() {
        query="";kind="all";category="all";account="all";person="all";group="all"
        start="";end="";minimum="";maximum="";monthText=""
    }
    val zone=ZoneId.systemDefault()
    val categoryNames=remember(s.categories) {s.categories.associate {it.id to it.name}}
    val accountNames=remember(s.accounts) {s.accounts.associate {it.id to it.name}}
    val personNames=remember(s.people) {s.people.associate {it.id to it.name}}
    val filtered=remember(s.records,categoryNames,accountNames,personNames,query,monthText,kind,category,account,person,group,start,end,minimum,maximum,zone) {
        val selectedMonth=monthText.takeIf {it.isNotEmpty()}?.let(YearMonth::parse)
        val from=runCatching {start.takeIf {it.isNotBlank()}?.let {LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli()}}.getOrNull()
        val through=runCatching {end.takeIf {it.isNotBlank()}?.let {LocalDate.parse(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()}}.getOrNull()
        val min=runCatching {minimum.takeIf {it.isNotBlank()}?.let(Money::parse)}.getOrNull()
        val max=runCatching {maximum.takeIf {it.isNotBlank()}?.let(Money::parse)}.getOrNull()
        if((start.isNotBlank() && from==null) || (end.isNotBlank() && through==null) ||
            (minimum.isNotBlank() && min==null) || (maximum.isNotBlank() && max==null)) return@remember emptyList<Record>()
        val search=query.trim()
        s.records.filter {r->
            (selectedMonth==null || YearMonth.from(Instant.ofEpochMilli(r.date).atZone(zone))==selectedMonth) &&
                (kind=="all" || r.kind.name==kind) && (category=="all" || r.categoryId==category) &&
                (account=="all" || account in r.accountIds) && (person=="all" || person in r.personIds) &&
                (group=="all" || r.groupId==group) && (from==null || r.date>=from) &&
                (through==null || r.date<through) && (min==null || r.amount>=min) && (max==null || r.amount<=max) &&
                (search.isEmpty() || r.description.contains(search,true) || r.notes.contains(search,true) ||
                    categoryNames[r.categoryId].orEmpty().contains(search,true) ||
                    r.accountIds.mapNotNull(accountNames::get).joinToString(" ").contains(search,true) ||
                    r.personIds.mapNotNull(personNames::get).joinToString(" ").contains(search,true))
        }.sortedByDescending {it.date}
    }
    val days=remember(filtered,zone) {filtered.groupBy {Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()}}
    val dailySpending=remember(days) {days.mapValues {(date,records)->Reports.period(records,date,date).spending}}
    Page("Transactions",largeTitle=true,gap=0.dp,actions={TextButton(onCalendar) {Text("Calendar")};StudioSettingsButton(onSettings)}) {
        item(key="search") {Box(Modifier.padding(vertical=16.dp)) {Field("Search transactions",query,{query=it})}}
        item(key="filters") {Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            OutlinedButton({monthPicker=true}) {Text((if(monthText.isEmpty()) "All dates" else YearMonth.parse(monthText).format(DateTimeFormatter.ofPattern("MMMM yyyy")))+" ⌄")}
            val count=listOf(kind,category,account,person,group).count {it!="all"}+listOf(start,end,minimum,maximum).count {it.isNotBlank()}
            OutlinedButton({previousFilters=listOf(kind,category,account,person,group,start,end,minimum,maximum,monthText);filters=true}) {Text("Filter"+if(count>0) " · $count" else "")}
        }}
        if(filtered.isEmpty()) item(key="empty") {
            EmptyLedger("No matching activity",if(s.records.isEmpty()) "Add your first transaction to start your journal." else "Try another search or clear your filters.",
                action=if(s.records.isEmpty()) null else "Reset search & filters",onAction=::resetFilters)
        }
        days.forEach {(date,records)->
            item(key="day-$date",contentType="history-date") {Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(when(date) {LocalDate.now()->"Today";LocalDate.now().minusDays(1)->"Yesterday";else->date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))},style=MaterialTheme.typography.titleSmall)
                val spending=dailySpending.getValue(date)
                Text(when {spending>0->"−"+referenceMoney(spending);spending<0->"+"+referenceMoney(spending).removePrefix("−");else->referenceMoney(0)},style=MaterialTheme.typography.titleSmall)
            }}
            itemsIndexed(records,key={_,record->"transaction-${record.id}"},contentType={_,_->"history-record"}) {index,record->
                val square=CornerSize(0.dp)
                val shape=Tokens.card.copy(
                    topStart=if(index==0) Tokens.card.topStart else square,
                    topEnd=if(index==0) Tokens.card.topEnd else square,
                    bottomStart=if(index==records.lastIndex) Tokens.card.bottomStart else square,
                    bottomEnd=if(index==records.lastIndex) Tokens.card.bottomEnd else square
                )
                Surface(Modifier.fillMaxWidth().padding(bottom=if(index==records.lastIndex) 16.dp else 0.dp),shape=shape,color=Tokens.paper) {
                    Column {
                        HistoryTransactionRow(record,s) {onDetails(record.id)}
                        if(index<records.lastIndex) GroupedDivider()
                    }
                }
            }
        }
    }
    if(monthPicker) EditorialMonthPicker(monthText.takeIf(String::isNotEmpty)?.let(YearMonth::parse)?:YearMonth.now(),{monthPicker=false}) {monthText=it.toString();monthPicker=false}
    if(filters) EditorialBottomSheet("Filter transactions",::dismissFilters) {
        Choice("Account",account,listOf("all" to "All")+s.accounts.map {it.id to it.name}) {account=it}
        Choice("Category",category,listOf("all" to "All")+s.categories.map {it.id to it.name}) {category=it}
        Choice("Type",kind,listOf("all" to "All")+Kind.entries.map {it.name to it.name.lowercase().replaceFirstChar(Char::titlecase)}) {kind=it}
        EditorialFilterDate("From date",start,{start=it})
        EditorialFilterDate("Through date",end,{end=it})
        TextButton({advanced=!advanced}) {Text(if(advanced) "Hide advanced filters" else "Advanced filters")}
        if(advanced) {
            Choice("Person",person,listOf("all" to "All")+s.people.map {it.id to personLabel(it,s.people)}) {person=it}
            Choice("Group",group,listOf("all" to "All")+s.groups.map {it.id to it.name}) {group=it}
            Field("Minimum (₹)",minimum,{minimum=it},true)
            Field("Maximum (₹)",maximum,{maximum=it},true)
        }
        val valid=runCatching {
            val from=start.takeIf(String::isNotBlank)?.let(LocalDate::parse);val through=end.takeIf(String::isNotBlank)?.let(LocalDate::parse)
            require(from==null || through==null || !through.isBefore(from)) {"The end date must follow the start date"}
            val min=minimum.takeIf(String::isNotBlank)?.let(Money::parse);val max=maximum.takeIf(String::isNotBlank)?.let(Money::parse)
            require((min==null || min>=0) && (max==null || max>=0) && (min==null || max==null || min<=max)) {"Check the amount range"}
        }
        valid.exceptionOrNull()?.let {Text(it.message.orEmpty(),color=Tokens.negative)}
        EditorialPrimaryButton("Apply filters",valid.isSuccess) {filters=false}
        TextButton({kind="all";category="all";account="all";person="all";group="all";start="";end="";minimum="";maximum="";monthText=""}) {Text("Clear filters")}
    }
}

@Composable internal fun HistoryTransactionRow(record:Record,snapshot:Snapshot,onClick:()->Unit) {
    val category=snapshot.categories.find {it.id==record.categoryId}
    val icon=when {
        record.kind==Kind.INCOME || record.kind==Kind.REFUND->R.drawable.ic_arrow_in
        record.kind==Kind.TRANSFER->R.drawable.ic_transfer
        record.kind==Kind.REPAYMENT->R.drawable.ic_people
        category?.icon=="food" || category?.name?.contains("food",true)==true->R.drawable.ic_food
        category?.icon=="shopping" || category?.name?.contains("grocer",true)==true->R.drawable.ic_basket
        category?.icon=="transport" || category?.name?.contains("travel",true)==true->R.drawable.ic_transit
        else->R.drawable.ic_activity
    }
    val amount=if(record.kind in listOf(Kind.EXPENSE,Kind.REFUND)) record.personalAmount else record.amount
    val amountText=(when(record.kind) {Kind.EXPENSE->"−";Kind.INCOME,Kind.REFUND->"+";else->""})+referenceMoney(amount)
    val large=LocalDensity.current.fontScale>1.3f || amountText.length>14
    val time=Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
    Surface(onClick=onClick,color=Tokens.paper,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            ReferenceIcon(icon,if(icon==R.drawable.ic_food || icon==R.drawable.ic_basket) ReferenceTone.Peach else ReferenceTone.Lavender)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(record.description,style=MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(time,category?.name).joinToString(" · "),color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                if(record.kind==Kind.EXPENSE && record.personalAmount!=record.amount) Text("Your share · ${referenceMoney(record.amount)} full bill",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
                if(record.reversed) Text("Reversed",color=Tokens.negative,style=MaterialTheme.typography.bodySmall)
                if(large) Text(amountText,style=MaterialTheme.typography.titleSmall)
            }
            if(!large) Text(amountText,style=MaterialTheme.typography.titleSmall)
            Icon(painterResource(R.drawable.ic_chevron_down),null,Modifier.size(14.dp).rotate(-90f),tint=Tokens.muted)
        }
    }
}
