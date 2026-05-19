package com.lucastrevvos.kmonemotor.radar.fingerprint

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.vision.CropKind

data class OfferTextFingerprint(
    val fingerprintId: String,
    val ocrObservationId: String,
    val observationId: String,
    val captureRequestId: String,
    val triggerSource: TriggerSource,
    val cropKind: CropKind,
    val platformTextHint: PlatformTextHint,
    val kind: OfferTextFingerprintKind,
    val offerLikeScore: Int,
    val nonOfferScore: Int,
    val positiveSignals: List<ExtractedSignal>,
    val negativeSignals: List<ExtractedSignal>,
    val priceCandidates: List<ExtractedNumericCandidate>,
    val valuePerKmCandidates: List<ExtractedNumericCandidate>,
    val distanceCandidates: List<ExtractedNumericCandidate>,
    val timeCandidates: List<ExtractedNumericCandidate>,
    val rawTextHash: String,
    val routeTextHash: String?,
    val normalizedPreview: String,
    val reason: String,
    val createdAtMs: Long
)
