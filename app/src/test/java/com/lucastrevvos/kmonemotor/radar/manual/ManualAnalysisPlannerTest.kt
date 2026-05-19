package com.lucastrevvos.kmonemotor.radar.manual

import android.graphics.Rect
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.observation.ObservationMetadata
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import com.lucastrevvos.kmonemotor.radar.vision.CropCandidate
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import com.lucastrevvos.kmonemotor.radar.vision.VisualOfferProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ManualAnalysisPlannerTest {
    private val planner = ManualAnalysisPlanner()

    @Test
    fun selectsCenterCardFallbackWhenVisualDoesNotAccept() {
        val selection = planner.selectPrimaryCandidate(
            observation = manualObservation(),
            visualResult = visualResult(acceptedForOcrFuture = false, bestCandidate = null),
            candidates = listOf(
                candidate("center", CropKind.CENTER_CARD_AREA),
                candidate("lower", CropKind.LOWER_HALF)
            )
        )

        assertNotNull(selection)
        assertEquals(CropKind.CENTER_CARD_AREA, selection?.candidate?.kind)
        assertEquals("manual_fallback_center_card", selection?.reason)
    }

    @Test
    fun selectsLowerHalfAsSecondaryWhenFirstFingerprintIsNotOfferLike() {
        val selection = planner.selectSecondaryCandidate(
            primary = candidate("center", CropKind.CENTER_CARD_AREA),
            fingerprintKind = OfferTextFingerprintKind.UNKNOWN,
            candidates = listOf(
                candidate("center", CropKind.CENTER_CARD_AREA),
                candidate("lower", CropKind.LOWER_HALF)
            )
        )

        assertNotNull(selection)
        assertEquals(CropKind.LOWER_HALF, selection?.candidate?.kind)
        assertEquals("manual_fallback_lower_half", selection?.reason)
    }

    @Test
    fun doesNotSelectSecondaryWhenFirstFingerprintIsOfferLike() {
        val selection = planner.selectSecondaryCandidate(
            primary = candidate("center", CropKind.CENTER_CARD_AREA),
            fingerprintKind = OfferTextFingerprintKind.OFFER_LIKE,
            candidates = listOf(
                candidate("center", CropKind.CENTER_CARD_AREA),
                candidate("lower", CropKind.LOWER_HALF)
            )
        )

        assertNull(selection)
    }

    private fun manualObservation() = ScreenObservation(
        id = "obs",
        createdAtMs = 1L,
        requestCreatedAtMs = 1L,
        captureApprovedAtMs = 2L,
        screenshotStartedAtMs = 3L,
        screenshotFinishedAtMs = 4L,
        observationCreatedAtMs = 5L,
        capturedAtMs = 4L,
        captureRequestId = "req",
        triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
        dominantPackage = "com.ubercab.driver",
        floatingPackage = "com.app99.driver",
        floatingBounds = "0 0 100 100",
        floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
        screenshotWidth = 1080,
        screenshotHeight = 2400,
        captureLatencyMs = 20L,
        eventToObservationMs = 40L,
        visualPlatformHint = null,
        offerCycleClassification = null,
        metadata = ObservationMetadata()
    )

    private fun candidate(id: String, kind: CropKind) = CropCandidate(
        id = id,
        observationId = "obs",
        kind = kind,
        rect = Rect(0, 0, 500, 500),
        width = 500,
        height = 500,
        reason = "test"
    )

    private fun visualResult(
        acceptedForOcrFuture: Boolean,
        bestCandidate: CropCandidate?
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
        reason = "test",
        cropPriorityReason = "test",
        selectedByRule = "test",
        previousBestByRawScore = null,
        visualFallbackApplied = false,
        originalBestRejectionReason = null
    )
}
