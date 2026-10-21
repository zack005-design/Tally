package com.smartexpense.domain

import java.time.Instant
import java.time.ZoneId

fun billSummary(detail:RecordDetail,people:List<Person>,debts:List<Debt>,zone:ZoneId=ZoneId.systemDefault()):String {
    val record=detail.record
    require(record.kind==Kind.EXPENSE && !record.reversed && detail.shares.size>1) {"Choose a saved, active shared bill"}
    fun name(id:String)=if(id=="self") "You" else people.find {it.id==id}?.name?:"Participant"
    return buildString {
        appendLine("Tally · ${record.description}")
        appendLine(Instant.ofEpochMilli(record.date).atZone(zone).toLocalDate().toString())
        appendLine("Bill total: ${Money.format(record.amount)}")
        appendLine()
        detail.shares.forEach {(id,share)->appendLine("${name(id)} paid ${Money.format(detail.contributions[id]?:0)} · share ${Money.format(share)}")}
        appendLine()
        appendLine("Remaining on this bill:")
        val remaining=debts.filter {it.transactionId==record.id && it.outstanding>0}
        if(remaining.isEmpty()) appendLine("No outstanding amounts on this bill")
        else remaining.forEach {appendLine("${name(it.from)} owes ${name(it.to)}: ${Money.format(it.outstanding)}")}
        appendLine("Based on repayments recorded in Tally. Please check together before paying.")
    }.trim()
}
