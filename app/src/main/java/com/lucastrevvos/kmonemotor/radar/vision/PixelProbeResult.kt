package com.lucastrevvos.kmonemotor.radar.vision

data class PixelProbeResult(
    val cropId: String,
    val cropKind: CropKind,
    val width: Int,
    val height: Int,
    val darkPixelRatio: Double,
    val lightPixelRatio: Double,
    val contrastScore: Double,
    val edgeDensityHint: Double,
    val dominantVisualHint: VisualPlatformHint,
    val offerLikeScore: Int,
    val rejectionReason: String?
)
