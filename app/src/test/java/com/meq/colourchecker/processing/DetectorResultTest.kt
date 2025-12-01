package com.meq.colourchecker.processing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectorResultTest {

    @Test
    fun `DetectorResult with high confidence and no failure passes`() {
        // Given
        val result = DetectorResult(
            confidence = 0.9f,
            failureReason = null,
            needsInput = false
        )

        // Then
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `DetectorResult with exact threshold confidence and no failure passes`() {
        // Given
        val result = DetectorResult(
            confidence = 0.7f,
            failureReason = null,
            needsInput = false
        )

        // Then
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `DetectorResult with below threshold confidence fails`() {
        // Given
        val result = DetectorResult(
            confidence = 0.69f,
            failureReason = null,
            needsInput = false
        )

        // Then
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `DetectorResult with high confidence but has failure reason fails`() {
        // Given
        val result = DetectorResult(
            confidence = 0.95f,
            failureReason = FailureReason.Blur,
            needsInput = false
        )

        // Then
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `DetectorResult with high confidence but needs input fails`() {
        // Given
        val result = DetectorResult(
            confidence = 0.95f,
            failureReason = null,
            needsInput = true
        )

        // Then
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `DetectorResult with all conditions met passes`() {
        // Given
        val result = DetectorResult(
            confidence = 1.0f,
            failureReason = null,
            needsInput = false
        )

        // Then
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `DetectorResult with zero confidence fails`() {
        // Given
        val result = DetectorResult(
            confidence = 0.0f,
            failureReason = null,
            needsInput = false
        )

        // Then
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `FailureReason has correct display names`() {
        assertThat(FailureReason.NotFound.displayName).isEqualTo("Checker not found")
        assertThat(FailureReason.Lighting.displayName).isEqualTo("Lighting issue")
        assertThat(FailureReason.Blur.displayName).isEqualTo("Motion blur")
        assertThat(FailureReason.Partial.displayName).isEqualTo("Partial frame")
    }

    @Test
    fun `DetectorResult with each FailureReason type fails correctly`() {
        FailureReason.values().forEach { reason ->
            val result = DetectorResult(
                confidence = 0.9f,
                failureReason = reason,
                needsInput = false
            )
            assertThat(result.passed).isFalse()
        }
    }
}
