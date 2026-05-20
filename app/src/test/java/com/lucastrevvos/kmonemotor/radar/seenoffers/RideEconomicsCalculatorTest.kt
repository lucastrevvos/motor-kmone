package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideEconomicsCalculatorTest {
    @Test
    fun usesExplicitTotalDistanceWhenAvailable() {
        val valuePerKm = RideEconomicsCalculator.calculateValuePerKm(
            price = 7.93,
            totalDistanceKm = 10.8,
            pickupDistanceKm = 5.5,
            tripDistanceKm = 5.3
        )

        assertEquals(0.73, valuePerKm ?: 0.0, 0.01)
    }

    @Test
    fun usesPickupPlusTripWhenTotalMissing() {
        val valuePerKm = RideEconomicsCalculator.calculateValuePerKm(
            price = 7.93,
            totalDistanceKm = null,
            pickupDistanceKm = 1.5,
            tripDistanceKm = 4.0
        )

        assertEquals(1.44, valuePerKm ?: 0.0, 0.01)
    }

    @Test
    fun resolvesDistanceFromPickupPlusTrip() {
        val total = RideEconomicsCalculator.resolveTotalDistanceKm(
            totalDistanceKm = null,
            pickupDistanceKm = 1.5,
            tripDistanceKm = 4.0
        )

        assertEquals(5.5, total ?: 0.0, 0.0)
    }

    @Test
    fun returnsNullWithoutUsableKm() {
        assertNull(
            RideEconomicsCalculator.calculateValuePerKm(
                price = 7.93,
                totalDistanceKm = null,
                pickupDistanceKm = null,
                tripDistanceKm = null
            )
        )
    }
}
