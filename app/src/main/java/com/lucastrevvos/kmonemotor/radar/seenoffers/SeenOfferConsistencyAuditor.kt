package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger

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
        val resolvedEconomics = RideEconomicsCalculator.resolveRideEconomics(
            platform = adjusted.platform,
            price = adjusted.price,
            explicitValuePerKm = adjusted.valuePerKm,
            totalDistanceKm = adjusted.totalDistanceKm,
            pickupDistanceKm = adjusted.pickupDistanceKm,
            tripDistanceKm = adjusted.tripDistanceKm
        )
        warnings += resolvedEconomics.warnings
        adjusted = adjusted.copy(
            pickupDistanceKm = resolvedEconomics.pickupDistanceKm,
            tripDistanceKm = resolvedEconomics.tripDistanceKm,
            totalDistanceKm = resolvedEconomics.totalDistanceKm,
            valuePerKm = resolvedEconomics.valuePerKm
        )

        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_ROUTE_METRICS_AUDITED",
            "observationId" to adjusted.observationId,
            "platform" to adjusted.platform,
            "price" to adjusted.price,
            "pickupDistanceKm" to adjusted.pickupDistanceKm,
            "tripDistanceKm" to adjusted.tripDistanceKm,
            "totalDistanceKm" to adjusted.totalDistanceKm,
            "valuePerKm" to adjusted.valuePerKm,
            "warnings" to warnings.joinToString(",")
        )
        if (warnings.contains("total_distance_inferred_from_value_per_km")) {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_TOTAL_DISTANCE_INFERRED_FROM_VALUE_PER_KM",
                "observationId" to adjusted.observationId,
                "platform" to adjusted.platform,
                "price" to adjusted.price,
                "valuePerKm" to adjusted.valuePerKm,
                "totalDistanceKm" to adjusted.totalDistanceKm
            )
        }
        if (
            adjusted.platform == RidePlatform.NINETY_NINE &&
            adjusted.price != null &&
            offer.valuePerKm != null &&
            adjusted.totalDistanceKm != null &&
            adjusted.totalDistanceKm > 0.0
        ) {
            val computedValuePerKm = adjusted.price / adjusted.totalDistanceKm
            val delta = kotlin.math.abs(computedValuePerKm - offer.valuePerKm)
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_99_ECONOMICS_COMPARE",
                "price" to adjusted.price,
                "explicitValuePerKm" to offer.valuePerKm,
                "pickupDistanceKm" to adjusted.pickupDistanceKm,
                "tripDistanceKm" to adjusted.tripDistanceKm,
                "resolvedTotalDistanceKm" to adjusted.totalDistanceKm,
                "computedValuePerKm" to computedValuePerKm,
                "delta" to delta,
                "reason" to if (delta <= 0.05) "within_tolerance" else if (delta > 0.10) "explicit_value_per_km_mismatch" else "minor_rounding_delta"
            )
            if (delta > 0.10) {
                warnings += "explicit_value_per_km_mismatch"
            }
        }

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
    }
}
