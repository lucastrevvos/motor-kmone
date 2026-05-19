package com.lucastrevvos.kmonemotor.radar.dedupe

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint

data class OfferCandidateCluster(
    val clusterId: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val platform: PlatformTextHint,
    val currentKey: OfferCandidateKey,
    val bestFingerprint: OfferFingerprintDedupeInput,
    val bestQuality: OfferCandidateQuality,
    val allObservationIds: List<String>,
    val updateCount: Int,
    val manualUpdateCount: Int,
    val automaticUpdateCount: Int,
    val lastTriggerSource: TriggerSource,
    val status: String
)
