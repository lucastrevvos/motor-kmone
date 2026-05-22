package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenOfferPresentationFactoryTest {
    private val factory = SeenOfferPresentationFactory()

    @Test
    fun manualReshow_usesAuditedSavedMetricsInsteadOfWrongRawTotal() {
        val presentation = factory.buildFromSeenOffer(
            seenOffer = SeenOffer(
                id = "seen-1",
                observationId = "obs-1",
                platform = RidePlatform.NINETY_NINE,
                sourceTrigger = "MANUAL_SCREEN_ANALYSIS",
                status = SeenOfferStatus.SEEN,
                price = 20.60,
                valuePerKm = 1.30,
                pickupDistanceKm = 1.9,
                pickupTimeMin = 8.0,
                tripDistanceKm = 14.0,
                tripTimeMin = 21.0,
                totalDistanceKm = 1.9,
                estimatedTotalTimeMin = 29.0,
                productName = "99",
                originPreview = "Origem",
                destinationPreview = "Destino",
                rawTextPreview = "Pagamento no app R$20,60 R$1,30/km",
                score = 10,
                rawTextHash = "hash",
                routeTextHash = "route",
                createdAtMs = 1_000L,
                updatedAtMs = 1_000L
            ),
            createdAtMs = 2_000L
        )

        assertTrue(presentation.primaryMetric?.contains("1,30") == true)
        assertTrue(presentation.secondaryMetric?.contains("15,9") == true)
        assertTrue(presentation.details.any { it.contains("Busca: 8 min / 1,9 km") })
        assertTrue(presentation.details.any { it.contains("Corrida: 21 min / 14,0 km") })
    }

    @Test
    fun manualReshow_recomputesValuePerKmWhenMissingFromSavedOffer() {
        val presentation = factory.buildFromSeenOffer(
            seenOffer = seenOffer(
                price = 18.41,
                valuePerKm = null,
                pickupDistanceKm = 5.6,
                tripDistanceKm = 11.7,
                totalDistanceKm = 17.3
            ),
            createdAtMs = 2_000L
        )

        assertTrue(presentation.primaryMetric?.contains("1,06") == true)
        assertTrue(presentation.secondaryMetric?.contains("17,3") == true)
    }

    @Test
    fun manualReshow_recomputesTotalFromPickupAndTripWhenMissing() {
        val presentation = factory.buildFromSeenOffer(
            seenOffer = seenOffer(
                price = 18.41,
                valuePerKm = null,
                pickupDistanceKm = 5.6,
                tripDistanceKm = 11.7,
                totalDistanceKm = null
            ),
            createdAtMs = 2_000L
        )

        assertTrue(presentation.secondaryMetric?.contains("17,3") == true)
        assertTrue(presentation.details.any { it.contains("Busca: 7 min / 5,6 km") })
        assertTrue(presentation.details.any { it.contains("Corrida: 17 min / 11,7 km") })
    }

    @Test
    fun manualReshow_usesSavedOfferDetailsInsteadOfPartialManualObservation() {
        val presentation = factory.buildFromSeenOffer(
            seenOffer = seenOffer(
                price = 7.20,
                valuePerKm = 1.36,
                pickupDistanceKm = 1.6,
                tripDistanceKm = 3.7,
                totalDistanceKm = 5.3,
                originPreview = "Origem salva",
                destinationPreview = "Destino salvo"
            ),
            createdAtMs = 2_000L
        )

        assertEquals("Oferta ja registrada", presentation.title)
        assertTrue(presentation.priceText?.contains("7,20") == true)
        assertTrue(presentation.primaryMetric?.contains("1,36") == true)
        assertTrue(presentation.secondaryMetric?.contains("5,3") == true)
        assertTrue(presentation.details.any { it.contains("Busca: 7 min / 1,6 km") })
        assertTrue(presentation.details.any { it.contains("Corrida: 17 min / 3,7 km") })
        assertTrue(presentation.details.any { it.contains("Origem: Origem salva") })
        assertTrue(presentation.details.any { it.contains("Destino: Destino salvo") })
    }

    private fun seenOffer(
        price: Double,
        valuePerKm: Double?,
        pickupDistanceKm: Double?,
        tripDistanceKm: Double?,
        totalDistanceKm: Double?,
        originPreview: String? = "Origem",
        destinationPreview: String? = "Destino"
    ) = SeenOffer(
        id = "seen-1",
        observationId = "obs-1",
        platform = RidePlatform.UBER,
        sourceTrigger = "UBER_DOMINANT_OFFER_DIAGNOSTIC",
        status = SeenOfferStatus.SEEN,
        price = price,
        valuePerKm = valuePerKm,
        pickupDistanceKm = pickupDistanceKm,
        pickupTimeMin = 7.0,
        tripDistanceKm = tripDistanceKm,
        tripTimeMin = 17.0,
        totalDistanceKm = totalDistanceKm,
        estimatedTotalTimeMin = 24.0,
        productName = "UberX",
        originPreview = originPreview,
        destinationPreview = destinationPreview,
        rawTextPreview = "UberX R$ 18,41 7 min (5,6 km) 17 minutos (11,7 km)",
        score = 10,
        rawTextHash = "hash",
        routeTextHash = "route",
        createdAtMs = 1_000L,
        updatedAtMs = 1_000L
    )
}
