package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.smartexpense.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun MonthlyReviewScreen(s:Snapshot,review:MonthlyReview?,month:String,onMonth:(String)->Unit,onDone:()->Unit) {
    var picker by rememberSaveable {mutableStateOf(false)}
    Page("Monthly review","See where your money went") {
        item {OutlinedButton({picker=true},Modifier.fillMaxWidth()) {Text(YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy")))}}
        if(review==null || review.month!=month) item {Text("Loading your local ledger…",color=Tokens.muted)}
        else if(!review.hasHistory) item {EmptyLedger("No recorded history yet","This month predates your saved records. Add opening balances to start tracking.")}
        else {
            item {Panel {
                SectionHeading("All tracked accounts")
                AmountRow("Money available",review.available)
                AmountRow("Your spending · net of refunds",review.spending)
                AmountRow("Money left at month end",review.closing)
                Text("Money left is your recorded cash, not automatically new savings.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }}
            item {Panel {
                SectionHeading("How cash changed")
                AmountRow("Opening cash",review.opening)
                AmountRow("Income",review.income)
                AmountRow("Top-ups / opening funds",review.funding)
                AmountRow("Balance corrections",review.corrections)
                AmountRow("Scheduled GPay adjustments",review.resetChanges)
                AmountRow("Cash paid for bills",review.cashPaid)
                AmountRow("Refunds received",review.refunds)
                AmountRow("Friend repayments received",review.repaymentsReceived)
                AmountRow("Friend repayments paid",review.repaymentsPaid)
                Text("Shared bills can cost you a different amount than you paid. Transfers between your accounts cancel out.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }}
            item {Panel {
                SectionHeading("Closing balances")
                s.accounts.forEach {account->if(account.id in review.closingByAccount) AmountRow(account.name+(if(account.archived) " · archived" else ""),review.closingByAccount.getValue(account.id))}
                AmountRow(if(review.surplus>=0) "Available less personal spending" else "Spending shortfall",review.surplus)
                Text("This difference excludes repayment timing and negative corrections; it is not a savings account balance. Outstanding friend debts stay separate from cash.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
            }}
        }
        item {EditorialPrimaryButton("Done",onClick=onDone)}
    }
    if(picker) EditorialMonthPicker(YearMonth.parse(month),{picker=false}) {onMonth(it.toString());picker=false}
}
