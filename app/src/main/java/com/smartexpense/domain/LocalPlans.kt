package com.smartexpense.domain

import java.time.*

data class GroupDefault(val groupId:String,val method:String,val values:Map<String,String>) {
    fun rule(people:List<String>):SplitRule {
        require(method in listOf("Equal","Weighted","Percentage"))
        if(method!="Equal") require(values.keys==people.toSet()) {"Group membership changed. Edit the saved split default."}
        val rule=when(method) {
            "Percentage"->SplitRule.Percentage(values.mapValues {Money.parse(it.value)})
            "Weighted"->SplitRule.Weighted(values.mapValues {it.value.toLongOrNull()?:throw IllegalArgumentException("Enter whole-number weights")})
            else->SplitRule.Equal
        }
        Finance.split(10000,people,rule);return rule
    }
}
data class RecurringTemplate(val id:String,val kind:Kind,val amount:Long,val description:String,val accountId:String,val firstDate:String,val frequency:String) {
    init {require(id.isNotBlank() && kind in listOf(Kind.EXPENSE,Kind.INCOME) && amount>0 && description.isNotBlank() && description.length<=200 && accountId.isNotBlank());require(frequency in listOf("Daily","Weekly","Monthly"));LocalDate.parse(firstDate)}
    fun entryId(date:LocalDate)="recurring:$id:$date"
    fun occurrences(today:LocalDate):List<LocalDate> {
        val first=LocalDate.parse(firstDate)
        if(first>today) return emptyList()
        val count=when(frequency) {"Daily"->java.time.temporal.ChronoUnit.DAYS.between(first,today);"Weekly"->java.time.temporal.ChronoUnit.WEEKS.between(first,today);else->java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(first),YearMonth.from(today))}
        return ((count-399).coerceAtLeast(0)..count).map {when(frequency) {"Daily"->first.plusDays(it);"Weekly"->first.plusWeeks(it);else->first.plusMonths(it)}}.filter {it<=today}
    }
}
data class GoalContribution(val id:String,val date:String,val amount:Long)
data class SavingsGoal(val id:String,val name:String,val target:Long,val contributions:List<GoalContribution> = emptyList()) {
    val allocated:Long get()=Finance.total(contributions.map {it.amount})
    init {require(id.isNotBlank() && name.isNotBlank() && name.length<=100 && target>0 && contributions.all {it.id.isNotBlank() && it.amount>0});contributions.forEach {LocalDate.parse(it.date)};require(contributions.map {it.id}.distinct().size==contributions.size)}
}
data class CategoryRule(val id:String,val merchant:String,val categoryId:String)
