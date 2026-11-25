package com.smartexpense.domain

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MultiExpenseTest {
    private fun parser(ai: Boolean) = CommandExpenseTextParser(
        if (ai) File("src/localAi/assets/tally-command.bin").inputStream().use(ScratchCommandModel::load) else null,
        extension = if (ai) File("src/localAi/assets/tally-command-extended.bin").inputStream().use { ScratchCommandModel.load(it, true) } else null)

    private fun drafts(text: String, ai: Boolean): List<ParsedDraft>? {
        val parts = MultiExpense.segments(text)
        if (parts.isEmpty()) return null
        val parsed = parts.map { parser(ai).parse(it, emptyList()) }
        return parsed.takeIf { it.all(MultiExpense::isComplete) }
    }

    @Test fun severalExpensesBecomeSeparateExactDrafts() {
        for (ai in listOf(false, true)) {
            val result = drafts("chai 20 cash, samosa 30 cash; bus 15 gpay", ai)!!
            assertEquals(listOf("chai", "samosa", "bus"), result.map { it.description })
            assertEquals(listOf(2000L, 3000L, 1500L), result.map { it.amount })
            assertEquals(listOf(2000L, 3000L), drafts("chai 20 cash and samosa 30 cash", ai)!!.map { it.amount })
            assertEquals(listOf(4500L, 3000L), drafts("chai 3x15 cash, samosa 30 cash", ai)!!.map { it.amount })
        }
    }

    @Test fun singleExpensesSentencesAndSharedBillsAreNotSplit() {
        assertTrue(MultiExpense.segments("bread and eggs 60 cash").isEmpty())
        assertTrue(MultiExpense.segments("coffee 45 cash").isEmpty())
        assertTrue(MultiExpense.segments("expense: coffee, 45, cash").isEmpty())
        assertNull(drafts("Food for 1500. I paid 1000, Rahul paid 500. Split equally", false))
        assertNull(drafts("chai 20 cash, received 500 salary gpay", false))
        assertTrue(MultiExpense.segments(List(11) { "tea $it cash" }.joinToString("; ")).isEmpty())
    }
}
