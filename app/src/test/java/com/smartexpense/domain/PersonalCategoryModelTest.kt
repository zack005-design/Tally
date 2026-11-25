package com.smartexpense.domain

import org.junit.Assert.*
import org.junit.Test

class PersonalCategoryModelTest {
    private var next = 0L
    private fun expense(description: String, category: String?, reversed: Boolean = false, kind: Kind = Kind.EXPENSE) =
        Record("r$next", kind, description, 1000, 1000, category, null, ++next, "", reversed, listOf("wallet"), listOf("self"))

    // A fictional user with their own categories, including custom "Pets" and "Office".
    private val history = listOf(
        "chai" to "food", "masala chai" to "food", "chai and samosa" to "food", "lunch" to "food", "dosa" to "food",
        "dog food" to "pets", "vet visit" to "pets", "dog grooming" to "pets", "cat litter" to "pets",
        "printer paper" to "office", "office supplies" to "office", "stapler" to "office", "printer ink" to "office",
        "uber" to "travel")
    private val active = setOf("food", "pets", "office", "travel")

    @Test fun learnsTheUsersOwnCategoriesAndGeneralisesToVariants() {
        val model = PersonalCategoryModel.train(history.map { expense(it.first, it.second) }, active)!!
        assertEquals("food", model.suggest("evening chai")!!.categoryId)
        assertEquals("food", model.suggest("lunch box")!!.categoryId)
        assertEquals("pets", model.suggest("dog food refill")!!.categoryId)
        assertEquals("office", model.suggest("printer ink cartridge")!!.categoryId)
        // "travel" has only one example, so it is never predicted.
        assertNotEquals("travel", model.suggest("uber")?.categoryId)
    }

    @Test fun abstainsOnUnfamiliarTextAndNeedsEnoughHistory() {
        val model = PersonalCategoryModel.train(history.map { expense(it.first, it.second) }, active)!!
        assertNull(model.suggest(""))
        assertNull(model.suggest("1234"))
        assertNull(model.suggest("qwerty"))
        assertNull(PersonalCategoryModel.train(history.take(5).map { expense(it.first, it.second) }, active))
        assertNull(PersonalCategoryModel.train(history.map { expense(it.first, it.second, reversed = true) }, active))
        assertNull(PersonalCategoryModel.train(history.map { expense(it.first, it.second) }, setOf("food")))
    }

    @Test fun trainingIsDeterministic() {
        val records = history.map { expense(it.first, it.second) }
        val a = PersonalCategoryModel.train(records, active)!!.suggest("dog food refill")
        val b = PersonalCategoryModel.train(records, active)!!.suggest("dog food refill")
        assertEquals(a, b)
    }
}
