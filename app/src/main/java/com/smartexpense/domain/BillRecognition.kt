package com.smartexpense.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class BillItem(val name:String,val amount:Long)
data class BillSuggestion(val merchant:String,val total:Long?,val date:String?,val items:List<BillItem>,val warnings:List<String>)
object BillRecognition {
    private val money=Regex("(?<![\\p{L}\\p{N}.,+\\-−])(?:₹\\s*|INR\\s*|Rs\\.?\\s*)?([0-9]{1,3}(?:,[0-9]{2,3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*$",RegexOption.IGNORE_CASE)
    private val signedPrefix=Regex("(?:[+\\-−]\\s*(?:₹|INR|Rs\\.?)?|(?:₹|INR|Rs\\.?)\\s*[+\\-−])\\s*$",RegexOption.IGNORE_CASE)
    private fun amountMatch(line:String):MatchResult? {
        val match=money.find(line)?:return null
        return match.takeUnless {signedPrefix.containsMatchIn(line.substring(0,match.range.first).trimEnd())}
    }
    private val totalLabel=Regex("^(?:grand\\s+total|total(?:\\s+amount)?|amount\\s+(?:due|payable)|net\\s+(?:total|amount))\\b",RegexOption.IGNORE_CASE)
    private val excluded=Regex("^(?:sub\\s*total|tax|gst|cgst|sgst|vat|cess|tip|service\\s+(?:charge|fee)s?|delivery\\s+(?:charge|fee)s?|packing|packaging|handling\\s+(?:charge|fee)s?|convenience\\s+(?:charge|fee)s?|discount|round|change|cash|card|balance|date|invoice|bill\\s*no|phone|tel)\\b",RegexOption.IGNORE_CASE)
    private val metadata=Regex("^(?:pager|pax|emb|outlet|visa|mastercard|amex|upi|payment|table|counter|pos)\\b|^\\d+(?:\\.\\d+)?\\s*%\\s*(?:gst|cgst|sgst|igst|vat|tax)\\b",RegexOption.IGNORE_CASE)
    fun parse(text:String):BillSuggestion {
        val lines=text.take(10000).lines().map(String::trim).filter(String::isNotBlank)
        val foreign=Regex("\\b(?:USD|EUR|GBP|AED|JPY|CAD|AUD)\\b|[$€£]",RegexOption.IGNORE_CASE).containsMatchIn(text)
        val totals=lines.filter {totalLabel.containsMatchIn(it)}.mapNotNull {line->amountMatch(line)?.groupValues?.get(1)?.replace(",","")?.let {runCatching {Money.parse(it)}.getOrNull()}}.filter {it>0}.distinct()
        val dates=Regex("\\b[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{4}\\b").findAll(text).mapNotNull {match->
            runCatching {LocalDate.parse(match.value.replace('.','/').replace('-','/'),DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)).toString()}.getOrNull()
        }.distinct().toList()
        val items=lines.filterNot {totalLabel.containsMatchIn(it) || excluded.containsMatchIn(it) || metadata.containsMatchIn(it)}.mapNotNull {line->
            val match=amountMatch(line)?:return@mapNotNull null
            val name=line.substring(0,match.range.first).trim().trimEnd(':').replace(Regex("^\\d+\\s*[*x×]\\s+",RegexOption.IGNORE_CASE),"").trim()
            val value=runCatching {Money.parse(match.groupValues[1].replace(",",""))}.getOrNull()
            if(name.any(Char::isLetter) && value!=null && value>0) BillItem(name.take(100),value) else null
        }.take(50)
        return BillSuggestion(lines.firstOrNull {it.length>2 && !money.containsMatchIn(it) && !Regex("^[0-9/.-]+$").matches(it)}?.take(100).orEmpty(),
            if(!foreign && totals.size==1) totals.single() else null,dates.singleOrNull(),items,
            buildList {if(foreign) add("Foreign currency detected. Enter a manual exchange rate before recording INR.");if(totals.size!=1) add("Total is missing or ambiguous. Enter it from the original bill.");if(dates.size!=1) add("Check the bill date.");add("Recognition can make mistakes. Check all amounts and items before saving.")})
    }
}
