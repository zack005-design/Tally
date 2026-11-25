package com.smartexpense.domain

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Kotlin inference for Tally's tiny decoder-only transformer (trained from random weights).
 * It only writes a short step program; [AssistantPlanner] runs the steps with exact code.
 */
class TinyLanguageModel private constructor(
    val vocabulary: List<String>, /** Ordinary question words kept literal by the anonymiser. */ val words: Set<String>, private val d: Int, private val layers: Int, private val heads: Int,
    private val ff: Int, private val maxLength: Int, val threshold: Float,
    /** Per first-step answer thresholds chosen on development data; missing steps are never answered. */
    val headThresholds: Map<String, Float>, private val tensors: List<FloatArray>
) {
    private val index = vocabulary.withIndex().associate { it.value to it.index }

    /** Greedy decoding; returns the program tokens and the lowest chosen-token probability. */
    fun generate(source: List<String>, limit: Int = 40): Pair<List<String>, Float> {
        val prompt = mutableListOf(index.getValue("<bos>"))
        source.forEach { prompt += index[it] ?: index.getValue("<unk>") }
        prompt += index.getValue("<sep>")
        val cache = Cache()
        var logits = FloatArray(0)
        for (token in prompt) logits = step(token, cache)
        val out = mutableListOf<String>(); var confidence = 1f
        while (out.size < limit && cache.length < maxLength) {
            val probabilities = softmax(logits)
            val choice = probabilities.indices.maxBy { probabilities[it] }
            confidence = minOf(confidence, probabilities[choice])
            if (vocabulary[choice] == "<eos>") break
            out += vocabulary[choice]
            if (cache.length >= maxLength) break
            logits = step(choice, cache)
        }
        return out to confidence
    }

    /** Keys and values of earlier positions per layer; a causal model never changes them. */
    private inner class Cache {
        val keys = List(layers) { mutableListOf<FloatArray>() }
        val values = List(layers) { mutableListOf<FloatArray>() }
        val length get() = keys[0].size
    }

    /** Processes one token at the next position and returns next-token logits. */
    private fun step(token: Int, cache: Cache): FloatArray {
        val position = cache.length
        require(position < maxLength)
        val embed = tensors[0]; val positions = tensors[1]
        var x = FloatArray(d) { j -> embed[token * d + j] + positions[position * d + j] }
        val headSize = d / heads; val scale = 1f / sqrt(headSize.toFloat())
        for (layer in 0 until layers) {
            val w = 2 + layer * 12
            val qkv = linear(layerNorm(x, tensors[w], tensors[w + 1]), tensors[w + 2], tensors[w + 3], 3 * d)
            cache.keys[layer] += qkv.copyOfRange(d, 2 * d)
            cache.values[layer] += qkv.copyOfRange(2 * d, 3 * d)
            val keys = cache.keys[layer]; val values = cache.values[layer]
            val attended = FloatArray(d)
            for (head in 0 until heads) {
                val offset = head * headSize
                val scores = FloatArray(keys.size) { j ->
                    var s = 0f
                    for (k in 0 until headSize) s += qkv[offset + k] * keys[j][offset + k]
                    s * scale
                }
                val weights = softmax(scores)
                for (j in keys.indices) for (k in 0 until headSize) attended[offset + k] += weights[j] * values[j][offset + k]
            }
            val projected = linear(attended, tensors[w + 4], tensors[w + 5], d)
            x = FloatArray(d) { x[it] + projected[it] }
            val up = linear(layerNorm(x, tensors[w + 6], tensors[w + 7]), tensors[w + 8], tensors[w + 9], ff)
            for (k in up.indices) up[k] = gelu(up[k])
            val down = linear(up, tensors[w + 10], tensors[w + 11], d)
            x = FloatArray(d) { x[it] + down[it] }
        }
        val final = layerNorm(x, tensors[2 + layers * 12], tensors[3 + layers * 12])
        return FloatArray(vocabulary.size) { v -> var s = 0f; for (j in 0 until d) s += final[j] * embed[v * d + j]; s }
    }

    private fun linear(input: FloatArray, weight: FloatArray, bias: FloatArray, rows: Int) = FloatArray(rows) { r ->
        var s = bias[r]; val offset = r * input.size
        for (c in input.indices) s += weight[offset + c] * input[c]
        s
    }

    private fun layerNorm(input: FloatArray, gain: FloatArray, bias: FloatArray): FloatArray {
        val mean = input.average().toFloat()
        var variance = 0f; for (v in input) variance += (v - mean) * (v - mean)
        val inv = 1f / sqrt(variance / input.size + 1e-5f)
        return FloatArray(input.size) { (input[it] - mean) * inv * gain[it] + bias[it] }
    }

    private fun gelu(v: Float) = (0.5 * v * (1 + tanh(0.7978845608028654 * (v + 0.044715 * v * v * v)))).toFloat()

    private fun softmax(values: FloatArray): FloatArray {
        val m = values.max(); val e = FloatArray(values.size) { exp((values[it] - m).toDouble()).toFloat() }
        val sum = e.sum(); return FloatArray(e.size) { e[it] / sum }
    }

    companion object {
        private const val MAGIC = 0x544C4D41

        /** Bounded loader: at most 12 layers, width 512, 8M parameters; every tensor size is checked. */
        fun load(stream: InputStream): TinyLanguageModel {
            try {
                val input = DataInputStream(stream)
                require(input.readInt() == MAGIC && input.readInt() == 2) { "Unsupported assistant model" }
                val size = input.readInt(); val d = input.readInt(); val layers = input.readInt(); val heads = input.readInt(); val ff = input.readInt()
                val threshold = input.readFloat(); val maxLength = input.readInt()
                require(size in 8..4096 && d in 8..512 && layers in 1..12 && heads in 1..16 && d % heads == 0 && ff in 8..4096 && maxLength in 8..256)
                require(threshold.isFinite() && threshold in 0f..1f)
                val vocabulary = List(size) {
                    val length = input.readInt(); require(length in 1..64)
                    ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
                }
                require(vocabulary.toSet().size == size && listOf("<bos>", "<sep>", "<eos>", "<unk>").all { it in vocabulary })
                val wordCount = input.readInt(); require(wordCount in 0..size)
                val words = List(wordCount) { input.readInt().also { require(it in 0 until size) } }.map { vocabulary[it] }.toSet()
                val headCount = input.readInt(); require(headCount in 0..64)
                val headLimits = (0 until headCount).associate { input.readInt().also { require(it in 0 until size) }.let(vocabulary::get) to input.readFloat().also { require(it.isFinite()) } }
                val expected = mutableListOf(size * d, maxLength * d)
                repeat(layers) { expected += listOf(d, d, 3 * d * d, 3 * d, d * d, d, d, d, ff * d, ff, d * ff, d) }
                expected += listOf(d, d)
                require(expected.sumOf { it.toLong() } <= 8_000_000L) { "Assistant model exceeds parameter budget" }
                val tensors = expected.map { count ->
                    require(input.readInt() == count) { "Tensor size mismatch" }
                    FloatArray(count) { input.readFloat().also { v -> require(v.isFinite()) } }
                }
                require(input.read() == -1) { "Trailing assistant model data" }
                return TinyLanguageModel(vocabulary, words, d, layers, heads, ff, maxLength, threshold, headLimits, tensors)
            } catch (error: java.io.IOException) {
                throw IllegalArgumentException("Invalid assistant model", error)
            }
        }
    }
}
