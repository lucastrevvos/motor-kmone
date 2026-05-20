package com.lucastrevvos.kmonemotor.radar.seenoffers

object RideEconomicsCalculator {
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
}
