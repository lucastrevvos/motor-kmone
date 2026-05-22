package com.lucastrevvos.kmonemotor.radar.vision

import android.graphics.Rect
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.observation.ObservationMetadata
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualOfferProbeTest {
    private val probe = VisualOfferProbe(context = null)

    @Test
    fun fullDebug_neverWinsBestCandidate() {
        val observation = observation(triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE)
        val candidates = listOf(
            candidate("full", CropKind.FULL_DEBUG),
            candidate("lower", CropKind.LOWER_HALF)
        )
        val results = listOf(
            result("full", CropKind.FULL_DEBUG, score = 10),
            result("lower", CropKind.LOWER_HALF, score = 5)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertNotEquals(CropKind.FULL_DEBUG, ranking.bestCandidate?.kind)
    }

    @Test
    fun uberOver99_prefersCenterCardAreaWhenScoreAtLeastFive() {
        val observation = observation(triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC)
        val candidates = listOf(
            candidate("center", CropKind.CENTER_CARD_AREA),
            candidate("lower", CropKind.LOWER_HALF),
            candidate("float", CropKind.FLOATING_BOUNDS_EXPANDED)
        )
        val results = listOf(
            result("center", CropKind.CENTER_CARD_AREA, score = 5),
            result("lower", CropKind.LOWER_HALF, score = 9),
            result("float", CropKind.FLOATING_BOUNDS_EXPANDED, score = 8)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertEquals(CropKind.CENTER_CARD_AREA, ranking.bestCandidate?.kind)
        assertEquals("selected_by_trigger_priority_center_card_area", ranking.reason)
    }

    @Test
    fun uberDominantDiagnostic_prefersLowerHalfWhenValidEvenIfCenterScoresHigher() {
        val observation = observation(triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC)
        val candidates = listOf(
            candidate("center", CropKind.CENTER_CARD_AREA),
            candidate("lower", CropKind.LOWER_HALF),
            candidate("third", CropKind.LOWER_THIRD)
        )
        val results = listOf(
            result("center", CropKind.CENTER_CARD_AREA, score = 5),
            result("lower", CropKind.LOWER_HALF, score = 9),
            result("third", CropKind.LOWER_THIRD, score = 8)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertEquals(CropKind.LOWER_HALF, ranking.bestCandidate?.kind)
        assertEquals("selected_by_trigger_priority_lower_half", ranking.reason)
    }

    @Test
    fun uberDominantDiagnostic_withAllRejectedCrops_usesLowerHalfFallbackBeforeCenter() {
        val observation = observation(triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC)
        val candidates = listOf(
            candidate("center", CropKind.CENTER_CARD_AREA),
            candidate("lower", CropKind.LOWER_HALF),
            candidate("third", CropKind.LOWER_THIRD),
            candidate("full", CropKind.FULL_DEBUG)
        )
        val results = listOf(
            result("center", CropKind.CENTER_CARD_AREA, score = 1, rejectionReason = "almost_all_light"),
            result("lower", CropKind.LOWER_HALF, score = 2, rejectionReason = "looks_like_map_or_home"),
            result("third", CropKind.LOWER_THIRD, score = 1, rejectionReason = "looks_like_map_or_home"),
            result("full", CropKind.FULL_DEBUG, score = 8, rejectionReason = null)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertEquals(CropKind.LOWER_HALF, ranking.bestCandidate?.kind)
        assertEquals("accepted_by_strong_uber_dominant_signal", ranking.reason)
        assertEquals("uber_dominant_signal_fallback", ranking.selectedByRule)
        assertTrue(ranking.acceptedForOcrFuture)
        assertTrue(ranking.visualFallbackApplied)
        assertEquals("looks_like_map_or_home", ranking.originalBestRejectionReason)
    }

    @Test
    fun ninetyNine_prefersLowerHalfWhenScoreAtLeastFive() {
        val observation = observation(triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE)
        val candidates = listOf(
            candidate("lower", CropKind.LOWER_HALF),
            candidate("third", CropKind.LOWER_THIRD),
            candidate("center", CropKind.CENTER_CARD_AREA)
        )
        val results = listOf(
            result("lower", CropKind.LOWER_HALF, score = 5),
            result("third", CropKind.LOWER_THIRD, score = 8),
            result("center", CropKind.CENTER_CARD_AREA, score = 9)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertEquals(CropKind.LOWER_HALF, ranking.bestCandidate?.kind)
        assertEquals("selected_by_trigger_priority_lower_half", ranking.reason)
    }

    @Test
    fun ninetyNineCompactDiagnostic_prefersLowerHalfWhenScoreAtLeastFive() {
        val observation = observation(triggerSource = TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC)
        val candidates = listOf(
            candidate("lower", CropKind.LOWER_HALF),
            candidate("third", CropKind.LOWER_THIRD),
            candidate("center", CropKind.CENTER_CARD_AREA)
        )
        val results = listOf(
            result("lower", CropKind.LOWER_HALF, score = 5),
            result("third", CropKind.LOWER_THIRD, score = 8),
            result("center", CropKind.CENTER_CARD_AREA, score = 9)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertEquals(CropKind.LOWER_HALF, ranking.bestCandidate?.kind)
        assertEquals("selected_by_trigger_priority_lower_half", ranking.reason)
    }

    @Test
    fun ninetyNineVisualProbe_prefersLowerHalfThenCenter() {
        val observation = observation(
            triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
            metadata = ObservationMetadata(
                notes = mapOf(
                    "ninetyNineVisualProbePreferredCropOrder" to "LOWER_HALF,CENTER_CARD_AREA,LOWER_THIRD,FULL_DEBUG"
                )
            )
        )
        val candidates = listOf(
            candidate("center", CropKind.CENTER_CARD_AREA),
            candidate("lower", CropKind.LOWER_HALF),
            candidate("third", CropKind.LOWER_THIRD)
        )
        val results = listOf(
            result("center", CropKind.CENTER_CARD_AREA, score = 9),
            result("lower", CropKind.LOWER_HALF, score = 5),
            result("third", CropKind.LOWER_THIRD, score = 8)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertEquals(CropKind.LOWER_HALF, ranking.bestCandidate?.kind)
    }

    @Test
    fun ninetyNine_doesNotUseUberDominantFallback() {
        val observation = observation(triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE)
        val candidates = listOf(
            candidate("center", CropKind.CENTER_CARD_AREA),
            candidate("lower", CropKind.LOWER_HALF)
        )
        val results = listOf(
            result("center", CropKind.CENTER_CARD_AREA, score = 1, rejectionReason = "almost_all_light"),
            result("lower", CropKind.LOWER_HALF, score = 2, rejectionReason = "looks_like_map_or_home")
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertNull(ranking.bestCandidate)
        assertFalse(ranking.visualFallbackApplied)
    }

    @Test
    fun uberOver99_doesNotUseUberDominantFallback() {
        val observation = observation(triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC)
        val candidates = listOf(
            candidate("center", CropKind.CENTER_CARD_AREA),
            candidate("lower", CropKind.LOWER_HALF)
        )
        val results = listOf(
            result("center", CropKind.CENTER_CARD_AREA, score = 1, rejectionReason = "almost_all_light"),
            result("lower", CropKind.LOWER_HALF, score = 2, rejectionReason = "looks_like_map_or_home")
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertNull(ranking.bestCandidate)
        assertFalse(ranking.visualFallbackApplied)
    }

    @Test
    fun systemUiFloating_preventsFloatingBoundsExpandedWinningFor99() {
        val observation = observation(
            triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            floatingKind = FloatingWindowKind.SYSTEM_UI_FLOATING
        )
        val candidates = listOf(
            candidate("float", CropKind.FLOATING_BOUNDS_EXPANDED),
            candidate("center", CropKind.CENTER_CARD_AREA)
        )
        val results = listOf(
            result("float", CropKind.FLOATING_BOUNDS_EXPANDED, score = 8),
            result("center", CropKind.CENTER_CARD_AREA, score = 7)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertEquals(CropKind.CENTER_CARD_AREA, ranking.bestCandidate?.kind)
    }

    @Test
    fun autoBurstPreferredCropOrder_prioritizesLowerHalf() {
        val observation = observation(
            triggerSource = TriggerSource.UBER_AUTO_BURST_RECOVERY,
            metadata = ObservationMetadata(
                notes = mapOf(
                    "autoBurstPreferredCropOrder" to "LOWER_HALF,PLATFORM_SPECIFIC_CANDIDATE,FLOATING_BOUNDS_EXPANDED,CENTER_CARD_AREA"
                )
            )
        )
        val candidates = listOf(
            candidate("center", CropKind.CENTER_CARD_AREA),
            candidate("lower", CropKind.LOWER_HALF)
        )
        val results = listOf(
            result("center", CropKind.CENTER_CARD_AREA, score = 8),
            result("lower", CropKind.LOWER_HALF, score = 5)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertEquals(CropKind.LOWER_HALF, ranking.bestCandidate?.kind)
        assertEquals("selected_by_trigger_priority_lower_half", ranking.reason)
    }

    @Test
    fun preOfferWatchdog_prefersFloatingBoundsExpanded() {
        val observation = observation(
            triggerSource = TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
            metadata = ObservationMetadata(
                notes = mapOf(
                    "preOfferWatchdogPreferredCropOrder" to "FLOATING_BOUNDS_EXPANDED,LOWER_HALF,LOWER_THIRD,CENTER_CARD_AREA"
                )
            )
        )
        val candidates = listOf(
            candidate("float", CropKind.FLOATING_BOUNDS_EXPANDED),
            candidate("lower", CropKind.LOWER_HALF),
            candidate("center", CropKind.CENTER_CARD_AREA)
        )
        val results = listOf(
            result("float", CropKind.FLOATING_BOUNDS_EXPANDED, score = 5),
            result("lower", CropKind.LOWER_HALF, score = 9),
            result("center", CropKind.CENTER_CARD_AREA, score = 8)
        )

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertEquals(CropKind.FLOATING_BOUNDS_EXPANDED, ranking.bestCandidate?.kind)
        assertEquals("selected_by_score_after_priority", ranking.reason)
    }

    @Test
    fun noValidCandidate_returnsNullBestCandidate() {
        val observation = observation(triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE)
        val candidates = listOf(candidate("full", CropKind.FULL_DEBUG))
        val results = listOf(result("full", CropKind.FULL_DEBUG, score = 9))

        val ranking = probe.rankCandidates(observation, candidates, results)

        assertNull(ranking.bestCandidate)
        assertEquals("no_valid_crop_candidate", ranking.reason)
    }

    private fun observation(
        triggerSource: TriggerSource,
        floatingKind: FloatingWindowKind = FloatingWindowKind.UNKNOWN_FLOATING,
        metadata: ObservationMetadata = ObservationMetadata()
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
        dominantPackage = "com.app99.driver",
        floatingPackage = "com.ubercab.driver",
        floatingBounds = "901 392 1080 571",
        floatingKind = floatingKind,
        screenshotWidth = 1080,
        screenshotHeight = 2400,
        captureLatencyMs = 2L,
        eventToObservationMs = 5L,
        visualPlatformHint = null,
        offerCycleClassification = OfferCycleClassification(
            kind = OfferCycleKind.UNKNOWN,
            cycleId = "cycle-1",
            previousCycleId = null,
            reason = "test",
            timeSincePreviousMs = null,
            shouldPreferForOcr = false
        ),
        metadata = metadata
    )

    private fun candidate(id: String, kind: CropKind) = CropCandidate(
        id = id,
        observationId = "obs",
        kind = kind,
        rect = Rect(0, 0, 600, 600),
        width = 600,
        height = 600,
        reason = "test"
    )

    private fun result(
        cropId: String,
        kind: CropKind,
        score: Int,
        rejectionReason: String? = null
    ) = PixelProbeResult(
        cropId = cropId,
        cropKind = kind,
        width = 600,
        height = 600,
        darkPixelRatio = 0.3,
        lightPixelRatio = 0.4,
        contrastScore = 0.2,
        edgeDensityHint = 0.2,
        dominantVisualHint = VisualPlatformHint.UBER_LIGHT_OR_DARK_CARD,
        offerLikeScore = score,
        rejectionReason = rejectionReason
    )
}
