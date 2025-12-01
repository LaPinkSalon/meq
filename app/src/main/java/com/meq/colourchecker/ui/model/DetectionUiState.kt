package com.meq.colourchecker.ui.model

data class DetectionUiState(
    val status: Status = Status.Scanning,
    val hint: String = "Align the colour checker within the frame",
    val failureReason: String? = null,
    val errorMessage: String? = null,
    val isTorchOn: Boolean = false,
    val permissionsRequired: Boolean = true
) {
    enum class Status {
        Scanning,
        Passed,
        Failed,
        Error
    }
}
