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
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingObstructionGuardTest {
    @Test
    fun floatingBoundsSignal_isDetected() {
        val guard = FloatingObstructionGuard(enabled = true)
        val selected = candidate(CropKind.CENTER_CARD_AREA, Rect(0, 400, 1080, 1900))

        val result = guard.evaluate(
            observation = observation(floatingBounds = "820 720 1040 1040"),
            visualResult = visualResult(selected),
            candidates = listOf(selected)
        )

        assertTrue(result.detected)
        assertTrue(result.reason.startsWith("floating_bounds"))
        assertTrue(result.confidence > 0)
    }

    @Test
    fun floatingBoundsExpandedSelection_keepsFloatingSignal() {
        val guard = FloatingObstructionGuard(enabled = true)
        val selected = candidate(CropKind.FLOATING_BOUNDS_EXPANDED, Rect(760, 620, 1080, 1240))
        val center = candidate(CropKind.CENTER_CARD_AREA, Rect(0, 420, 1080, 1940))

        val result = guard.evaluate(
            observation = observation(floatingBounds = "820 720 1040 1040"),
            visualResult = visualResult(selected),
            candidates = listOf(selected, center)
        )

        assertTrue(result.detected)
        assertTrue(result.reason.startsWith("floating_bounds"))
    }

    @Test
    fun nonOverlappingFloatingBounds_doNotMarkCriticalOverlap() {
        val guard = FloatingObstructionGuard(enabled = true)
        val selected = candidate(CropKind.CENTER_CARD_AREA, Rect(0, 400, 1080, 1900))

        val result = guard.evaluate(
            observation = observation(floatingBounds = "0 0 120 120"),
            visualResult = visualResult(selected),
            candidates = listOf(selected)
        )

        assertTrue(result.detected)
        assertFalse(result.overlapsCriticalOfferArea)
        assertEquals(FloatingObstructionAction.NONE, result.suggestedAction)
    }

    @Test
    fun disabledGuard_returnsNoDetection() {
        val guard = FloatingObstructionGuard(enabled = false)
        val selected = candidate(CropKind.CENTER_CARD_AREA, Rect(0, 400, 1080, 1900))

        val result = guard.evaluate(
            observation = observation(floatingBounds = "820 720 1040 1040"),
            visualResult = visualResult(selected),
            candidates = listOf(selected)
        )

        assertFalse(result.detected)
        assertEquals("guard_disabled", result.reason)
        assertEquals(FloatingObstructionAction.NONE, result.suggestedAction)
    }

    private fun observation(
        floatingBounds: String?,
        floatingKind: FloatingWindowKind = FloatingWindowKind.FLOATING_BUBBLE
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
        triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
        dominantPackage = "com.app99.driver",
        floatingPackage = "com.ubercab.driver",
        floatingBounds = floatingBounds,
        floatingKind = floatingKind,
        screenshotWidth = 1080,
        screenshotHeight = 2340,
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
        metadata = ObservationMetadata()
    )

    private fun candidate(kind: CropKind, rect: Rect) = CropCandidate(
        id = kind.name.lowercase(),
        observationId = "obs",
        kind = kind,
        rect = rect,
        width = rect.right - rect.left,
        height = rect.bottom - rect.top,
        reason = "test"
    )

    private fun visualResult(selected: CropCandidate) = VisualOfferProbeResult(
        observationId = "obs",
        startedAtMs = 1L,
        finishedAtMs = 2L,
        durationMs = 1L,
        bestCandidate = selected,
        bestProbe = PixelProbeResult(
            cropId = selected.id,
            cropKind = selected.kind,
            width = selected.width,
            height = selected.height,
            darkPixelRatio = 0.6,
            lightPixelRatio = 0.3,
            contrastScore = 0.8,
            edgeDensityHint = 0.5,
            dominantVisualHint = VisualPlatformHint.UBER_LIGHT_OR_DARK_CARD,
            offerLikeScore = 8,
            rejectionReason = null
        ),
        rankedCandidates = listOf(selected.kind),
        allProbeResults = emptyList(),
        acceptedForOcrFuture = true,
        reason = "selected",
        cropPriorityReason = "test",
        selectedByRule = "test",
        previousBestByRawScore = selected.kind,
        visualFallbackApplied = false,
        originalBestRejectionReason = null
    )
}
