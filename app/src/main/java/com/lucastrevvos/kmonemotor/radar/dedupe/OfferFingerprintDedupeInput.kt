package com.lucastrevvos.kmonemotor.radar.dedupe

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.vision.CropKind

data class OfferFingerprintDedupeInput(
    val observationId: String,
    val triggerSource: TriggerSource,
    val platformHint: PlatformTextHint,
    val fingerprintKind: OfferTextFingerprintKind,
    val rawTextHash: String?,
    val routeTextHash: String?,
    val prices: List<ExtractedNumericCandidate>,
    val valuePerKm: List<ExtractedNumericCandidate>,
    val distances: List<ExtractedNumericCandidate>,
    val times: List<ExtractedNumericCandidate>,
    val offerLikeScore: Int,
    val nonOfferScore: Int,
    val cropKind: CropKind?,
    val isManual: Boolean,
    val analysisEpoch: Long?,
    val capturedAtMs: Long,
    val fingerprintCreatedAtMs: Long,
    val rawTextPreview: String?
)
