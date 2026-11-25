package com.smartexpense.domain

import java.io.DataInputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sqrt

/** On-device category suggester trained from random weights (hashed character n-grams).
 * Suggests one of the bundled category names or abstains; it never changes saved records.
 */
class CategoryModel private constructor(
    private val categories: List<String>, private val threshold: Float,
    private val weights: FloatArray, private val bias: FloatArray
) {
    data class Suggestion(val category: String, val confidence: Float)

    /** Returns the best category and its score, or null below the bundled threshold. */
    fun suggest(description: String): Suggestion? = score(description)?.takeIf { it.confidence >= threshold }

    internal fun score(description: String): Suggestion? {
        val features = features(description)
        if (features.isEmpty()) return null
        val classes = categories.size
        val weight = 1f / sqrt(features.size.toFloat())
        val logits = FloatArray(classes)
        for (bucket in features) for (c in 0 until classes) logits[c] += weights[bucket * classes + c] * weight
        for (c in 0 until classes) logits[c] += bias[c]
        val maximum = logits.max()
        val values = FloatArray(classes) { exp((logits[it] - maximum).toDouble()).toFloat() }
        val sum = values.sum()
        val best = values.indices.maxBy { values[it] }
        return Suggestion(categories[best], values[best] / sum)
    }

    companion object {
        private const val MAGIC = 0x54434154
        private const val BUCKETS = 4096
        private val word = Regex("[a-z]+")

        internal fun features(description: String): List<Int> {
            val words = word.findAll(description.lowercase(Locale.ROOT)).map { it.value }.take(12).toList()
            val names = mutableListOf<String>()
            for (w in words) {
                names += "w=$w"
                val marked = "<$w>"
                for (n in 3..5) for (i in 0..marked.length - n) names += "g=" + marked.substring(i, i + n)
            }
            words.zipWithNext().forEach { (left, right) -> names += "b=${left}_$right" }
            return names.map { (fnv1a(it) and (BUCKETS - 1).toLong()).toInt() }
        }

        private fun fnv1a(text: String): Long {
            var value = 0x811C9DC5L
            for (byte in text.toByteArray(Charsets.UTF_8)) value = ((value xor (byte.toLong() and 0xFF)) * 0x01000193L) and 0xFFFFFFFFL
            return value
        }

        /** Bounded format: fixed table size, at most 32 named classes, finite weights, no trailing data. */
        fun load(stream: InputStream): CategoryModel {
            try {
                val input = DataInputStream(stream)
                require(input.readInt() == MAGIC && input.readInt() == 1) { "Unsupported category model" }
                require(input.readInt() == BUCKETS)
                val classes = input.readInt()
                require(classes in 2..32)
                val threshold = input.readFloat()
                require(threshold.isFinite() && threshold in 0f..1f)
                val categories = List(classes) {
                    val length = input.readInt(); require(length in 1..64)
                    ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
                }
                require(categories.toSet().size == classes)
                fun floats(count: Int) = FloatArray(count) { input.readFloat().also { require(it.isFinite()) } }
                val weights = floats(BUCKETS * classes)
                val bias = floats(classes)
                require(input.read() == -1) { "Trailing category model data" }
                return CategoryModel(categories, threshold, weights, bias)
            } catch (error: java.io.IOException) {
                throw IllegalArgumentException("Invalid category model", error)
            }
        }
    }
}
