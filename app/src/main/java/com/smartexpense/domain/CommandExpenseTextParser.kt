package com.smartexpense.domain

/** Keeps all existing non-expense and shared-bill behavior with the rules parser. */
class CommandExpenseTextParser(
    private val model: ScratchCommandModel?,
    private val fallback: ExpenseTextParser = RuleBasedTextParser()
) : ExpenseTextParser {
    override fun parse(text: String, people: List<PersonRef>): ParsedDraft {
        val base = fallback.parse(text, people)
        ExpenseCommandReview.reason(text, base)?.let { reason ->
            return base.copy(amount = null, fromAccount = null, toAccount = null,
                contributions = emptyMap(), splitRule = null,
                missing = base.missing + setOf("amount", "account"), warnings = base.warnings + reason)
        }
        if (model == null || (base.kind != null && base.kind != Kind.EXPENSE) ||
            base.contributions.keys.any { it != "self" } || base.splitRule != null ||
            "participants" in base.missing || "split method" in base.missing) return base
        val suggestion = model.suggest(text) ?: return base
        return base.copy(description = suggestion.description, kind = Kind.EXPENSE,
            amount = suggestion.amount, fromAccount = suggestion.account.lowercase(java.util.Locale.ROOT),
            missing = base.missing - setOf("amount", "transaction type"),
            warnings = base.warnings + "On-device suggestion. Review all fields before saving.")
    }
}
