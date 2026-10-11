package com.smartexpense.domain

data class PersonRef(val id: String, val name: String, val aliases: List<String> = emptyList())
data class ParsedDraft(
    val description: String, val kind: Kind?, val amount: Long?, val category: String?,
    val contributions: Map<String, Long> = emptyMap(), val splitRule: SplitRule? = null,
    val personId: String? = null, val fromAccount: String? = null, val toAccount: String? = null,
    val missing: Set<String> = emptySet(), val warnings: List<String> = emptyList(),val date:java.time.LocalDate?=null
)
interface ExpenseTextParser { fun parse(text: String, people: List<PersonRef>): ParsedDraft }
/** Suggestions only. The caller must show an editable draft and require confirmation. */
class RuleBasedTextParser(private val clock:java.time.Clock=java.time.Clock.systemDefaultZone()) : ExpenseTextParser {
    override fun parse(text: String, people: List<PersonRef>): ParsedDraft {
        val normalized = text.lowercase().replace("grocries", "groceries").replace("luch", "lunch")
        val missing = linkedSetOf<String>()
        val warnings = mutableListOf<String>()
        fun resolve(name: String): String? {
            if (name.lowercase() in setOf("i", "me", "user", "myself")) return "self"
            val matches = people.filter { p -> (p.aliases + p.name).any { it.equals(name.trim(), true) } }
            if (matches.size == 1) return matches.single().id
            warnings += if(matches.isEmpty()) "Unknown person: $name" else "Choose which $name you mean"
            missing += "participants"
            return null
        }
        val kind = when {
            Regex("\\btransfer(red)?\\b").containsMatchIn(normalized) -> Kind.TRANSFER
            Regex("\\b(returned|repaid|repayment)\\b").containsMatchIn(normalized) -> Kind.REPAYMENT
            Regex("\\b(refund|refunded)\\b").containsMatchIn(normalized) -> Kind.REFUND
            Regex("\\b(salary|income|earned|received)\\b").containsMatchIn(normalized) -> Kind.INCOME
            Regex("\\b(spent|paid|bought|bill|food|lunch|dinner|groceries)\\b").containsMatchIn(normalized) -> Kind.EXPENSE
            else -> null
        }
        val amountPattern = "(?:₹\\s*)?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"
        val mealTotal = Regex("(?:dinner|lunch)(?:\\s+with\\s+[^₹\\d.]+)?\\s+$amountPattern", RegexOption.IGNORE_CASE).find(text)
        val billMatch = mealTotal ?: Regex("(?:bill\\s+(?:was|is)|(?:food|groceries|dinner)\\s+for|spent|bought\\s+.+?\\s+for|paid)\\s+$amountPattern",RegexOption.IGNORE_CASE).find(text)
        val dated=Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").find(text)?.value
        val numbers = Regex(amountPattern).findAll(if(dated==null) text else text.replace(dated,"")).toList()
        var amount = (billMatch?.groupValues?.get(1) ?: numbers.firstOrNull()?.groupValues?.get(1))?.let {
            try { Money.parse(it) } catch (e: IllegalArgumentException) { warnings += e.message.orEmpty(); null }
        }
        val payers = linkedMapOf<String, Long>()
        Regex("\\b([\\p{L}]+)\\s+paid\\s+(?:$amountPattern|nothing)", RegexOption.IGNORE_CASE).findAll(text).forEach {
            val id = resolve(it.groupValues[1])
            val paid = if (it.groupValues[2].isBlank()) 0L else try { Money.parse(it.groupValues[2]) } catch(e: IllegalArgumentException) { warnings += "Invalid contribution"; null }
            if(id != null && paid != null) {
                if(id in payers) { warnings += "Repeated payer: ${it.groupValues[1]}"; missing += "contributions" } else payers[id] = paid
            }
        }
        val shared = payers.keys.any { it != "self" } || Regex("\\b(people|split|shared)\\b").containsMatchIn(normalized)
        if(!shared && numbers.size>1) {amount=null;warnings+="Multiple amounts found. Choose the transaction amount."}
        val split = if (Regex("\\b(equal|equally)\\b").containsMatchIn(normalized)) SplitRule.Equal else null
        if(shared && split == null) missing += "split method"
        if(shared && payers.size < 2) missing += "participants"
        val repaymentName = Regex("([\\p{L}]+)\\s+(?:returned|repaid)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val person = repaymentName?.let(::resolve)
        if(kind == Kind.REPAYMENT && person == null) missing += "person"
        val transfer = Regex("from\\s+(.+?)\\s+to\\s+(.+?)[.!]?$",RegexOption.IGNORE_CASE).find(text)
        if(amount == null || amount <= 0) missing += "amount"
        if(kind == null) missing += "transaction type"
        missing += "account"
        if(kind == Kind.TRANSFER) missing += "destination account"
        if(shared && amount != null && runCatching { Finance.total(payers.values) }.getOrNull() != amount) missing += "contributions"
        val category = when {
            Regex("lunch|dinner|food|coffee|breakfast").containsMatchIn(normalized) -> "Food"
            "grocer" in normalized -> "Groceries"
            Regex("bus|taxi|uber|train|fuel").containsMatchIn(normalized) -> "Transport"
            Regex("medicine|doctor|hospital").containsMatchIn(normalized) -> "Health"
            else -> null
        }
        val date=when {dated!=null->runCatching {java.time.LocalDate.parse(dated)}.getOrNull();"yesterday" in normalized->java.time.LocalDate.now(clock).minusDays(1);"today" in normalized->java.time.LocalDate.now(clock);else->null}
        return ParsedDraft(text,kind,amount,category,payers,split,person,transfer?.groupValues?.get(1)?.trim(),transfer?.groupValues?.get(2)?.trim(),missing,warnings,date)
    }
}

/** Future adapters return drafts; neither scanner nor local AI can write financial records. */
interface ReceiptDraftSource { suspend fun draft(image: ByteArray): ParsedDraft }
interface LocalIntelligenceDraftSource { suspend fun draft(text: String, people: List<PersonRef>): ParsedDraft }

