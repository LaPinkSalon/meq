package com.meq.colourchecker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meq.colourchecker.domain.AnalysisOutcome
import com.meq.colourchecker.domain.DetectionResult
import com.meq.colourchecker.domain.DetectionUseCase
import com.meq.colourchecker.processing.AnalysisFrame
import com.meq.colourchecker.ui.model.DetectionDebugInfo
import com.meq.colourchecker.ui.model.DetectionUiState
import com.meq.colourchecker.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

@HiltViewModel
class DetectionViewModel @Inject constructor(
    private val detectionUseCase: DetectionUseCase,
    private val logger: Logger
) : ViewModel() {

    private val analyzing = AtomicBoolean(false)
    private var latestFrame: AnalysisFrame? = null
    private var currentAnalysisJob: Job? = null
    private val _debug = MutableStateFlow<DetectionDebugInfo?>(null)
    val debug: StateFlow<DetectionDebugInfo?> = _debug.asStateFlow()

    private val _uiState = MutableStateFlow(DetectionUiState())
    val uiState: StateFlow<DetectionUiState> = _uiState.asStateFlow()

    fun onCameraPermissionRequested() {
        _uiState.update { it.copy(permissionsRequired = false) }
    }

    fun requireCameraPermission() {
        _uiState.update { it.copy(permissionsRequired = true) }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun onFrameCaptured(frame: AnalysisFrame) {
        latestFrame = frame
        // Only analyze if not currently analyzing
        if (analyzing.compareAndSet(false, true)) {
            analyze(frame)
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                status = DetectionUiState.Status.Scanning,
                errorMessage = null,
                failureReason = null
            )
        }
    }

    private fun analyze(frame: AnalysisFrame) {
        // Cancel any existing analysis job
        currentAnalysisJob?.cancel()

        currentAnalysisJob = viewModelScope.launch {
            try {
                logger.d("Starting frame analysis: %dx%d", frame.width, frame.height)

                _uiState.update {
                    it.copy(
                        status = DetectionUiState.Status.Scanning,
                        hint = "Analyzing frame…",
                        failureReason = null,
                        errorMessage = null
                    )
                }

                val outcome = detectionUseCase.analyze(frame)

                // Check if still active (not cancelled)
                if (!isActive) {
                    logger.d("Analysis was cancelled")
                    return@launch
                }

                // Update debug info or clear it when none was produced
                _debug.value = outcome.debug?.let { debugInfo ->
                    DetectionDebugInfo(
                        areaScore = debugInfo.areaScore,
                        aspectScore = debugInfo.aspectScore,
                        contrastScore = debugInfo.contrastScore,
                        blurScore = debugInfo.blurScore,
                        patchScore = debugInfo.patchScore,
                        avgDeltaE = debugInfo.avgDeltaE,
                        maxDeltaE = debugInfo.maxDeltaE,
                        confidence = debugInfo.confidence,
                        quad = debugInfo.quad,
                        frameWidth = debugInfo.frameWidth,
                        frameHeight = debugInfo.frameHeight,
                        rotationDegrees = debugInfo.rotationDegrees,
                        secondaryQuad = debugInfo.secondaryQuad,
                        secondaryValid = debugInfo.secondaryValid
                    )
                }

                updateStateFromResult(outcome.result)

            } catch (e: CancellationException) {
                logger.d("Analysis cancelled")
                // Don't update state on cancellation
                throw e
            } catch (e: Exception) {
                logger.e("Unexpected error in ViewModel: %s", e, e.message)
                _uiState.update {
                    it.copy(
                        status = DetectionUiState.Status.Error,
                        errorMessage = "An unexpected error occurred",
                        hint = "Please try again or restart the app",
                        failureReason = null
                    )
                }
            } finally {
                analyzing.set(false)
            }
        }
    }

    private fun updateStateFromResult(result: DetectionResult) {
        when (result) {
            is DetectionResult.Pass -> {
                logger.i("Detection passed: %s", result.summary)
                _uiState.update {
                    it.copy(
                        status = DetectionUiState.Status.Passed,
                        hint = result.summary,
                        failureReason = null,
                        errorMessage = null
                    )
                }
            }

            is DetectionResult.Fail -> {
                logger.d("Detection failed: reason=%s", result.reason)
                _uiState.update {
                    it.copy(
                        status = DetectionUiState.Status.Failed,
                        hint = result.hint,
                        failureReason = result.reason,
                        errorMessage = null
                    )
                }
            }

            is DetectionResult.NeedsInput -> {
                logger.d("Detection needs input")
                _uiState.update {
                    it.copy(
                        status = DetectionUiState.Status.Scanning,
                        hint = result.hint,
                        failureReason = null,
                        errorMessage = null
                    )
                }
            }

            is DetectionResult.Error -> {
                logger.e("Detection error: %s", result.error.cause, result.userMessage)
                _uiState.update {
                    it.copy(
                        status = DetectionUiState.Status.Error,
                        errorMessage = result.userMessage,
                        hint = result.hint,
                        failureReason = null
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentAnalysisJob?.cancel()
        logger.d("ViewModel cleared")
    }
}
