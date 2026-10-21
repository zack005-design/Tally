package com.smartexpense.domain

import java.text.Normalizer
import java.util.Locale

data class NamedMealItem(val name:String,val amount:Long,val people:List<String>)

/** Conservatively matches receipt row names; ambiguous instructions never partially apply. */
fun matchMealItems(description:String,names:List<String>):Result<Set<Int>> = runCatching {
    val text=Normalizer.normalize(description,Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
    require(text.isNotBlank()) {"Type the item names you ate, or select them below."}
    require(!Regex("\\b(?:not|except|without|didn't|didn’t|half|quarter|shared|share|both|we|some)\\b|\\bone\\s+of\\b|\\b\\d+\\s+(?:of|out\\s+of)\\b").containsMatchIn(text)) {
        "Use the item buttons for shared dishes, quantities or exceptions. Nothing has been changed."
    }
    fun normalized(value:String)=Normalizer.normalize(value,Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().replace(Regex("\\s+")," ")
    val labels=names.map(::normalized)
    val meal=text.replace(Regex("^(?:i\\s+(?:ate|had|ordered)|my\\s+(?:food|items)(?:\\s+(?:was|were))?)\\s*[:\\-]?\\s*"),"").trim()
    val exact=labels.indices.filter {labels[it]==normalized(meal)}
    if(exact.size==1) return@runCatching exact.toSet()
    require(exact.size<2) {"Several bill rows have that name. Select the rows below. Nothing has been changed."}
    val parts=meal.split(Regex("\\s*(?:[,;\\n+&]|\\band\\b)\\s*")).filter(String::isNotBlank)
    require(parts.isNotEmpty()) {"Type the item names you ate, or select them below."}
    parts.map {part->
        val phrase=normalized(part).removePrefix("the ")
        require(phrase.isNotBlank()) {"Enter a food item name."}
        val full=labels.indices.filter {labels[it]==phrase}
        val candidates=if(full.isNotEmpty()) full else labels.indices.filter {Regex("(?:^| )${Regex.escape(phrase)}(?: |$)").containsMatchIn(labels[it])}
        require(candidates.isNotEmpty()) {"Could not find ‘$part’ on this bill. Select the item below. Nothing has been changed."}
        require(candidates.size==1) {"‘$part’ matches several bill rows. Select the rows below. Nothing has been changed."}
        candidates.single()
    }.toSet()
}

fun namedMealItems(names:List<String>,lines:String):List<NamedMealItem> {
    val rows=lines.lines().filter(String::isNotBlank)
    require(rows.isNotEmpty() && rows.size==names.size) {"The bill rows changed. Review them in the advanced editor."}
    return rows.mapIndexed {index,line->
        val parts=line.split('=')
        require(parts.size==2) {"Check the amount and people on each bill row."}
        val amount=Money.parse(parts[0]).also {require(it>0) {"Each item needs a positive amount."}}
        val people=parts[1].split('+').map(String::trim)
        require(people.isNotEmpty() && people.all(String::isNotBlank) && people.distinct()==people) {"Choose who ate each item."}
        NamedMealItem(names[index].ifBlank {"Item ${index+1}"},amount,people)
    }
}

/** Existing foodSplitRule encoding; preserves all amounts and assigns every row exactly once. */
fun assignMealItems(lines:String,mine:Set<Int>,shared:Set<Int>,friend:String):String {
    require(friend.isNotBlank() && friend!="self" && '+' !in friend && '=' !in friend) {"Choose one friend."}
    val rows=lines.lines().filter(String::isNotBlank)
    require(rows.isNotEmpty() && (mine+shared).all {it in rows.indices} && mine.intersect(shared).isEmpty()) {"Check which bill rows were selected."}
    return rows.mapIndexed {index,line->
        val amount=Money.parse(line.substringBefore('=')).also {require(it>0)}
        val owners=when(index) {in shared->"self+$friend";in mine->"self";else->friend}
        "${Money.input(amount)}=$owners"
    }.joinToString("\n")
}
