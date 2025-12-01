package com.meq.colourchecker.testutil

import com.meq.colourchecker.processing.AnalysisFrame
import com.meq.colourchecker.processing.ColorCheckerDetector
import com.meq.colourchecker.processing.DetectorResult
import com.meq.colourchecker.processing.DetectionOutput
import com.meq.colourchecker.processing.FailureReason

/**
 * Fake implementation of ColorCheckerDetector for testing.
 * Allows tests to control detector behavior without OpenCV dependencies.
 */
class FakeColorCheckerDetector : ColorCheckerDetector {

    private var nextResult: DetectionOutput? = null
    private val detectedFrames = mutableListOf<AnalysisFrame>()

    /**
     * Set the result that will be returned on next detect() call.
     */
    fun setNextResult(result: DetectionOutput) {
        nextResult = result
    }

    /**
     * Get all frames that were passed to detect().
     */
    fun getDetectedFrames(): List<AnalysisFrame> = detectedFrames.toList()

    /**
     * Clear all recorded frames.
     */
    fun clearFrames() {
        detectedFrames.clear()
    }

    override suspend fun detect(frame: AnalysisFrame): DetectionOutput {
        detectedFrames.add(frame)
        return nextResult ?: DetectionOutput(
            result = DetectorResult(
                confidence = 0.0f,
                failureReason = FailureReason.NotFound,
                needsInput = false
            ),
            metrics = null
        )
    }

    companion object {
        /**
         * Create a successful detection result.
         */
        fun passResult(confidence: Float = 0.95f): DetectionOutput {
            return DetectionOutput(
                result = DetectorResult(
                    confidence = confidence,
                    failureReason = null,
                    needsInput = false
                ),
                metrics = null
            )
        }

        /**
         * Create a failed detection result.
         */
        fun failResult(
            reason: FailureReason,
            confidence: Float = 0.2f
        ): DetectionOutput {
            return DetectionOutput(
                result = DetectorResult(
                    confidence = confidence,
                    failureReason = reason,
                    needsInput = false
                ),
                metrics = null
            )
        }

        /**
         * Create a needs-input result.
         */
        fun needsInputResult(): DetectionOutput {
            return DetectionOutput(
                result = DetectorResult(
                    confidence = 0.0f,
                    failureReason = null,
                    needsInput = true
                ),
                metrics = null
            )
        }
    }
}
