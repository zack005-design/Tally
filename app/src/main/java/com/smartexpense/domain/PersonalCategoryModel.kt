package com.smartexpense.domain

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A small category classifier trained on the phone from the user's own saved expenses, including
 * categories they created. It uses the same hashed character n-gram features as [CategoryModel] and
 * multinomial logistic regression with plain stochastic gradient descent. Training is deterministic,
 * bounded (at most [maxExamples] recent entries), held only in memory and never leaves the device.
 */
class PersonalCategoryModel private constructor(
    private val categories: List<String>, private val weights: Array<FloatArray>, private val bias: FloatArray
) {
    data class Suggestion(val categoryId: String, val confidence: Float)

    /**
     * The user's category for [description], or null unless the best score reaches [threshold] and clearly leads
     * (at least [lead] times the runner-up). Small personal histories give modest scores, so the lead matters most.
     */
    fun suggest(description: String, threshold: Float = 0.45f, lead: Float = 1.5f): Suggestion? {
        val probabilities = probabilities(CategoryModel.features(description)) ?: return null
        val order = probabilities.indices.sortedByDescending { probabilities[it] }
        val best = probabilities[order[0]]; val second = probabilities[order[1]]
        return Suggestion(categories[order[0]], best).takeIf { best >= threshold && best >= second * lead }
    }

    private fun probabilities(features: List<Int>): FloatArray? {
        if (features.isEmpty()) return null
        val scale = 1f / sqrt(features.size.toFloat())
        val logits = FloatArray(categories.size) { c -> bias[c] + features.sumOf { weights[it][c].toDouble() }.toFloat() * scale }
        val maximum = logits.max()
        val values = FloatArray(logits.size) { exp((logits[it] - maximum).toDouble()).toFloat() }
        val sum = values.sum()
        return FloatArray(values.size) { values[it] / sum }
    }

    companion object {
        const val maxExamples = 3000
        private const val buckets = 4096
        private const val minimumPerCategory = 3

        /** Null when the user has fewer than two categories with at least three labelled expenses each. */
        fun train(records: List<Record>, activeCategoryIds: Set<String>, epochs: Int = 12): PersonalCategoryModel? {
            val examples = records.asSequence()
                .filter { !it.reversed && it.kind == Kind.EXPENSE && it.categoryId in activeCategoryIds && it.description.any(Char::isLetter) }
                .sortedByDescending { it.date }.take(maxExamples)
                .map { it.categoryId!! to CategoryModel.features(it.description) }.filter { it.second.isNotEmpty() }.toList()
            val counts = examples.groupingBy { it.first }.eachCount()
            val categories = counts.filterValues { it >= minimumPerCategory }.keys.sorted()
            if (categories.size < 2) return null
            val index = categories.withIndex().associate { it.value to it.index }
            val data = examples.filter { it.first in index }
            val weights = Array(buckets) { FloatArray(categories.size) }
            val bias = FloatArray(categories.size)
            val model = PersonalCategoryModel(categories, weights, bias)
            // Deterministic order: a fixed stride walk instead of random shuffling.
            val stride = generateSequence(data.size / 2 + 1) { it + 1 }.first { gcd(it, data.size) == 1 }
            repeat(epochs) { epoch ->
                val rate = 0.5f / (1 + epoch)
                var position = epoch % data.size
                repeat(data.size) {
                    val (label, features) = data[position]
                    position = (position + stride) % data.size
                    val p = model.probabilities(features)!!
                    val scale = 1f / sqrt(features.size.toFloat())
                    val target = index.getValue(label)
                    for (c in categories.indices) {
                        val gradient = p[c] - if (c == target) 1f else 0f
                        bias[c] -= rate * gradient * 0.1f
                        for (f in features) weights[f][c] -= rate * gradient * scale
                    }
                }
            }
            return model
        }

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
