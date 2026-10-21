package com.smartexpense.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

object ManualExchange {
    fun convert(amount:String,rate:String):Long {
        require(Regex("[0-9]{1,30}(?:\\.[0-9]{1,2})?").matches(amount.trim())) {"Enter a foreign amount with up to two decimals"}
        require(Regex("[0-9]{1,15}(?:\\.[0-9]{1,8})?").matches(rate.trim())) {"Enter INR per foreign unit, with up to eight decimals"}
        val foreign=BigDecimal(amount.trim());val exchange=BigDecimal(rate.trim())
        require(foreign.signum()>=0 && exchange.signum()>0)
        return foreign.multiply(exchange).movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact()
    }
}
object ReportCsv {
    fun cell(value:String):String {
        val numeric=Regex("-?[0-9]+(?:\\.[0-9]+)?").matches(value)
        val safe=if(!numeric && value.trimStart().firstOrNull() in listOf('=','+','-','@')) "'$value" else value
        return "\"${safe.replace("\"","\"\"")}\""
    }
    fun rows(snapshot:Snapshot,review:MonthlyReview,reportType:String="Combined"):List<List<String>> {
        require(reportType in listOf("Combined","Statement","Transactions")) {"Choose a report type"}
        if(reportType!="Combined") {
            val combined=rows(snapshot,review)
            val transactions=combined.indexOfFirst {it.firstOrNull()=="Date"}
            if(reportType=="Transactions") return listOf(combined.first())+combined.drop(transactions)
            return buildList {
                add(listOf("Tally statement",review.month,"INR"))
                add(listOf("Opening balance",Money.input(review.opening)))
                add(listOf("Income",Money.input(review.income)))
                add(listOf("Funding",Money.input(review.funding)))
                add(listOf("Cash expenses",Money.input(Math.negateExact(review.personalCashPaid))))
                add(listOf("Shared bill payments",Money.input(Math.negateExact(review.sharedCashPaid))))
                add(listOf("Transfers (net)",Money.input(review.transfersNet)))
                add(listOf("Refunds",Money.input(review.refunds)))
                add(listOf("Repayments received",Money.input(review.repaymentsReceived)))
                add(listOf("Repayments paid",Money.input(Math.negateExact(review.repaymentsPaid))))
                add(listOf("Balance corrections",Money.input(review.corrections)))
                add(listOf("Scheduled adjustments",Money.input(review.resetChanges)))
                add(listOf("Closing balance",Money.input(review.closing)))
                add(listOf("Your spending",Money.input(review.spending)))
                add(emptyList());add(listOf("Category","Personal INR"))
                snapshot.records.filter {!it.reversed && it.kind in listOf(Kind.EXPENSE,Kind.REFUND) && YearMonth.from(Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()))==YearMonth.parse(review.month)}
                    .groupBy {it.categoryId}.forEach {(id,entries)->
                        add(listOf(snapshot.categories.find {it.id==id}?.name?:"Uncategorised",Money.input(Finance.total(entries.map {if(it.kind==Kind.REFUND) Math.negateExact(it.personalAmount) else it.personalAmount}))))
                    }
            }
        }
        val month=YearMonth.parse(review.month)
        val accounts=snapshot.accounts.associate {it.id to it.name};val categories=snapshot.categories.associate {it.id to it.name}
        return buildList {
            add(listOf("Tally monthly report",month.toString(),"INR"))
            add(listOf("Money available",Money.input(review.available)))
            add(listOf("Personal spending",Money.input(review.spending)))
            add(listOf("Closing cash",Money.input(review.closing)))
            add(listOf("Available less personal spending",Money.input(review.surplus),"Includes opening cash; not proof of new savings"))
            add(listOf("Net new funding less personal spending",Money.input(review.netFunding),"Excludes opening cash, repayment timing and negative corrections"))
            add(emptyList());add(listOf("Account","Opening INR","Closing INR"))
            (review.openingByAccount.keys+review.closingByAccount.keys).sorted().forEach {id->add(listOf(accounts[id]?:id,Money.input(review.openingByAccount[id]?:0),Money.input(review.closingByAccount[id]?:0)))}
            add(emptyList());add(listOf("Date","Type","Description","Total INR","Personal INR","Category","Notes"))
            snapshot.records.filter {!it.reversed && YearMonth.from(Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()))==month}.sortedBy {it.date}.forEach {record->
                add(listOf(Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).toLocalDateTime().toString(),record.kind.name,record.description,Money.input(record.amount),Money.input(record.personalAmount),categories[record.categoryId].orEmpty(),record.notes))
            }
        }
    }
    fun encode(rows:List<List<String>>):String=rows.joinToString("\r\n") {row->row.joinToString(",",transform=::cell)}+"\r\n"
}
