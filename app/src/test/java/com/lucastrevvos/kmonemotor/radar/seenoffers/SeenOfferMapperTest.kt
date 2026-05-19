package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedSignal
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.parser.OfferParserResult
import com.lucastrevvos.kmonemotor.radar.parser.ParsedMoney
import com.lucastrevvos.kmonemotor.radar.parser.ParsedNumber
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferConfidence
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferDraft
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferSanityIssue
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferSanityStatus
import com.lucastrevvos.kmonemotor.radar.parser.ParsedPlatform
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SeenOfferMapperTest {
    private val mapper = SeenOfferMapper()

    @Test
    fun offerLikeWithParsedDraft_createsSeenOffer() {
        val seenOffer = mapper.fromPipelineResult(
            fingerprint = fingerprint(OfferTextFingerprintKind.OFFER_LIKE),
            observation = observation(),
            parserResult = OfferParserResult(
                status = "parsed",
                reason = "parsed",
                draft = parsedDraft()
            )
        )

        assertNotNull(seenOffer)
        assertEquals(RidePlatform.UBER, seenOffer?.platform)
        assertEquals(12.5, seenOffer?.price)
        assertEquals(1.2, seenOffer?.pickupDistanceKm)
        assertEquals(4.8, seenOffer?.tripDistanceKm)
        assertEquals(6.0, seenOffer?.totalDistanceKm)
    }

    @Test
    fun unknownDoesNotCreateSeenOffer() {
        val seenOffer = mapper.fromPipelineResult(
            fingerprint = fingerprint(OfferTextFingerprintKind.UNKNOWN),
            observation = observation(),
            parserResult = null
        )

        assertNull(seenOffer)
    }

    @Test
    fun nonOfferDoesNotCreateSeenOffer() {
        val seenOffer = mapper.fromPipelineResult(
            fingerprint = fingerprint(OfferTextFingerprintKind.NON_OFFER),
            observation = observation(),
            parserResult = null
        )

        assertNull(seenOffer)
    }

    private fun parsedDraft() = ParsedOfferDraft(
        parserVersion = "test",
        observationId = "obs-1",
        clusterId = "cluster-1",
        platform = ParsedPlatform.UBER,
        product = "UberX",
        paymentMethod = null,
        price = ParsedMoney(12.5, sourceText = "R$ 12,50", confidence = 0.9),
        valuePerKm = ParsedMoney(2.1, sourceText = "R$ 2,10/km", confidence = 0.8),
        pickupTimeMinutes = ParsedNumber(4.0, "min", confidence = 0.8),
        pickupDistanceKm = ParsedNumber(1.2, "km", confidence = 0.8),
        tripTimeMinutes = ParsedNumber(10.0, "min", confidence = 0.8),
        tripDistanceKm = ParsedNumber(4.8, "km", confidence = 0.8),
        multiplier = null,
        rating = null,
        passengerInfo = null,
        rawTextPreview = "UberX R$ 12,50 4 min (1,2 km) 10 min (4,8 km)",
        confidence = ParsedOfferConfidence(0.9, 0.9, 0.8, 0.9, 0.7),
        warnings = emptyList(),
        sanityStatus = ParsedOfferSanityStatus.VALID,
        sanityIssues = emptyList(),
        shouldBlockEconomicDecisionFuture = false,
        createdAtMs = 1000L
    )

    private fun observation() = OcrObservation(
        ocrObservationId = "ocr-1",
        observationId = "obs-1",
        captureRequestId = "req-1",
        triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
        cropId = "crop-1",
        cropKind = CropKind.CENTER_CARD_AREA,
        startedAtMs = 1L,
        finishedAtMs = 2L,
        durationMs = 1L,
        success = true,
        rawText = "UberX R$ 12,50 4 min (1,2 km) 10 min (4,8 km)",
        lineCount = 1,
        blockCount = 1,
        errorMessage = null,
        offerCycleKind = OfferCycleKind.NEW_OFFER_CYCLE,
        shouldPreferForOcr = true
    )

    private fun fingerprint(kind: OfferTextFingerprintKind) = OfferTextFingerprint(
        fingerprintId = "fp-1",
        ocrObservationId = "ocr-1",
        observationId = "obs-1",
        captureRequestId = "req-1",
        triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
        cropKind = CropKind.CENTER_CARD_AREA,
        platformTextHint = PlatformTextHint.UBER,
        kind = kind,
        offerLikeScore = 9,
        nonOfferScore = 1,
        positiveSignals = listOf(ExtractedSignal("offer", "UberX", 3)),
        negativeSignals = emptyList(),
        priceCandidates = listOf(ExtractedNumericCandidate("R$ 12,50", 12.5, "BRL", "PRICE", 3)),
        valuePerKmCandidates = listOf(ExtractedNumericCandidate("R$ 2,10/km", 2.1, "BRL_PER_KM", "VALUE_PER_KM", 3)),
        distanceCandidates = listOf(
            ExtractedNumericCandidate("1,2 km", 1.2, "km", "DISTANCE_KM", 2),
            ExtractedNumericCandidate("4,8 km", 4.8, "km", "DISTANCE_KM", 2)
        ),
        timeCandidates = listOf(
            ExtractedNumericCandidate("4 min", 4.0, "min", "TIME_MINUTES", 2),
            ExtractedNumericCandidate("10 min", 10.0, "min", "TIME_MINUTES", 2)
        ),
        rawTextHash = "hash",
        routeTextHash = "route",
        normalizedPreview = "UberX R$ 12,50",
        reason = "offer_like_positive_signals",
        createdAtMs = 1000L
    )
}
