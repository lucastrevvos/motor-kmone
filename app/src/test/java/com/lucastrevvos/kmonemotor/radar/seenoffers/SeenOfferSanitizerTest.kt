package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenOfferSanitizerTest {
    private val sanitizer = SeenOfferSanitizer()

    @Test
    fun fuelPromoOffer_isRejected() {
        val result = sanitizer.sanitize(
            offer(
                price = 479.0,
                rawTextPreview = "99 Abastece Posto Santa Monica Rede Primos R$4,79"
            )
        )

        assertFalse(result.shouldPersist)
        assertEquals("non_offer_fuel_or_promo_screen", result.reason)
    }

    @Test
    fun suspiciousHighPrice_isRejected() {
        val result = sanitizer.sanitize(
            offer(
                price = 479.0,
                rawTextPreview = "Oferta 99 R$ 479 10 min 3,2 km"
            )
        )

        assertFalse(result.shouldPersist)
        assertEquals("suspicious_price_too_high", result.reason)
    }

    @Test
    fun suspiciousDistanceMismatch_isRejected() {
        val result = sanitizer.sanitize(
            offer(
                price = 11.3,
                tripTimeMin = 12.0,
                tripDistanceKm = 319.3,
                totalDistanceKm = 319.3
            )
        )

        assertFalse(result.shouldPersist)
        assertEquals("suspicious_distance_time_mismatch", result.reason)
    }

    @Test
    fun zeroDistances_areNormalizedToNull() {
        val result = sanitizer.sanitize(
            offer(
                pickupDistanceKm = 0.0,
                tripDistanceKm = 0.0,
                totalDistanceKm = 0.0
            )
        )

        assertTrue(result.shouldPersist)
        assertNotNull(result.sanitizedOffer)
        assertNull(result.sanitizedOffer?.pickupDistanceKm)
        assertNull(result.sanitizedOffer?.tripDistanceKm)
        assertNull(result.sanitizedOffer?.totalDistanceKm)
    }

    @Test
    fun plausibleOffer_isAccepted() {
        val result = sanitizer.sanitize(
            offer(
                platform = RidePlatform.UBER,
                price = 26.30,
                pickupTimeMin = 23.0,
                pickupDistanceKm = 23.3,
                tripTimeMin = 23.0,
                tripDistanceKm = 0.1,
                totalDistanceKm = 23.4
            )
        )

        assertTrue(result.shouldPersist)
        assertEquals("accepted", result.reason)
    }

    private fun offer(
        platform: RidePlatform = RidePlatform.NINETY_NINE,
        price: Double? = 11.3,
        pickupDistanceKm: Double? = 1.1,
        pickupTimeMin: Double? = 10.0,
        tripDistanceKm: Double? = 3.8,
        tripTimeMin: Double? = 12.0,
        totalDistanceKm: Double? = 4.9,
        rawTextPreview: String = "99 Pop R$ 11,30 10 min (1,1 km) 12 min (3,8 km)"
    ) = SeenOffer(
        id = "seen-1",
        observationId = "obs-1",
        platform = platform,
        sourceTrigger = "UBER_DOMINANT_OFFER_DIAGNOSTIC",
        status = SeenOfferStatus.SEEN,
        price = price,
        valuePerKm = 1.5,
        pickupDistanceKm = pickupDistanceKm,
        pickupTimeMin = pickupTimeMin,
        tripDistanceKm = tripDistanceKm,
        tripTimeMin = tripTimeMin,
        totalDistanceKm = totalDistanceKm,
        estimatedTotalTimeMin = 22.0,
        productName = "Pop",
        originPreview = null,
        destinationPreview = null,
        rawTextPreview = rawTextPreview,
        score = 8,
        rawTextHash = "hash",
        routeTextHash = "route",
        createdAtMs = 1_000L,
        updatedAtMs = 1_000L
    )
}
