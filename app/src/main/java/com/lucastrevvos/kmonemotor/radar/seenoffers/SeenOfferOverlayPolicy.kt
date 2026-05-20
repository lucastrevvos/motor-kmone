package com.lucastrevvos.kmonemotor.radar.seenoffers

data class SeenOfferOverlayDecision(
    val shouldShowOverlay: Boolean,
    val overlayKind: String? = null,
    val finalReasonOverride: String? = null,
    val reShowExistingSeenOfferId: String? = null
)

object SeenOfferOverlayPolicy {
    fun resolve(
        persistenceResult: SeenOfferPersistenceResult,
        computedOverlayKind: String?,
        isManual: Boolean = false
    ): SeenOfferOverlayDecision {
        if (isManual && shouldReShowExistingForManual(persistenceResult) && persistenceResult.seenOffer?.id != null) {
            return SeenOfferOverlayDecision(
                shouldShowOverlay = false,
                overlayKind = null,
                finalReasonOverride = persistenceResult.reason,
                reShowExistingSeenOfferId = persistenceResult.seenOffer.id
            )
        }
        if (!persistenceResult.persisted) {
            return SeenOfferOverlayDecision(
                shouldShowOverlay = false,
                overlayKind = null,
                finalReasonOverride = persistenceResult.reason
            )
        }
        if (!shouldSuppressOverlay(persistenceResult.reason)) {
            return SeenOfferOverlayDecision(
                shouldShowOverlay = true,
                overlayKind = computedOverlayKind,
                finalReasonOverride = null
            )
        }
        return SeenOfferOverlayDecision(
            shouldShowOverlay = false,
            overlayKind = null,
            finalReasonOverride = persistenceResult.reason
        )
    }

    fun shouldSuppressOverlay(persistReason: String): Boolean {
        val normalizedReason = persistReason.trim().lowercase()
        return normalizedReason == "existing_observation_already_saved" ||
            normalizedReason == "fingerprint_not_offer_like" ||
            normalizedReason == "sanitization_rejected" ||
            normalizedReason == "suspicious_distance_time_mismatch" ||
            normalizedReason == "suspicious_price_too_high" ||
            normalizedReason == "non_offer_fuel_or_promo_screen" ||
            normalizedReason == "own_overlay_capture" ||
            normalizedReason.startsWith("manual_recent_authority") ||
            normalizedReason.contains("duplicate") ||
            normalizedReason.contains("merged")
    }

    private fun shouldReShowExistingForManual(persistenceResult: SeenOfferPersistenceResult): Boolean {
        val normalizedReason = persistenceResult.reason.trim().lowercase()
        return persistenceResult.seenOffer != null && (
            normalizedReason == "existing_observation_already_saved" ||
                normalizedReason.startsWith("manual_recent_authority") ||
                normalizedReason.contains("duplicate") ||
                normalizedReason.contains("merged")
            )
    }
}
