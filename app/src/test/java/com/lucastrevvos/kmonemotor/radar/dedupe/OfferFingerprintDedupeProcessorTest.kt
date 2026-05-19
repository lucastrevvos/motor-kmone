package com.lucastrevvos.kmonemotor.radar.dedupe

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedSignal
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferFingerprintDedupeProcessorTest {
    @Test
    fun process_isCalledForManualFingerprintFlow() {
        val processor = OfferFingerprintDedupeProcessor(
            engine = OfferFingerprintDedupeEngine(OfferFingerprintDedupeStore()),
            clock = RadarClock { 2_000L }
        )

        val result = processor.process(
            fingerprint = fingerprint("obs-manual", createdAtMs = 1_500L),
            ocrObservation = ocrObservation("obs-manual", isManual = true)
        )

        assertTrue(result.decision == OfferDedupeDecision.NEW_OFFER_CANDIDATE ||
            result.decision == OfferDedupeDecision.SAME_OFFER_UPDATED)
        assertEquals(500L, result.fingerprintToDedupeMs)
    }

    @Test
    fun process_isCalledForAutomaticFingerprintFlow() {
        val processor = OfferFingerprintDedupeProcessor(
            engine = OfferFingerprintDedupeEngine(OfferFingerprintDedupeStore()),
            clock = RadarClock { 2_000L }
        )

        val result = processor.process(
            fingerprint = fingerprint("obs-auto", createdAtMs = 1_800L),
            ocrObservation = ocrObservation("obs-auto", isManual = false)
        )

        assertTrue(result.decision == OfferDedupeDecision.NEW_OFFER_CANDIDATE ||
            result.decision == OfferDedupeDecision.SAME_OFFER_UPDATED)
        assertEquals(200L, result.fingerprintToDedupeMs)
    }

    private fun fingerprint(observationId: String, createdAtMs: Long) = OfferTextFingerprint(
        fingerprintId = "fp-$observationId",
        ocrObservationId = "ocr-$observationId",
        observationId = observationId,
        captureRequestId = "req-$observationId",
        triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
        cropKind = CropKind.CENTER_CARD_AREA,
        platformTextHint = PlatformTextHint.UBER,
        kind = OfferTextFingerprintKind.OFFER_LIKE,
        offerLikeScore = 10,
        nonOfferScore = 0,
        positiveSignals = listOf(ExtractedSignal("price", "12.10", 8)),
        negativeSignals = emptyList(),
        priceCandidates = listOf(candidate(12.10, "price", "currency")),
        valuePerKmCandidates = listOf(candidate(2.30, "value_per_km", "currency_per_km")),
        distanceCandidates = listOf(candidate(1.8, "distance", "km")),
        timeCandidates = listOf(candidate(4.0, "time", "min")),
        rawTextHash = "raw-$observationId",
        routeTextHash = "route-a",
        normalizedPreview = "R$ 12,10 4 min 1,8 km",
        reason = "offer_like_positive_signals",
        createdAtMs = createdAtMs
    )

    private fun ocrObservation(observationId: String, isManual: Boolean) = OcrObservation(
        ocrObservationId = "ocr-$observationId",
        observationId = observationId,
        captureRequestId = "req-$observationId",
        triggerSource = if (isManual) TriggerSource.MANUAL_SCREEN_ANALYSIS else TriggerSource.UBER_STATE_TRANSITION,
        cropId = "crop-$observationId",
        cropKind = CropKind.CENTER_CARD_AREA,
        startedAtMs = 1_000L,
        finishedAtMs = 1_400L,
        durationMs = 400L,
        success = true,
        rawText = "R$ 12,10 4 min 1,8 km",
        lineCount = 2,
        blockCount = 1,
        errorMessage = null,
        offerCycleKind = OfferCycleKind.NEW_OFFER_CYCLE,
        shouldPreferForOcr = true,
        analysisEpoch = if (isManual) 12L else 0L,
        isManual = isManual,
        manualReason = if (isManual) "driver_requested_current_screen_analysis" else null
    )

    private fun candidate(value: Double, kind: String, unit: String) = ExtractedNumericCandidate(
        raw = value.toString(),
        normalizedValue = value,
        unit = unit,
        kind = kind,
        confidence = 8
    )
}
