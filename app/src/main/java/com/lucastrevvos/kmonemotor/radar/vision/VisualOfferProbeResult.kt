package com.lucastrevvos.kmonemotor.radar.vision

data class VisualOfferProbeResult(
    val observationId: String,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val durationMs: Long,
    val bestCandidate: CropCandidate?,
    val bestProbe: PixelProbeResult?,
    val rankedCandidates: List<CropKind>,
    val allProbeResults: List<PixelProbeResult>,
    val acceptedForOcrFuture: Boolean,
    val reason: String,
    val cropPriorityReason: String,
    val selectedByRule: String,
    val previousBestByRawScore: CropKind?,
    val visualFallbackApplied: Boolean,
    val originalBestRejectionReason: String?
)
