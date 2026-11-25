package com.smartexpense.domain

import java.io.File
import java.time.LocalDate
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class AssistantModelTest {
    private val model by lazy { File("src/localAi/assets/tally-assistant.bin").inputStream().buffered().use(TinyLanguageModel::load) }
    private fun decode(text: String) = String(Base64.getDecoder().decode(text), Charsets.UTF_8)
    private fun lines(name: String) = File("src/test/resources/$name").readLines().filter(String::isNotBlank)

    @Test fun kotlinGenerationMatchesPythonOnFrozenQuestions() {
        val cases = lines("assistant-parity.tsv")
        assertEquals(200, cases.size)
        for (line in cases) {
            val (source, program, confidence) = line.split('\t')
            val tokens = decode(source).split(' ').filter(String::isNotEmpty)
            val (generated, score) = model.generate(tokens)
            assertEquals(decode(source), decode(program), generated.joinToString(" "))
            assertEquals(decode(source), confidence.toDouble(), score.toDouble(), 2e-3)
        }
    }

    @Test fun anonymiserMatchesPython() {
        for (line in lines("assistant-anonymise.tsv")) {
            val f = line.split('\t').map(::decode)
            val result = AssistantPlanner.anonymise(f[0], f[1].split('|').filter(String::isNotEmpty), f[2].split('|').filter(String::isNotEmpty), model.words)
            assertEquals(f[0], f[3], result?.first?.joinToString(" ") ?: "-")
            if (result != null) {
                val expected = Regex("\"([^\"]+)\": \"([^\"]*)\"").findAll(f[4]).associate { it.groupValues[1] to it.groupValues[2] }
                assertEquals(f[0], expected, result.second)
            }
        }
    }

    @Test fun validatorMatchesPython() {
        val cases = lines("assistant-validate.tsv")
        assertTrue(cases.size > 600)
        for (line in cases) {
            val (source, program, valid) = line.split('\t')
            assertEquals(decode(source) + " | " + decode(program), valid.toBoolean(),
                AssistantValidator.valid(decode(source).split(' ').filter(String::isNotEmpty), decode(program).split(' ').filter(String::isNotEmpty)))
        }
    }

    @Test fun endToEndAnswersComeFromRecordsAndRefusalsFallThrough() {
        val today = LocalDate.of(2026, 11, 25)
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val food = Category("food", "Food", 0, "", false)
        fun at(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val records = listOf(Record("a", Kind.EXPENSE, "Lunch", 25_000, 25_000, "food", null, at(today), "", false, listOf("w"), listOf("self")),
            Record("b", Kind.EXPENSE, "Dinner", 40_000, 40_000, "food", null, at(LocalDate.of(2026, 10, 3)), "", false, listOf("w"), listOf("self")))
        val data = AssistantPlanner.Data(records, listOf(food), emptyList(), emptyList(), today, zone)
        val spend = AssistantPlanner.answer("how much did i spend on food this month", model, data)!!
        assertEquals(listOf("spend", "this_month", "cat", "CAT1", ";", "answer"), spend.program)
        assertTrue(spend.text, spend.text.startsWith("You spent ₹250.00 on Food this month"))
        val compare = AssistantPlanner.answer("compare food this month and last month", model, data)
        if (compare != null) assertTrue(compare.text, compare.text.contains("₹250.00") && compare.text.contains("₹400.00"))
        assertNull(AssistantPlanner.answer("tell me a joke", model, data))
    }
}
