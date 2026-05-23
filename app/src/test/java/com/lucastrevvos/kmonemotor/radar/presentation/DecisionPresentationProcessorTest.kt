package com.lucastrevvos.kmonemotor.radar.presentation

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.decision.DecisionSource
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionKind
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionMetrics
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionProcessResult
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionReason
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DecisionPresentationProcessorTest {
    @Test
    fun buildsAfterEconomicDecision() {
        val processor = DecisionPresentationProcessor(clock = RadarClock { 2_000L })

        val result = processor.process(decisionResult(EconomicDecisionKind.GOOD, DecisionSource.AUTOMATIC))

        assertEquals("built", result.status)
        assertNotNull(result.presentation)
    }

    @Test
    fun badAutomaticWithPlausibleEconomicsBuildsVisiblePresentation() {
        val processor = DecisionPresentationProcessor(clock = RadarClock { 2_000L })

        val result = processor.process(decisionResult(EconomicDecisionKind.BAD, DecisionSource.AUTOMATIC))

        assertEquals("built", result.status)
        assertEquals(DecisionPresentationKind.SHOW_BAD, result.presentation?.kind)
    }

    @Test
    fun unknownAutomaticIsSkippedButKeepsDoNotShowPresentation() {
        val processor = DecisionPresentationProcessor(clock = RadarClock { 2_000L })

        val result = processor.process(decisionResult(EconomicDecisionKind.UNKNOWN, DecisionSource.AUTOMATIC))

        assertEquals("skipped", result.status)
        assertEquals("unknown_automatic", result.reason)
        assertEquals(DecisionPresentationKind.DO_NOT_SHOW, result.presentation?.kind)
    }

    @Test
    fun skippedDecisionDoesNotBuildPresentation() {
        val processor = DecisionPresentationProcessor(clock = RadarClock { 2_000L })

        val result = processor.process(
            EconomicDecisionProcessResult(status = "skipped", reason = "dedupe_weaker")
        )

        assertEquals("skipped", result.status)
        assertNull(result.presentation)
    }

    @Test
    fun debugWriterFailureDoesNotBreakPipeline() {
        val processor = DecisionPresentationProcessor(
            clock = RadarClock { 2_000L },
            debugWriter = object : PresentationDebugWriter {
                override fun write(presentation: DecisionPresentation) {
                    throw IllegalStateException("boom")
                }
            }
        )

        val result = processor.process(decisionResult(EconomicDecisionKind.WARNING, DecisionSource.MANUAL))

        assertEquals("built", result.status)
        assertNotNull(result.presentation)
    }

    private fun decisionResult(
        kind: EconomicDecisionKind,
        source: DecisionSource
    ) = EconomicDecisionProcessResult(
        status = "evaluated",
        reason = kind.name.lowercase(),
        result = EconomicDecisionResult(
            observationId = "obs-1",
            clusterId = "cluster-1",
            decision = kind,
            score = 1,
            confidence = 0.8,
            metrics = EconomicDecisionMetrics(
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
            reasons = listOf(
                when (kind) {
                    EconomicDecisionKind.GOOD -> EconomicDecisionReason.ABOVE_GOOD_TOTAL_KM
                    EconomicDecisionKind.WARNING -> EconomicDecisionReason.SHORT_TRIP
                    EconomicDecisionKind.BAD -> EconomicDecisionReason.BELOW_MIN_TOTAL_KM
                    EconomicDecisionKind.BLOCKED -> EconomicDecisionReason.SANITY_BLOCKED
                    EconomicDecisionKind.UNKNOWN -> EconomicDecisionReason.MISSING_TRIP_DISTANCE
                }
            ),
            warnings = emptyList(),
            source = source,
            createdAtMs = 1_000L
        ),
        durationMs = 10L
    )
}
