package com.meq.colourchecker.processing

interface ColorCheckerDetector {
    suspend fun detect(frame: AnalysisFrame): DetectionOutput
}

data class AnalysisFrame(
    val timestamp: Long = System.currentTimeMillis(),
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    /**
     * Raw RGBA pixels (width * height * 4) in row-major order.
     */
    val rgbaPixels: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnalysisFrame) return false

        return timestamp == other.timestamp &&
            width == other.width &&
            height == other.height &&
            rotationDegrees == other.rotationDegrees &&
            when {
                rgbaPixels === other.rgbaPixels -> true
                rgbaPixels == null || other.rgbaPixels == null -> false
                else -> rgbaPixels.contentEquals(other.rgbaPixels)
            }
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + rotationDegrees
        result = 31 * result + (rgbaPixels?.contentHashCode() ?: 0)
        return result
    }
}

data class DetectorResult(
    val confidence: Float,
    val failureReason: FailureReason? = null,
    val needsInput: Boolean = false
) {
    val passed: Boolean = confidence >= 0.70f && failureReason == null && !needsInput
}

enum class FailureReason(val displayName: String) {
    NotFound("Checker not found"),
    Lighting("Lighting issue"),
    Blur("Motion blur"),
    Partial("Partial frame")
}

data class DetectionDebug(
    val areaScore: Double,
    val aspectScore: Double,
    val contrastScore: Double,
    val blurScore: Double,
    val patchScore: Double,
    val avgDeltaE: Double?,
    val maxDeltaE: Double?,
    val confidence: Float,
    val quad: List<Point> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val rotationDegrees: Int = 0,
    val secondaryQuad: List<Point> = emptyList(),
    val secondaryValid: Boolean = false
)

data class DetectionMetrics(
    val areaScore: Double,
    val aspectScore: Double,
    val contrastScore: Double,
    val blurScore: Double,
    val colorScore: Double,
    val avgDeltaE: Double?,
    val maxDeltaE: Double?,
    val confidence: Float,
    val quad: List<Point>,
    val frameWidth: Int,
    val frameHeight: Int,
    val rotationDegrees: Int = 0,
    val secondaryQuad: List<Point> = emptyList(),
    val secondaryValid: Boolean = false
)

data class Point(val x: Float, val y: Float)
data class PatchScores(val avgDeltaE: Double, val maxDeltaE: Double)
data class BoundingBox(val width: Double, val height: Double)

data class DetectionOutput(
    val result: DetectorResult,
    val metrics: DetectionMetrics? = null
)
