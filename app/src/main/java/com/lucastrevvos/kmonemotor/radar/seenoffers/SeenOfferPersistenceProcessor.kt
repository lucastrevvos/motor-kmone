package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.parser.OfferParserResult

class SeenOfferPersistenceProcessor(
    private val repository: SeenOfferRepository,
    private val mapper: SeenOfferMapper = SeenOfferMapper(),
    private val sanitizer: SeenOfferSanitizer = SeenOfferSanitizer()
) {
    fun process(
        fingerprint: OfferTextFingerprint,
        observation: OcrObservation,
        parserResult: OfferParserResult?
    ): SeenOfferPersistenceResult {
        if (fingerprint.kind != OfferTextFingerprintKind.OFFER_LIKE) {
            return SeenOfferPersistenceResult(
                attempted = false,
                persisted = false,
                reason = "fingerprint_not_offer_like"
            )
        }
        val offer = mapper.fromPipelineResult(fingerprint, observation, parserResult)
            ?: return SeenOfferPersistenceResult(
                attempted = true,
                persisted = false,
                reason = "mapping_failed"
            )
        val sanitization = sanitizer.sanitize(offer)
        if (!sanitization.shouldPersist || sanitization.sanitizedOffer == null) {
            RadarLogger.w(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_SANITIZATION_REJECTED",
                "observationId" to observation.observationId,
                "reason" to sanitization.reason,
                "price" to offer.price,
                "tripDistanceKm" to offer.tripDistanceKm,
                "totalDistanceKm" to offer.totalDistanceKm
            )
            return SeenOfferPersistenceResult(
                attempted = true,
                persisted = false,
                reason = sanitization.reason
            )
        }
        val sanitizedOffer = sanitization.sanitizedOffer
        if (sanitization.reason == "adjusted") {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_SANITIZATION_ADJUSTED",
                "observationId" to observation.observationId,
                "reason" to sanitization.warnings.joinToString(",")
            )
        } else {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_SANITIZATION_ACCEPTED",
                "observationId" to observation.observationId,
                "price" to sanitizedOffer.price,
                "totalDistanceKm" to sanitizedOffer.totalDistanceKm,
                "warnings" to sanitization.warnings.joinToString(",")
            )
        }
        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_SEEN_OFFER_SAVE_ATTEMPT",
            "observationId" to observation.observationId,
            "platform" to sanitizedOffer.platform,
            "fingerprintKind" to fingerprint.kind
        )
        val saveResult = repository.saveSeenOffer(sanitizedOffer)
        if (saveResult.persisted) {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_SAVED",
                "observationId" to observation.observationId,
                "seenOfferId" to saveResult.seenOffer?.id,
                "platform" to saveResult.seenOffer?.platform,
                "price" to saveResult.seenOffer?.price,
                "valuePerKm" to saveResult.seenOffer?.valuePerKm,
                "status" to saveResult.seenOffer?.status
            )
        } else {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_DEDUPE_SKIPPED",
                "observationId" to observation.observationId,
                "existingSeenOfferId" to saveResult.seenOffer?.id,
                "reason" to saveResult.reason
            )
        }
        return SeenOfferPersistenceResult(
            attempted = true,
            persisted = saveResult.persisted,
            seenOffer = saveResult.seenOffer,
            reason = saveResult.reason
        )
    }
}
