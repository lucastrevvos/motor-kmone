package com.lucastrevvos.kmonemotor.radar.seenoffers

import kotlin.math.abs

data class SeenOfferAuditResult(
    val normalizedOffer: SeenOffer,
    val warnings: List<String>,
    val shouldReject: Boolean,
    val rejectReason: String?
)

class SeenOfferConsistencyAuditor {
    fun audit(offer: SeenOffer): SeenOfferAuditResult {
        var adjusted = offer
        val warnings = mutableListOf<String>()

        val pickup = adjusted.pickupDistanceKm?.takeIf { it > 0.0 }
        val trip = adjusted.tripDistanceKm?.takeIf { it > 0.0 }
        val total = adjusted.totalDistanceKm?.takeIf { it > 0.0 }
        val pickupPlusTrip = if (pickup != null && trip != null) pickup + trip else null
        val shouldPreferPickupPlusTrip = pickupPlusTrip != null &&
            total != null &&
            total > 0.0 &&
            abs(total - pickupPlusTrip) / total > TOTAL_DISTANCE_DIVERGENCE_THRESHOLD

        if (shouldPreferPickupPlusTrip) {
            adjusted = adjusted.copy(totalDistanceKm = pickupPlusTrip)
            warnings += "total_distance_mismatch_using_pickup_plus_trip"
        } else {
            adjusted = adjusted.copy(
                totalDistanceKm = RideEconomicsCalculator.resolveTotalDistanceKm(
                    totalDistanceKm = adjusted.totalDistanceKm,
                    pickupDistanceKm = adjusted.pickupDistanceKm,
                    tripDistanceKm = adjusted.tripDistanceKm
                )
            )
        }

        val calculatedValuePerKm = RideEconomicsCalculator.calculateValuePerKm(
            price = adjusted.price,
            totalDistanceKm = adjusted.totalDistanceKm,
            pickupDistanceKm = adjusted.pickupDistanceKm,
            tripDistanceKm = adjusted.tripDistanceKm
        )
        val ocrValuePerKm = adjusted.valuePerKm?.takeIf { it > 0.0 }
        if (
            calculatedValuePerKm != null &&
            ocrValuePerKm != null &&
            abs(calculatedValuePerKm - ocrValuePerKm) > VALUE_PER_KM_MISMATCH_THRESHOLD
        ) {
            warnings += "value_per_km_mismatch"
        }
        adjusted = adjusted.copy(valuePerKm = calculatedValuePerKm ?: ocrValuePerKm)

        val price = adjusted.price
        val tripTime = adjusted.tripTimeMin
        val tripDistance = adjusted.tripDistanceKm
        val resolvedKm = adjusted.totalDistanceKm
        val resolvedValuePerKm = adjusted.valuePerKm

        if (price != null && price > MAX_ALLOWED_PRICE_BRL) {
            return SeenOfferAuditResult(adjusted, warnings, true, "suspicious_price_too_high")
        }
        if (tripTime != null && tripDistance != null && tripTime <= 30.0 && tripDistance > 100.0) {
            return SeenOfferAuditResult(adjusted, warnings, true, "suspicious_distance_time_mismatch")
        }
        if (resolvedKm != null && resolvedKm > 150.0 && (price ?: 0.0) < 100.0) {
            return SeenOfferAuditResult(adjusted, warnings, true, "suspicious_distance_time_mismatch")
        }

        if (resolvedValuePerKm != null && resolvedValuePerKm < 0.4) {
            warnings += "value_per_km_very_low"
        }
        if (resolvedValuePerKm != null && resolvedValuePerKm > 20.0) {
            warnings += "value_per_km_very_high"
        }
        if (resolvedKm != null && resolvedKm > 50.0 && (price ?: 0.0) < 20.0) {
            warnings += "price_too_low_for_distance"
        }

        return SeenOfferAuditResult(
            normalizedOffer = adjusted,
            warnings = warnings.distinct(),
            shouldReject = false,
            rejectReason = null
        )
    }

    companion object {
        private const val MAX_ALLOWED_PRICE_BRL = 300.0
        private const val TOTAL_DISTANCE_DIVERGENCE_THRESHOLD = 0.30
        private const val VALUE_PER_KM_MISMATCH_THRESHOLD = 0.20
    }
}
