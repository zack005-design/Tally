package com.smartexpense.domain

import java.io.File
import java.time.LocalDate
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class AssistantModelTest {
    private val model by lazy { File("src/localAi/assets/tally-assistant.bin").inputStream().buffered().use(TinyLanguageModel::load) }
    private fun decode(text: String) = String(Base64.getDecoder().decode(text), Charsets.UTF_8)
    private fun tokens(text: String) = decode(text).split(' ').filter(String::isNotEmpty)
    private fun lines(name: String) = File("src/test/resources/$name").readLines().filter(String::isNotBlank)
    private fun json(text: String) = Regex("\"([^\"]+)\": \"([^\"]*)\"").findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

    @Test fun kotlinGenerationMatchesPythonOnFrozenQuestionsAndFollowUps() {
        val cases = lines("assistant-parity.tsv")
        assertTrue(cases.size >= 200)
        for (line in cases) {
            val (context, source, program, confidence) = line.split('\t')
            val (generated, score) = model.generate(tokens(source), context = tokens(context))
            assertEquals(decode(context) + " || " + decode(source), decode(program), generated.joinToString(" "))
            assertEquals(decode(source), confidence.toDouble(), score.toDouble(), 2e-3)
        }
    }

    @Test fun anonymiserMatchesPythonIncludingCarriedNames() {
        for (line in lines("assistant-anonymise.tsv")) {
            val f = line.split('\t').map(::decode)
            val result = AssistantPlanner.anonymise(f[0], f[1].split('|').filter(String::isNotEmpty), f[2].split('|').filter(String::isNotEmpty),
                model.words, json(f[3]))
            assertEquals(f[0], f[4], result?.first?.joinToString(" ") ?: "-")
            if (result != null) assertEquals(f[0], json(f[5]), result.second)
        }
    }

    @Test fun validatorMatchesPython() {
        val cases = lines("assistant-validate.tsv")
        assertTrue(cases.size > 600)
        for (line in cases) {
            val (context, source, program, valid) = line.split('\t')
            assertEquals(decode(context) + " || " + decode(source) + " | " + decode(program), valid.toBoolean(),
                AssistantValidator.valid(tokens(source), tokens(program), tokens(context)))
        }
    }

    @Test fun endToEndAnswersAndFollowUpsComeFromRecords() {
        val today = LocalDate.of(2026, 11, 25)
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val food = Category("food", "Food", 0, "", false); val travel = Category("travel", "Travel", 0, "", false)
        fun at(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        fun r(id: String, day: LocalDate, paise: Long, category: String) =
            Record(id, Kind.EXPENSE, id, paise, paise, category, null, at(day), "", false, listOf("w"), listOf("self"))
        val records = listOf(r("Lunch", today, 25_000, "food"), r("Dinner", LocalDate.of(2026, 10, 3), 40_000, "food"),
            r("Train", today, 90_000, "travel"))
        val data = AssistantPlanner.Data(records, listOf(food, travel), emptyList(), emptyList(), today, zone)
        val spend = AssistantPlanner.answer("how much did i spend on food this month", model, data)!!
        assertEquals(listOf("spend", "this_month", "cat", "CAT1", ";", "answer"), spend.program)
        assertTrue(spend.text, spend.text.startsWith("You spent ₹250.00 on Food this month"))
        AssistantPlanner.answer("and last month?", model, data, spend)?.let { followUp ->
            assertEquals(listOf("spend", "last_month", "cat", "CAT1", ";", "answer"), followUp.program)
            assertTrue(followUp.text, followUp.text.startsWith("You spent ₹400.00 on Food last month"))
        }
        AssistantPlanner.answer("what about travel", model, data, spend)?.let { followUp ->
            assertTrue(followUp.text, followUp.text.startsWith("You spent ₹900.00 on Travel this month"))
        }
        assertNull(AssistantPlanner.answer("tell me a joke", model, data))
    }
}
