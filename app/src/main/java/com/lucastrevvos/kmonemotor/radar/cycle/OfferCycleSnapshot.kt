package com.lucastrevvos.kmonemotor.radar.cycle

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource

data class OfferCycleSnapshot(
    val triggerSource: TriggerSource,
    val createdAtMs: Long,
    val dominantPackage: String?,
    val floatingPackage: String?,
    val nodeTreePackage: String?,
    val nodeCount: Int,
    val visibleTextNodeCount: Int,
    val numericTextNodeCount: Int,
    val buttonLikeNodeCount: Int,
    val knownStateTexts: List<String>,
    val matchedConditions: List<String>,
    val captureRequestId: String?
)
