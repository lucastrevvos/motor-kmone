package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.PlatformHint
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource

data class CaptureRequest(
    val id: String,
    val sourceEventAtMs: Long,
    val signalEmittedAtMs: Long,
    val createdAtMs: Long,
    val approvedAtMs: Long?,
    val triggerSource: TriggerSource,
    val platformHint: PlatformHint?,
    val priority: CapturePriority,
    val dominantPackage: String?,
    val floatingPackage: String?,
    val floatingBounds: String?,
    val floatingKind: FloatingWindowKind,
    val reason: String,
    val offerCycleClassification: OfferCycleClassification? = null,
    val analysisEpoch: Long = 0L,
    val isManual: Boolean = false,
    val manualReason: String? = null
)
