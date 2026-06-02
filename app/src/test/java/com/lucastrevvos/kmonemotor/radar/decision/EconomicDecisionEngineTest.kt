package com.lucastrevvos.kmonemotor.radar.decision

import com.lucastrevvos.kmonemotor.radar.parser.ParsedMoney
import com.lucastrevvos.kmonemotor.radar.parser.ParsedNumber
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferConfidence
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferDraft
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferSanityIssue
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferSanityStatus
import com.lucastrevvos.kmonemotor.radar.parser.ParsedPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomicDecisionEngineTest {
    private val engine = EconomicDecisionEngine()

    @Test
    fun sanityBlockedGeneratesBlocked() {
        val result = engine.evaluate(input(draft(blockEconomic = true)))

        assertEquals(EconomicDecisionKind.BLOCKED, result.decision)
        assertEquals(EconomicDecisionReason.SANITY_BLOCKED, result.reasons.first())
    }

    @Test
    fun missingPriceGeneratesUnknown() {
        val result = engine.evaluate(input(draft(price = null)))

        assertEquals(EconomicDecisionKind.UNKNOWN, result.decision)
        assertEquals(EconomicDecisionReason.MISSING_PRICE, result.reasons.first())
    }

    @Test
    fun missingTripDistanceWithoutValuePerKmGeneratesUnknown() {
        val result = engine.evaluate(input(draft(tripDistanceKm = null, valuePerKm = null)))

        assertEquals(EconomicDecisionKind.UNKNOWN, result.decision)
        assertEquals(EconomicDecisionReason.MISSING_TRIP_DISTANCE, result.reasons.first())
    }

    @Test
    fun belowMinTotalKmGeneratesBad() {
        val result = engine.evaluate(input(draft(price = 8.96, pickupDistanceKm = 9.5, pickupTimeMin = 14.0, tripDistanceKm = 4.8, tripTimeMin = 10.0)))

        assertEquals(EconomicDecisionKind.BAD, result.decision)
        assertEquals(0.63, result.metrics.grossPerTotalKm ?: 0.0, 0.0)
        assertTrue(result.reasons.contains(EconomicDecisionReason.BELOW_MIN_TOTAL_KM))
        assertTrue(result.reasons.contains(EconomicDecisionReason.PICKUP_DISTANCE_GREATER_THAN_TRIP))
        assertTrue(result.reasons.contains(EconomicDecisionReason.PICKUP_TIME_GREATER_THAN_TRIP))
    }

    @Test
    fun betweenMinAndGoodGeneratesWarning() {
        val result = engine.evaluate(input(draft(price = 14.0, pickupDistanceKm = 2.0, tripDistanceKm = 6.0)))

        assertEquals(EconomicDecisionKind.WARNING, result.decision)
        assertTrue(result.reasons.contains(EconomicDecisionReason.ABOVE_MIN_TOTAL_KM))
    }

    @Test
    fun aboveGoodGeneratesGood() {
        val result = engine.evaluate(input(draft(price = 18.0, pickupDistanceKm = 1.0, tripDistanceKm = 7.0)))

        assertEquals(EconomicDecisionKind.GOOD, result.decision)
        assertTrue(result.reasons.contains(EconomicDecisionReason.ABOVE_GOOD_TOTAL_KM))
    }

    @Test
    fun explicitValuePerKmIsUsedAsSignal() {
        val result = engine.evaluate(input(draft(price = 9.10, pickupDistanceKm = 0.666, tripDistanceKm = 3.5, valuePerKm = 2.18)))

        assertTrue(result.metrics.valuePerKmExplicit == 2.18)
        assertTrue(result.decision == EconomicDecisionKind.GOOD || result.decision == EconomicDecisionKind.WARNING)
    }

    @Test
    fun plausibleUberEconomicsDoesNotBecomeUnknown() {
        val result = engine.evaluate(
            input(
                draft(
                    price = 16.16,
                    pickupDistanceKm = 0.1,
                    pickupTimeMin = 1.0,
                    tripDistanceKm = 5.7,
                    tripTimeMin = null,
                    valuePerKm = null
                )
            )
        )

        assertTrue(result.decision == EconomicDecisionKind.GOOD || result.decision == EconomicDecisionKind.WARNING)
        assertEquals(2.79, result.metrics.grossPerTotalKm ?: 0.0, 0.02)
    }

    @Test
    fun ninetyNineDecisionMetricsUsePickupPlusTripAndMainPrice() {
        val result = engine.evaluate(
            input(
                draft(
                    price = 24.10,
                    pickupDistanceKm = 2.4,
                    pickupTimeMin = 6.0,
                    tripDistanceKm = 16.7,
                    tripTimeMin = 21.0,
                    valuePerKm = 1.26,
                    platform = ParsedPlatform.NINETY_NINE
                )
            )
        )

        assertEquals(19.1, result.metrics.totalDistanceKm ?: 0.0, 0.0)
        assertEquals(1.26, result.metrics.grossPerTotalKm ?: 0.0, 0.01)
        assertTrue(result.metrics.totalDistanceKm != 37.7)
    }

    @Test
    fun lowConfidenceRouteAddsReason() {
        val result = engine.evaluate(input(draft(sanityIssues = listOf(ParsedOfferSanityIssue.LOW_CONFIDENCE_ROUTE))))

        assertTrue(result.reasons.contains(EconomicDecisionReason.LOW_CONFIDENCE_ROUTE))
        assertTrue(result.confidence < 0.8)
    }

    private fun input(draft: ParsedOfferDraft) = EconomicDecisionInput(
        observationId = draft.observationId,
        clusterId = draft.clusterId,
        parsedOffer = draft,
        source = DecisionSource.AUTOMATIC,
        createdAtMs = 2_000L
    )

    private fun draft(
        price: Double? = 18.0,
        pickupDistanceKm: Double? = 1.0,
        pickupTimeMin: Double? = 4.0,
        tripDistanceKm: Double? = 7.0,
        tripTimeMin: Double? = 7.0,
        valuePerKm: Double? = null,
        platform: ParsedPlatform = ParsedPlatform.UBER,
        blockEconomic: Boolean = false,
        sanityStatus: ParsedOfferSanityStatus = ParsedOfferSanityStatus.VALID,
        sanityIssues: List<ParsedOfferSanityIssue> = emptyList()
    ) = ParsedOfferDraft(
        parserVersion = "4.5-draft",
        observationId = "obs-1",
        clusterId = "cluster-1",
        platform = platform,
        product = "UberX",
        paymentMethod = null,
        price = price?.let { ParsedMoney(it, sourceText = "R$$it", confidence = 0.95) },
        valuePerKm = valuePerKm?.let { ParsedMoney(it, sourceText = "R$$it/km", confidence = 0.95) },
        pickupTimeMinutes = pickupTimeMin?.let { ParsedNumber(it, "min", "pickup", 0.9) },
        pickupDistanceKm = pickupDistanceKm?.let { ParsedNumber(it, "km", "pickup", 0.9) },
        tripTimeMinutes = tripTimeMin?.let { ParsedNumber(it, "min", "trip", 0.9) },
        tripDistanceKm = tripDistanceKm?.let { ParsedNumber(it, "km", "trip", 0.9) },
        multiplier = null,
        rating = null,
        passengerInfo = null,
        rawTextPreview = "preview",
        confidence = ParsedOfferConfidence(overall = 0.8, price = 0.95, route = 0.9, platform = 0.9, product = 0.9),
        warnings = emptyList(),
        sanityStatus = sanityStatus,
        sanityIssues = sanityIssues,
        shouldBlockEconomicDecisionFuture = blockEconomic,
        createdAtMs = 1_000L
    )
}
