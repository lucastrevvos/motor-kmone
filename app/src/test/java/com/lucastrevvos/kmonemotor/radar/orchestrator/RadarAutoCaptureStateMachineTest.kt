package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarAutoCaptureStateMachineTest {
    @Test
    fun idleToPreOfferMapState() {
        val machine = RadarAutoCaptureStateMachine()

        val transition = machine.transitionToPreOffer("map_or_searching_signal", searchingEvidence(1_000L))

        assertEquals(RadarAutoCaptureState.IDLE, transition?.from)
        assertEquals(RadarAutoCaptureState.PRE_OFFER_MAP_STATE, transition?.to)
    }

    @Test
    fun etaRangeIsolated_staysInPreOfferMapState() {
        val machine = RadarAutoCaptureStateMachine()

        val transition = machine.transitionToPreOffer("map_eta_range_without_offer_evidence", etaRangeEvidence(1_000L))

        assertEquals(RadarAutoCaptureState.IDLE, transition?.from)
        assertEquals(RadarAutoCaptureState.PRE_OFFER_MAP_STATE, transition?.to)
    }

    @Test
    fun candidateToStabilizing() {
        val machine = RadarAutoCaptureStateMachine()
        val evidence = strongEvidence(1_000L)

        machine.transitionToCandidate("offer_card_tree_signal", evidence)
        val transition = machine.transitionToStabilizing("stabilization_started", evidence)

        assertEquals(RadarAutoCaptureState.OFFER_CARD_CANDIDATE, transition?.from)
        assertEquals(RadarAutoCaptureState.OFFER_CARD_STABILIZING, transition?.to)
    }

    @Test
    fun stabilizingToPreOfferWhenOperationalAppears() {
        val machine = RadarAutoCaptureStateMachine()
        val evidence = strongEvidence(1_000L)
        machine.transitionToCandidate("offer_card_tree_signal", evidence)
        machine.transitionToStabilizing("stabilization_started", evidence)

        val transition = machine.transitionToPreOffer("stabilization_cancelled_operational_text", searchingEvidence(1_200L))

        assertEquals(RadarAutoCaptureState.OFFER_CARD_STABILIZING, transition?.from)
        assertEquals(RadarAutoCaptureState.PRE_OFFER_MAP_STATE, transition?.to)
    }

    @Test
    fun stabilizingToConfirmed() {
        val machine = RadarAutoCaptureStateMachine()
        val evidence = strongEvidence(1_000L)
        machine.transitionToCandidate("offer_card_tree_signal", evidence)
        machine.transitionToStabilizing("stabilization_started", evidence)

        val transition = machine.transitionToConfirmed("stabilization_confirmed", evidence)

        assertEquals(RadarAutoCaptureState.OFFER_CARD_STABILIZING, transition?.from)
        assertEquals(RadarAutoCaptureState.OFFER_CARD_CONFIRMED, transition?.to)
    }

    @Test
    fun confirmedToCaptureInProgress() {
        val machine = RadarAutoCaptureStateMachine()
        val evidence = strongEvidence(1_000L)
        machine.transitionToCandidate("offer_card_tree_signal", evidence)
        machine.transitionToStabilizing("stabilization_started", evidence)
        machine.transitionToConfirmed("stabilization_confirmed", evidence)

        val transition = machine.transitionToCaptureInProgress("capture_request_created", evidence)

        assertEquals(RadarAutoCaptureState.OFFER_CARD_CONFIRMED, transition?.from)
        assertEquals(RadarAutoCaptureState.CAPTURE_IN_PROGRESS, transition?.to)
    }

    @Test
    fun captureInProgressToOfferCapturedThenCooldown() {
        val machine = RadarAutoCaptureStateMachine()
        val evidence = strongEvidence(1_000L)
        machine.transitionToCandidate("offer_card_tree_signal", evidence)
        machine.transitionToStabilizing("stabilization_started", evidence)
        machine.transitionToConfirmed("stabilization_confirmed", evidence)
        machine.transitionToCaptureInProgress("capture_request_created", evidence)

        val transitions = machine.onPipelineFinished(
            AutoCapturePipelineResult(
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                fingerprintKind = "OFFER_LIKE",
                wasPersisted = true,
                finalReason = "saved",
                timestampMs = 2_000L
            )
        )

        assertEquals(RadarAutoCaptureState.OFFER_CAPTURED, transitions[0].to)
        assertEquals(RadarAutoCaptureState.POST_OFFER_COOLDOWN, transitions[1].to)
        assertTrue(machine.shouldBlockAutomaticCapture(2_500L))
    }

    @Test
    fun cooldownExpiresToIdle() {
        val machine = RadarAutoCaptureStateMachine()
        val evidence = strongEvidence(1_000L)
        machine.transitionToCandidate("offer_card_tree_signal", evidence)
        machine.transitionToStabilizing("stabilization_started", evidence)
        machine.transitionToConfirmed("stabilization_confirmed", evidence)
        machine.transitionToCaptureInProgress("capture_request_created", evidence)
        machine.onPipelineFinished(
            AutoCapturePipelineResult(
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                fingerprintKind = "OFFER_LIKE",
                wasPersisted = true,
                finalReason = "saved",
                timestampMs = 2_000L
            )
        )

        val transition = machine.expireCooldownIfNeeded(4_500L)

        assertEquals(RadarAutoCaptureState.POST_OFFER_COOLDOWN, transition?.from)
        assertEquals(RadarAutoCaptureState.IDLE, transition?.to)
    }

    @Test
    fun failedCaptureReturnsToPreOffer() {
        val machine = RadarAutoCaptureStateMachine()
        val evidence = strongEvidence(1_000L)
        machine.transitionToCandidate("offer_card_tree_signal", evidence)
        machine.transitionToStabilizing("stabilization_started", evidence)
        machine.transitionToConfirmed("stabilization_confirmed", evidence)
        machine.transitionToCaptureInProgress("capture_request_created", evidence)

        val transitions = machine.onPipelineFinished(
            AutoCapturePipelineResult(
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                fingerprintKind = "UNKNOWN",
                wasPersisted = false,
                finalReason = "capture_result_map_or_unknown",
                timestampMs = 2_000L
            )
        )

        assertEquals(1, transitions.size)
        assertEquals(RadarAutoCaptureState.PRE_OFFER_MAP_STATE, transitions.single().to)
    }

    @Test
    fun manualTriggerDoesNotAffectAutoStateMachine() {
        val machine = RadarAutoCaptureStateMachine()
        val transitions = machine.onPipelineFinished(
            AutoCapturePipelineResult(
                triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
                fingerprintKind = "OFFER_LIKE",
                wasPersisted = true,
                finalReason = "saved",
                timestampMs = 2_000L
            )
        )

        assertTrue(transitions.isEmpty())
        assertFalse(machine.shouldBlockAutomaticCapture(2_500L))
    }

    private fun strongEvidence(timestampMs: Long) = RadarAutoCaptureEvidence(
        triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
        hasPriceText = true,
        hasUberProductText = true,
        hasRoutePairText = true,
        hasSearchingText = false,
        treeScore = 8,
        matchedConditions = listOf("numeric_text_with_visible_text"),
        knownStateTexts = listOf("uberx", "r$ 10,00", "4 min (1.3 km)"),
        timestampMs = timestampMs
    )

    private fun searchingEvidence(timestampMs: Long) = RadarAutoCaptureEvidence(
        triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
        hasPriceText = false,
        hasUberProductText = false,
        hasRoutePairText = false,
        hasSearchingText = true,
        treeScore = 0,
        matchedConditions = emptyList(),
        knownStateTexts = listOf("procurando viagens"),
        timestampMs = timestampMs
    )

    private fun etaRangeEvidence(timestampMs: Long) = RadarAutoCaptureEvidence(
        triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
        hasPriceText = false,
        hasUberProductText = false,
        hasRoutePairText = false,
        hasSearchingText = false,
        treeScore = 2,
        matchedConditions = emptyList(),
        knownStateTexts = listOf("1-15 min", "1-11 min", "1-5 min"),
        timestampMs = timestampMs
    )
}
