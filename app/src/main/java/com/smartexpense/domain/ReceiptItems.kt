package com.smartexpense.domain

/** Itemised lines for a shared receipt: the items you reviewed at import, else the attached scan's recognised items. */
object ReceiptItems {
    const val IMPORT_HEADER="Imported printed bill"

    fun from(notes:String,receiptText:String?):List<BillItem> {
        val lines=notes.lines()
        if(lines.firstOrNull()?.startsWith(IMPORT_HEADER)==true) {
            val reviewed=lines.drop(1).mapNotNull {line->
                val split=line.lastIndexOf(':')
                if(split<=0) null else {
                    val name=line.substring(0,split).trim()
                    val amount=runCatching {Money.parse(line.substring(split+1).trim())}.getOrNull()
                    if(name.isBlank() || amount==null || amount<=0) null else BillItem(name,amount)
                }
            }
            if(reviewed.isNotEmpty()) return reviewed
        }
        return receiptText?.takeIf {it.isNotBlank()}?.let {runCatching {BillRecognition.parse(it).items}.getOrNull()}.orEmpty()
    }

    fun merchant(receiptText:String?):String? = receiptText?.takeIf {it.isNotBlank()}
        ?.let {runCatching {BillRecognition.parse(it).merchant}.getOrNull()}?.trim()?.takeIf {it.isNotBlank()}
}
