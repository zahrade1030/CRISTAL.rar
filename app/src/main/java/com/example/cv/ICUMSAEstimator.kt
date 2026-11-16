
package com.example.cv

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Legacy mathematical similarity utilities.
 *
 * The previous 191-dimensional ICUMSA interpolation implementation
 * is incompatible with the current 63-dimensional classification pipeline.
 *
 * Numerical ICUMSA estimation is intentionally not performed here.
 * Current classification is handled by OpenCvPipeline and SimilarityCalculator.
 */
@Deprecated(
    message = "Legacy estimator. Use OpenCvPipeline and SimilarityCalculator."
)
class ICUMSAEstimator {

    /**
     * Calculates Euclidean distance between two equal-sized vectors.
     *
     * Returns 999.0 when dimensions differ or vectors are invalid.
     */
    fun euclideanDistance(
        v1: DoubleArray,
        v2: DoubleArray
    ): Double {

        if (v1.isEmpty() || v1.size != v2.size) {
            return 999.0
        }

        var sum = 0.0

        for (i in v1.indices) {

            if (!v1[i].isFinite() || !v2[i].isFinite()) {
                return 999.0
            }

            val difference = v1[i] - v2[i]
            sum += difference * difference
        }

        return sqrt(sum)
    }

    /**
     * Calculates cosine similarity between two equal-sized vectors.
     *
     * Result is between -1.0 and 1.0.
     * Returns 0.0 when dimensions differ or a vector has zero magnitude.
     */
    fun cosineSimilarity(
        v1: DoubleArray,
        v2: DoubleArray
    ): Double {

        if (v1.isEmpty() || v1.size != v2.size) {
            return 0.0
        }

        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in v1.indices) {

            if (!v1[i].isFinite() || !v2[i].isFinite()) {
                return 0.0
            }

            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }

        if (norm1 <= 1e-18 || norm2 <= 1e-18) {
            return 0.0
        }

        val denominator = sqrt(norm1 * norm2)

        return (dot / denominator).coerceIn(-1.0, 1.0)
    }

    /**
     * Calculates Bhattacharyya distance and coefficient
     * between two equal-sized non-negative histograms.
     *
     * Returns:
     * Pair(distance, coefficient)
     */
    fun bhattacharyya(
        h1: DoubleArray,
        h2: DoubleArray
    ): Pair<Double, Double> {

        if (
            h1.isEmpty() ||
            h1.size != h2.size
        ) {
            return Pair(1.0, 0.0)
        }

        var coefficient = 0.0

        for (i in h1.indices) {

            if (
                !h1[i].isFinite() ||
                !h2[i].isFinite() ||
                h1[i] < 0.0 ||
                h2[i] < 0.0
            ) {
                return Pair(1.0, 0.0)
            }

            coefficient += sqrt(h1[i] * h2[i])
        }

        coefficient = coefficient.coerceIn(0.0, 1.0)

        val distance = sqrt(
            max(0.0, 1.0 - coefficient)
        )

        return Pair(distance, coefficient)
    }
}
