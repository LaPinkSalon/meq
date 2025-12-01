package com.meq.colourchecker.processing

import javax.inject.Inject
import kotlin.math.abs

/**
 * Turns detector measurements into metrics and a DetectorResult.
 * Heuristics blend geometry, contrast, blur, and color into a confidence score,
 * and enforce that both panels are present/valid.
 */
class DetectionScorer @Inject constructor() {
    fun score(
        frameWidth: Int,
        frameHeight: Int,
        bounds: BoundingBox,
        laplacianVariance: Double,
        contrastScore: Double,
        colorScores: PatchScores,
        expectedAspect: Double = 1.5,
        blurRef: Double = 120.0,
        passAvgDeltaE: Double = 24.0,
        passMaxDeltaE: Double = 40.0,
        quad: List<Point> = emptyList(),
        rotationDegrees: Int = 0,
        secondaryQuad: List<Point> = emptyList(),
        secondaryValid: Boolean = true
    ): DetectionOutput {
        val areaScore = (bounds.width * bounds.height) / (frameWidth.toDouble() * frameHeight)
        val aspect = bounds.width / bounds.height.coerceAtLeast(1.0)
        val aspectScore = (1.0 - abs(aspect - expectedAspect) / expectedAspect).coerceIn(0.0, 1.0)
        // Laplacian variance tends to ~100-150 for sharp MCC at phone distances.
        val blurScore = (laplacianVariance / blurRef).coerceIn(0.0, 1.0)

        val colorScore = run {
            val avgScore = (1.0 - colorScores.avgDeltaE / passAvgDeltaE).coerceIn(0.0, 1.0)
            val maxScore = (1.0 - colorScores.maxDeltaE / passMaxDeltaE).coerceIn(0.0, 1.0)
            (avgScore * 0.7 + maxScore * 0.3).coerceIn(0.0, 1.0)
        }

        // Normalize area score relative to a larger expected footprint to help flat images.
        val boostedArea = (areaScore * 8.0).coerceIn(0.0, 1.0)

        val confidence = (
            boostedArea * 0.7 +
                aspectScore * 0.1 +
                contrastScore * 0.05 +
                blurScore * 0.05 +
                colorScore * 0.1
            ).toFloat()

        val failure = when {
            blurScore < 0.15 -> FailureReason.Blur // motion or defocus
            areaScore < 0.005 -> FailureReason.Partial // too small in frame
            contrastScore < 0.08 -> FailureReason.Lighting // washed out / glare
            colorScores.avgDeltaE > passAvgDeltaE * 1.3 -> FailureReason.NotFound // poor color match (blocked/occluded)
            else -> null
        }

        val result = DetectorResult(
            confidence = confidence,
            failureReason = failure,
            needsInput = failure == FailureReason.NotFound
        )

        val metrics = DetectionMetrics(
            areaScore = areaScore,
            aspectScore = aspectScore,
            contrastScore = contrastScore,
            blurScore = blurScore,
            colorScore = colorScore,
            avgDeltaE = colorScores.avgDeltaE,
            maxDeltaE = colorScores.maxDeltaE,
            confidence = confidence,
            quad = quad,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            rotationDegrees = rotationDegrees,
            secondaryQuad = secondaryQuad,
            secondaryValid = secondaryValid
        )

        return DetectionOutput(result = result, metrics = metrics)
    }
}
