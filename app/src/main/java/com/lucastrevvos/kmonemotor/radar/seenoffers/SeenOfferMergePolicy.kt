package com.lucastrevvos.kmonemotor.radar.seenoffers

import kotlin.math.abs

class SeenOfferMergePolicy(
    private val duplicateWindowMs: Long = 60_000L,
    private val manualAuthorityWindowMs: Long = 30_000L
) {
    fun isSameOffer(candidate: SeenOffer, existing: SeenOffer): Boolean {
        if (abs(candidate.createdAtMs - existing.createdAtMs) > duplicateWindowMs) return false
        val platformResolution = resolvePlatformResolution(candidate = candidate, existing = existing) ?: return false
        if (candidate.rawTextHash != null && candidate.rawTextHash == existing.rawTextHash) return true
        if (candidate.routeTextHash != null && candidate.routeTextHash == existing.routeTextHash) return true
        if (!similarMoney(candidate.price, existing.price)) return false
        if (isManualRecentAuthorityCandidate(candidate = candidate, existing = existing)) {
            return true
        }
        if (platformResolution.effectiveCandidatePlatform == platformResolution.effectiveExistingPlatform &&
            similarMoney(candidate.price, existing.price)
        ) {
            return true
        }
        if (!compatibleText(candidate.productName, existing.productName)) return false
        if (similarDistance(candidate.pickupDistanceKm, existing.pickupDistanceKm)) return true
        if (similarDistance(candidate.tripDistanceKm, existing.tripDistanceKm)) return true
        if (similarDistance(candidate.totalDistanceKm, existing.totalDistanceKm)) return true
        if (compatibleText(candidate.originPreview, existing.originPreview) &&
            compatibleText(candidate.destinationPreview, existing.destinationPreview)
        ) {
            return true
        }
        return false
    }

    fun isManualRecentAuthorityCandidate(candidate: SeenOffer, existing: SeenOffer): Boolean {
        if (!isManual(existing.sourceTrigger) || isManual(candidate.sourceTrigger)) return false
        val platformResolution = resolvePlatformResolution(candidate = candidate, existing = existing) ?: return false
        if (platformResolution.effectiveCandidatePlatform != platformResolution.effectiveExistingPlatform) return false
        if (!similarMoney(candidate.price, existing.price)) return false
        return abs(candidate.createdAtMs - existing.createdAtMs) <= manualAuthorityWindowMs
    }

    fun resolvePlatformResolution(candidate: SeenOffer, existing: SeenOffer): PlatformResolution? {
        val inferredCandidate = inferPlatform(candidate, existing)
        val inferredExisting = inferPlatform(existing, candidate)
        if (inferredCandidate == RidePlatform.UNKNOWN || inferredExisting == RidePlatform.UNKNOWN) {
            return null
        }
        if (inferredCandidate != inferredExisting) {
            return null
        }
        val reason = when {
            candidate.platform == RidePlatform.UNKNOWN || existing.platform == RidePlatform.UNKNOWN ->
                "trigger_source_or_recent_candidate"
            else -> "platform_match"
        }
        return PlatformResolution(
            effectiveCandidatePlatform = inferredCandidate,
            effectiveExistingPlatform = inferredExisting,
            reason = reason
        )
    }

    fun qualityScore(offer: SeenOffer): Int {
        var score = 0
        if (offer.price != null) score += 2
        if (offer.pickupDistanceKm != null) score += 2
        if (offer.tripDistanceKm != null) score += 2
        if (offer.totalDistanceKm != null) score += 2
        if (RideEconomicsCalculator.calculateValuePerKm(
                price = offer.price,
                totalDistanceKm = offer.totalDistanceKm,
                pickupDistanceKm = offer.pickupDistanceKm,
                tripDistanceKm = offer.tripDistanceKm
            ) != null
        ) score += 2
        if (offer.pickupTimeMin != null) score += 1
        if (offer.tripTimeMin != null) score += 1
        if (!offer.originPreview.isNullOrBlank()) score += 2
        if (!offer.destinationPreview.isNullOrBlank()) score += 2

        val pickupPlusTrip = if (offer.pickupDistanceKm != null && offer.tripDistanceKm != null) {
            offer.pickupDistanceKm + offer.tripDistanceKm
        } else {
            null
        }
        if (pickupPlusTrip != null && offer.totalDistanceKm != null && offer.totalDistanceKm > 0.0) {
            if (abs(offer.totalDistanceKm - pickupPlusTrip) / offer.totalDistanceKm > 0.30) {
                score -= 3
            }
        }

        val calculatedValuePerKm = RideEconomicsCalculator.calculateValuePerKm(
            price = offer.price,
            totalDistanceKm = offer.totalDistanceKm,
            pickupDistanceKm = offer.pickupDistanceKm,
            tripDistanceKm = offer.tripDistanceKm
        )
        if (calculatedValuePerKm != null && offer.valuePerKm != null && abs(calculatedValuePerKm - offer.valuePerKm) > 0.20) {
            score -= 3
        }
        if (offer.productName?.let(SeenOfferSanitizationRules::isBadProductName) == true) {
            score -= 5
        }
        return score
    }

    fun mergeBetter(existing: SeenOffer, candidate: SeenOffer): SeenOffer {
        fun chooseString(newValue: String?, oldValue: String?): String? {
            return when {
                !newValue.isNullOrBlank() -> newValue
                else -> oldValue
            }
        }

        fun chooseDouble(newValue: Double?, oldValue: Double?): Double? {
            return newValue ?: oldValue
        }

        return existing.copy(
            observationId = candidate.observationId,
            sourceTrigger = candidate.sourceTrigger,
            price = chooseDouble(candidate.price, existing.price),
            valuePerKm = chooseDouble(candidate.valuePerKm, existing.valuePerKm),
            pickupDistanceKm = chooseDouble(candidate.pickupDistanceKm, existing.pickupDistanceKm),
            pickupTimeMin = chooseDouble(candidate.pickupTimeMin, existing.pickupTimeMin),
            tripDistanceKm = chooseDouble(candidate.tripDistanceKm, existing.tripDistanceKm),
            tripTimeMin = chooseDouble(candidate.tripTimeMin, existing.tripTimeMin),
            totalDistanceKm = chooseDouble(candidate.totalDistanceKm, existing.totalDistanceKm),
            estimatedTotalTimeMin = chooseDouble(candidate.estimatedTotalTimeMin, existing.estimatedTotalTimeMin),
            productName = chooseString(candidate.productName, existing.productName),
            originPreview = chooseString(candidate.originPreview, existing.originPreview),
            destinationPreview = chooseString(candidate.destinationPreview, existing.destinationPreview),
            rawTextPreview = chooseString(candidate.rawTextPreview, existing.rawTextPreview),
            score = maxOf(candidate.score ?: Int.MIN_VALUE, existing.score ?: Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            rawTextHash = chooseString(candidate.rawTextHash, existing.rawTextHash),
            routeTextHash = chooseString(candidate.routeTextHash, existing.routeTextHash),
            updatedAtMs = maxOf(existing.updatedAtMs, candidate.updatedAtMs)
        )
    }

    private fun similarMoney(first: Double?, second: Double?): Boolean {
        if (first == null || second == null) return false
        return abs(first - second) <= 0.25
    }

    private fun similarDistance(first: Double?, second: Double?): Boolean {
        if (first == null || second == null) return false
        return abs(first - second) <= 0.40
    }

    private fun compatibleText(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return true
        val normalizedFirst = first.trim().lowercase()
        val normalizedSecond = second.trim().lowercase()
        return normalizedFirst == normalizedSecond ||
            normalizedFirst.contains(normalizedSecond) ||
            normalizedSecond.contains(normalizedFirst)
    }

    private fun isManual(triggerSource: String?): Boolean {
        return triggerSource == "MANUAL_SCREEN_ANALYSIS"
    }

    private fun inferPlatform(primary: SeenOffer, counterpart: SeenOffer): RidePlatform {
        if (primary.platform != RidePlatform.UNKNOWN) return primary.platform
        inferPlatformFromTrigger(primary.sourceTrigger)?.let { return it }
        if (abs(primary.createdAtMs - counterpart.createdAtMs) <= duplicateWindowMs &&
            similarMoney(primary.price, counterpart.price) &&
            counterpart.platform != RidePlatform.UNKNOWN
        ) {
            return counterpart.platform
        }
        return RidePlatform.UNKNOWN
    }

    private fun inferPlatformFromTrigger(triggerSource: String?): RidePlatform? {
        val normalized = triggerSource?.trim()?.uppercase() ?: return null
        return when {
            normalized.contains("UBER") -> RidePlatform.UBER
            normalized.contains("NINETY_NINE") || normalized.contains("99") -> RidePlatform.NINETY_NINE
            else -> null
        }
    }

    data class PlatformResolution(
        val effectiveCandidatePlatform: RidePlatform,
        val effectiveExistingPlatform: RidePlatform,
        val reason: String
    )
}
