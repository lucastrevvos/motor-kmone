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
import org.junit.Assert.assertTrue
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
        assertEquals(12.5 / 6.0, seenOffer?.valuePerKm ?: 0.0, 0.01)
        assertTrue(seenOffer?.originPreview?.contains("Avenida dos Merlins") == true)
        assertTrue(seenOffer?.destinationPreview?.contains("Rua X") == true)
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

    @Test
    fun ninetyNine_prefersSegmentedDistancesAndKeepsExplicitEconomicsCoherent() {
        val seenOffer = mapper.fromPipelineResult(
            fingerprint = fingerprint(
                kind = OfferTextFingerprintKind.OFFER_LIKE,
                platformHint = PlatformTextHint.NINETY_NINE,
                price = 20.6,
                valuePerKm = 1.3,
                distances = listOf(
                    ExtractedNumericCandidate("21 m", 21.0, "m", "DISTANCE_METERS", 1),
                    ExtractedNumericCandidate("1,9 km", 1.9, "km", "DISTANCE_KM", 2),
                    ExtractedNumericCandidate("14,0 km", 14.0, "km", "DISTANCE_KM", 2)
                ),
                times = listOf(
                    ExtractedNumericCandidate("8 min", 8.0, "min", "TIME_MINUTES", 2),
                    ExtractedNumericCandidate("21 min", 21.0, "min", "TIME_MINUTES", 2)
                )
            ),
            observation = observation(
                rawText = "Pagamento no app R$20,60 R$1,30/km Taxa de deslocamento R$2,42 8min (1,9km) 21min (14,0km)"
            ),
            parserResult = OfferParserResult(
                status = "parsed",
                reason = "parsed",
                draft = parsedDraft(
                    platform = ParsedPlatform.NINETY_NINE,
                    price = 20.6,
                    valuePerKm = 1.3,
                    pickupTimeMinutes = 8.0,
                    pickupDistanceKm = null,
                    tripTimeMinutes = 21.0,
                    tripDistanceKm = null,
                    rawTextPreview = "Pagamento no app R$20,60 R$1,30/km Taxa de deslocamento R$2,42"
                )
            )
        )

        assertNotNull(seenOffer)
        assertEquals(RidePlatform.NINETY_NINE, seenOffer?.platform)
        assertEquals(1.9, seenOffer?.pickupDistanceKm ?: 0.0, 0.01)
        assertEquals(14.0, seenOffer?.tripDistanceKm ?: 0.0, 0.01)
        assertEquals(15.9, seenOffer?.totalDistanceKm ?: 0.0, 0.05)
        assertEquals(20.6 / 15.9, seenOffer?.valuePerKm ?: 0.0, 0.02)
    }

    @Test
    fun ninetyNine_rejectsTinyMeterTripWhenLargerKmCandidateExists() {
        val seenOffer = mapper.fromPipelineResult(
            fingerprint = fingerprint(
                kind = OfferTextFingerprintKind.OFFER_LIKE,
                platformHint = PlatformTextHint.NINETY_NINE,
                price = 21.4,
                valuePerKm = 1.3,
                distances = listOf(
                    ExtractedNumericCandidate("858 m", 858.0, "m", "DISTANCE_METERS", 2),
                    ExtractedNumericCandidate("25 m", 25.0, "m", "DISTANCE_METERS", 1),
                    ExtractedNumericCandidate("17,6 km", 17.6, "km", "DISTANCE_KM", 2)
                ),
                times = listOf(
                    ExtractedNumericCandidate("5 min", 5.0, "min", "TIME_MINUTES", 2),
                    ExtractedNumericCandidate("25 min", 25.0, "min", "TIME_MINUTES", 2)
                )
            ),
            observation = observation(
                rawText = "Pagamento no app R$21,40 5min (858m) Taxa de deslocamento 25min 17,6km"
            ),
            parserResult = OfferParserResult(
                status = "parsed",
                reason = "parsed",
                draft = parsedDraft(
                    platform = ParsedPlatform.NINETY_NINE,
                    price = 21.4,
                    valuePerKm = 1.3,
                    pickupTimeMinutes = 5.0,
                    pickupDistanceKm = null,
                    tripTimeMinutes = 25.0,
                    tripDistanceKm = null,
                    rawTextPreview = "Pagamento no app R$21,40 R$1,30/km"
                )
            )
        )

        assertNotNull(seenOffer)
        assertEquals(0.858, seenOffer?.pickupDistanceKm ?: 0.0, 0.001)
        assertEquals(17.6, seenOffer?.tripDistanceKm ?: 0.0, 0.001)
        assertEquals(18.458, seenOffer?.totalDistanceKm ?: 0.0, 0.01)
        assertEquals(21.4 / 18.458, seenOffer?.valuePerKm ?: 0.0, 0.02)
    }

    private fun parsedDraft(
        platform: ParsedPlatform = ParsedPlatform.UBER,
        price: Double = 12.5,
        valuePerKm: Double = 2.1,
        pickupTimeMinutes: Double? = 4.0,
        pickupDistanceKm: Double? = 1.2,
        tripTimeMinutes: Double? = 10.0,
        tripDistanceKm: Double? = 4.8,
        rawTextPreview: String = "UberX R$ 12,50 4 min (1,2 km) 10 min (4,8 km)"
    ) = ParsedOfferDraft(
        parserVersion = "test",
        observationId = "obs-1",
        clusterId = "cluster-1",
        platform = platform,
        product = "UberX",
        paymentMethod = null,
        price = ParsedMoney(price, sourceText = "R$ $price", confidence = 0.9),
        valuePerKm = ParsedMoney(valuePerKm, sourceText = "R$ $valuePerKm/km", confidence = 0.8),
        pickupTimeMinutes = pickupTimeMinutes?.let { ParsedNumber(it, "min", confidence = 0.8) },
        pickupDistanceKm = pickupDistanceKm?.let { ParsedNumber(it, "km", confidence = 0.8) },
        tripTimeMinutes = tripTimeMinutes?.let { ParsedNumber(it, "min", confidence = 0.8) },
        tripDistanceKm = tripDistanceKm?.let { ParsedNumber(it, "km", confidence = 0.8) },
        multiplier = null,
        rating = null,
        passengerInfo = null,
        rawTextPreview = rawTextPreview,
        confidence = ParsedOfferConfidence(0.9, 0.9, 0.8, 0.9, 0.7),
        warnings = emptyList(),
        sanityStatus = ParsedOfferSanityStatus.VALID,
        sanityIssues = emptyList(),
        shouldBlockEconomicDecisionFuture = false,
        createdAtMs = 1000L
    )

    private fun observation(
        rawText: String = "UberX R$ 12,50 4 min (1,2 km) Avenida dos Merlins, Jurere Oeste, Florianopolis 10 min (4,8 km) Rua X"
    ) = OcrObservation(
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
        rawText = rawText,
        lineCount = 1,
        blockCount = 1,
        errorMessage = null,
        offerCycleKind = OfferCycleKind.NEW_OFFER_CYCLE,
        shouldPreferForOcr = true
    )

    private fun fingerprint(
        kind: OfferTextFingerprintKind,
        platformHint: PlatformTextHint = PlatformTextHint.UBER,
        price: Double = 12.5,
        valuePerKm: Double = 2.1,
        distances: List<ExtractedNumericCandidate> = listOf(
            ExtractedNumericCandidate("1,2 km", 1.2, "km", "DISTANCE_KM", 2),
            ExtractedNumericCandidate("4,8 km", 4.8, "km", "DISTANCE_KM", 2)
        ),
        times: List<ExtractedNumericCandidate> = listOf(
            ExtractedNumericCandidate("4 min", 4.0, "min", "TIME_MINUTES", 2),
            ExtractedNumericCandidate("10 min", 10.0, "min", "TIME_MINUTES", 2)
        )
    ) = OfferTextFingerprint(
        fingerprintId = "fp-1",
        ocrObservationId = "ocr-1",
        observationId = "obs-1",
        captureRequestId = "req-1",
        triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
        cropKind = CropKind.CENTER_CARD_AREA,
        platformTextHint = platformHint,
        kind = kind,
        offerLikeScore = 9,
        nonOfferScore = 1,
        positiveSignals = listOf(ExtractedSignal("offer", "UberX", 3)),
        negativeSignals = emptyList(),
        priceCandidates = listOf(ExtractedNumericCandidate("R$ $price", price, "BRL", "PRICE", 3)),
        valuePerKmCandidates = listOf(ExtractedNumericCandidate("R$ $valuePerKm/km", valuePerKm, "BRL_PER_KM", "VALUE_PER_KM", 3)),
        distanceCandidates = distances,
        timeCandidates = times,
        rawTextHash = "hash",
        routeTextHash = "route",
        normalizedPreview = "UberX R$ 12,50",
        reason = "offer_like_positive_signals",
        createdAtMs = 1000L
    )
}
