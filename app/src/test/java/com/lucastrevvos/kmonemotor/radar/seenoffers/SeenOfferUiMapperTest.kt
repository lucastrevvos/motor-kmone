package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Test

class SeenOfferUiMapperTest {
    private val mapper = SeenOfferUiMapper()

    @Test
    fun divergentTotal_usesResolvedDistanceAndCalculatedValuePerKm() {
        val model = mapper.map(
            offer(
                price = 7.93,
                valuePerKm = 1.50,
                pickupDistanceKm = 1.5,
                tripDistanceKm = 4.0,
                totalDistanceKm = 10.8
            )
        )

        assertEquals("5,5 km", model.totalDistanceLabel)
        assertEquals("R$ 1,44/km", model.valuePerKmLabel)
    }

    @Test
    fun coherentTotal_keepsCorrectCalculatedValuePerKm() {
        val model = mapper.map(
            offer(
                price = 10.34,
                valuePerKm = 1.60,
                pickupDistanceKm = 2.0,
                tripDistanceKm = 5.0,
                totalDistanceKm = 7.0
            )
        )

        assertEquals("R$ 1,48/km", model.valuePerKmLabel)
    }

    @Test
    fun noKm_showsDash() {
        val model = mapper.map(
            offer(
                price = 10.34,
                valuePerKm = null,
                pickupDistanceKm = null,
                tripDistanceKm = null,
                totalDistanceKm = null
            )
        )

        assertEquals("R$/km —", model.valuePerKmLabel)
        assertEquals("—", model.totalDistanceLabel)
    }

    private fun offer(
        price: Double?,
        valuePerKm: Double?,
        pickupDistanceKm: Double?,
        tripDistanceKm: Double?,
        totalDistanceKm: Double?
    ) = SeenOffer(
        id = "seen-1",
        observationId = "obs-1",
        platform = RidePlatform.UBER,
        sourceTrigger = "MANUAL_SCREEN_ANALYSIS",
        status = SeenOfferStatus.SEEN,
        price = price,
        valuePerKm = valuePerKm,
        pickupDistanceKm = pickupDistanceKm,
        pickupTimeMin = 4.0,
        tripDistanceKm = tripDistanceKm,
        tripTimeMin = 8.0,
        totalDistanceKm = totalDistanceKm,
        estimatedTotalTimeMin = 12.0,
        productName = "UberX",
        originPreview = "Avenida das Nacoes",
        destinationPreview = "Rua Leonel Pereira",
        rawTextPreview = "UberX",
        score = 9,
        rawTextHash = "hash",
        routeTextHash = "route",
        createdAtMs = 1_000L,
        updatedAtMs = 1_000L
    )
}
