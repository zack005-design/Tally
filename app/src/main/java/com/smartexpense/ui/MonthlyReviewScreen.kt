package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.R
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun MonthlyReviewScreen(s:Snapshot,review:MonthlyReview?,month:String,onMonth:(String)->Unit,onDone:()->Unit) {
    var picker by rememberSaveable {mutableStateOf(false)}
    var details by rememberSaveable {mutableStateOf(false)}
    Page("Monthly review",actions={IconButton({details=!details}) {Text("⋯",color=Tokens.accent)}}) {
        item {TextButton({picker=true},contentPadding=PaddingValues(0.dp)) {Text(YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy")),color=Tokens.text,style=MaterialTheme.typography.titleLarge)}}
        if(review==null || review.month!=month) item {Text("Loading your local ledger…",color=Tokens.muted)}
        else if(!review.hasHistory) item {EmptyLedger("No recorded history yet","This month predates your saved records. Add opening balances to start tracking.")}
        else {
            item {GroupedCard {
                ReviewMoneyRow("Opening balance",review.opening,R.drawable.ic_account,ReferenceTone.Gray)
                GroupedDivider();ReviewMoneyRow("Income",review.income,R.drawable.ic_arrow_in,ReferenceTone.Blue)
                GroupedDivider();ReviewMoneyRow("Funding",review.funding,R.drawable.ic_add,ReferenceTone.Blue)
                GroupedDivider();ReviewMoneyRow("Cash expenses",Math.negateExact(review.personalCashPaid),R.drawable.ic_arrow_out,ReferenceTone.Peach)
                GroupedDivider();ReviewMoneyRow("Shared bill payments",Math.negateExact(review.sharedCashPaid),R.drawable.ic_people,ReferenceTone.Lavender)
                GroupedDivider();ReviewMoneyRow("Refunds",review.refunds,R.drawable.ic_transfer,ReferenceTone.Blue)
                GroupedDivider();ReviewMoneyRow("Transfers (net)",review.transfersNet,R.drawable.ic_transfer,ReferenceTone.Gray)
                GroupedDivider();ReviewMoneyRow("Closing balance",review.closing,R.drawable.ic_account,ReferenceTone.Gray)
            }}
            item {GroupedCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Your spending",style=MaterialTheme.typography.titleSmall)
                Text(referenceMoney(review.spending),style=MaterialTheme.typography.headlineLarge)
                if(s.reportMonth==month) s.selectedReport.categories.entries.sortedByDescending {it.value}.forEach {(id,spending)->
                    val name=s.categories.find {it.id==id}?.name?:"Uncategorised"
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {ReferenceIcon(referenceSymbol(name,R.drawable.ic_basket),ReferenceTone.Lavender,28.dp);Text(name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(referenceMoney(spending),style=MaterialTheme.typography.titleSmall)}
                }
            }}}

            if(details) item {Panel {
                SectionHeading("How cash changed")
                AmountRow("Money available",review.available)
                AmountRow("Balance corrections",review.corrections)
                AmountRow("Scheduled GPay adjustments",review.resetChanges)
                AmountRow("Friend repayments received",review.repaymentsReceived)
                AmountRow("Friend repayments paid",review.repaymentsPaid)
                Text("Shared bills can cost you a different amount than you paid. Transfers between your accounts cancel out.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }}
            if(details) item {Panel {
                SectionHeading("Closing balances")
                s.accounts.forEach {account->if(account.id in review.closingByAccount) AmountRow(account.name+(if(account.archived) " · archived" else ""),review.closingByAccount.getValue(account.id))}
                AmountRow(if(review.surplus>=0) "Available less personal spending" else "Spending shortfall",review.surplus)
                AmountRow(if(review.netFunding>=0) "New funding less personal spending" else "Net new funding shortfall",review.netFunding)
                Text("This difference excludes repayment timing and negative corrections; it is not a savings account balance. Outstanding friend debts stay separate from cash.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }}
        }
        item {EditorialPrimaryButton(if(review?.hasHistory==true) "Mark as reviewed" else "Done",onClick=onDone)}
        item {Text("This helps you keep track of completed months.",style=MaterialTheme.typography.bodySmall,color=Tokens.muted)}
    }
    if(picker) EditorialMonthPicker(YearMonth.parse(month),{picker=false}) {onMonth(it.toString());picker=false}
}

@Composable private fun ReviewMoneyRow(label:String,amount:Long,icon:Int,tone:ReferenceTone) {
    val stacked=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f || referenceMoney(amount).length>14
    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        ReferenceIcon(icon,tone,30.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(label,style=MaterialTheme.typography.bodyMedium)
            if(stacked) Text(referenceMoney(amount),style=MaterialTheme.typography.titleSmall)
        }
        if(!stacked) Text(referenceMoney(amount),style=MaterialTheme.typography.titleSmall)
    }
}
