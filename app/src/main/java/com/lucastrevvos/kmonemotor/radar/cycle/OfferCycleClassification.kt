package com.lucastrevvos.kmonemotor.radar.cycle

data class OfferCycleClassification(
    val kind: OfferCycleKind,
    val cycleId: String,
    val previousCycleId: String?,
    val reason: String,
    val timeSincePreviousMs: Long?,
    val shouldPreferForOcr: Boolean
)
