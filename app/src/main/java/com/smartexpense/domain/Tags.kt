package com.smartexpense.domain

/** Personal labels such as "Goa trip" that cut across categories. Tags never change money. */
object Tags {
    const val MAX_TAGS = 10
    const val MAX_LENGTH = 30

    /** Turns "#Goa trip, work ,goa TRIP" into ["Goa trip", "work"]. */
    fun parse(input: String): List<String> = normalize(input.split(','))

    /** Trims, drops a leading #, collapses spaces and removes case-insensitive duplicates. */
    fun normalize(values: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val tags = values.map { it.trim().trimStart('#').trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
        tags.forEach {
            require(it.length <= MAX_LENGTH) { "Keep each tag to $MAX_LENGTH characters or fewer" }
            require(it.none(Char::isISOControl)) { "Tags cannot contain control characters" }
        }
        require(tags.size <= MAX_TAGS) { "Use at most $MAX_TAGS tags on one transaction" }
        return tags
    }

    /** True when [values] is a non-empty list already in the stored form. */
    fun isNormalized(values: List<String>): Boolean =
        values.isNotEmpty() && values.none { ',' in it } && runCatching { normalize(values) == values }.getOrDefault(false)

    fun has(record: Record, tag: String): Boolean = record.tags.any { it.equals(tag, ignoreCase = true) }

    /** Every tag in use, alphabetically, using the first spelling seen for each. */
    fun all(records: List<Record>): List<String> =
        records.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
}
