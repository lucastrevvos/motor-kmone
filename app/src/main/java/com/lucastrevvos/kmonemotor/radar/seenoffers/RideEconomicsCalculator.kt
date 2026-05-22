package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import kotlin.math.abs

data class ResolvedRideEconomics(
    val pickupDistanceKm: Double?,
    val tripDistanceKm: Double?,
    val totalDistanceKm: Double?,
    val valuePerKm: Double?,
    val warnings: List<String>
)

object RideEconomicsCalculator {
    fun resolveRideEconomics(
        platform: RidePlatform,
        price: Double?,
        explicitValuePerKm: Double?,
        totalDistanceKm: Double?,
        pickupDistanceKm: Double?,
        tripDistanceKm: Double?,
        routeWarnings: List<String> = emptyList()
    ): ResolvedRideEconomics {
        val normalizedPickup = pickupDistanceKm?.takeIf { it > 0.0 }
        val normalizedTrip = tripDistanceKm?.takeIf { it > 0.0 }
        val normalizedTotal = totalDistanceKm?.takeIf { it > 0.0 }
        val normalizedValuePerKm = explicitValuePerKm?.takeIf { it > 0.0 }
        val pickupPlusTrip = if (normalizedPickup != null && normalizedTrip != null) {
            normalizedPickup + normalizedTrip
        } else {
            null
        }
        val inferredFromValuePerKm = if (
            price != null &&
            normalizedValuePerKm != null &&
            normalizedValuePerKm > 0.0
        ) {
            price / normalizedValuePerKm
        } else {
            null
        }

        val warnings = routeWarnings.toMutableList()
        var resolvedPickup = normalizedPickup
        var resolvedTrip = normalizedTrip
        val hasSuspiciousTinyTrip = (
            platform == RidePlatform.NINETY_NINE &&
                normalizedTrip != null &&
                normalizedTrip < 0.2 &&
                inferredFromValuePerKm != null &&
                inferredFromValuePerKm >= 1.0
            )
        if (
            platform == RidePlatform.NINETY_NINE &&
            normalizedPickup != null &&
            normalizedTrip != null &&
            inferredFromValuePerKm != null
        ) {
            val sortedPickup = minOf(normalizedPickup, normalizedTrip)
            val sortedTrip = maxOf(normalizedPickup, normalizedTrip)
            val currentDelta = abs((normalizedPickup + normalizedTrip) - inferredFromValuePerKm)
            val sortedDelta = abs((sortedPickup + sortedTrip) - inferredFromValuePerKm)
            if (sortedDelta <= currentDelta) {
                resolvedPickup = sortedPickup
                resolvedTrip = sortedTrip
            }
        }
        val singleDistanceCandidate = when {
            resolvedPickup != null && resolvedTrip == null -> resolvedPickup
            resolvedPickup == null && resolvedTrip != null -> resolvedTrip
            resolvedPickup != null &&
                resolvedTrip != null &&
                abs(resolvedPickup - resolvedTrip) <= 0.05 &&
                inferredFromValuePerKm != null &&
                inferredFromValuePerKm > (resolvedPickup + resolvedTrip + 0.5) -> resolvedPickup
            else -> null
        }
        var resolvedTotal = when (platform) {
            RidePlatform.NINETY_NINE -> {
                if (hasSuspiciousTinyTrip) {
                    warnings += "suspicious_meter_distance_probably_time"
                }
                when {
                    singleDistanceCandidate != null && inferredFromValuePerKm != null -> {
                        if (resolvedPickup != null) {
                            val inferredTripKm = (inferredFromValuePerKm - resolvedPickup).takeIf { it > 0.0 }
                            if (resolvedTrip == null && inferredTripKm != null) {
                                resolvedTrip = inferredTripKm
                                warnings += "trip_distance_inferred_from_explicit_value_per_km"
                            }
                        }
                        warnings += "single_distance_total_inferred_from_explicit_value_per_km"
                        RadarLogger.i(
                            "KM_V2_SEEN",
                            "KM_V2_99_SINGLE_DISTANCE_TOTAL_INFERRED_FROM_VALUE_PER_KM",
                            "price" to price,
                            "explicitValuePerKm" to normalizedValuePerKm,
                            "singleDistanceKm" to singleDistanceCandidate,
                            "inferredTotalKm" to inferredFromValuePerKm,
                            "inferredTripKm" to resolvedTrip
                        )
                        inferredFromValuePerKm
                    }
                    hasSuspiciousTinyTrip && inferredFromValuePerKm != null -> {
                        warnings += "total_distance_inferred_from_value_per_km"
                        inferredFromValuePerKm
                    }
                    resolvedPickup != null && resolvedTrip != null && (
                        normalizedTotal == null ||
                            isDistanceDivergent(normalizedTotal, resolvedPickup + resolvedTrip)
                    ) -> {
                        if (normalizedTotal != null) {
                            warnings += "total_distance_mismatch_using_pickup_plus_trip"
                        }
                        resolvedPickup + resolvedTrip
                    }
                    resolvedPickup != null && resolvedTrip != null -> resolvedPickup + resolvedTrip
                    inferredFromValuePerKm != null && (
                        normalizedTotal == null ||
                            normalizedTotal < inferredFromValuePerKm * 0.8
                    ) -> {
                        warnings += "total_distance_inferred_from_value_per_km"
                        inferredFromValuePerKm
                    }
                    normalizedTotal != null -> normalizedTotal
                    normalizedTrip != null -> normalizedTrip
                    else -> null
                }
            }
            else -> resolveGeneralTotalDistanceKm(
                totalDistanceKm = normalizedTotal,
                pickupDistanceKm = normalizedPickup,
                tripDistanceKm = normalizedTrip,
                warnings = warnings
            )
        }

        var resolvedValuePerKm = when {
            platform == RidePlatform.NINETY_NINE && normalizedValuePerKm != null -> normalizedValuePerKm
            price != null && resolvedTotal != null && resolvedTotal > 0.0 -> price / resolvedTotal
            else -> normalizedValuePerKm
        }

        if (
            platform == RidePlatform.NINETY_NINE &&
            inferredFromValuePerKm != null &&
            resolvedTotal != null &&
            resolvedValuePerKm != null &&
            resolvedValuePerKm > 8.0 &&
            inferredFromValuePerKm > resolvedTotal * 2.0
        ) {
            resolvedTotal = inferredFromValuePerKm
            resolvedValuePerKm = if (price != null && resolvedTotal > 0.0) {
                price / resolvedTotal
            } else {
                normalizedValuePerKm
            }
            warnings += "total_distance_inferred_from_value_per_km"
        }

        if (
            resolvedValuePerKm != null &&
            normalizedValuePerKm != null &&
            abs(resolvedValuePerKm - normalizedValuePerKm) > 0.20
        ) {
            warnings += "value_per_km_mismatch"
        }

        return ResolvedRideEconomics(
            pickupDistanceKm = resolvedPickup,
            tripDistanceKm = resolvedTrip,
            totalDistanceKm = resolvedTotal,
            valuePerKm = resolvedValuePerKm,
            warnings = warnings.distinct()
        )
    }

