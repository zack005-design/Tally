package com.smartexpense.domain

import java.io.DataInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.exp
import kotlin.math.tanh

/** Portable, immutable inference for our own randomly initialized/trained GRU.
 * No network, native runtime, financial writes, or generated monetary values.
 */
class ScratchCommandModel private constructor(
    private val vocabulary: Map<String, Int>, private val embeddingSize: Int,
    private val hidden: Int, private val threshold: Float, private val weights: Map<String, FloatArray>
) {
    data class Suggestion(val description: String, val amount: Long, val account: String, val confidence: Float)
    private data class Token(val text: String, val start: Int, val end: Int)

    fun suggest(text: String): Suggestion? {
        if (text.length > 240 || unsupported.containsMatchIn(text) || future.containsMatchIn(text) || text.any { it.isLetter() && it.code > 127 }) return null
        val tokens = tokenizer.findAll(text).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()
        if (tokens.isEmpty() || tokens.size > 48) return null
        val numbers = tokens.indices.filter { number.matches(tokens[it].text) }
        if (numbers.size != 1) return null
        val amountIndex = numbers.single()
        if (amountIndex > 0 && tokens[amountIndex - 1].text in listOf("-", "−", "+", ".", "/")) return null
        if (amountIndex + 1 < tokens.size && tokens[amountIndex + 1].text in listOf("/", "%")) return null
        val rawAmount = tokens[amountIndex].text
        if (!amountPattern.matches(rawAmount)) return null
        val amount = try { BigDecimal(rawAmount.replace(",", "")).movePointRight(2).longValueExact() }
            catch (_: ArithmeticException) { return null }
            catch (_: NumberFormatException) { return null }
        if (amount !in 1L..100_000_000L) return null
        val embedding = requireNotNull(weights["embedding.weight"])
        val unknown = mutableListOf<Int>()
        val vectors = tokens.mapIndexed { tokenIndex, token ->
            val normalized = when {
                number.matches(token.text) -> "<number>"
                token.text.first() in 'a'..'z' || token.text.first() in 'A'..'Z' -> token.text.lowercase(Locale.ROOT)
                else -> "<punct>"
            }
            val index = vocabulary[normalized] ?: 1.also { unknown += tokenIndex }
            embedding.copyOfRange(index * embeddingSize, (index + 1) * embeddingSize)
        }
        val forward = recurrent(vectors, false)
        val backward = recurrent(vectors, true)
        val encoded = tokens.indices.map { forward[it] + backward[it] }
        val pooled = FloatArray(hidden * 2)
        encoded.forEach { vector -> for (j in pooled.indices) pooled[j] += vector[j] / tokens.size }
        val intent = softmax(linear("intent", pooled, 2))[1]
        if (intent < threshold) return null
        var confidence = intent
        val tags = encoded.map { vector ->
            val probabilities = softmax(linear("tags", vector, 4))
            val tag = probabilities.indices.maxBy { probabilities[it] }
            if (tag != 0) confidence = minOf(confidence, probabilities[tag])
            tag
        }
        if (confidence < threshold || tags.indices.filter { tags[it] == 2 } != numbers) return null
        val descriptions = tags.indices.filter { tags[it] == 1 }
        val accounts = tags.indices.filter { tags[it] == 3 }
        if (!contiguous(descriptions) || !contiguous(accounts)) return null
        if (unknown.isNotEmpty()) {
            if (unknown.any { tags[it] != 1 }) return null
            val first = descriptions.first(); val last = descriptions.last()
            val anchored = first > 0 && tokens[first - 1].text.lowercase(Locale.ROOT) in setOf("for", "on", "at", "towards")
            val compact = first == 0 && last + 2 < tags.size && tags[last + 1] == 2 && tags.drop(last + 2).all { it == 3 }
            if (!anchored && !compact) return null
        }
        val account = accountNames[accounts.joinToString(" ") { tokens[it].text.lowercase(Locale.ROOT) }] ?: return null
        val mentions = tokens.mapNotNull { accountNames[it.text.lowercase(Locale.ROOT)] }.toMutableSet()
        tokens.zipWithNext().forEach { (left, right) ->
            accountNames[left.text.lowercase(Locale.ROOT) + " " + right.text.lowercase(Locale.ROOT)]?.let(mentions::add)
        }
        if (mentions.isNotEmpty() && mentions != setOf(account)) return null
        val description = text.substring(tokens[descriptions.first()].start, tokens[descriptions.last()].end).trim()
        if (description.isBlank() || description.length > 100 || description.none(Char::isLetter)) return null
        return Suggestion(description, amount, account, confidence)
    }

    private fun recurrent(vectors: List<FloatArray>, reverse: Boolean): List<FloatArray> {
        val suffix = if (reverse) "_reverse" else ""
        val wi = requireNotNull(weights["encoder.weight_ih_l0$suffix"])
        val wh = requireNotNull(weights["encoder.weight_hh_l0$suffix"])
        val bi = requireNotNull(weights["encoder.bias_ih_l0$suffix"])
        val bh = requireNotNull(weights["encoder.bias_hh_l0$suffix"])
        var state = FloatArray(hidden)
        val output = MutableList(vectors.size) { FloatArray(hidden) }
        val order = if (reverse) vectors.indices.reversed() else vectors.indices
        for (index in order) {
            val inputGates = multiply(wi, vectors[index], hidden * 3, bi)
            val hiddenGates = multiply(wh, state, hidden * 3, bh)
            val next = FloatArray(hidden)
            for (j in 0 until hidden) {
                val reset = sigmoid(inputGates[j] + hiddenGates[j])
                val update = sigmoid(inputGates[hidden + j] + hiddenGates[hidden + j])
                val candidate = tanh(inputGates[hidden * 2 + j] + reset * hiddenGates[hidden * 2 + j])
                next[j] = (1 - update) * candidate + update * state[j]
            }
            state = next
            output[index] = next
        }
        return output
    }

    private fun linear(name: String, input: FloatArray, rows: Int) =
        multiply(requireNotNull(weights["$name.weight"]), input, rows, requireNotNull(weights["$name.bias"]))

    companion object {
        private val tokenizer = Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*|[0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?|[^\\s]")
        private val number = Regex("[0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?")
        private val amountPattern = Regex("(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+|[1-9][0-9]?(?:,[0-9]{2})*,[0-9]{3})(?:\\.[0-9]{1,2})?")
        private val unsupported = Regex("\\b(?:transfer|transferred|refund|refunded|income|salary|earned|received|repay|repaid|repayment|returned|split|shared|owe|owed|owes|between|dollars|usd|eur|gbp|balance|budget|delete|remove|cancel|not|never|didn\\W?t|don\\W?t|haven\\W?t|tomorrow|plan|planning)\\b|[\u0024€£]|\\?", RegexOption.IGNORE_CASE)
        private val future = Regex("\\bi\\s+(?:will|would|might|may|could|should)\\b", RegexOption.IGNORE_CASE)
        private val accountNames = mapOf("cash" to "WALLET", "wallet" to "WALLET", "gpay" to "GPAY", "google pay" to "GPAY", "g pay" to "GPAY", "g-pay" to "GPAY", "googlepay" to "GPAY")
        private fun contiguous(indices: List<Int>) = indices.isNotEmpty() && indices == (indices.first()..indices.last()).toList()
        private fun sigmoid(value: Float) = (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()
        private fun softmax(logits: FloatArray): FloatArray {
            val maximum = logits.max()
            val values = FloatArray(logits.size) { exp((logits[it] - maximum).toDouble()).toFloat() }
            val sum = values.sum()
            return FloatArray(values.size) { values[it] / sum }
        }
        private fun multiply(matrix: FloatArray, input: FloatArray, rows: Int, bias: FloatArray): FloatArray =
            FloatArray(rows) { row ->
                var sum = bias[row]
                for (column in input.indices) sum += matrix[row * input.size + column] * input[column]
                sum
            }

        /** The bundled format is bounded and all expected tensor shapes are checked. */
        fun load(stream: InputStream): ScratchCommandModel {
            try {
                val input = DataInputStream(stream)
                require(input.readInt() == 0x54434D44 && input.readInt() == 1) { "Unsupported command model" }
                val size = input.readInt(); val embedding = input.readInt(); val hidden = input.readInt()
                require(size in 2..4096 && embedding in 1..128 && hidden in 1..128)
                val threshold = input.readFloat()
                require(threshold.isFinite() && threshold in 0f..1f)
                fun text(): String {
                    val length = input.readInt(); require(length in 0..1000)
                    return ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
                }
                val vocabulary = (0 until size).associate { text() to it }
                require(vocabulary.size == size && vocabulary["<pad>"] == 0 && vocabulary["<unknown>"] == 1)
                val expected = linkedMapOf("embedding.weight" to listOf(size, embedding))
                listOf("", "_reverse").forEach { suffix ->
                    expected["encoder.weight_ih_l0$suffix"] = listOf(hidden * 3, embedding)
                    expected["encoder.weight_hh_l0$suffix"] = listOf(hidden * 3, hidden)
                    expected["encoder.bias_ih_l0$suffix"] = listOf(hidden * 3)
                    expected["encoder.bias_hh_l0$suffix"] = listOf(hidden * 3)
                }
                listOf("tags" to 4, "intent" to 2).forEach { (name, rows) ->
                    expected["$name.weight"] = listOf(rows, hidden * 2)
                    expected["$name.bias"] = listOf(rows)
                }
                require(input.readInt() == expected.size)
                val weights = mutableMapOf<String, FloatArray>()
                repeat(expected.size) {
                    val name = text(); val rank = input.readInt()
                    val shape = requireNotNull(expected[name]); require(name !in weights && rank == shape.size)
                    shape.forEach { require(input.readInt() == it) }
                    weights[name] = FloatArray(shape.reduce(Int::times)) { input.readFloat().also { require(it.isFinite()) } }
                }
                require(input.read() == -1) { "Trailing model data" }
                return ScratchCommandModel(vocabulary, embedding, hidden, threshold, weights)
            } catch (error: java.io.IOException) {
                throw IllegalArgumentException("Invalid command model", error)
            }
        }
    }
}
