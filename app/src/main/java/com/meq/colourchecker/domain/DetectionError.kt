package com.meq.colourchecker.domain

/**
 * Sealed hierarchy representing all possible errors during detection.
 * Provides type-safe error handling and user-facing error messages.
 */
sealed class DetectionError(
    open val message: String,
    open val cause: Throwable? = null
) {
    /**
     * OpenCV native library failed to load or initialize.
     */
    data class OpenCvInitializationError(
        override val message: String = "Failed to initialize image processing library",
        override val cause: Throwable? = null
    ) : DetectionError(message, cause)

    /**
     * Error during image processing operations (edge detection, contour finding, etc.).
     */
    data class ImageProcessingError(
        override val message: String = "Error processing image",
        override val cause: Throwable? = null
    ) : DetectionError(message, cause)

    /**
     * Invalid frame data (null pixels, mismatched dimensions, corrupted data).
     */
    data class InvalidFrameError(
        override val message: String = "Invalid camera frame data",
        override val cause: Throwable? = null
    ) : DetectionError(message, cause)

    /**
     * Out of memory error during processing.
     */
    data class OutOfMemoryError(
        override val message: String = "Insufficient memory for processing",
        override val cause: Throwable? = null
    ) : DetectionError(message, cause)

    /**
     * Timeout waiting for detection to complete.
     */
    data class TimeoutError(
        override val message: String = "Detection timed out",
        override val cause: Throwable? = null
    ) : DetectionError(message, cause)

    /**
     * Generic unexpected error.
     */
    data class UnknownError(
        override val message: String = "An unexpected error occurred",
        override val cause: Throwable? = null
    ) : DetectionError(message, cause)

    /**
     * Get user-facing error message.
     */
    fun getUserMessage(): String = when (this) {
        is OpenCvInitializationError -> "Camera processing unavailable. Please restart the app."
        is ImageProcessingError -> "Failed to analyze image. Please try again."
        is InvalidFrameError -> "Camera error. Please check camera permissions."
        is OutOfMemoryError -> "Device memory low. Close other apps and try again."
        is TimeoutError -> "Analysis taking too long. Please try again with better lighting."
        is UnknownError -> "Something went wrong. Please try again."
    }

    /**
     * Get user-facing hint for fixing the error.
     */
    fun getHint(): String = when (this) {
        is OpenCvInitializationError -> "Restart the app or reinstall if problem persists"
        is ImageProcessingError -> "Ensure the colour checker is clearly visible and well-lit"
        is InvalidFrameError -> "Check that camera permissions are granted"
        is OutOfMemoryError -> "Close other apps to free up memory"
        is TimeoutError -> "Improve lighting and try again"
        is UnknownError -> "Restart the app if the problem continues"
    }
}
