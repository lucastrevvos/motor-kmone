package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferDedupeDecision
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedSignal
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserHelpersTest {
    private val helpers = OfferParserHelpers()

    @Test
    fun extractsPrimaryPriceIgnoringSmallFee() {
        val price = helpers.selectMainPrice(input("R$9,10 Dinheiro R$1,04 R$2,18/km", prices = listOf(price(9.10), price(1.04))))

        assertEquals(9.10, price?.value ?: 0.0, 0.0)
    }

    @Test
    fun extractsValuePerKm() {
        val valuePerKm = helpers.selectValuePerKm(input("R$2,18/km", valuePerKm = listOf(valuePerKm(2.18))))

        assertEquals(2.18, valuePerKm?.value ?: 0.0, 0.0)
    }

    @Test
    fun extractsTwoRoutePairs() {
        val warnings = mutableListOf<String>()
        val route = helpers.selectRoute(input("4min (666m) 7min (3,5km)"), warnings)

        assertEquals(4.0, route.pickupTimeMinutes?.value ?: 0.0, 0.0)
        assertEquals(0.666, route.pickupDistanceKm?.value ?: 0.0, 0.0)
        assertEquals(7.0, route.tripTimeMinutes?.value ?: 0.0, 0.0)
        assertEquals(3.5, route.tripDistanceKm?.value ?: 0.0, 0.0)
    }

    @Test
    fun convertsMetersToKm() {
        val warnings = mutableListOf<String>()
        val route = helpers.selectRoute(input("4min (614m)"), warnings)

        assertEquals(0.614, route.pickupDistanceKm?.value ?: 0.0, 0.0)
    }

    @Test
    fun convertsCommaDecimal() {
        val warnings = mutableListOf<String>()
        val route = helpers.selectRoute(input("63min (43,8km)"), warnings)

        assertEquals(43.8, route.pickupDistanceKm?.value ?: 0.0, 0.0)
    }

    @Test
    fun ignoresRatingAsPrice() {
        val price = helpers.selectMainPrice(input("UberX R$ 10,29 * 4,79 (125)", prices = listOf(price(10.29))))

        assertEquals(10.29, price?.value ?: 0.0, 0.0)
    }

    @Test
    fun ignoresRideCounterAsRoute() {
        val warnings = mutableListOf<String>()
        val route = helpers.selectRoute(input("UberX R$ 10,29 * 4,79 (125)"), warnings)

        assertNull(route.pickupTimeMinutes)
        assertNull(route.tripTimeMinutes)
    }

    @Test
    fun extractsUberProduct() {
        val product = helpers.extractUberProduct(input("UberX Exclusivo R$ 5,50"))

        assertEquals("UberX Exclusivo", product)
    }

    @Test
    fun extractsPaymentDinheiro() {
        val payment = helpers.extractPaymentMethod(input("R$9,10 Dinheiro"))

        assertEquals("Dinheiro", payment)
    }

    @Test
    fun extractsPagamentoNoApp() {
        val payment = helpers.extractPaymentMethod(input("Pagamento no app Corrida longa"))

        assertEquals("Pagamento no app", payment)
    }

    @Test
    fun extractsMultiplier() {
        val warnings = mutableListOf<String>()
        val multiplier = helpers.extractMultiplier(input("Preco x1,3", platformTextHint = PlatformTextHint.NINETY_NINE), ParsedPlatform.NINETY_NINE, warnings)

        assertEquals(1.3, multiplier?.value ?: 0.0, 0.0)
    }

    @Test
    fun doesNotExtractFalseMultiplierFromLooseNumber() {
        val warnings = mutableListOf<String>()
        val multiplier = helpers.extractMultiplier(input("UberX 5,00 (29) R$ 15,15", platformTextHint = PlatformTextHint.UBER), ParsedPlatform.UBER, warnings)

        assertNull(multiplier)
    }

    @Test
    fun unknownWithNinetyNineSignalsCanExtractMultiplier() {
        val warnings = mutableListOf<String>()
        val multiplier = helpers.extractMultiplier(
            input("Pagamento no app Preco x1,3 R$9,10", platformTextHint = PlatformTextHint.UNKNOWN),
            ParsedPlatform.UNKNOWN,
            warnings
        )

        assertEquals(1.3, multiplier?.value ?: 0.0, 0.0)
    }

    @Test
    fun unknownWithUberSignalsDoesNotExtractMultiplier() {
        val warnings = mutableListOf<String>()
        val multiplier = helpers.extractMultiplier(
            input("UberX x1,3 R$15,15", platformTextHint = PlatformTextHint.UNKNOWN),
            ParsedPlatform.UNKNOWN,
            warnings
        )

        assertNull(multiplier)
    }

    @Test
    fun brokenPairDoesNotCreateRoute() {
        val warnings = mutableListOf<String>()
        val route = helpers.selectRoute(input("UberX R$ 17,03 l min ( km)"), warnings)

        assertNull(route.pickupTimeMinutes)
        assertNull(route.tripTimeMinutes)
        assertTrue(warnings.contains("low_confidence_route"))
    }

    @Test
    fun looseValuesDoNotBecomeRoutePair() {
        val warnings = mutableListOf<String>()
        val route = helpers.selectRoute(input("21 minutos 12.6 km"), warnings)

        assertNull(route.pickupTimeMinutes)
        assertNull(route.tripTimeMinutes)
        assertTrue(warnings.contains("low_confidence_route"))
    }

    @Test
    fun estimatesDistanceFromPriceAndValuePerKmWhenMissing() {
        val warnings = mutableListOf<String>()
        val estimated = helpers.estimateTripDistance(
            price = ParsedMoney(9.10, sourceText = "R$9,10", confidence = 0.95),
            valuePerKm = ParsedMoney(2.18, sourceText = "R$2,18/km", confidence = 0.95),
            warnings = warnings
        )

        assertEquals(4.17, estimated?.value ?: 0.0, 0.0)
        assertTrue(warnings.contains("trip_distance_estimated_from_value_per_km"))
    }

    private fun input(
        rawText: String,
        prices: List<ExtractedNumericCandidate> = listOf(price(10.29)),
        valuePerKm: List<ExtractedNumericCandidate> = emptyList(),
        platformTextHint: PlatformTextHint = PlatformTextHint.UNKNOWN
    ): OfferParserInput {
        return OfferParserInput(
            fingerprint = fingerprint(rawText, prices, valuePerKm, platformTextHint),
            ocrObservation = ocrObservation(rawText),
            clusterId = "cluster_1",
            dedupeDecision = OfferDedupeDecision.NEW_OFFER_CANDIDATE,
            rawText = rawText,
            normalizedText = helpers.normalizeText(rawText),
            triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
            createdAtMs = 1_000L
        )
    }

    private fun fingerprint(
        rawText: String,
        prices: List<ExtractedNumericCandidate>,
        valuePerKm: List<ExtractedNumericCandidate>,
        platformTextHint: PlatformTextHint
    ) = OfferTextFingerprint(
        fingerprintId = "fp",
        ocrObservationId = "ocr",
        observationId = "obs",
        captureRequestId = "req",
        triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
        cropKind = CropKind.CENTER_CARD_AREA,
        platformTextHint = platformTextHint,
        kind = OfferTextFingerprintKind.OFFER_LIKE,
        offerLikeScore = 10,
        nonOfferScore = 0,
        positiveSignals = listOf(ExtractedSignal("price", "R$", 8)),
        negativeSignals = emptyList(),
        priceCandidates = prices,
        valuePerKmCandidates = valuePerKm,
        distanceCandidates = emptyList(),
        timeCandidates = emptyList(),
        rawTextHash = "raw-hash",
        routeTextHash = null,
        normalizedPreview = rawText,
        reason = "offer_like_positive_signals",
        createdAtMs = 900L
    )

    private fun ocrObservation(rawText: String) = OcrObservation(
        ocrObservationId = "ocr",
        observationId = "obs",
        captureRequestId = "req",
        triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
        cropId = "crop",
        cropKind = CropKind.CENTER_CARD_AREA,
        startedAtMs = 800L,
        finishedAtMs = 900L,
        durationMs = 100L,
        success = true,
        rawText = rawText,
        lineCount = 1,
        blockCount = 1,
        errorMessage = null,
        offerCycleKind = null,
        shouldPreferForOcr = null,
        analysisEpoch = 1L,
        isManual = true,
        manualReason = "driver_requested_current_screen_analysis"
    )

    private fun price(value: Double) = ExtractedNumericCandidate(value.toString(), value, "BRL", "PRICE", 3)
    private fun valuePerKm(value: Double) = ExtractedNumericCandidate(value.toString(), value, "BRL_PER_KM", "VALUE_PER_KM", 3)
}
