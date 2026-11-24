package com.smartexpense.domain

/** Keeps all existing non-expense and shared-bill behavior with the rules parser. */
class CommandExpenseTextParser(
    private val model: ScratchCommandModel?,
    private val fallback: ExpenseTextParser = RuleBasedTextParser(),
    private val extension: ScratchCommandModel? = null,
    private val clock: java.time.Clock = java.time.Clock.systemDefaultZone(),
    private val categories: CategoryModel? = null
) : ExpenseTextParser {
    override fun parse(text: String, people: List<PersonRef>): ParsedDraft {
        val input = CommandInputNormalizer.normalize(text)
        return withCategory(parseNormalized(input, people), input)
    }

    /** Fills only a missing category of a personal expense whose description was extracted from the command. */
    private fun withCategory(draft: ParsedDraft, input: String): ParsedDraft {
        if (categories == null || draft.category != null || draft.kind != Kind.EXPENSE) return draft
        if (draft.contributions.keys.any { it != "self" } || draft.splitRule != null) return draft
        val description = draft.description.trim()
        if (description.isEmpty() || description.equals(input.trim(), ignoreCase = true) || description.any(Char::isDigit)) return draft
        val suggestion = categories.suggest(description) ?: return draft
        return draft.copy(category = suggestion.category, warnings = draft.warnings + "Category suggested on device. Change it if it does not fit.")
    }

    private fun parseNormalized(input: String, people: List<PersonRef>): ParsedDraft {
        val prepared = EnglishCommandInput.prepare(input, clock)
        val text = prepared.text
        val parsed = fallback.parse(text, people)
        val base = parsed.copy(date = prepared.date ?: parsed.date)
        fun incomplete(reason: String, invalidDate: Boolean = false) = base.copy(
            amount = null, fromAccount = null, toAccount = null, contributions = emptyMap(), splitRule = null,
            date = if (invalidDate) null else base.date,
            missing = base.missing + setOf("amount", "account") + if (invalidDate) setOf("date") else emptySet(),
            warnings = base.warnings + reason)
        prepared.error?.let { return incomplete(it, prepared.invalidDate) }
        ExpenseCommandReview.reason(text, base)?.let { return incomplete(it) }
        if ((base.kind != null && base.kind != Kind.EXPENSE) ||
            base.contributions.keys.any { it != "self" } || base.splitRule != null ||
            "participants" in base.missing || "split method" in base.missing) return base
        // Preserve accepted compatibility-model drafts, then extend only its abstentions.
        val suggestion = model?.suggest(text) ?: extension?.suggest(text)
        if (suggestion == null) {
            if (!newCapability.containsMatchIn(text)) return base
            return base.copy(amount = null, fromAccount = null, contributions = emptyMap(),
                missing = base.missing + setOf("amount", "account"),
                warnings = base.warnings + "Could not confidently interpret this command. Confirm the description, INR amount and payment account manually.")
        }
        return base.copy(description = suggestion.description, kind = Kind.EXPENSE,
            amount = suggestion.amount, fromAccount = suggestion.account.lowercase(java.util.Locale.ROOT),
            missing = base.missing - setOf("amount", "transaction type"),
            warnings = base.warnings + "On-device suggestion. Review all fields before saving.")
    }

    private companion object {
        val newCapability = Regex("\\b(?:maine|kharch|diye|rupaye|gpy|gpaay|wallett|walllet)\\b|\\bgooogle\\s+pay\\b", RegexOption.IGNORE_CASE)
    }
}
