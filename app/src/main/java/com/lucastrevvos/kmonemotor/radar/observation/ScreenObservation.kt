package com.lucastrevvos.kmonemotor.radar.observation

import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.PlatformHint
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource

data class ScreenObservation(
    val id: String,
    val createdAtMs: Long,
    val requestCreatedAtMs: Long,
    val captureApprovedAtMs: Long,
    val screenshotStartedAtMs: Long,
    val screenshotFinishedAtMs: Long,
    val observationCreatedAtMs: Long,
    val capturedAtMs: Long,
    val captureRequestId: String,
    val triggerSource: TriggerSource,
    val dominantPackage: String?,
    val floatingPackage: String?,
    val floatingBounds: String?,
    val floatingKind: FloatingWindowKind,
    val screenshotWidth: Int,
    val screenshotHeight: Int,
    val captureLatencyMs: Long,
    val eventToObservationMs: Long,
    val visualPlatformHint: PlatformHint?,
    val offerCycleClassification: OfferCycleClassification?,
    val analysisEpoch: Long = 0L,
    val isManual: Boolean = false,
    val manualReason: String? = null,
    val metadata: ObservationMetadata = ObservationMetadata()
)
