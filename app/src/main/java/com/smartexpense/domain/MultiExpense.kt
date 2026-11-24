package com.smartexpense.domain

/**
 * Splits one line holding several personal expenses ("chai 20 cash, samosa 30 cash; bus 15 upi")
 * into separate commands. The caller accepts the split only when every part is a complete personal
 * expense on its own, so shared bills, sentences with commas and single expenses are left unchanged.
 */
object MultiExpense {
    const val maximum = 10
    private val hardSeparators = Regex("\\s*(?:;|\\n|,\\s+(?:and\\s+)?|\\s+then\\s+)\\s*", RegexOption.IGNORE_CASE)
    private val and = Regex("\\s+(?:and|aur|&)\\s+", RegexOption.IGNORE_CASE)
    private val digit = Regex("[0-9]")

    /** Two to [maximum] parts each containing a number, or an empty list when the line is not a list. */
    fun segments(text: String): List<String> {
        val parts = text.split(hardSeparators).flatMap { part ->
            // "and" separates expenses only when both sides carry an amount ("bread and eggs 60 cash" stays whole).
            val pieces = part.split(and)
            if (pieces.size > 1 && pieces.all { digit.containsMatchIn(it) }) pieces else listOf(part)
        }.map { it.trim().trimEnd('.', ',') }.filter(String::isNotBlank)
        return if (parts.size in 2..maximum && parts.all { digit.containsMatchIn(it) }) parts else emptyList()
    }

    /** A part is usable alone only when it is an unshared expense with an amount and a worded description. */
    fun isComplete(draft: ParsedDraft): Boolean =
        draft.kind == Kind.EXPENSE && draft.amount != null && draft.amount > 0 && draft.splitRule == null &&
            draft.contributions.keys.all { it == "self" } && "participants" !in draft.missing &&
            draft.description.any(Char::isLetter) && draft.description.none(Char::isDigit)
}
