package com.smartexpense.domain

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Kotlin inference for Tally's tiny decoder-only transformer (trained from random weights).
 * It only writes a short step program; [AssistantPlanner] runs the steps with exact code.
 * Format 2 stores float weights; format 3 stores each weight matrix as int8 rows with one scale per row,
 * used directly (never expanded) to keep memory close to the file size.
 */
class TinyLanguageModel private constructor(
    val vocabulary: List<String>, /** Ordinary question words kept literal by the anonymiser. */ val words: Set<String>,
    private val d: Int, private val layers: Int, private val heads: Int, private val ff: Int, private val maxLength: Int,
    val threshold: Float,
    /** Per first-step answer thresholds chosen on development data; missing steps are never answered. */
    val headThresholds: Map<String, Float>, private val tensors: List<Tensor>
) {
    private val index = vocabulary.withIndex().associate { it.value to it.index }
    /** True when the model was trained with a previous-turn context (`<ctx>`), so follow-ups are supported. */
    val supportsContext: Boolean = "<ctx>" in index

    /** A weight tensor: plain floats, or int8 rows with one float scale per row. */
    private sealed interface Tensor {
        fun row(r: Int, input: FloatArray): Float
        fun at(r: Int, c: Int): Float
    }
    private class Dense(val values: FloatArray, val cols: Int) : Tensor {
        override fun row(r: Int, input: FloatArray): Float { var s = 0f; val o = r * cols; for (c in 0 until cols) s += values[o + c] * input[c]; return s }
        override fun at(r: Int, c: Int) = values[r * cols + c]
    }
    private class Quantized(val values: ByteArray, val scales: FloatArray, val cols: Int) : Tensor {
        override fun row(r: Int, input: FloatArray): Float { var s = 0f; val o = r * cols; for (c in 0 until cols) s += values[o + c] * input[c]; return s * scales[r] }
        override fun at(r: Int, c: Int) = values[r * cols + c] * scales[r]
    }

    /** Greedy decoding; returns the program tokens and the lowest chosen-token probability. */
    fun generate(source: List<String>, limit: Int = 40, context: List<String>? = null): Pair<List<String>, Float> {
        val prompt = mutableListOf(index.getValue("<bos>"))
        if (supportsContext) {
            context.orEmpty().forEach { prompt += index[it] ?: index.getValue("<unk>") }
            prompt += index.getValue("<ctx>")
        }
        source.forEach { prompt += index[it] ?: index.getValue("<unk>") }
        prompt += index.getValue("<sep>")
        if (prompt.size >= maxLength) return emptyList<String>() to 0f
        val cache = Cache()
        var logits = FloatArray(0)
        for (token in prompt) logits = step(token, cache)
        val out = mutableListOf<String>(); var confidence = 1f
        while (out.size < limit) {
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
        val embed = tensors[0]; val positions = tensors[1]
        var x = FloatArray(d) { j -> embed.at(token, j) + positions.at(position, j) }
        val headSize = d / heads; val scale = 1f / sqrt(headSize.toFloat())
        for (layer in 0 until layers) {
            val w = 2 + layer * 12
            val qkv = linear(layerNorm(x, w), tensors[w + 2], tensors[w + 3], 3 * d)
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
            val up = linear(layerNorm(x, w + 6), tensors[w + 8], tensors[w + 9], ff)
            for (k in up.indices) up[k] = gelu(up[k])
            val down = linear(up, tensors[w + 10], tensors[w + 11], d)
            x = FloatArray(d) { x[it] + down[it] }
        }
        val final = layerNorm(x, 2 + layers * 12)
        return FloatArray(vocabulary.size) { v -> embed.row(v, final) }
    }

    private fun linear(input: FloatArray, weight: Tensor, bias: Tensor, rows: Int) =
        FloatArray(rows) { r -> weight.row(r, input) + (bias as Dense).values[r] }

    private fun layerNorm(input: FloatArray, at: Int): FloatArray {
        val gain = (tensors[at] as Dense).values; val bias = (tensors[at + 1] as Dense).values
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

        /** Bounded loader for formats 2 and 3: at most 16 layers, width 768, 40M parameters; every tensor is checked. */
        fun load(stream: InputStream): TinyLanguageModel {
            try {
                val input = DataInputStream(stream)
                require(input.readInt() == MAGIC) { "Unsupported assistant model" }
                val version = input.readInt(); require(version == 2 || version == 3) { "Unsupported assistant model" }
                val size = input.readInt(); val d = input.readInt(); val layers = input.readInt(); val heads = input.readInt(); val ff = input.readInt()
                val threshold = input.readFloat(); val maxLength = input.readInt()
                require(size in 8..4096 && d in 8..768 && layers in 1..16 && heads in 1..16 && d % heads == 0 && ff in 8..4096 && maxLength in 8..256)
                require(threshold.isFinite() && threshold in 0f..1.1f)
                val vocabulary = List(size) {
                    val length = input.readInt(); require(length in 1..64)
                    ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
                }
                require(vocabulary.toSet().size == size && listOf("<bos>", "<sep>", "<eos>", "<unk>").all { it in vocabulary })
                val wordCount = input.readInt(); require(wordCount in 0..size)
                val words = List(wordCount) { input.readInt().also { require(it in 0 until size) } }.map { vocabulary[it] }.toSet()
                val headCount = input.readInt(); require(headCount in 0..64)
                val headLimits = (0 until headCount).associate { input.readInt().also { require(it in 0 until size) }.let(vocabulary::get) to input.readFloat().also { require(it.isFinite()) } }
                // (rows, cols) per tensor in file order; cols == 0 marks a vector.
                val shapes = mutableListOf(size to d, maxLength to d)
                repeat(layers) { shapes += listOf(d to 0, d to 0, 3 * d to d, 3 * d to 0, d to d, d to 0, d to 0, d to 0, ff to d, ff to 0, d to ff, d to 0) }
                shapes += listOf(d to 0, d to 0)
                require(shapes.sumOf { (r, c) -> r.toLong() * maxOf(c, 1) } <= 40_000_000L) { "Assistant model exceeds parameter budget" }
                val tensors = shapes.map { (rows, cols) ->
                    val count = input.readInt()
                    if (count == -1) {
                        require(version == 3 && cols > 0 && input.readInt() == rows && input.readInt() == cols) { "Tensor size mismatch" }
                        val scales = FloatArray(rows) { input.readFloat().also { v -> require(v.isFinite() && v >= 0f) } }
                        Quantized(ByteArray(rows * cols).also(input::readFully), scales, cols)
                    } else {
                        require(count == rows * maxOf(cols, 1)) { "Tensor size mismatch" }
                        Dense(FloatArray(count) { input.readFloat().also { v -> require(v.isFinite()) } }, maxOf(cols, 1))
                    }
                }
                require(input.read() == -1) { "Trailing assistant model data" }
                return TinyLanguageModel(vocabulary, words, d, layers, heads, ff, maxLength, threshold, headLimits, tensors)
            } catch (error: java.io.IOException) {
                throw IllegalArgumentException("Invalid assistant model", error)
            }
        }
    }
}
