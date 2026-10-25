package com.smartexpense.domain

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class ScratchCommandModelTest {
    @Test fun boundedOneMillionParameterModelLoadsAndRefusesWithoutEvidence() {
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use { out ->
            out.writeInt(0x54434D44); out.writeInt(1)
            val vocabulary = 201; val embedding = 128; val hidden = 342
            out.writeInt(vocabulary); out.writeInt(embedding); out.writeInt(hidden); out.writeFloat(.9f)
            fun text(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); out.writeInt(bytes.size); out.write(bytes) }
            repeat(vocabulary) { text(when (it) { 0 -> "<pad>"; 1 -> "<unknown>"; else -> "word$it" }) }
            val shapes = linkedMapOf("embedding.weight" to listOf(vocabulary, embedding))
            listOf("", "_reverse").forEach { suffix ->
                shapes["encoder.weight_ih_l0$suffix"] = listOf(hidden * 3, embedding)
                shapes["encoder.weight_hh_l0$suffix"] = listOf(hidden * 3, hidden)
                shapes["encoder.bias_ih_l0$suffix"] = listOf(hidden * 3)
                shapes["encoder.bias_hh_l0$suffix"] = listOf(hidden * 3)
            }
            listOf("tags" to 4, "intent" to 2).forEach { (name, rows) -> shapes["$name.weight"] = listOf(rows, hidden * 2); shapes["$name.bias"] = listOf(rows) }
            out.writeInt(shapes.size)
            shapes.forEach { (name, shape) -> text(name); out.writeInt(shape.size); shape.forEach(out::writeInt); repeat(shape.reduce(Int::times)) { out.writeFloat(0f) } }
        }
        assertNull(ScratchCommandModel.load(stream.toByteArray().inputStream()).suggest("coffee 45 cash"))
    }

    @Test fun excessiveModelDimensionsAreRejectedBeforeAllocatingWeights() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out -> listOf(0x54434D44, 1, 4096, 128, 512).forEach(out::writeInt) }
        assertThrows(IllegalArgumentException::class.java) { ScratchCommandModel.load(bytes.toByteArray().inputStream()) }
    }
    private fun model(): ScratchCommandModel = File("src/localAi/assets/tally-command.bin").inputStream().use(ScratchCommandModel::load)

    @Test fun exactAmountsAreCopiedInsteadOfGenerated() {
        val model = model()
        listOf("Coffee 45 cash" to 4500L, "Lunch 180 GPay" to 18000L,
            "Paid 99.50 for snacks using google pay" to 9950L,
            "office lunch 0.01 wallet" to 1L).forEach { (text, amount) ->
            val draft = requireNotNull(model.suggest(text)) { text }
            assertEquals(amount, draft.amount)
        }
    }

    @Test fun uncertainCommandsDoNotBecomeNeuralExpenseDrafts() {
        val model = model()
        listOf("coffee 45", "coffee 45 cash gpay", "coffee 45 cash google pay", "coffee 45 g pay wallet", "coffee 45 and 80 cash",
            "coffee -45 cash", "coffee 45.001 cash", "split lunch 900 gpay",
            "refund 45 coffee cash", "I did not spend 45 on coffee using cash",
            "transfer 400 from wallet to gpay", "coffee 45 dollars cash", "x".repeat(241)).forEach {
            assertNull(it, model.suggest(it))
        }
    }

    @Test fun malformedModelIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ScratchCommandModel.load("bad".byteInputStream()) }
    }

    @Test fun portableRuntimeMatchesPythonForAllFixtureFields() {
        val model = model()
        val lines = requireNotNull(javaClass.getResourceAsStream("/command-parity.tsv")).bufferedReader().use { it.readLines() }
        assertTrue(lines.size >= 100)
        val decoder = java.util.Base64.getDecoder()
        lines.forEach { line ->
            val fields = line.split('\t')
            val text = String(decoder.decode(fields[0]), Charsets.UTF_8)
            val draft = model.suggest(text)
            if (fields[1] == "-") assertNull(text, draft) else {
                requireNotNull(draft) { text }
                assertEquals(text, fields[1].toLong(), draft.amount)
                assertEquals(text, fields[2], draft.account)
                assertEquals(text, String(decoder.decode(fields[3]), Charsets.UTF_8), draft.description)
                assertEquals(text, fields[4].toDouble(), draft.confidence.toDouble(), 0.00002)
            }
        }
    }

    @Test fun missingModelKeepsExistingParserBehavior() {
        val text = "Transferred 1000 from bank to cash"
        assertEquals(RuleBasedTextParser().parse(text, emptyList()), CommandExpenseTextParser(null).parse(text, emptyList()))
    }
}
