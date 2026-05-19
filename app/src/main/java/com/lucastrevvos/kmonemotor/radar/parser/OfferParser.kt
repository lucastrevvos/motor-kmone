package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferDedupeDecision
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferDedupeResult
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextNormalizer
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation

class OfferParser(
    private val clock: RadarClock = RadarClock.System,
    private val normalizer: OfferTextNormalizer = OfferTextNormalizer(),
    private val uberParser: UberOfferParser = UberOfferParser(),
    private val ninetyNineParser: NinetyNineOfferParser = NinetyNineOfferParser(),
    private val genericParser: GenericOfferParser = GenericOfferParser(),
    private val sanityValidator: ParsedOfferSanityValidator = ParsedOfferSanityValidator(),
    private val debugWriter: ParserDebugWriter? = null
) {
    fun process(
        fingerprint: OfferTextFingerprint,
        ocrObservation: OcrObservation,
        dedupeResult: OfferDedupeResult
    ): OfferParserResult {
        val shouldParse = dedupeResult.decision == OfferDedupeDecision.NEW_OFFER_CANDIDATE ||
            dedupeResult.decision == OfferDedupeDecision.SAME_OFFER_UPDATED
        if (!shouldParse) {
            val reason = when (dedupeResult.decision) {
                OfferDedupeDecision.SAME_OFFER_IGNORED_WEAKER -> "dedupe_weaker"
                OfferDedupeDecision.NON_OFFER_IGNORED -> "non_offer"
                OfferDedupeDecision.UNKNOWN_IGNORED -> "unknown"
                else -> "dedupe_skip"
            }
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_PARSER_SKIPPED",
                "observationId" to fingerprint.observationId,
                "reason" to reason
            )
            return OfferParserResult(status = "skipped", reason = reason)
        }
        val startedAtMs = clock.nowMs()
        val normalizedText = normalizer.normalize(ocrObservation.rawText).normalizedText
        val input = OfferParserInput(
            fingerprint = fingerprint,
            ocrObservation = ocrObservation,
            clusterId = dedupeResult.clusterId,
            dedupeDecision = dedupeResult.decision,
            rawText = ocrObservation.rawText,
            normalizedText = normalizedText,
            triggerSource = fingerprint.triggerSource,
            createdAtMs = clock.nowMs()
        )
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_STARTED",
            "observationId" to fingerprint.observationId,
            "clusterId" to dedupeResult.clusterId,
            "decision" to dedupeResult.decision
        )
        val parser = when (OfferParserHelpers().inferPlatform(input)) {
            ParsedPlatform.UBER -> uberParser
            ParsedPlatform.NINETY_NINE -> ninetyNineParser
            ParsedPlatform.UNKNOWN -> genericParser
        }
        val parsedDraft = parser.parse(input, ParsedPlatform.UNKNOWN)
        if (parsedDraft.platform.name != fingerprint.platformTextHint.name) {
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_PARSER_PLATFORM_CONFLICT_RESOLVED",
                "observationId" to fingerprint.observationId,
                "fingerprintPlatform" to fingerprint.platformTextHint,
                "finalPlatform" to parsedDraft.platform
            )
        }
        val sanity = sanityValidator.validate(parsedDraft, input)
        val draft = parsedDraft.copy(
            confidence = sanity.adjustedConfidence,
            sanityStatus = sanity.status,
            sanityIssues = sanity.issues,
            shouldBlockEconomicDecisionFuture = sanity.shouldBlockEconomicDecisionFuture
        )
        val result = OfferParserResult(
            status = "parsed",
            reason = dedupeResult.decision.name.lowercase(),
            draft = draft,
            dedupeToParserMs = dedupeResult.dedupeDurationMs,
            parserDurationMs = (clock.nowMs() - startedAtMs).coerceAtLeast(0L)
        )
        try {
            debugWriter?.write(result)
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_PARSER",
                "KM_V2_DEBUG_PARSER_SAVE_FAILED",
                "observationId" to fingerprint.observationId,
                "error" to throwable.message
            )
        }
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_RESULT",
            "observationId" to draft.observationId,
            "price" to draft.price?.value,
            "pickupDistanceKm" to draft.pickupDistanceKm?.value,
            "tripDistanceKm" to draft.tripDistanceKm?.value,
            "sanityStatus" to draft.sanityStatus,
            "sanityIssues" to draft.sanityIssues.joinToString(",")
        )
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_LATENCY_PARSER",
            "observationId" to fingerprint.observationId,
            "dedupeToParserMs" to result.dedupeToParserMs,
            "parserDurationMs" to result.parserDurationMs
        )
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_LATENCY_PARSER_SANITY",
            "observationId" to fingerprint.observationId,
            "parserDurationMs" to result.parserDurationMs
        )
        return result
    }

    companion object {
        const val PARSER_VERSION = "4.4-draft"
    }
}
