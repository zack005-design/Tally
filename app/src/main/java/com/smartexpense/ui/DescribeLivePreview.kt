package com.smartexpense.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.smartexpense.domain.*

/** Local suggestions only; typing never changes the saved ledger or the editable manual draft. */
@Composable fun DescribeLivePreview(text:String,people:List<Person>) {
    if(text.isBlank()) return
    val parsed=remember(text,people) {runCatching {RuleBasedTextParser().parse(text,people.map {PersonRef(it.id,it.name,it.aliases)})}}
    val result=parsed.getOrNull()
    if(result?.amount==null) return
    Panel {
        SectionHeading("Live description calculation")
        Text("Detected amount: ${editorialMoney(result.amount)}",style=MaterialTheme.typography.titleMedium)
        result.kind?.let {Text("${it.name.lowercase().replaceFirstChar(Char::titlecase)} draft",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)}
        if(result.kind==Kind.EXPENSE && "participants" !in result.missing && (result.contributions.size<=1 || result.splitRule==SplitRule.Equal)) {
            val ids=result.contributions.keys.toList().ifEmpty {listOf("self")}
            val shares=runCatching {Finance.split(result.amount,ids,SplitRule.Equal)}.getOrNull()
            shares?.get("self")?.let {Text("Your share: ${editorialMoney(it)}",style=MaterialTheme.typography.bodyMedium)}
            if(shares!=null && ids.size>1) runCatching {Finance.net(shares,result.contributions)}.getOrNull()?.get("self")?.let {net->
                Text("${when {net>0->"You receive";net<0->"You owe";else->"No balance due"}}: ${editorialMoney(if(net<0) Math.negateExact(net) else net)}",style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(result.warnings.isNotEmpty()) Text(result.warnings.joinToString("\n"),color=Tokens.negative,style=MaterialTheme.typography.bodySmall)
        Text("Continue to review accounts and missing details before saving.",color=Tokens.muted,style=MaterialTheme.typography.bodySmall)
    }
}
