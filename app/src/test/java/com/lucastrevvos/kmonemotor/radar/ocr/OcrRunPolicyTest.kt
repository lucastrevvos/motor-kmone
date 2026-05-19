package com.lucastrevvos.kmonemotor.radar.ocr

import android.graphics.Rect
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.observation.ObservationMetadata
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import com.lucastrevvos.kmonemotor.radar.vision.CropCandidate
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import com.lucastrevvos.kmonemotor.radar.vision.PixelProbeResult
import com.lucastrevvos.kmonemotor.radar.vision.VisualOfferProbeResult
import com.lucastrevvos.kmonemotor.radar.vision.VisualPlatformHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrRunPolicyTest {
    @Test
    fun disabledByFlag_skipsOcr() {
        val policy = OcrRunPolicy(
            OcrRunPolicy.OcrFlags(enableRegionalOcr = false)
        )

        val decision = policy.decide(observation(OfferCycleKind.NEW_OFFER_CYCLE), visualResult())

        assertFalse(decision.shouldRun)
        assertEquals("ocr_disabled_by_flag", decision.reason)
    }

    @Test
    fun noBestCandidate_skipsOcr() {
        val policy = OcrRunPolicy()

        val decision = policy.decide(observation(OfferCycleKind.NEW_OFFER_CYCLE), visualResult(bestCandidate = null))

        assertFalse(decision.shouldRun)
        assertEquals("ocr_skipped_no_best_candidate", decision.reason)
    }

    @Test
    fun visualNotAccepted_skipsOcr() {
        val policy = OcrRunPolicy()

        val decision = policy.decide(
            observation(OfferCycleKind.NEW_OFFER_CYCLE),
            visualResult(acceptedForOcrFuture = false)
        )

        assertFalse(decision.shouldRun)
        assertEquals("ocr_skipped_visual_not_accepted", decision.reason)
    }

    @Test
    fun newOfferCycle_allowsOcr() {
        val policy = OcrRunPolicy()

        val decision = policy.decide(observation(OfferCycleKind.NEW_OFFER_CYCLE), visualResult())

        assertTrue(decision.shouldRun)
        assertEquals("ocr_allowed_new_offer_cycle", decision.reason)
    }

    @Test
    fun followup_allowsOcrWhenFlagEnabled() {
        val policy = OcrRunPolicy(
            OcrRunPolicy.OcrFlags(enableOcrOnFollowup = true)
        )

        val decision = policy.decide(observation(OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP), visualResult())

        assertTrue(decision.shouldRun)
        assertEquals("ocr_allowed_followup_diagnostic", decision.reason)
    }

    @Test
    fun postTransition_skipsOcrByDefault() {
        val policy = OcrRunPolicy(
            OcrRunPolicy.OcrFlags(enableOcrOnPostTransition = false)
        )

        val decision = policy.decide(observation(OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION), visualResult())

        assertFalse(decision.shouldRun)
        assertEquals("ocr_skipped_post_transition", decision.reason)
    }

    @Test
    fun unknown_allowsOcrWhenFlagEnabled() {
        val policy = OcrRunPolicy(
            OcrRunPolicy.OcrFlags(enableOcrOnUnknown = true)
        )

        val decision = policy.decide(observation(OfferCycleKind.UNKNOWN), visualResult())

        assertTrue(decision.shouldRun)
        assertEquals("ocr_allowed_unknown_diagnostic", decision.reason)
    }

    @Test
    fun manualBestCandidate_allowsOcrWithoutOfferCycle() {
        val policy = OcrRunPolicy()

        val decision = policy.decide(
            observation(OfferCycleKind.UNKNOWN, TriggerSource.MANUAL_SCREEN_ANALYSIS),
            visualResult()
        )

        assertTrue(decision.shouldRun)
        assertEquals("ocr_allowed_manual_best_candidate", decision.reason)
    }

    @Test
    fun manualFallbackCenterCard_isAllowed() {
        val policy = OcrRunPolicy()

        val decision = policy.decideManualFallback(CropKind.CENTER_CARD_AREA)

        assertTrue(decision.shouldRun)
        assertEquals("ocr_allowed_manual_fallback_center_card", decision.reason)
    }

    private fun observation(
        cycleKind: OfferCycleKind,
        triggerSource: TriggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC
    ) = ScreenObservation(
        id = "obs",
        createdAtMs = 1L,
        requestCreatedAtMs = 1L,
        captureApprovedAtMs = 2L,
        screenshotStartedAtMs = 3L,
        screenshotFinishedAtMs = 4L,
        observationCreatedAtMs = 5L,
        capturedAtMs = 4L,
        captureRequestId = "req",
        triggerSource = triggerSource,
        dominantPackage = "com.ubercab.driver",
        floatingPackage = "com.app99.driver",
        floatingBounds = "0 0 1080 1200",
        floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
        screenshotWidth = 1080,
        screenshotHeight = 2400,
        captureLatencyMs = 50L,
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
        metadata = ObservationMetadata()
    )

    private fun visualResult(
        bestCandidate: CropCandidate? = candidate(),
        acceptedForOcrFuture: Boolean = true
    ) = VisualOfferProbeResult(
        observationId = "obs",
        startedAtMs = 1L,
        finishedAtMs = 2L,
        durationMs = 1L,
        bestCandidate = bestCandidate,
        bestProbe = PixelProbeResult(
            cropId = "crop",
            cropKind = CropKind.CENTER_CARD_AREA,
            width = 600,
            height = 600,
            darkPixelRatio = 0.2,
            lightPixelRatio = 0.4,
            contrastScore = 0.3,
            edgeDensityHint = 0.2,
            dominantVisualHint = VisualPlatformHint.UBER_LIGHT_OR_DARK_CARD,
            offerLikeScore = 6,
            rejectionReason = null
        ),
        rankedCandidates = listOf(CropKind.CENTER_CARD_AREA),
        allProbeResults = emptyList(),
        acceptedForOcrFuture = acceptedForOcrFuture,
        reason = "selected_by_trigger_priority_center_card_area",
        cropPriorityReason = "uber_dominant_priority",
        selectedByRule = "trigger_priority_override",
        previousBestByRawScore = CropKind.CENTER_CARD_AREA,
        visualFallbackApplied = false,
        originalBestRejectionReason = null
    )

    private fun candidate() = CropCandidate(
        id = "crop",
        observationId = "obs",
        kind = CropKind.CENTER_CARD_AREA,
        rect = Rect(0, 0, 600, 600),
        width = 600,
        height = 600,
        reason = "test"
    )
}
