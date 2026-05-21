package com.lucastrevvos.kmonemotor.radar.seenoffers

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
}
