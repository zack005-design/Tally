package com.smartexpense.domain

/** Validation shared by the rules fallback and learned personal-expense path. */
internal object ExpenseCommandReview {
    private val hindiNonAction = Regex("\\b(?:nahi|nahin|mat|kal|karunga|karungi|karenge|dunga|dungi)\\b", RegexOption.IGNORE_CASE)
    private val nonAction = Regex("\\b(?:not|never|didn['’]?t|don['’]?t|haven['’]?t|tomorrow|planning)\\b|\\b(?:i|we|you|they|he|she)\\s+(?:will|would|might|may|could|should)\\b|^\\s*plan\\b|\\bno\\s+(?:expense|payment|purchase|spending)\\b|^\\s*(?:what|how|show|tell|find)\\b|^\\s*(?:can|could|would|should)\\s+(?:i|you|we)\\b|\\?", RegexOption.IGNORE_CASE)
    private val foreign = Regex("[$€£]|\\b(?:usd|eur|gbp|dollars|euros|pounds)\\b", RegexOption.IGNORE_CASE)
    private val isoDate = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b")
    private val numeric = Regex("[0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?")
    private val valid = Regex("(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+|[1-9][0-9]?(?:,[0-9]{2})*,[0-9]{3})(?:\\.[0-9]{1,2})?")
    private val account = Regex("\\b(?:google\\s+pay|gooogle\\s+pay|g[\\s-]+pay|googlepay|gpay|gpy|gpaay|wallet|wallett|walllet|cash)\\b", RegexOption.IGNORE_CASE)

    fun reason(text: String, base: ParsedDraft): String? {
        if (text.codePoints().anyMatch { Character.getType(it) == Character.FORMAT.toInt() || Character.isISOControl(it) })
            return "Hidden control characters found. Retype the transaction before entering its amount and account."
        if (text.codePoints().anyMatch { Character.getType(it) == Character.LETTER_NUMBER.toInt() || Character.getType(it) == Character.OTHER_NUMBER.toInt() })
            return "Ambiguous numeric characters found. Enter the amount using ordinary decimal digits."
        if (nonAction.containsMatchIn(text) || hindiNonAction.containsMatchIn(text)) return "This looks like a question, plan, negated entry or ambiguous date. Enter the intended transaction manually."
        if (foreign.containsMatchIn(text) || text.codePoints().anyMatch { Character.getType(it) == Character.CURRENCY_SYMBOL.toInt() && it != '₹'.code }) return "Review the currency and convert to INR before entering this expense."
        val withoutDates = isoDate.replace(text) { match ->
            if (runCatching { java.time.LocalDate.parse(match.value) }.isSuccess) " ".repeat(match.value.length) else match.value
        }
        val matches = numeric.findAll(withoutDates).toList()
        // Validate each monetary token before shared/non-expense routing; zero contributions remain valid.
        if (matches.any { match ->
            val previous = withoutDates.substring(0, match.range.first).trimEnd().lastOrNull()
            val next = withoutDates.substring(match.range.last + 1).trimStart().firstOrNull()
            previous in listOf('-', '−', '+', '.', '/') || next in listOf('/', '%') || !valid.matches(match.value)
        }) return "Invalid amount. Enter an unsigned INR amount with at most two decimal places."
        if ((base.kind != null && base.kind != Kind.EXPENSE) || base.contributions.keys.any { it != "self" } ||
            base.splitRule != null || "participants" in base.missing || "split method" in base.missing) return null
        val sources = account.findAll(text).map {
            if (it.value.lowercase(java.util.Locale.ROOT) in setOf("cash", "wallet", "wallett", "walllet")) "WALLET" else "GPAY"
        }.toSet()
        if (sources.size > 1) return "Multiple payment accounts found. Enter the amount and account manually."
        if (matches.size > 1) return "Multiple amounts found. Choose the transaction amount manually."
        val match = matches.singleOrNull() ?: return null
        if (runCatching { Money.parse(match.value) }.getOrNull()?.let { it > 0 } != true)
            return "Invalid amount. Enter a positive INR amount within the supported range."
        return null
    }
}
