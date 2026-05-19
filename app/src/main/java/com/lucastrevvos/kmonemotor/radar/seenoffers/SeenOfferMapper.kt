package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.parser.OfferParserResult
import com.lucastrevvos.kmonemotor.radar.parser.ParsedPlatform
import java.util.UUID

class SeenOfferMapper {
    fun fromPipelineResult(
        fingerprint: OfferTextFingerprint,
        observation: OcrObservation,
        parserResult: OfferParserResult?
    ): SeenOffer? {
        if (fingerprint.kind != OfferTextFingerprintKind.OFFER_LIKE) return null
        val draft = parserResult?.draft
        val pickupDistance = draft?.pickupDistanceKm?.value ?: distanceAt(fingerprint.distanceCandidates, 0)
        val tripDistance = draft?.tripDistanceKm?.value ?: distanceAt(fingerprint.distanceCandidates, 1)
        val pickupTime = draft?.pickupTimeMinutes?.value ?: timeAt(fingerprint.timeCandidates, 0)
        val tripTime = draft?.tripTimeMinutes?.value ?: timeAt(fingerprint.timeCandidates, 1)
        val totalDistance = listOfNotNull(pickupDistance, tripDistance).takeIf { it.isNotEmpty() }?.sum()
        val totalTime = listOfNotNull(pickupTime, tripTime).takeIf { it.isNotEmpty() }?.sum()
        return SeenOffer(
            id = UUID.randomUUID().toString(),
            observationId = observation.observationId,
            platform = mapPlatform(draft?.platform, fingerprint.platformTextHint),
            sourceTrigger = observation.triggerSource.name,
            status = SeenOfferStatus.SEEN,
            price = draft?.price?.value ?: selectMaxValue(fingerprint.priceCandidates),
            valuePerKm = draft?.valuePerKm?.value ?: selectMaxValue(fingerprint.valuePerKmCandidates),
            pickupDistanceKm = pickupDistance,
            pickupTimeMin = pickupTime,
            tripDistanceKm = tripDistance,
            tripTimeMin = tripTime,
            totalDistanceKm = totalDistance,
            estimatedTotalTimeMin = totalTime,
            productName = draft?.product,
            originPreview = null,
            destinationPreview = null,
            rawTextPreview = draft?.rawTextPreview ?: observation.rawText.take(160),
            score = fingerprint.offerLikeScore,
            rawTextHash = fingerprint.rawTextHash,
            routeTextHash = fingerprint.routeTextHash,
            createdAtMs = fingerprint.createdAtMs,
            updatedAtMs = fingerprint.createdAtMs
        )
    }

    private fun mapPlatform(parsed: ParsedPlatform?, fingerprintHint: PlatformTextHint): RidePlatform {
        return when (parsed) {
            ParsedPlatform.UBER -> RidePlatform.UBER
            ParsedPlatform.NINETY_NINE -> RidePlatform.NINETY_NINE
            ParsedPlatform.UNKNOWN, null -> when (fingerprintHint) {
                PlatformTextHint.UBER -> RidePlatform.UBER
                PlatformTextHint.NINETY_NINE -> RidePlatform.NINETY_NINE
                PlatformTextHint.UNKNOWN -> RidePlatform.UNKNOWN
            }
        }
    }

    private fun selectMaxValue(candidates: List<ExtractedNumericCandidate>): Double? {
        return candidates.mapNotNull { it.normalizedValue }.maxOrNull()
    }

    private fun distanceAt(candidates: List<ExtractedNumericCandidate>, index: Int): Double? {
        val candidate = candidates.getOrNull(index) ?: return null
        val value = candidate.normalizedValue ?: return null
        return when (candidate.unit) {
            "m" -> value / 1000.0
            else -> value
        }
    }

    private fun timeAt(candidates: List<ExtractedNumericCandidate>, index: Int): Double? {
        return candidates.getOrNull(index)?.normalizedValue
    }
}
