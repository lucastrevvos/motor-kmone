package com.lucastrevvos.kmonemotor.radar.presentation

import com.lucastrevvos.kmonemotor.radar.decision.DecisionSource
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionKind
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionMetrics
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionReason
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecisionPresentationBuilderTest {
    private val builder = DecisionPresentationBuilder()

    @Test
    fun goodBecomesShowGood() {
        val presentation = builder.build(result(decision = EconomicDecisionKind.GOOD, reasons = listOf(EconomicDecisionReason.ABOVE_GOOD_TOTAL_KM)))

        assertEquals(DecisionPresentationKind.SHOW_GOOD, presentation.kind)
        assertEquals("Boa", presentation.title)
    }

    @Test
    fun warningWithShortTripUsesCorridaCurtaReason() {
        val presentation = builder.build(
            result(
                decision = EconomicDecisionKind.WARNING,
                reasons = listOf(EconomicDecisionReason.ABOVE_GOOD_TOTAL_KM, EconomicDecisionReason.SHORT_TRIP)
            )
        )

        assertEquals(DecisionPresentationKind.SHOW_WARNING, presentation.kind)
        assertEquals("Corrida curta", presentation.shortReason)
    }

    @Test
    fun badWithPickupGreaterThanTripUsesSpecificReason() {
        val presentation = builder.build(
            result(
                decision = EconomicDecisionKind.BAD,
                reasons = listOf(EconomicDecisionReason.PICKUP_DISTANCE_GREATER_THAN_TRIP)
            )
        )

        assertEquals(DecisionPresentationKind.SHOW_BAD, presentation.kind)
        assertEquals("Deslocamento maior que a corrida", presentation.shortReason)
    }

    @Test
    fun blockedAutomaticBecomesDoNotShow() {
        val presentation = builder.build(
            result(
                decision = EconomicDecisionKind.BLOCKED,
                source = DecisionSource.AUTOMATIC,
                reasons = listOf(EconomicDecisionReason.SANITY_BLOCKED)
            )
        )

        assertEquals(DecisionPresentationKind.DO_NOT_SHOW, presentation.kind)
    }

    @Test
    fun unknownAutomaticBecomesDoNotShow() {
        val presentation = builder.build(
            result(
                decision = EconomicDecisionKind.UNKNOWN,
                source = DecisionSource.AUTOMATIC,
                reasons = listOf(EconomicDecisionReason.MISSING_TRIP_DISTANCE)
            )
        )

        assertEquals(DecisionPresentationKind.DO_NOT_SHOW, presentation.kind)
    }

    @Test
    fun unknownManualBecomesShowUnknown() {
        val presentation = builder.build(
            result(
                decision = EconomicDecisionKind.UNKNOWN,
                source = DecisionSource.MANUAL,
                reasons = listOf(EconomicDecisionReason.MISSING_TRIP_DISTANCE)
            )
        )

        assertEquals(DecisionPresentationKind.SHOW_UNKNOWN, presentation.kind)
        assertEquals("Nao deu para decidir", presentation.title)
    }

    @Test
    fun formatsBrazilianMetrics() {
        assertEquals("R$ 5,50", builder.formatMoney(5.5))
        assertEquals("R$ 2,12/km", builder.formatPerKm(2.115))
        assertEquals("2,6 km", builder.formatKm(2.64))
        assertEquals("5 min", builder.formatMinutes(5.0))
    }

    @Test
    fun automaticTtlIsEightSeconds() {
        val presentation = builder.build(result(createdAtMs = 1_000L, source = DecisionSource.AUTOMATIC))

        assertEquals(9_000L, presentation.expiresAtMs)
    }

    @Test
    fun manualTtlIsTenSeconds() {
        val presentation = builder.build(result(createdAtMs = 1_000L, source = DecisionSource.MANUAL))

        assertEquals(11_000L, presentation.expiresAtMs)
    }

    @Test
    fun builderHandlesNullMetrics() {
        val presentation = builder.build(
            result(
                decision = EconomicDecisionKind.UNKNOWN,
                source = DecisionSource.MANUAL,
                metrics = EconomicDecisionMetrics(
                    price = null,
                    pickupDistanceKm = null,
                    pickupTimeMin = null,
                    tripDistanceKm = null,
                    tripTimeMin = null,
                    valuePerKmExplicit = null,
                    grossPerTripKm = null,
                    grossPerTotalKm = null,
                    totalDistanceKm = null,
                    totalTimeMin = null
                ),
                reasons = listOf(EconomicDecisionReason.MISSING_PRICE)
            )
        )

        assertNull(presentation.primaryMetric)
        assertEquals("Dados insuficientes", presentation.shortReason)
    }

    private fun result(
        decision: EconomicDecisionKind = EconomicDecisionKind.WARNING,
        source: DecisionSource = DecisionSource.AUTOMATIC,
        createdAtMs: Long = 1_000L,
        metrics: EconomicDecisionMetrics = EconomicDecisionMetrics(
            price = 5.50,
            pickupDistanceKm = 0.9,
            pickupTimeMin = 3.0,
            tripDistanceKm = 1.7,
            tripTimeMin = 5.0,
            valuePerKmExplicit = null,
            grossPerTripKm = 3.24,
            grossPerTotalKm = 2.12,
            totalDistanceKm = 2.6,
            totalTimeMin = 8.0
        ),
        reasons: List<EconomicDecisionReason> = listOf(EconomicDecisionReason.ABOVE_GOOD_TOTAL_KM)
    ) = EconomicDecisionResult(
        observationId = "obs-1",
        clusterId = "cluster-1",
        decision = decision,
        score = 1,
        confidence = 0.8,
        metrics = metrics,
        reasons = reasons,
        warnings = emptyList(),
        source = source,
        createdAtMs = createdAtMs
    )
}
