package com.meq.colourchecker.domain

import com.meq.colourchecker.processing.AnalysisFrame
import com.meq.colourchecker.processing.ColorCheckerDetector
import com.meq.colourchecker.processing.DetectionDebug
import com.meq.colourchecker.processing.DetectorResult
import com.meq.colourchecker.processing.FailureReason
import com.meq.colourchecker.util.Logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class DetectionUseCase @Inject constructor(
    private val detector: ColorCheckerDetector,
    private val logger: Logger
) {
    companion object {
        private const val DETECTION_TIMEOUT_MS = 10_000L // 10 seconds
    }

    suspend fun analyze(frame: AnalysisFrame? = null): AnalysisOutcome {
        return try {
            logger.d("Starting detection analysis")

            // Validate frame
            val validFrame = frame ?: AnalysisFrame()
            if (!isValidFrame(validFrame)) {
                logger.w(
                    "Invalid frame data: width=%d, height=%d, hasPixels=%s",
                    validFrame.width,
                    validFrame.height,
                    validFrame.rgbaPixels != null
                )
                return AnalysisOutcome(
                    DetectionResult.Error(
                        DetectionError.InvalidFrameError(
                            message = "Frame validation failed: ${getFrameValidationError(validFrame)}"
                        )
                    ),
                    debug = null
                )
            }

            // Run detection with timeout
            val output = withTimeout(DETECTION_TIMEOUT_MS) {
                detector.detect(validFrame)
            }

            logger.d(
                "Detection complete: confidence=%.2f, passed=%s",
                output.result.confidence,
                output.result.passed
            )

            val debugInfo = output.metrics?.let { metrics ->
                DetectionDebug(
                    areaScore = metrics.areaScore,
                    aspectScore = metrics.aspectScore,
                    contrastScore = metrics.contrastScore,
                    blurScore = metrics.blurScore,
                    patchScore = metrics.colorScore,
                    avgDeltaE = metrics.avgDeltaE,
                    maxDeltaE = metrics.maxDeltaE,
                    confidence = metrics.confidence,
                    quad = metrics.quad,
                    frameWidth = metrics.frameWidth,
                    frameHeight = metrics.frameHeight,
                    rotationDegrees = metrics.rotationDegrees,
                    secondaryQuad = metrics.secondaryQuad,
                    secondaryValid = metrics.secondaryValid
                )
            }

            AnalysisOutcome(mapResult(output.result), debugInfo)
        } catch (e: TimeoutCancellationException) {
            logger.e("Detection timed out after %d ms", e, DETECTION_TIMEOUT_MS)
            AnalysisOutcome(
                DetectionResult.Error(
                    DetectionError.TimeoutError(cause = e)
                ),
                debug = null
            )
        } catch (e: OutOfMemoryError) {
            logger.e("Out of memory during detection", e)
            AnalysisOutcome(
                DetectionResult.Error(
                    DetectionError.OutOfMemoryError(cause = e)
                ),
                debug = null
            )
        } catch (e: Exception) {
            logger.e("Unexpected error during detection: %s", e, e.message)

            // Try to categorize the error based on message/type
            val error = categorizeError(e)
            AnalysisOutcome(DetectionResult.Error(error), debug = null)
        }
    }

    private fun isValidFrame(frame: AnalysisFrame): Boolean {
        // Allow empty frames for initial state
        if (frame.width == 0 && frame.height == 0 && frame.rgbaPixels == null) {
            return true
        }

        // Validate dimensions
        if (frame.width <= 0 || frame.height <= 0) {
            return false
        }

        // Validate pixel data
        if (frame.rgbaPixels == null) {
            return false
        }

        // Validate pixel array size
        val expectedSize = frame.width * frame.height * 4
        if (frame.rgbaPixels.size < expectedSize) {
            return false
        }

        return true
    }

    private fun getFrameValidationError(frame: AnalysisFrame): String {
        return when {
            frame.width <= 0 || frame.height <= 0 -> "Invalid dimensions (${frame.width}x${frame.height})"
            frame.rgbaPixels == null -> "No pixel data"
            frame.rgbaPixels.size < frame.width * frame.height * 4 ->
                "Insufficient pixel data (${frame.rgbaPixels.size} bytes, expected ${frame.width * frame.height * 4})"
            else -> "Unknown validation error"
        }
    }

    private fun categorizeError(exception: Exception): DetectionError {
        val message = exception.message?.lowercase() ?: ""

        return when {
            message.contains("opencv") || message.contains("javacpp") -> {
                DetectionError.OpenCvInitializationError(
                    message = "OpenCV error: ${exception.message}",
                    cause = exception
                )
            }
            message.contains("memory") -> {
                DetectionError.OutOfMemoryError(cause = exception)
            }
            message.contains("mat") || message.contains("image") || message.contains("contour") -> {
                DetectionError.ImageProcessingError(
                    message = "Image processing failed: ${exception.message}",
                    cause = exception
                )
            }
            else -> {
                DetectionError.UnknownError(
                    message = exception.message ?: "Unknown error",
                    cause = exception
                )
            }
        }
    }

    private fun mapResult(result: DetectorResult): DetectionResult {
        if (result.needsInput) {
            return DetectionResult.NeedsInput()
        }
        if (result.passed) {
            return DetectionResult.Pass("Colour checker detected with confidence ${(result.confidence * 100).toInt()}%")
        }

        val reason = result.failureReason ?: FailureReason.NotFound
        val hint = when (reason) {
            FailureReason.NotFound -> "Move the colour checker to the center of the frame."
            FailureReason.Lighting -> "Improve lighting or reduce glare."
            FailureReason.Blur -> "Hold steady; image appears blurry."
            FailureReason.Partial -> "Ensure the full chart is visible and flat."
        }
        return DetectionResult.Fail(
            reason = reason.displayName,
            hint = hint
        )
    }
}
