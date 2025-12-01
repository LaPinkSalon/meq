package com.meq.colourchecker.processing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalysisFrameTest {

    @Test
    fun `AnalysisFrame with default values creates empty frame`() {
        // When
        val frame = AnalysisFrame()

        // Then
        assertThat(frame.width).isEqualTo(0)
        assertThat(frame.height).isEqualTo(0)
        assertThat(frame.rgbaPixels).isNull()
        assertThat(frame.timestamp).isGreaterThan(0L)
    }

    @Test
    fun `AnalysisFrame with custom values stores them correctly`() {
        // Given
        val width = 640
        val height = 480
        val pixels = ByteArray(width * height * 4) { it.toByte() }
        val timestamp = 12345L

        // When
        val frame = AnalysisFrame(
            timestamp = timestamp,
            width = width,
            height = height,
            rgbaPixels = pixels
        )

        // Then
        assertThat(frame.width).isEqualTo(width)
        assertThat(frame.height).isEqualTo(height)
        assertThat(frame.rgbaPixels).isEqualTo(pixels)
        assertThat(frame.timestamp).isEqualTo(timestamp)
    }

    @Test
    fun `AnalysisFrame pixel array size should match dimensions`() {
        // Given
        val width = 100
        val height = 50
        val expectedSize = width * height * 4 // RGBA = 4 bytes per pixel

        // When
        val pixels = ByteArray(expectedSize)
        val frame = AnalysisFrame(
            width = width,
            height = height,
            rgbaPixels = pixels
        )

        // Then
        assertThat(frame.rgbaPixels?.size).isEqualTo(expectedSize)
    }

    @Test
    fun `AnalysisFrame timestamp defaults to current time`() {
        // Given
        val beforeCreation = System.currentTimeMillis()

        // When
        val frame = AnalysisFrame()
        val afterCreation = System.currentTimeMillis()

        // Then
        assertThat(frame.timestamp).isAtLeast(beforeCreation.toLong())
        assertThat(frame.timestamp).isAtMost(afterCreation.toLong())
    }

    @Test
    fun `AnalysisFrame can be created with null pixels`() {
        // When
        val frame = AnalysisFrame(
            width = 640,
            height = 480,
            rgbaPixels = null
        )

        // Then
        assertThat(frame.rgbaPixels).isNull()
    }
}
