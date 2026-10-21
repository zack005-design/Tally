package com.smartexpense.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.smartexpense.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable fun EverydayInsights(s:Snapshot,today:LocalDate=LocalDate.now(),onDetails:(()->Unit)?=null) {
    val comparison=runCatching {spendingComparison(s.records,today)}
    StudioCard {
        SectionHeading("Spending check-in")
        comparison.fold(onSuccess={value->
            val previous=YearMonth.from(today).minusMonths(1)
            Text("Days 1–${value.days}: ${today.format(DateTimeFormatter.ofPattern("MMM yyyy"))} vs ${previous.format(DateTimeFormatter.ofPattern("MMM yyyy"))}",color=Tokens.muted)
            if(s.records.none {!it.reversed && it.kind in listOf(Kind.EXPENSE,Kind.REFUND)}) Text("Add expenses to start comparing your spending.",color=Tokens.muted)
            else {
                AmountRow("This period · your shares after refunds",value.current)
                AmountRow("Previous period",value.previous)
                if(value.previous<=0) Text("There is no positive recorded baseline for a percentage comparison.",color=Tokens.muted)
                else {
                    val percent=value.percentChange!!
                    Text(when {percent.signum()>0->"Recorded spending is $percent% higher in this comparison window."
                        percent.signum()<0->"Recorded spending is ${percent.abs()}% lower in this comparison window."
                        else->"Recorded spending is unchanged in this comparison window."})
                }
            }
        },onFailure={Text("The recorded totals are too large to compare safely.",color=Tokens.negative)})
        s.budgets.filter {it.month==YearMonth.from(today).toString()}.forEach {budget->
            val report=runCatching {Reports.period(s.records,today.withDayOfMonth(1),today)}
            val allowance=runCatching {val period=report.getOrThrow();budgetAllowance(budget.amount,if(budget.categoryId==null) period.spending else period.categories[budget.categoryId]?:0,today)}
            allowance.getOrNull()?.let {value->
                val label=if(budget.categoryId==null) "Overall" else s.categories.find {it.id==budget.categoryId}?.name?:"Category"
                Text("$label budget",style=MaterialTheme.typography.titleSmall)
                if(value.remaining<0) Text("Over budget by ${Money.format(Math.negateExact(value.remaining))}")
                else Text("${Money.format(value.perDay)}/day budget allowance · ${value.daysRemaining} days including today")
            }
        }
        Text("Budget allowances use recorded spending, not available cash. Transfers, repayments and adjustments are excluded.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
        if(onDetails!=null) TextButton(onDetails) {Text("View insights & budgets")}
    }
}
