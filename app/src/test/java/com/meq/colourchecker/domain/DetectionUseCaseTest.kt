package com.meq.colourchecker.domain

import com.google.common.truth.Truth.assertThat
import com.meq.colourchecker.processing.FailureReason
import com.meq.colourchecker.testutil.FakeColorCheckerDetector
import com.meq.colourchecker.testutil.TestFrames
import com.meq.colourchecker.util.NoOpLogger
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DetectionUseCaseTest {

    private lateinit var fakeDetector: FakeColorCheckerDetector
    private lateinit var useCase: DetectionUseCase

    @Before
    fun setup() {
        fakeDetector = FakeColorCheckerDetector()
        useCase = DetectionUseCase(fakeDetector, NoOpLogger())
    }

    @Test
    fun `analyze with high confidence returns Pass result`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult(confidence = 0.95f))

        // When
        val result = useCase.analyze(frame).result

        // Then
        assertThat(result).isInstanceOf(DetectionResult.Pass::class.java)
        val passResult = result as DetectionResult.Pass
        assertThat(passResult.summary).contains("95%")
    }

    @Test
    fun `analyze with low confidence returns Fail result`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(
                reason = FailureReason.NotFound,
                confidence = 0.3f
            )
        )

        // When
        val result = useCase.analyze(frame).result

        // Then
        assertThat(result).isInstanceOf(DetectionResult.Fail::class.java)
        val failResult = result as DetectionResult.Fail
        assertThat(failResult.reason).isEqualTo("Checker not found")
    }

    @Test
    fun `analyze with blur failure returns appropriate hint`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.Blur)
        )

        // When
        val result = useCase.analyze(frame).result

        // Then
        assertThat(result).isInstanceOf(DetectionResult.Fail::class.java)
        val failResult = result as DetectionResult.Fail
        assertThat(failResult.reason).isEqualTo("Motion blur")
        assertThat(failResult.hint).contains("Hold steady")
    }

    @Test
    fun `analyze with lighting failure returns appropriate hint`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.Lighting)
        )

        // When
        val result = useCase.analyze(frame).result

        // Then
        assertThat(result).isInstanceOf(DetectionResult.Fail::class.java)
        val failResult = result as DetectionResult.Fail
        assertThat(failResult.reason).isEqualTo("Lighting issue")
        assertThat(failResult.hint).contains("lighting")
    }

    @Test
    fun `analyze with partial failure returns appropriate hint`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.Partial)
        )

        // When
        val result = useCase.analyze(frame).result

        // Then
        assertThat(result).isInstanceOf(DetectionResult.Fail::class.java)
        val failResult = result as DetectionResult.Fail
        assertThat(failResult.reason).isEqualTo("Partial frame")
        assertThat(failResult.hint).contains("full chart")
    }

    @Test
    fun `analyze with not found failure returns appropriate hint`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.NotFound)
        )

        // When
        val result = useCase.analyze(frame).result

        // Then
        assertThat(result).isInstanceOf(DetectionResult.Fail::class.java)
        val failResult = result as DetectionResult.Fail
        assertThat(failResult.hint).contains("center of the frame")
    }

    @Test
    fun `analyze with needs input returns NeedsInput result`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(FakeColorCheckerDetector.needsInputResult())

        // When
        val result = useCase.analyze(frame).result

        // Then
        assertThat(result).isInstanceOf(DetectionResult.NeedsInput::class.java)
        val needsInputResult = result as DetectionResult.NeedsInput
        assertThat(needsInputResult.hint).isNotEmpty()
    }

    @Test
    fun `analyze with null frame creates empty frame`() = runTest {
        // Given
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult())

        // When
        useCase.analyze(frame = null)

        // Then
        val detectedFrames = fakeDetector.getDetectedFrames()
        assertThat(detectedFrames).hasSize(1)
        val frame = detectedFrames[0]
        assertThat(frame.width).isEqualTo(0)
        assertThat(frame.height).isEqualTo(0)
    }

    @Test
    fun `analyze passes frame to detector`() = runTest {
        // Given
        val frame = TestFrames.validFrame(width = 1920, height = 1080)
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult())

        // When
        useCase.analyze(frame)

        // Then
        val detectedFrames = fakeDetector.getDetectedFrames()
        assertThat(detectedFrames).hasSize(1)
        assertThat(detectedFrames[0]).isEqualTo(frame)
    }

    @Test
    fun `analyze with confidence at threshold returns Pass`() = runTest {
        // Given - exactly 0.7 confidence (threshold)
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult(confidence = 0.7f))

        // When
        val result = useCase.analyze(frame).result

        // Then
        assertThat(result).isInstanceOf(DetectionResult.Pass::class.java)
    }

    @Test
    fun `analyze with confidence just below threshold returns Fail`() = runTest {
        // Given - just below 0.7 confidence (threshold)
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(
                reason = FailureReason.NotFound,
                confidence = 0.49f
            )
        )

        // When
        val result = useCase.analyze(frame).result

        // Then
        assertThat(result).isInstanceOf(DetectionResult.Fail::class.java)
    }
}