    fun resolveTotalDistanceKm(
        totalDistanceKm: Double?,
        pickupDistanceKm: Double?,
        tripDistanceKm: Double?
    ): Double? {
        val normalizedTotal = totalDistanceKm?.takeIf { it > 0.0 }
        val normalizedPickup = pickupDistanceKm?.takeIf { it > 0.0 }
        val normalizedTrip = tripDistanceKm?.takeIf { it > 0.0 }
        return normalizedTotal
            ?: if (normalizedPickup != null && normalizedTrip != null) {
                normalizedPickup + normalizedTrip
            } else {
                normalizedTrip
            }
    }

    fun calculateValuePerKm(
        price: Double?,
        totalDistanceKm: Double?,
        pickupDistanceKm: Double?,
        tripDistanceKm: Double?
    ): Double? {
        val resolvedKm = resolveTotalDistanceKm(
            totalDistanceKm = totalDistanceKm,
            pickupDistanceKm = pickupDistanceKm,
            tripDistanceKm = tripDistanceKm
        )
        return if (price != null && resolvedKm != null && resolvedKm > 0.0) {
            price / resolvedKm
        } else {
            null
        }
    }

    private fun isDistanceDivergent(first: Double, second: Double): Boolean {
        val reference = maxOf(first, second)
        if (reference <= 0.0) return false
        return abs(first - second) / reference > 0.30
    }

    private fun resolveGeneralTotalDistanceKm(
        totalDistanceKm: Double?,
        pickupDistanceKm: Double?,
        tripDistanceKm: Double?,
        warnings: MutableList<String>
    ): Double? {
        val pickupPlusTrip = if (pickupDistanceKm != null && tripDistanceKm != null) {
            pickupDistanceKm + tripDistanceKm
        } else {
            null
        }
        return when {
            pickupPlusTrip != null && totalDistanceKm != null && isDistanceDivergent(totalDistanceKm, pickupPlusTrip) -> {
                warnings += "total_distance_mismatch_using_pickup_plus_trip"
                pickupPlusTrip
            }
            totalDistanceKm != null -> totalDistanceKm
            pickupPlusTrip != null -> pickupPlusTrip
            tripDistanceKm != null -> tripDistanceKm
            else -> null
        }
    }
}
