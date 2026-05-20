package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.parser.OfferParserResult

class SeenOfferPersistenceProcessor(
    private val repository: SeenOfferRepository,
    private val mapper: SeenOfferMapper = SeenOfferMapper(),
    private val sanitizer: SeenOfferSanitizer = SeenOfferSanitizer(),
    private val consistencyAuditor: SeenOfferConsistencyAuditor = SeenOfferConsistencyAuditor()
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
        val auditResult = consistencyAuditor.audit(sanitizedOffer)
        if (auditResult.shouldReject || auditResult.rejectReason != null) {
            RadarLogger.w(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_CONSISTENCY_REJECTED",
                "observationId" to observation.observationId,
                "reason" to auditResult.rejectReason
            )
            return SeenOfferPersistenceResult(
                attempted = true,
                persisted = false,
                reason = auditResult.rejectReason ?: "consistency_rejected"
            )
        }
        auditResult.warnings.forEach { warning ->
            RadarLogger.w(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_CONSISTENCY_WARNING",
                "observationId" to observation.observationId,
                "reason" to warning,
                "price" to auditResult.normalizedOffer.price,
                "resolvedKm" to auditResult.normalizedOffer.totalDistanceKm,
                "ocrValuePerKm" to sanitizedOffer.valuePerKm,
                "calculatedValuePerKm" to auditResult.normalizedOffer.valuePerKm
            )
        }
        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_SEEN_OFFER_CONSISTENCY_AUDITED",
            "observationId" to observation.observationId,
            "price" to auditResult.normalizedOffer.price,
            "resolvedKm" to auditResult.normalizedOffer.totalDistanceKm,
            "valuePerKm" to auditResult.normalizedOffer.valuePerKm,
            "warnings" to auditResult.warnings.joinToString(",")
        )
        val auditedOffer = auditResult.normalizedOffer
        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_SEEN_OFFER_SAVE_ATTEMPT",
            "observationId" to observation.observationId,
            "platform" to auditedOffer.platform,
            "fingerprintKind" to fingerprint.kind
        )
        val saveResult = repository.saveSeenOffer(auditedOffer)
        if (saveResult.persisted && saveResult.reason == "merged_better_version") {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_MERGED_BETTER_VERSION",
                "seenOfferId" to saveResult.seenOffer?.id
            )
        } else if (saveResult.persisted && saveResult.reason == "manual_recent_authority_better_auto_merged_silently") {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_MERGED_BETTER_VERSION",
                "seenOfferId" to saveResult.seenOffer?.id,
                "reason" to "manual_recent_authority"
            )
        } else if (saveResult.persisted) {
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
        } else if (
            saveResult.reason == "weaker_duplicate_offer_recently_saved" ||
            saveResult.reason == "manual_recent_authority_weaker_auto_ignored"
        ) {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_DEDUPE_SKIPPED_WEAKER_VERSION",
                "observationId" to observation.observationId,
                "existingSeenOfferId" to saveResult.seenOffer?.id,
                "reason" to saveResult.reason
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
