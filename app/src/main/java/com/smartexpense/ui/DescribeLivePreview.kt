package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.domain.*

/** Read-only suggestions. Account suggestions resolve against actual active accounts; saving still requires review. */
@Composable fun DescribeLivePreview(text:String,people:List<Person>,accountName:String?=null,accounts:List<Account> = emptyList(),walletSources:WalletSources?=null) {
    if(text.isBlank()) return
    val parsed=remember(text,people) {runCatching {RuleBasedTextParser().parse(text,people.map {PersonRef(it.id,it.name,it.aliases)})}}
    val result=parsed.getOrNull()
    val resolved=result?.fromAccount?.let {resolveParsedAccount(it,accounts,walletSources)}
    val selectedName=accounts.find {it.id==resolved}?.name?:accountName
    if(result?.amount==null) return
    Surface(color=Tokens.secondarySurface,shape=Tokens.card) {Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Parsed draft",style=MaterialTheme.typography.titleMedium)
        GroupedCard {
            ReferenceValueRow("Description",result.description)
            GroupedDivider();ReferenceValueRow("Amount",referenceMoney(result.amount))
            GroupedDivider();ReferenceValueRow("Account",selectedName?:"Choose when reviewing")
            GroupedDivider();ReferenceValueRow("Category",result.category?:"Choose when reviewing")
        }
        if(result.kind==Kind.EXPENSE && "participants" !in result.missing && (result.contributions.size<=1 || result.splitRule==SplitRule.Equal)) {
            val ids=result.contributions.keys.toList().ifEmpty {listOf("self")}
            val shares=runCatching {Finance.split(result.amount,ids,SplitRule.Equal)}.getOrNull()
            if(ids.size>1) shares?.get("self")?.let {Text("Your share: ${editorialMoney(it)}",style=MaterialTheme.typography.bodyMedium)}
            if(shares!=null && ids.size>1) runCatching {Finance.net(shares,result.contributions)}.getOrNull()?.get("self")?.let {net->
                Text("${when {net>0->"You receive";net<0->"You owe";else->"No balance due"}}: ${editorialMoney(if(net<0) Math.negateExact(net) else net)}",style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(result.warnings.isNotEmpty()) Text(result.warnings.joinToString("\n"),color=Tokens.negative,style=MaterialTheme.typography.bodySmall)
    }}
}
