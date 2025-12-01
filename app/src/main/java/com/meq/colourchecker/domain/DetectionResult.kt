package com.meq.colourchecker.domain

sealed interface DetectionResult {
    data class Pass(val summary: String = "Colour checker detected") : DetectionResult
    data class Fail(
        val reason: String,
        val hint: String
    ) : DetectionResult

    data class NeedsInput(val hint: String = "Align the colour checker fully in frame") : DetectionResult

    data class Error(
        val error: DetectionError,
        val userMessage: String = error.getUserMessage(),
        val hint: String = error.getHint()
    ) : DetectionResult
}

data class AnalysisOutcome(
    val result: DetectionResult,
    val debug: com.meq.colourchecker.processing.DetectionDebug?
)
