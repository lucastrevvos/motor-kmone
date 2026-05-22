package com.lucastrevvos.kmonemotor.radar.vision

import android.graphics.Rect
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NinetyNineVisualProbeFallbackTest {
    @Test
    fun noValidCropCandidate_appliesCenterCardFallbackForNinetyNineProbe() {
        val center = candidate("center", CropKind.CENTER_CARD_AREA)
        val lower = candidate("lower", CropKind.LOWER_HALF)
        val full = candidate("full", CropKind.FULL_DEBUG)
        val visualResult = visualResult(
            bestCandidate = null,
            reason = "no_valid_crop_candidate",
            probes = listOf(
                probe(center),
                probe(lower),
                probe(full)
            )
        )

        val decision = NinetyNineVisualProbeFallback.applyIfNeeded(
            triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
            visualResult = visualResult,
            candidates = listOf(center, lower, full)
        )

        assertTrue(decision.applied)
        assertEquals(CropKind.CENTER_CARD_AREA, decision.fallbackCropKind)
        assertEquals(CropKind.CENTER_CARD_AREA, decision.visualResult.bestCandidate?.kind)
        assertTrue(decision.visualResult.acceptedForOcrFuture)
    }

    @Test
    fun noValidCropCandidate_doesNotApplyForUberTrigger() {
        val center = candidate("center", CropKind.CENTER_CARD_AREA)
        val visualResult = visualResult(
            bestCandidate = null,
            reason = "no_valid_crop_candidate",
            probes = listOf(probe(center))
        )

        val decision = NinetyNineVisualProbeFallback.applyIfNeeded(
            triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            visualResult = visualResult,
            candidates = listOf(center)
        )

        assertFalse(decision.applied)
        assertEquals(null, decision.visualResult.bestCandidate)
    }

    private fun candidate(id: String, kind: CropKind) = CropCandidate(
        id = id,
        observationId = "obs-1",
        kind = kind,
        rect = Rect(0, 0, 100, 100),
        width = 100,
        height = 100,
        reason = kind.name
    )

    private fun probe(candidate: CropCandidate) = PixelProbeResult(
        cropId = candidate.id,
        cropKind = candidate.kind,
        width = candidate.width,
        height = candidate.height,
        darkPixelRatio = 0.2,
        lightPixelRatio = 0.8,
        contrastScore = 0.5,
        edgeDensityHint = 0.3,
        dominantVisualHint = VisualPlatformHint.UNKNOWN,
        offerLikeScore = 1,
        rejectionReason = "looks_like_map_or_home"
    )

    private fun visualResult(
        bestCandidate: CropCandidate?,
        reason: String,
        probes: List<PixelProbeResult>
    ) = VisualOfferProbeResult(
        observationId = "obs-1",
        startedAtMs = 0L,
        finishedAtMs = 10L,
        durationMs = 10L,
        bestCandidate = bestCandidate,
        bestProbe = null,
        rankedCandidates = probes.map { it.cropKind },
        allProbeResults = probes,
        acceptedForOcrFuture = false,
        reason = reason,
        cropPriorityReason = "ninety_nine_priority",
        selectedByRule = "no_valid_crop_candidate",
        previousBestByRawScore = null,
        visualFallbackApplied = false,
        originalBestRejectionReason = null
    )
}
