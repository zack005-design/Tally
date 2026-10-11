package com.smartexpense.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun ActivityScreen(s:Snapshot,onDetails:(String)->Unit,onCalendar:()->Unit={}) {
    var query by rememberSaveable {mutableStateOf("")}
    var searching by rememberSaveable {mutableStateOf(false)}
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
        if(previousFilters.size==9) {kind=previousFilters[0];category=previousFilters[1];account=previousFilters[2];person=previousFilters[3];group=previousFilters[4];start=previousFilters[5];end=previousFilters[6];minimum=previousFilters[7];maximum=previousFilters[8]}
        filters=false
    }
    val filtered=s.records.filter {r->listOf(r.description,r.notes,s.categories.find {it.id==r.categoryId}?.name.orEmpty(),r.accountIds.mapNotNull {id->s.accounts.find {it.id==id}?.name}.joinToString(" "),r.personIds.mapNotNull {id->s.people.find {it.id==id}?.name}.joinToString(" ")).any {it.contains(query,true)}}.filter {r->
        (kind=="all" || r.kind.name==kind) && (category=="all" || r.categoryId==category) && (account=="all" || account in r.accountIds) && (person=="all" || person in r.personIds) && (group=="all" || r.groupId==group) &&
            (start.isBlank() || runCatching {r.date>=LocalDate.parse(start).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrDefault(false)) &&
            (end.isBlank() || runCatching {r.date<LocalDate.parse(end).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrDefault(false)) &&
            (minimum.isBlank() || runCatching {r.amount>=Money.parse(minimum)}.getOrDefault(false)) && (maximum.isBlank() || runCatching {r.amount<=Money.parse(maximum)}.getOrDefault(false))
    }
    Page("Activity",actions={TextButton({searching=!searching}) {Text("Search",style=MaterialTheme.typography.labelMedium)};IconButton({previousFilters=listOf(kind,category,account,person,group,start,end,minimum,maximum);filters=true}) {Icon(androidx.compose.ui.res.painterResource(com.smartexpense.R.drawable.ic_more),"Activity filters")}}) {
        item {OutlinedButton(onCalendar,Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Spending calendar ›")}}
        if(searching) item {Field("Search description, notes or category",query,{query=it})}
        item {ChipChoices(kind,listOf("all" to "All",Kind.EXPENSE.name to "Expenses",Kind.INCOME.name to "Income",Kind.TRANSFER.name to "Transfers",Kind.REFUND.name to "Refunds")) {kind=it}}
        
        if(filtered.isEmpty()) item {EmptyLedger("No matching activity",if(s.records.isEmpty()) "Add your first transaction to start your journal." else "Try another search or clear your filters.")}
        filtered.sortedByDescending {it.date}.groupBy {Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)}.forEach {(date,records)->
            item {AmountRow(date.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(),Reports.period(records,date,date.plusMonths(1).minusDays(1)).spending)}
            records.forEach {record->item(key=record.id) {TransactionRow(record,s,showPersonalAmount=true) {onDetails(record.id)}}}
        }
    }
    if(filters) EditorialBottomSheet("Activity filters",::dismissFilters) {
        Choice("Type",kind,listOf("all" to "All")+Kind.entries.map {it.name to it.name.lowercase().replaceFirstChar(Char::titlecase)}) {kind=it}
        Choice("Category",category,listOf("all" to "All")+s.categories.map {it.id to it.name}) {category=it}
        Choice("Account",account,listOf("all" to "All")+s.accounts.map {it.id to it.name}) {account=it}
        Choice("Person",person,listOf("all" to "All")+s.people.map {it.id to personLabel(it,s.people)}) {person=it}
        Choice("Group",group,listOf("all" to "All")+s.groups.map {it.id to it.name}) {group=it}
        EditorialFilterDate("From date",start,{start=it})
        EditorialFilterDate("Through date",end,{end=it})
        Field("Minimum (₹)",minimum,{minimum=it},true)
        Field("Maximum (₹)",maximum,{maximum=it},true)
        val valid=runCatching {
            val from=start.takeIf(String::isNotBlank)?.let(LocalDate::parse);val through=end.takeIf(String::isNotBlank)?.let(LocalDate::parse)
            require(from==null || through==null || !through.isBefore(from)) {"The end date must follow the start date"}
            val min=minimum.takeIf(String::isNotBlank)?.let(Money::parse);val max=maximum.takeIf(String::isNotBlank)?.let(Money::parse)
            require((min==null || min>=0) && (max==null || max>=0) && (min==null || max==null || min<=max)) {"Check the amount range"}
        }
        valid.exceptionOrNull()?.let {Text(it.message.orEmpty(),color=Tokens.negative)}
        EditorialPrimaryButton("Apply filters",valid.isSuccess) {filters=false}
        TextButton({kind="all";category="all";account="all";person="all";group="all";start="";end="";minimum="";maximum=""}) {Text("Clear filters")}
        OutlinedButton(::dismissFilters,Modifier.fillMaxWidth()) {Text("Cancel")}
    }
}
