package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class PersonalSuggestionsTest {
    private var next = 0L
    private fun expense(description: String, category: String?, account: String, kind: Kind = Kind.EXPENSE,
                        people: List<String> = listOf("self"), reversed: Boolean = false) =
        Record("r${next}", kind, description, 1000, 1000, category, null, ++next, "", reversed, listOf(account), people)
    private val accounts = setOf("wallet", "gpay")
    private val categories = setOf("food", "bills")

    @Test fun suggestsAgreedCategoryAndAccountFromRepeatedEntries() {
        val records = listOf(expense("Chai", "food", "wallet"), expense("chai ", "food", "wallet"), expense("CHAI", "food", "gpay"))
        assertEquals(PersonalSuggestion("food", "wallet"), personalSuggestion("  chai", records, accounts, categories))
    }

    @Test fun needsRepeatedAgreementBeforeSuggesting() {
        assertEquals(PersonalSuggestion(), personalSuggestion("chai", listOf(expense("chai", "food", "wallet")), accounts, categories))
        val mixed = listOf(expense("chai", "food", "wallet"), expense("chai", "bills", "gpay"))
        assertEquals(PersonalSuggestion(), personalSuggestion("chai", mixed, accounts, categories))
    }

    @Test fun ignoresSharedReversedNonExpenseArchivedAndDeletedChoices() {
        val records = listOf(
            expense("rent", "bills", "wallet", people = listOf("self", "rahul")), expense("rent", "bills", "wallet", reversed = true),
            expense("rent", "bills", "wallet", kind = Kind.INCOME), expense("rent", "old-category", "archived"),
            expense("rent", "old-category", "archived"))
        assertEquals(PersonalSuggestion(), personalSuggestion("rent", records, accounts, categories))
    }

    @Test fun prefersRecentHabitsAndNeverMatchesOtherDescriptions() {
        val old = List(5) { expense("lunch", "bills", "gpay") }
        val recent = List(5) { expense("lunch", "food", "wallet") }
        assertEquals(PersonalSuggestion("food", "wallet"), personalSuggestion("lunch", old + recent, accounts, categories, recent = 5))
        assertEquals(PersonalSuggestion(), personalSuggestion("lunch box", recent, accounts, categories))
        assertEquals(PersonalSuggestion(), personalSuggestion("   ", recent, accounts, categories))
    }
}
