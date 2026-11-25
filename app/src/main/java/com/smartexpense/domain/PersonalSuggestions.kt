package com.smartexpense.domain

import java.util.Locale

/** What the user's own saved entries suggest for a new description. Computed on the phone from local records only. */
data class PersonalSuggestion(val categoryId: String? = null, val accountId: String? = null)

/**
 * Learns from the user's past personal expenses with the same description (ignoring case and spacing).
 * A field is suggested only when at least [minimumMatches] recent entries agree and the leading choice
 * covers at least two thirds of them, so one-off or mixed habits never produce a guess.
 */
fun personalSuggestion(
    description: String, records: List<Record>, activeAccountIds: Set<String>, categoryIds: Set<String>,
    minimumMatches: Int = 2, recent: Int = 30
): PersonalSuggestion {
    val key = normalizedDescription(description)
    if (key.isEmpty()) return PersonalSuggestion()
    val matches = records.asSequence()
        .filter { it.kind == Kind.EXPENSE && !it.reversed && it.personIds == listOf("self") && normalizedDescription(it.description) == key }
        .sortedWith(compareByDescending<Record> { it.date }.thenBy { it.id })
        .take(recent).toList()
    fun <T : Any> agreed(values: List<T?>): T? {
        val present = values.filterNotNull()
        if (present.size < minimumMatches) return null
        val (best, count) = present.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<T, Int>> { it.value }.thenBy { it.key.toString() }).first()
        return if (count * 3 >= present.size * 2) best else null
    }
    return PersonalSuggestion(
        categoryId = agreed(matches.map { it.categoryId?.takeIf(categoryIds::contains) }),
        accountId = agreed(matches.map { r -> r.accountIds.singleOrNull()?.takeIf(activeAccountIds::contains) })
    )
}

internal fun normalizedDescription(text: String): String =
    text.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
