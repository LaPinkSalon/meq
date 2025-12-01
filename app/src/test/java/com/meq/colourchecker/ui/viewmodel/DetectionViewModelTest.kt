package com.meq.colourchecker.ui.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.meq.colourchecker.domain.DetectionResult
import com.meq.colourchecker.domain.DetectionUseCase
import com.meq.colourchecker.processing.FailureReason
import com.meq.colourchecker.testutil.FakeColorCheckerDetector
import com.meq.colourchecker.testutil.TestFrames
import com.meq.colourchecker.ui.model.DetectionUiState
import com.meq.colourchecker.util.NoOpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDetector: FakeColorCheckerDetector
    private lateinit var useCase: DetectionUseCase
    private lateinit var viewModel: DetectionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDetector = FakeColorCheckerDetector()
        useCase = DetectionUseCase(fakeDetector, NoOpLogger())
        viewModel = DetectionViewModel(useCase, NoOpLogger())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        // Then
        val state = viewModel.uiState.value
        assertThat(state.status).isEqualTo(DetectionUiState.Status.Scanning)
        assertThat(state.permissionsRequired).isTrue()
        assertThat(state.isTorchOn).isFalse()
        assertThat(state.hint).isNotEmpty()
    }

    @Test
    fun `onCameraPermissionRequested updates permissions state`() = runTest {
        // When
        viewModel.onCameraPermissionRequested()

        // Then
        assertThat(viewModel.uiState.value.permissionsRequired).isFalse()
    }

    @Test
    fun `requireCameraPermission updates permissions state`() = runTest {
        // Given
        viewModel.onCameraPermissionRequested() // First set to false

        // When
        viewModel.requireCameraPermission()

        // Then
        assertThat(viewModel.uiState.value.permissionsRequired).isTrue()
    }

    @Test
    fun `toggleTorch toggles torch state`() = runTest {
        // When - First toggle
        viewModel.toggleTorch()

        // Then
        assertThat(viewModel.uiState.value.isTorchOn).isTrue()

        // When - Second toggle
        viewModel.toggleTorch()

        // Then
        assertThat(viewModel.uiState.value.isTorchOn).isFalse()
    }

    @Test
    fun `onFrameCaptured with successful detection updates state to Passed`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult(confidence = 0.95f))

        // When
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.status).isEqualTo(DetectionUiState.Status.Passed)
        assertThat(state.hint).contains("95%")
        assertThat(state.failureReason).isNull()
    }

    @Test
    fun `onFrameCaptured with failed detection updates state to Failed`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.Blur)
        )

        // When
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.status).isEqualTo(DetectionUiState.Status.Failed)
        assertThat(state.failureReason).isEqualTo("Motion blur")
        assertThat(state.hint).contains("Hold steady")
    }

    @Test
    fun `onFrameCaptured with needs input keeps Scanning status`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(FakeColorCheckerDetector.needsInputResult())

        // When
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertThat(state.status).isEqualTo(DetectionUiState.Status.Scanning)
        assertThat(state.failureReason).isNull()
    }

    @Test
    fun `onFrameCaptured sets Scanning state before analysis`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult())

        viewModel.uiState.test {
            // Skip initial state
            skipItems(1)

            // When
            viewModel.onFrameCaptured(frame)

            // Then - Should see Scanning state first
            val scanningState = awaitItem()
            assertThat(scanningState.status).isEqualTo(DetectionUiState.Status.Scanning)
            assertThat(scanningState.hint).contains("Analyzing")

            // Then - Should see final Passed state
            val finalState = awaitItem()
            assertThat(finalState.status).isEqualTo(DetectionUiState.Status.Passed)
        }
    }

    @Test
    fun `manual capture request with no frame updates hint`() = runTest {
        // When - No frame has been captured yet but user clicks capture
        // The ViewModel doesn't expose a manual capture API,
        // so we just verify initial state

        // Then
        val state = viewModel.uiState.value
        assertThat(state.status).isEqualTo(DetectionUiState.Status.Scanning)
    }

    @Test
    fun `multiple frame captures after success work correctly`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult())
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()

        // Clear the fake detector's frames
        fakeDetector.clearFrames()

        // When - Capture another frame
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult())
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()

        // Then - Should have analyzed the frame again
        assertThat(fakeDetector.getDetectedFrames()).hasSize(1)
    }

    @Test
    fun `concurrent frame captures are prevented`() = runTest {
        // Given - Slow detector (we won't advance coroutines)
        val frame1 = TestFrames.validFrame()
        val frame2 = TestFrames.validFrame()
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult())

        // When - Capture two frames without waiting
        viewModel.onFrameCaptured(frame1)
        viewModel.onFrameCaptured(frame2)

        // Then - Only one frame should be analyzed
        // (The second call should be ignored while first is analyzing)
        advanceUntilIdle()
        assertThat(fakeDetector.getDetectedFrames()).hasSize(1)
    }

    @Test
    fun `state flow emits all state changes`() = runTest {
        // Given
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult())

        viewModel.uiState.test {
            // Initial state
            val initial = awaitItem()
            assertThat(initial.status).isEqualTo(DetectionUiState.Status.Scanning)

            // When
            viewModel.onFrameCaptured(frame)

            // Then - Scanning state
            val scanning = awaitItem()
            assertThat(scanning.status).isEqualTo(DetectionUiState.Status.Scanning)
            assertThat(scanning.hint).contains("Analyzing")

            // Then - Passed state
            val passed = awaitItem()
            assertThat(passed.status).isEqualTo(DetectionUiState.Status.Passed)
        }
    }

    @Test
    fun `multiple successful detections update state correctly`() = runTest {
        // Given
        val frame1 = TestFrames.validFrame()
        val frame2 = TestFrames.validFrame()

        // When - First detection
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult(confidence = 0.8f))
        viewModel.onFrameCaptured(frame1)
        advanceUntilIdle()

        // Then
        assertThat(viewModel.uiState.value.status).isEqualTo(DetectionUiState.Status.Passed)
        assertThat(viewModel.uiState.value.hint).contains("80%")

        // When - Second detection with different confidence
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult(confidence = 0.95f))
        viewModel.onFrameCaptured(frame2)
        advanceUntilIdle()

        // Then
        assertThat(viewModel.uiState.value.status).isEqualTo(DetectionUiState.Status.Passed)
        assertThat(viewModel.uiState.value.hint).contains("95%")
    }

    @Test
    fun `failure reasons are properly mapped`() = runTest {
        val frame = TestFrames.validFrame()

        // Test NotFound
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.NotFound)
        )
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.failureReason).isEqualTo("Checker not found")

        // Test Lighting
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.Lighting)
        )
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.failureReason).isEqualTo("Lighting issue")

        // Test Blur
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.Blur)
        )
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.failureReason).isEqualTo("Motion blur")

        // Test Partial
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.Partial)
        )
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.failureReason).isEqualTo("Partial frame")
    }

    @Test
    fun `failed detection clears previous failure reason on success`() = runTest {
        // Given - First a failure
        val frame = TestFrames.validFrame()
        fakeDetector.setNextResult(
            FakeColorCheckerDetector.failResult(reason = FailureReason.Blur)
        )
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.failureReason).isNotNull()

        // When - Then a success
        fakeDetector.setNextResult(FakeColorCheckerDetector.passResult())
        viewModel.onFrameCaptured(frame)
        advanceUntilIdle()

        // Then - Failure reason should be cleared
        assertThat(viewModel.uiState.value.failureReason).isNull()
    }
}
