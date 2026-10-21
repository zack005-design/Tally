package com.smartexpense

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartexpense.domain.CommandExpenseTextParser
import com.smartexpense.domain.RuleBasedTextParser
import com.smartexpense.domain.ScratchCommandModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only the bundled model and fictional phrases; never opens the database. */
@RunWith(AndroidJUnit4::class)
class LocalCommandModelTest {
    @Test fun bundledModelImprovesDraftsAndRunsLocally() {
        org.junit.Assume.assumeTrue(BuildConfig.LOCAL_AI_ENABLED)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.assets.open("tally-command.bin").use(ScratchCommandModel::load)
        val parser = CommandExpenseTextParser(model)
        val cases = listOf(
            Triple("Lunch 180 GPay", "Lunch", 18000L), Triple("Coffee 45 cash", "Coffee", 4500L),
            Triple("Paid 120 for groceries using wallet", "groceries", 12000L),
            Triple("Spent 99.50 on snacks through google pay", "snacks", 9950L),
            Triple("Record 250 for fuel via cash", "fuel", 25000L),
            Triple("Dinner was 450 and I paid with GPay", "Dinner", 45000L),
            Triple("Please add my bus ticket, 35 rupees, cash", "bus ticket", 3500L),
            Triple("I used wallet for tea costing 12.75", "tea", 1275L))
        var baselineCorrect = 0
        for ((text, description, amount) in cases) {
            val base = RuleBasedTextParser().parse(text, emptyList())
            if (base.description == description && base.amount == amount && base.fromAccount != null) baselineCorrect++
            val parsed = parser.parse(text, emptyList())
            assertEquals(text, description, parsed.description)
            assertEquals(text, amount, parsed.amount)
            assertNotNull(text, parsed.fromAccount)
        }
        assertTrue(baselineCorrect < cases.size)
        listOf("coffee 45 cash google pay", "coffee -45 cash", "coffee 45.001 cash",
            "split lunch 900 gpay", "refund 45 coffee cash").forEach { assertNull(it, model.suggest(it)) }
        val transfer = "Transferred 1000 from bank to cash"
        assertEquals(RuleBasedTextParser().parse(transfer, emptyList()), parser.parse(transfer, emptyList()))
        repeat(20) { model.suggest("Lunch 180 GPay") }
        val timings = List(100) {
            val start = System.nanoTime()
            requireNotNull(model.suggest("Lunch 180 GPay"))
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        println("COMMAND_MODEL_ANDROID baseline=$baselineCorrect/8 neural=8/8 p50_ms=${timings[50]} p95_ms=${timings[94]}")
    }
}
