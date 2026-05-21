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
    fun suspiciousHighPrice_isLeftForAuditor() {
        val result = sanitizer.sanitize(
            offer(
                price = 479.0,
                rawTextPreview = "Oferta 99 R$ 479 10 min 3,2 km"
            )
        )

        assertTrue(result.shouldPersist)
    }

    @Test
    fun suspiciousDistanceMismatch_isLeftForAuditor() {
        val result = sanitizer.sanitize(
            offer(
                price = 11.3,
                tripTimeMin = 12.0,
                tripDistanceKm = 319.3,
                totalDistanceKm = 319.3
            )
        )

        assertTrue(result.shouldPersist)
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
                productName = "UberX",
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

    @Test
    fun uberPriorityWithIncludedPriorityAmount_isAccepted() {
        val result = sanitizer.sanitize(
            offer(
                platform = RidePlatform.UBER,
                productName = "Priority",
                price = 12.0,
                pickupTimeMin = 4.0,
                pickupDistanceKm = 2.2,
                tripTimeMin = 9.0,
                tripDistanceKm = 6.3,
                totalDistanceKm = 8.5,
                rawTextPreview = "2 Priority R$ 12 +R$ 3,26 incluido para prioridade 4 min (2,2 km) 9 minutos (6,3 km)"
            )
        )

        assertTrue(result.shouldPersist)
        assertEquals("accepted", result.reason)
    }

    @Test
    fun uberXOffer_isAccepted() {
        val result = sanitizer.sanitize(
            offer(
                platform = RidePlatform.UBER,
                productName = "UberX",
                price = 8.34,
                pickupTimeMin = 6.0,
                pickupDistanceKm = 1.7,
                tripTimeMin = 9.0,
                tripDistanceKm = 3.2,
                totalDistanceKm = 4.9,
                rawTextPreview = "UberX R$ 8,34 6 min (1,7 km) 9 minutos (3,2 km)"
            )
        )

        assertTrue(result.shouldPersist)
        assertEquals("accepted", result.reason)
    }

    @Test
    fun operationalMoneyScreen_isNotAcceptedAsOffer() {
        val result = sanitizer.sanitize(
            offer(
                platform = RidePlatform.UNKNOWN,
                productName = "Ganhos",
                price = 2.0,
                pickupDistanceKm = null,
                pickupTimeMin = null,
                tripDistanceKm = null,
                tripTimeMin = null,
                totalDistanceKm = null,
                rawTextPreview = "+R$ 2 Ganhos Oportunidades Pagina inicial Mensagens Menu"
            )
        )

        assertFalse(result.shouldPersist)
        assertEquals("non_offer_fuel_or_promo_screen", result.reason)
    }

    private fun offer(
        platform: RidePlatform = RidePlatform.NINETY_NINE,
        price: Double? = 11.3,
        pickupDistanceKm: Double? = 1.1,
        pickupTimeMin: Double? = 10.0,
        tripDistanceKm: Double? = 3.8,
        tripTimeMin: Double? = 12.0,
        totalDistanceKm: Double? = 4.9,
        rawTextPreview: String = "99 Pop R$ 11,30 10 min (1,1 km) 12 min (3,8 km)",
        productName: String = "Pop"
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
        productName = productName,
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
