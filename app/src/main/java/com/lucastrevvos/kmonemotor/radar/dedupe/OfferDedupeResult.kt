package com.lucastrevvos.kmonemotor.radar.dedupe

import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint

data class OfferDedupeResult(
    val decision: OfferDedupeDecision,
    val clusterId: String?,
    val qualityScore: Int?,
    val reason: String,
    val matchedPreviousObservationId: String? = null,
    val isBestForCluster: Boolean = false,
    val activeClusterCount: Int = 0,
    val bestOfferPreview: String? = null,
    val bestOfferMainPrice: Double? = null,
    val bestOfferPlatform: PlatformTextHint? = null,
    val fingerprintToDedupeMs: Long? = null,
    val dedupeDurationMs: Long? = null
)
