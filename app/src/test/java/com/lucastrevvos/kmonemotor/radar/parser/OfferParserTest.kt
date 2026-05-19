package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferDedupeDecision
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferDedupeResult
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedSignal
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserTest {
    private val parser = OfferParser(clock = RadarClock { 2_000L })

    @Test
    fun parsesUberDraftOnNewOfferCandidate() {
        val result = parser.process(
            fingerprint = uberFingerprint(),
            ocrObservation = ocr("UberX Exclusivo R$ 5,50 1 min (0.3 km) 5 minutos (1.7 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals("parsed", result.status)
        assertEquals(ParsedPlatform.UBER, result.draft?.platform)
        assertEquals("UberX Exclusivo", result.draft?.product)
        assertEquals(5.50, result.draft?.price?.value ?: 0.0, 0.0)
        assertEquals(1.0, result.draft?.pickupTimeMinutes?.value ?: 0.0, 0.0)
        assertEquals(0.3, result.draft?.pickupDistanceKm?.value ?: 0.0, 0.0)
        assertEquals(5.0, result.draft?.tripTimeMinutes?.value ?: 0.0, 0.0)
        assertEquals(1.7, result.draft?.tripDistanceKm?.value ?: 0.0, 0.0)
    }

    @Test
    fun parsesUpdatedCluster() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 15.36, createdAtMs = 1_500L),
            ocrObservation = ocr("UberX R$ 15,36 4 min (2.0 km) 15 min (10.4 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.SAME_OFFER_UPDATED)
        )

        assertEquals("parsed", result.status)
        assertNotNull(result.draft)
        assertEquals(ParsedOfferSanityStatus.VALID, result.draft?.sanityStatus)
    }

    @Test
    fun skipsWeakerClusterRead() {
        val result = parser.process(
            fingerprint = uberFingerprint(),
            ocrObservation = ocr("UberX R$ 5,50"),
            dedupeResult = dedupe(OfferDedupeDecision.SAME_OFFER_IGNORED_WEAKER)
        )

        assertEquals("skipped", result.status)
        assertEquals("dedupe_weaker", result.reason)
        assertNull(result.draft)
    }

    @Test
    fun skipsUnknownAndNonOffer() {
        val unknown = parser.process(uberFingerprint(), ocr("mapa"), dedupe(OfferDedupeDecision.UNKNOWN_IGNORED))
        val nonOffer = parser.process(uberFingerprint(), ocr("operacional"), dedupe(OfferDedupeDecision.NON_OFFER_IGNORED))

        assertEquals("unknown", unknown.reason)
        assertEquals("non_offer", nonOffer.reason)
    }

    @Test
    fun parsesNinetyNineDraftWithFeeAndValuePerKm() {
        val result = parser.process(
            fingerprint = ninetyNineFingerprint(),
            ocrObservation = ocr("R$9,10 Dinheiro Preco x1,3 R$1,04 R$2,18/km 4min (666m) 7min (3,5km) Perfil Premium"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals(ParsedPlatform.NINETY_NINE, result.draft?.platform)
        assertEquals(9.10, result.draft?.price?.value ?: 0.0, 0.0)
        assertEquals(2.18, result.draft?.valuePerKm?.value ?: 0.0, 0.0)
        assertEquals("Dinheiro", result.draft?.paymentMethod)
        assertEquals(1.3, result.draft?.multiplier?.value ?: 0.0, 0.0)
        assertTrue(result.draft?.passengerInfo?.contains("Perfil Premium") == true)
        assertEquals(ParsedOfferSanityStatus.VALID, result.draft?.sanityStatus)
    }

    @Test
    fun estimatesTripDistanceOnlyWhenExplicitRouteAbsent() {
        val fingerprint = ninetyNineFingerprint(
            prices = listOf(candidate(9.10, "PRICE", "BRL")),
            valuePerKm = listOf(candidate(2.18, "VALUE_PER_KM", "BRL_PER_KM"))
        )
        val result = parser.process(
            fingerprint = fingerprint,
            ocrObservation = ocr("R$9,10 Dinheiro R$2,18/km 4min (666m)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals(4.17, result.draft?.tripDistanceKm?.value ?: 0.0, 0.0)
        assertTrue(result.draft?.warnings?.contains("trip_distance_estimated_from_value_per_km") == true)
    }

    @Test
    fun explicitTripDistanceIsNotOverwrittenByEstimate() {
        val result = parser.process(
            fingerprint = ninetyNineFingerprint(),
            ocrObservation = ocr("Pagamento no app Corrida longa R$54,50 R$1,23/km 4min (614m) 63min (43,8km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals(43.8, result.draft?.tripDistanceKm?.value ?: 0.0, 0.0)
        assertTrue(result.draft?.warnings?.contains("trip_distance_estimated_from_value_per_km") != true)
    }

    @Test
    fun absurdPriceBecomesInvalidAndBlocksFutureEconomicDecision() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 479.0),
            ocrObservation = ocr("UberX R$479 5 min (2.4 km) 12 minutos (69km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals(ParsedOfferSanityStatus.INVALID, result.draft?.sanityStatus)
        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.PRICE_OUT_OF_PLAUSIBLE_RANGE) == true)
        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.PRICE_PROBABLY_MISSING_DECIMAL_SEPARATOR) == true)
        assertTrue(result.draft?.shouldBlockEconomicDecisionFuture == true)
    }

    @Test
    fun impossibleRoutePairBecomesSuspicious() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 15.15),
            ocrObservation = ocr("UberX R$ 15,15 5 min (2.4 km) 12 minutos (69km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.IMPOSSIBLE_TIME_DISTANCE_PAIR) == true)
        assertTrue(result.draft?.sanityStatus == ParsedOfferSanityStatus.SUSPICIOUS || result.draft?.sanityStatus == ParsedOfferSanityStatus.INVALID)
    }

    @Test
    fun looseUberNumberDoesNotBecomeMultiplier() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 15.15),
            ocrObservation = ocr("UberX 5,00 (29) R$ 15,15 5 min (2.4 km) 12 minutos (6.9 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertNull(result.draft?.multiplier)
        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.FALSE_MULTIPLIER_CONTEXT) != true)
    }

    @Test
    fun uberAlwaysHasNullMultiplierEvenWithExplicitX() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 17.03),
            ocrObservation = ocr("UberX R$ 17,03 x1,3 3 min (0.8 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals(ParsedPlatform.UBER, result.draft?.platform)
        assertNull(result.draft?.multiplier)
        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.FALSE_MULTIPLIER_CONTEXT) != true)
    }

    @Test
    fun uberBrokenRouteGetsLowConfidenceWithoutMultiplierNoise() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 17.03),
            ocrObservation = ocr("UberX R$ 17,03 4,99 (109) l min ( km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertNull(result.draft?.multiplier)
        assertNull(result.draft?.pickupTimeMinutes)
        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.LOW_CONFIDENCE_ROUTE) == true)
        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.FALSE_MULTIPLIER_CONTEXT) != true)
    }

    @Test
    fun unknownWithNinetyNineSignalsCanParseMultiplier() {
        val result = parser.process(
            fingerprint = unknownFingerprint(
                rawPreview = "Pagamento no app Preco x1,3 R$9,10",
                prices = listOf(candidate(9.10, "PRICE", "BRL")),
                valuePerKm = listOf(candidate(2.18, "VALUE_PER_KM", "BRL_PER_KM"))
            ),
            ocrObservation = ocr("Pagamento no app Preco x1,3 R$9,10 R$2,18/km 4min (666m) 7min (3,5km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals(1.3, result.draft?.multiplier?.value ?: 0.0, 0.0)
    }

    @Test
    fun unknownWithUberSignalsDoesNotParseMultiplier() {
        val result = parser.process(
            fingerprint = unknownFingerprint(rawPreview = "UberX x1,3 R$15,15"),
            ocrObservation = ocr("UberX x1,3 R$15,15 3 min (0.8 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertNull(result.draft?.multiplier)
    }

    @Test
    fun parserResolvesFingerprintPlatformConflictInFavorOfUberText() {
        val result = parser.process(
            fingerprint = ninetyNineFingerprint(
                prices = listOf(candidate(5.81, "PRICE", "BRL")),
                valuePerKm = emptyList()
            ),
            ocrObservation = ocr("2 UberX R$ 5,81 4 min (2.3 km) 4 minutos (1.4 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals(ParsedPlatform.UBER, result.draft?.platform)
        assertNull(result.draft?.multiplier)
    }

    @Test
    fun oneGoodPairLowersRouteConfidence() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 8.38),
            ocrObservation = ocr("UberX R$ 8,38 4,86 (397) 3 min (0.8 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertTrue((result.draft?.confidence?.route ?: 0.0) < 0.5)
        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.LOW_CONFIDENCE_ROUTE) == true)
    }

    @Test
    fun twoGoodPairsKeepHighRouteConfidence() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 15.15),
            ocrObservation = ocr("UberX R$ 15,15 5 min (2.4 km) 12 minutos (6.9 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals(0.9, result.draft?.confidence?.route ?: 0.0, 0.0)
    }

    @Test
    fun piuTextGeneratesWarning() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 15.15),
            ocrObservation = ocr("R$ 0,00 Analisar UberX R$ 15,15 5 min (2.4 km) 12 minutos (6.9 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.KMONE_PIU_TEXT_DETECTED) == true)
    }

    @Test
    fun mixedFuelTextOnUberGeneratesWarning() {
        val result = parser.process(
            fingerprint = uberFingerprint(price = 15.15),
            ocrObservation = ocr("UberX 99 Abastece Posto R$4,79 R$ 15,15 5 min (2.4 km) 12 minutos (6.9 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.MIXED_PLATFORM_TEXT_DETECTED) == true)
        assertTrue(result.draft?.sanityIssues?.contains(ParsedOfferSanityIssue.FUEL_OR_ABASTECE_TEXT_DETECTED) == true)
    }

    @Test
    fun debugWriterFailureDoesNotBreakPipeline() {
        val parser = OfferParser(
            clock = RadarClock { 2_000L },
            debugWriter = object : ParserDebugWriter {
                override fun write(result: OfferParserResult) {
                    throw IllegalStateException("boom")
                }
            }
        )
        val result = parser.process(
            fingerprint = uberFingerprint(),
            ocrObservation = ocr("UberX R$ 10,29 9 min (5.4 km) 12 minutos (2.2 km)"),
            dedupeResult = dedupe(OfferDedupeDecision.NEW_OFFER_CANDIDATE)
        )

        assertEquals("parsed", result.status)
    }

    private fun dedupe(decision: OfferDedupeDecision) = OfferDedupeResult(
        decision = decision,
        clusterId = "cluster_1",
        qualityScore = 20,
        reason = decision.name.lowercase(),
        isBestForCluster = decision != OfferDedupeDecision.SAME_OFFER_IGNORED_WEAKER,
        activeClusterCount = 1,
        bestOfferPreview = null,
        bestOfferMainPrice = null,
        bestOfferPlatform = null,
        fingerprintToDedupeMs = 10L,
        dedupeDurationMs = 1L
    )

    private fun uberFingerprint(price: Double = 5.50, createdAtMs: Long = 1_000L) = OfferTextFingerprint(
        fingerprintId = "fp-uber",
        ocrObservationId = "ocr-uber",
        observationId = "obs-uber",
        captureRequestId = "req-uber",
        triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
        cropKind = CropKind.CENTER_CARD_AREA,
        platformTextHint = PlatformTextHint.UBER,
        kind = OfferTextFingerprintKind.OFFER_LIKE,
        offerLikeScore = 12,
        nonOfferScore = 0,
        positiveSignals = listOf(ExtractedSignal("price", "R$", 8)),
        negativeSignals = emptyList(),
        priceCandidates = listOf(candidate(price, "PRICE", "BRL")),
        valuePerKmCandidates = emptyList(),
        distanceCandidates = listOf(candidate(0.3, "DISTANCE_KM", "km"), candidate(1.7, "DISTANCE_KM", "km")),
        timeCandidates = listOf(candidate(1.0, "TIME_MINUTES", "min"), candidate(5.0, "TIME_MINUTES", "min")),
        rawTextHash = "raw-uber",
        routeTextHash = "route-uber",
        normalizedPreview = "UberX Exclusivo R$ 5,50 1 min (0.3 km) 5 minutos (1.7 km)",
        reason = "offer_like_positive_signals",
        createdAtMs = createdAtMs
    )

    private fun ninetyNineFingerprint(
        prices: List<ExtractedNumericCandidate> = listOf(candidate(9.10, "PRICE", "BRL"), candidate(1.04, "PRICE", "BRL")),
        valuePerKm: List<ExtractedNumericCandidate> = listOf(candidate(2.18, "VALUE_PER_KM", "BRL_PER_KM"))
    ) = OfferTextFingerprint(
        fingerprintId = "fp-99",
        ocrObservationId = "ocr-99",
        observationId = "obs-99",
        captureRequestId = "req-99",
        triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE,
        cropKind = CropKind.CENTER_CARD_AREA,
        platformTextHint = PlatformTextHint.NINETY_NINE,
        kind = OfferTextFingerprintKind.OFFER_LIKE,
        offerLikeScore = 12,
        nonOfferScore = 0,
        positiveSignals = listOf(ExtractedSignal("price", "R$", 8)),
        negativeSignals = emptyList(),
        priceCandidates = prices,
        valuePerKmCandidates = valuePerKm,
        distanceCandidates = listOf(candidate(0.666, "DISTANCE_KM", "km"), candidate(3.5, "DISTANCE_KM", "km")),
        timeCandidates = listOf(candidate(4.0, "TIME_MINUTES", "min"), candidate(7.0, "TIME_MINUTES", "min")),
        rawTextHash = "raw-99",
        routeTextHash = "route-99",
        normalizedPreview = "R$9,10 Dinheiro Preco x1,3",
        reason = "offer_like_positive_signals",
        createdAtMs = 1_000L
    )

    private fun unknownFingerprint(
        rawPreview: String,
        prices: List<ExtractedNumericCandidate> = listOf(candidate(15.15, "PRICE", "BRL")),
        valuePerKm: List<ExtractedNumericCandidate> = emptyList()
    ) = OfferTextFingerprint(
        fingerprintId = "fp-unknown",
        ocrObservationId = "ocr-unknown",
        observationId = "obs-unknown",
        captureRequestId = "req-unknown",
        triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
        cropKind = CropKind.CENTER_CARD_AREA,
        platformTextHint = PlatformTextHint.UNKNOWN,
        kind = OfferTextFingerprintKind.OFFER_LIKE,
        offerLikeScore = 10,
        nonOfferScore = 0,
        positiveSignals = listOf(ExtractedSignal("price", "R$", 8)),
        negativeSignals = emptyList(),
        priceCandidates = prices,
        valuePerKmCandidates = valuePerKm,
        distanceCandidates = emptyList(),
        timeCandidates = emptyList(),
        rawTextHash = "raw-unknown",
        routeTextHash = "route-unknown",
        normalizedPreview = rawPreview,
        reason = "offer_like_positive_signals",
        createdAtMs = 1_000L
    )

    private fun ocr(rawText: String) = OcrObservation(
        ocrObservationId = "ocr",
        observationId = "obs",
        captureRequestId = "req",
        triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
        cropId = "crop",
        cropKind = CropKind.CENTER_CARD_AREA,
        startedAtMs = 900L,
        finishedAtMs = 1_100L,
        durationMs = 200L,
        success = true,
        rawText = rawText,
        lineCount = 2,
        blockCount = 1,
        errorMessage = null,
        offerCycleKind = null,
        shouldPreferForOcr = null,
        analysisEpoch = 1L,
        isManual = true,
        manualReason = "driver_requested_current_screen_analysis"
    )

    private fun candidate(value: Double, kind: String, unit: String) = ExtractedNumericCandidate(
        raw = value.toString(),
        normalizedValue = value,
        unit = unit,
        kind = kind,
        confidence = 8
    )
}
