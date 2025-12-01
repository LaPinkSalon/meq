package com.meq.colourchecker.testutil

import com.meq.colourchecker.processing.AnalysisFrame

/**
 * Test data factory for creating test AnalysisFrame instances.
 */
object TestFrames {

    /**
     * Create a valid test frame with specified dimensions.
     */
    fun validFrame(
        width: Int = 640,
        height: Int = 480,
        timestamp: Long = System.currentTimeMillis()
    ): AnalysisFrame {
        val pixels = ByteArray(width * height * 4) { 0xFF.toByte() }
        return AnalysisFrame(
            timestamp = timestamp,
            width = width,
            height = height,
            rgbaPixels = pixels
        )
    }

    /**
     * Create an empty frame (no pixel data).
     */
    fun emptyFrame(): AnalysisFrame {
        return AnalysisFrame(
            timestamp = System.currentTimeMillis(),
            width = 0,
            height = 0,
            rgbaPixels = null
        )
    }

    /**
     * Create a frame with invalid dimensions but valid pixels.
     */
    fun invalidDimensionsFrame(): AnalysisFrame {
        return AnalysisFrame(
            timestamp = System.currentTimeMillis(),
            width = -1,
            height = -1,
            rgbaPixels = ByteArray(100)
        )
    }
}
