package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenOfferConsistencyAuditorTest {
    private val auditor = SeenOfferConsistencyAuditor()

    @Test
    fun totalMismatch_prefersPickupPlusTrip() {
        val result = auditor.audit(
            offer(
                price = 7.93,
                valuePerKm = 1.5,
                pickupDistanceKm = 1.5,
                tripDistanceKm = 4.0,
                totalDistanceKm = 10.8
            )
        )

        assertFalse(result.shouldReject)
        assertEquals(5.5, result.normalizedOffer.totalDistanceKm ?: 0.0, 0.0)
        assertEquals(1.44, result.normalizedOffer.valuePerKm ?: 0.0, 0.01)
        assertTrue(result.warnings.contains("total_distance_mismatch_using_pickup_plus_trip"))
    }

    @Test
    fun mismatchedOcrValuePerKm_prefersCalculated() {
        val result = auditor.audit(
            offer(
                price = 7.93,
                valuePerKm = 1.50,
                pickupDistanceKm = 5.5,
                tripDistanceKm = 5.3,
                totalDistanceKm = 10.8
            )
        )

        assertEquals(0.73, result.normalizedOffer.valuePerKm ?: 0.0, 0.01)
    }

    @Test
    fun suspiciousHighPrice_isRejected() {
        val result = auditor.audit(offer(price = 479.0))

        assertTrue(result.shouldReject)
        assertEquals("suspicious_price_too_high", result.rejectReason)
    }

    @Test
    fun suspiciousDistanceMismatch_isRejected() {
        val result = auditor.audit(
            offer(
                price = 11.3,
                tripTimeMin = 12.0,
                tripDistanceKm = 319.3,
                totalDistanceKm = 319.3
            )
        )

        assertTrue(result.shouldReject)
        assertEquals("suspicious_distance_time_mismatch", result.rejectReason)
    }

    @Test
    fun coherentOffer_isAccepted() {
        val result = auditor.audit(
            offer(
                price = 7.93,
                pickupDistanceKm = 1.5,
                tripDistanceKm = 4.0,
                totalDistanceKm = 5.5
            )
        )

        assertFalse(result.shouldReject)
        assertEquals(1.44, result.normalizedOffer.valuePerKm ?: 0.0, 0.01)
    }

    private fun offer(
        price: Double? = 12.5,
        valuePerKm: Double? = 2.1,
        pickupDistanceKm: Double? = 1.2,
        pickupTimeMin: Double? = 4.0,
        tripDistanceKm: Double? = 4.8,
        tripTimeMin: Double? = 10.0,
        totalDistanceKm: Double? = 6.0
    ) = SeenOffer(
        id = "seen-1",
        observationId = "obs-1",
        platform = RidePlatform.UBER,
        sourceTrigger = "MANUAL_SCREEN_ANALYSIS",
        status = SeenOfferStatus.SEEN,
        price = price,
        valuePerKm = valuePerKm,
        pickupDistanceKm = pickupDistanceKm,
        pickupTimeMin = pickupTimeMin,
        tripDistanceKm = tripDistanceKm,
        tripTimeMin = tripTimeMin,
        totalDistanceKm = totalDistanceKm,
        estimatedTotalTimeMin = 14.0,
        productName = "UberX",
        originPreview = null,
        destinationPreview = null,
        rawTextPreview = "UberX R$ 12,50",
        score = 8,
        rawTextHash = "hash",
        routeTextHash = "route",
        createdAtMs = 1_000L,
        updatedAtMs = 1_000L
    )
}
