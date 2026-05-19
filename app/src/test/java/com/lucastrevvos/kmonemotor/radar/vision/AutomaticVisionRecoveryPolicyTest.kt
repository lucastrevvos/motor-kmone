package com.lucastrevvos.kmonemotor.radar.vision

import android.graphics.Rect
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.observation.ObservationMetadata
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticVisionRecoveryPolicyTest {
    @Test
    fun uberFloatingNoValidCrop_prefersLowerHalf() {
        val policy = AutomaticVisionRecoveryPolicy()

        val decision = policy.decide(
            observation = observation(triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC),
            visualResult = visualResult(bestCandidate = null, reason = "no_valid_crop_candidate"),
            candidates = listOf(candidate(CropKind.CENTER_CARD_AREA), candidate(CropKind.LOWER_HALF)),
            nowMs = 2_000L
        )

        assertTrue(decision.shouldForceOcr)
        assertEquals(CropKind.LOWER_HALF, decision.selectedCandidate?.kind)
        assertEquals("ocr_allowed_auto_recovery_lower_half", decision.reason)
    }

    @Test
    fun uberFloatingFallsBackToCenterWhenLowerHalfTooSmall() {
        val policy = AutomaticVisionRecoveryPolicy()

        val decision = policy.decide(
            observation = observation(triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC),
            visualResult = visualResult(bestCandidate = null, reason = "no_valid_crop_candidate"),
            candidates = listOf(
                candidate(CropKind.LOWER_HALF, width = 20, height = 20),
                candidate(CropKind.CENTER_CARD_AREA)
            ),
            nowMs = 2_000L
        )

        assertTrue(decision.shouldForceOcr)
        assertEquals(CropKind.CENTER_CARD_AREA, decision.selectedCandidate?.kind)
        assertEquals("ocr_allowed_auto_recovery_center_card", decision.reason)
    }

    @Test
    fun uberDominantNoValidCrop_prefersCenterCard() {
        val policy = AutomaticVisionRecoveryPolicy()

        val decision = policy.decide(
            observation = observation(triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC),
            visualResult = visualResult(bestCandidate = null, reason = "no_valid_crop_candidate"),
            candidates = listOf(candidate(CropKind.LOWER_HALF), candidate(CropKind.CENTER_CARD_AREA)),
            nowMs = 2_000L
        )

        assertTrue(decision.shouldForceOcr)
        assertEquals(CropKind.CENTER_CARD_AREA, decision.selectedCandidate?.kind)
    }

    @Test
    fun nonEligibleTrigger_doesNotApplyRecovery() {
        val policy = AutomaticVisionRecoveryPolicy()

        val decision = policy.decide(
            observation = observation(triggerSource = TriggerSource.DOMINANT_WINDOW_CHANGE),
            visualResult = visualResult(bestCandidate = null, reason = "no_valid_crop_candidate"),
            candidates = listOf(candidate(CropKind.CENTER_CARD_AREA)),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldForceOcr)
        assertEquals("not_eligible_trigger", decision.suppressionReason)
    }

    @Test
    fun visualNotAcceptedOnStrongTrigger_forcesAcceptance() {
        val policy = AutomaticVisionRecoveryPolicy()

        val decision = policy.decide(
            observation = observation(triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC),
            visualResult = visualResult(
                bestCandidate = candidate(CropKind.CENTER_CARD_AREA),
                acceptedForOcrFuture = false,
                reason = "selected_by_score_after_priority"
            ),
            candidates = emptyList(),
            nowMs = 2_000L
        )

        assertTrue(decision.shouldForceOcr)
        assertTrue(decision.forcedAcceptance)
        assertEquals("ocr_allowed_auto_recovery_visual_not_accepted", decision.reason)
    }

    @Test
    fun recentUberDominantPostTransition_allowsSingleOverride() {
        val policy = AutomaticVisionRecoveryPolicy()
        val observation = observation(
            triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            cycleKind = OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION,
            requestCreatedAtMs = 100L
        )

        val decision = policy.decide(
            observation = observation,
            visualResult = visualResult(
                bestCandidate = candidate(CropKind.CENTER_CARD_AREA),
                reason = "accepted_by_strong_uber_dominant_signal"
            ),
            candidates = emptyList(),
            nowMs = 2_000L
        )

        assertTrue(decision.shouldForceOcr)
        assertTrue(decision.overridePostTransition)
        assertEquals("ocr_allowed_auto_post_transition_override", decision.reason)
    }

    @Test
    fun oldUberDominantPostTransition_doesNotOverride() {
        val policy = AutomaticVisionRecoveryPolicy()
        val observation = observation(
            triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            cycleKind = OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION,
            requestCreatedAtMs = 100L
        )

        val decision = policy.decide(
            observation = observation,
            visualResult = visualResult(
                bestCandidate = candidate(CropKind.CENTER_CARD_AREA),
                reason = "accepted_by_strong_uber_dominant_signal"
            ),
            candidates = emptyList(),
            nowMs = 4_000L
        )

        assertFalse(decision.shouldForceOcr)
        assertEquals("not_needed", decision.suppressionReason)
    }

    @Test
    fun sameObservation_isLimitedToOneRecoveryAttempt() {
        val policy = AutomaticVisionRecoveryPolicy()
        val observation = observation(triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC)
        val first = policy.decide(
            observation = observation,
            visualResult = visualResult(bestCandidate = null, reason = "no_valid_crop_candidate"),
            candidates = listOf(candidate(CropKind.CENTER_CARD_AREA)),
            nowMs = 2_000L
        )
        val second = policy.decide(
            observation = observation,
            visualResult = visualResult(bestCandidate = null, reason = "no_valid_crop_candidate"),
            candidates = listOf(candidate(CropKind.CENTER_CARD_AREA)),
            nowMs = 4_000L
        )

        assertTrue(first.shouldForceOcr)
        assertFalse(second.shouldForceOcr)
        assertEquals("attempt_limit_reached", second.suppressionReason)
    }

    @Test
    fun cooldownSuppressesImmediateSecondRecovery() {
        val policy = AutomaticVisionRecoveryPolicy()
        val first = policy.decide(
            observation = observation(id = "obs-1", triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC),
            visualResult = visualResult(bestCandidate = null, reason = "no_valid_crop_candidate"),
            candidates = listOf(candidate(CropKind.CENTER_CARD_AREA)),
            nowMs = 2_000L
        )
        val second = policy.decide(
            observation = observation(id = "obs-2", triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC),
            visualResult = visualResult(bestCandidate = null, reason = "no_valid_crop_candidate"),
            candidates = listOf(candidate(CropKind.LOWER_HALF)),
            nowMs = 3_000L
        )

        assertTrue(first.shouldForceOcr)
        assertFalse(second.shouldForceOcr)
        assertEquals("cooldown_suppressed", second.suppressionReason)
    }

    @Test
    fun manualObservation_isIgnoredByRecovery() {
        val policy = AutomaticVisionRecoveryPolicy()

        val decision = policy.decide(
            observation = observation(
                triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
                isManual = true
            ),
            visualResult = visualResult(bestCandidate = null, reason = "no_valid_crop_candidate"),
            candidates = listOf(candidate(CropKind.CENTER_CARD_AREA)),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldForceOcr)
        assertEquals("not_eligible_trigger", decision.suppressionReason)
        assertNull(decision.selectedCandidate)
    }

    private fun observation(
        id: String = "obs",
        triggerSource: TriggerSource,
        cycleKind: OfferCycleKind = OfferCycleKind.NEW_OFFER_CYCLE,
        requestCreatedAtMs: Long = 500L,
        isManual: Boolean = false
    ) = ScreenObservation(
        id = id,
        createdAtMs = 100L,
        requestCreatedAtMs = requestCreatedAtMs,
        captureApprovedAtMs = requestCreatedAtMs + 50L,
        screenshotStartedAtMs = requestCreatedAtMs + 60L,
        screenshotFinishedAtMs = requestCreatedAtMs + 80L,
        observationCreatedAtMs = requestCreatedAtMs + 80L,
        capturedAtMs = requestCreatedAtMs + 80L,
        captureRequestId = "req-$id",
        triggerSource = triggerSource,
        dominantPackage = "com.ubercab.driver",
        floatingPackage = "com.ubercab.driver",
        floatingBounds = "0 0 1080 1200",
        floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
        screenshotWidth = 1080,
        screenshotHeight = 2400,
        captureLatencyMs = 30L,
        eventToObservationMs = 80L,
        visualPlatformHint = null,
        offerCycleClassification = OfferCycleClassification(
            kind = cycleKind,
            cycleId = "cycle-1",
            previousCycleId = null,
            reason = "test",
            timeSincePreviousMs = null,
            shouldPreferForOcr = cycleKind == OfferCycleKind.NEW_OFFER_CYCLE
        ),
        isManual = isManual,
        metadata = ObservationMetadata(isManual = isManual)
    )

    private fun visualResult(
        bestCandidate: CropCandidate?,
        acceptedForOcrFuture: Boolean = true,
        reason: String
    ) = VisualOfferProbeResult(
        observationId = "obs",
        startedAtMs = 1L,
        finishedAtMs = 2L,
        durationMs = 1L,
        bestCandidate = bestCandidate,
        bestProbe = null,
        rankedCandidates = emptyList(),
        allProbeResults = emptyList(),
        acceptedForOcrFuture = acceptedForOcrFuture,
        reason = reason,
        cropPriorityReason = "test",
        selectedByRule = "test",
        previousBestByRawScore = null,
        visualFallbackApplied = false,
        originalBestRejectionReason = null
    )

    private fun candidate(
        kind: CropKind,
        width: Int = 600,
        height: Int = 600
    ) = CropCandidate(
        id = "crop-$kind-$width-$height",
        observationId = "obs",
        kind = kind,
        rect = Rect(0, 0, width, height),
        width = width,
        height = height,
        reason = "test"
    )
}
