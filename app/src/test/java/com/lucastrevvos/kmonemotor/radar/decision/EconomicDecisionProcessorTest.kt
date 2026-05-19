package com.lucastrevvos.kmonemotor.radar.decision

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.parser.OfferParserResult
import com.lucastrevvos.kmonemotor.radar.parser.ParsedMoney
import com.lucastrevvos.kmonemotor.radar.parser.ParsedNumber
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferConfidence
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferDraft
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferSanityStatus
import com.lucastrevvos.kmonemotor.radar.parser.ParsedPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EconomicDecisionProcessorTest {
    @Test
    fun skipsForDedupeWeaker() {
        val processor = EconomicDecisionProcessor(clock = RadarClock { 2_000L })

        val result = processor.process(
            parserResult = OfferParserResult(status = "skipped", reason = "dedupe_weaker"),
            source = DecisionSource.AUTOMATIC
        )

        assertEquals("skipped", result.status)
        assertEquals("dedupe_weaker", result.reason)
        assertNull(result.result)
    }

    @Test
    fun runsForParsedOffer() {
        val processor = EconomicDecisionProcessor(clock = RadarClock { 2_000L })

        val result = processor.process(
            parserResult = OfferParserResult(status = "parsed", reason = "new_offer_candidate", draft = draft()),
            source = DecisionSource.AUTOMATIC
        )

        assertEquals("evaluated", result.status)
        assertNotNull(result.result)
    }

    @Test
    fun debugWriterFailureDoesNotBreakPipeline() {
        val processor = EconomicDecisionProcessor(
            clock = RadarClock { 2_000L },
            debugWriter = object : DecisionDebugWriter {
                override fun write(result: EconomicDecisionResult) {
                    throw IllegalStateException("boom")
                }
            }
        )

        val result = processor.process(
            parserResult = OfferParserResult(status = "parsed", reason = "new_offer_candidate", draft = draft()),
            source = DecisionSource.MANUAL
        )

        assertEquals("evaluated", result.status)
        assertNotNull(result.result)
    }

    private fun draft() = ParsedOfferDraft(
        parserVersion = "4.5-draft",
        observationId = "obs-1",
        clusterId = "cluster-1",
        platform = ParsedPlatform.UBER,
        product = "UberX",
        paymentMethod = null,
        price = ParsedMoney(18.0, sourceText = "R$18,00", confidence = 0.95),
        valuePerKm = null,
        pickupTimeMinutes = ParsedNumber(1.0, "min", "pickup", 0.9),
        pickupDistanceKm = ParsedNumber(1.0, "km", "pickup", 0.9),
        tripTimeMinutes = ParsedNumber(7.0, "min", "trip", 0.9),
        tripDistanceKm = ParsedNumber(7.0, "km", "trip", 0.9),
        multiplier = null,
        rating = null,
        passengerInfo = null,
        rawTextPreview = "preview",
        confidence = ParsedOfferConfidence(overall = 0.9, price = 0.95, route = 0.9, platform = 0.9, product = 0.9),
        warnings = emptyList(),
        sanityStatus = ParsedOfferSanityStatus.VALID,
        sanityIssues = emptyList(),
        shouldBlockEconomicDecisionFuture = false,
        createdAtMs = 1_000L
    )
}
