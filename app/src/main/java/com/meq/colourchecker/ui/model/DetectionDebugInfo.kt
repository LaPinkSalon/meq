package com.meq.colourchecker.ui.model

data class DetectionDebugInfo(
    val areaScore: Double,
    val aspectScore: Double,
    val contrastScore: Double,
    val blurScore: Double,
    val patchScore: Double,
    val avgDeltaE: Double?,
    val maxDeltaE: Double?,
    val confidence: Float,
    val quad: List<com.meq.colourchecker.processing.Point> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val rotationDegrees: Int = 0,
    val secondaryQuad: List<com.meq.colourchecker.processing.Point> = emptyList(),
    val secondaryValid: Boolean = false
)
