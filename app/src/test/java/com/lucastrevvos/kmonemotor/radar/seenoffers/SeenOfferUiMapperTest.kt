package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals("R$/km -", model.valuePerKmLabel)
        assertEquals("-", model.totalDistanceLabel)
    }

    @Test
    fun ninetyNine_segmentedDistances_showAuditedTotalAndExplicitEconomics() {
        val model = mapper.map(
            offer(
                platform = RidePlatform.NINETY_NINE,
                price = 20.60,
                valuePerKm = 1.30,
                pickupDistanceKm = 1.9,
                tripDistanceKm = 14.0,
                totalDistanceKm = 1.9
            )
        )

        assertEquals("15,9 km", model.totalDistanceLabel)
        assertEquals("R$ 1,30/km", model.valuePerKmLabel)
    }

    @Test
    fun ninetyNine_pickupOnlyWrongTotal_doesNotShowTenPlusPerKmWhenExplicitValuePerKmIsLow() {
        val model = mapper.map(
            offer(
                platform = RidePlatform.NINETY_NINE,
                price = 20.60,
                valuePerKm = 1.30,
                pickupDistanceKm = 1.9,
                tripDistanceKm = null,
                totalDistanceKm = 1.9
            )
        )

        assertEquals("15,8 km", model.totalDistanceLabel)
        assertEquals("R$ 1,30/km", model.valuePerKmLabel)
    }

    @Test
    fun ninetyNineComplete_usesAuditedValueAndCollapsedSummary() {
        val model = mapper.map(
            offer(
                platform = RidePlatform.NINETY_NINE,
                sourceTrigger = "NINETY_NINE_VISUAL_PROBE",
                price = 22.20,
                valuePerKm = 2.42,
                pickupDistanceKm = 0.605,
                pickupTimeMin = 6.0,
                tripDistanceKm = 8.6,
                tripTimeMin = 13.0,
                totalDistanceKm = 9.205
            )
        )

        assertEquals("R$ 22,20", model.priceLabel)
        assertEquals("R$ 2,42/km", model.valuePerKmLabel)
        assertEquals("9,2 km total \u2022 6 min busca \u2022 13 min viagem", model.collapsedSummaryLabel)
        assertEquals("AUTO", model.sourceBadgeLabel)
        assertEquals("99", model.platformLabel)
    }

    @Test
    fun ninetyNineComplete_manualPartialDoesNotOverrideAuditedSeenOffer() {
        val model = mapper.map(
            offer(
                platform = RidePlatform.NINETY_NINE,
                sourceTrigger = "NINETY_NINE_VISUAL_PROBE",
                price = 11.98,
                valuePerKm = 2.26,
                pickupDistanceKm = 0.6,
                tripDistanceKm = 4.7,
                totalDistanceKm = 5.3
            )
        )

        assertEquals("R$ 2,26/km", model.valuePerKmLabel)
        assertEquals("5,3 km", model.totalDistanceLabel)
        assertFalse(model.valuePerKmLabel.contains("2,25"))
        assertFalse(model.totalDistanceLabel.contains("3,2"))
    }

    @Test
    fun missingDestinationAndOrigin_useHumanText() {
        val model = mapper.map(
            offer(
                originPreview = null,
                destinationPreview = null
            )
        )

        assertEquals("N\u00E3o identificada", model.originLabel)
        assertEquals("N\u00E3o identificado", model.destinationLabel)
    }

    @Test
    fun decisionBadge_usesConfiguredThresholds() {
        assertEquals(
            "BOA",
            mapper.map(offer(price = 22.20, valuePerKm = 2.42, totalDistanceKm = 9.205)).decisionBadgeLabel
        )
        assertEquals(
            "ATEN\u00C7\u00C3O",
            mapper.map(offer(price = 12.75, valuePerKm = 1.70, totalDistanceKm = 7.5)).decisionBadgeLabel
        )
        assertEquals(
            "RUIM",
            mapper.map(offer(price = 7.20, valuePerKm = 1.10, totalDistanceKm = 6.5)).decisionBadgeLabel
        )
        assertEquals(
            "INCOMPLETA",
            mapper.map(offer(price = null, valuePerKm = null, totalDistanceKm = null)).decisionBadgeLabel
        )
    }

    @Test
    fun collapsedSummary_containsTotalPickupAndTripTextsWhenAvailable() {
        val model = mapper.map(
            offer(
                price = 18.0,
                valuePerKm = 2.0,
                pickupDistanceKm = 1.2,
                pickupTimeMin = 5.0,
                tripDistanceKm = 7.8,
                tripTimeMin = 11.0,
                totalDistanceKm = 9.0
            )
        )

        assertEquals("5 min \u2022 1,2 km", model.pickupLabel)
        assertEquals("11 min \u2022 7,8 km", model.tripLabel)
        assertEquals("9,0 km total \u2022 5 min busca \u2022 11 min viagem", model.collapsedSummaryLabel)
    }

    private fun offer(
        platform: RidePlatform = RidePlatform.UBER,
        sourceTrigger: String = "MANUAL_SCREEN_ANALYSIS",
        price: Double? = 10.34,
        valuePerKm: Double? = 1.60,
        pickupDistanceKm: Double? = 2.0,
        pickupTimeMin: Double = 4.0,
        tripDistanceKm: Double? = 5.0,
        tripTimeMin: Double = 8.0,
        totalDistanceKm: Double? = 7.0,
        originPreview: String? = "Avenida das Nacoes",
        destinationPreview: String? = "Rua Leonel Pereira"
    ) = SeenOffer(
        id = "seen-1",
        observationId = "obs-1",
        platform = platform,
        sourceTrigger = sourceTrigger,
        status = SeenOfferStatus.SEEN,
        price = price,
        valuePerKm = valuePerKm,
        pickupDistanceKm = pickupDistanceKm,
        pickupTimeMin = pickupTimeMin,
        tripDistanceKm = tripDistanceKm,
        tripTimeMin = tripTimeMin,
        totalDistanceKm = totalDistanceKm,
        estimatedTotalTimeMin = 12.0,
        productName = if (platform == RidePlatform.NINETY_NINE) "99" else "UberX",
        originPreview = originPreview,
        destinationPreview = destinationPreview,
        rawTextPreview = "Oferta",
        score = 9,
        rawTextHash = "hash",
        routeTextHash = "route",
        createdAtMs = 1_000L,
        updatedAtMs = 1_000L
    )
}
