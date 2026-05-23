package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger

class ParsedOfferSanityValidator {
    fun validate(
        draft: ParsedOfferDraft,
        input: OfferParserInput
    ): ParsedOfferSanityResult {
        val issues = linkedSetOf<ParsedOfferSanityIssue>()
        var status = ParsedOfferSanityStatus.VALID
        var blockEconomic = false
        var keepDraft = true
        var adjusted = draft.confidence
        val normalizedText = input.normalizedText
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_SANITY_STARTED",
            "observationId" to draft.observationId,
            "clusterId" to draft.clusterId
        )

        draft.price?.let { price ->
            when {
                price.value > MAX_ABSOLUTE_OFFER_PRICE_BRL -> {
                    issues += ParsedOfferSanityIssue.PRICE_OUT_OF_PLAUSIBLE_RANGE
                    status = ParsedOfferSanityStatus.INVALID
                    blockEconomic = true
                    RadarLogger.i(
                        "KM_V2_PARSER",
                        "KM_V2_PARSER_SANITY_PRICE_OUT_OF_RANGE",
                        "price" to price.value,
                        "severity" to "absolute"
                    )
                }
                price.value > MAX_NORMAL_OFFER_PRICE_BRL -> {
                    issues += ParsedOfferSanityIssue.PRICE_OUT_OF_PLAUSIBLE_RANGE
                    status = maxStatus(status, ParsedOfferSanityStatus.SUSPICIOUS)
                    blockEconomic = true
                    RadarLogger.i(
                        "KM_V2_PARSER",
                        "KM_V2_PARSER_SANITY_PRICE_OUT_OF_RANGE",
                        "price" to price.value,
                        "severity" to "normal"
                    )
                }
                price.value < MIN_PLAUSIBLE_OFFER_PRICE_BRL -> {
                    issues += ParsedOfferSanityIssue.LOW_CONFIDENCE_PRICE
                    status = maxStatus(status, ParsedOfferSanityStatus.WARNING)
                }
            }
            if (price.value >= 300.0 || (price.value >= 100.0 && Regex("""\br\$?\s*4[,.]79\b""", RegexOption.IGNORE_CASE).containsMatchIn(input.rawText))) {
                issues += ParsedOfferSanityIssue.PRICE_PROBABLY_MISSING_DECIMAL_SEPARATOR
                status = maxStatus(status, ParsedOfferSanityStatus.SUSPICIOUS)
                blockEconomic = true
            }
        }

        val fuelContext = listOf("99 abastece", "abastece", "posto", "gasolina", "etanol").any { normalizedText.contains(it) }
        if (fuelContext) {
            issues += ParsedOfferSanityIssue.FUEL_OR_ABASTECE_TEXT_DETECTED
            RadarLogger.i("KM_V2_PARSER", "KM_V2_PARSER_SANITY_FUEL_CONTEXT_DETECTED", "observationId" to draft.observationId)
            status = maxStatus(status, ParsedOfferSanityStatus.WARNING)
            if ((draft.price?.value ?: 0.0) in 3.0..8.0 && draft.tripDistanceKm == null && draft.pickupDistanceKm == null) {
                issues += ParsedOfferSanityIssue.PRICE_RESEMBLES_FUEL_PRICE
                status = maxStatus(status, ParsedOfferSanityStatus.SUSPICIOUS)
                blockEconomic = true
            }
            if (draft.platform == ParsedPlatform.UBER) {
                issues += ParsedOfferSanityIssue.MIXED_PLATFORM_TEXT_DETECTED
                RadarLogger.i("KM_V2_PARSER", "KM_V2_PARSER_SANITY_MIXED_PLATFORM_TEXT", "observationId" to draft.observationId)
                status = maxStatus(status, ParsedOfferSanityStatus.WARNING)
            }
        }

        if (normalizedText.contains("analisar") && normalizedText.contains("r$ 0,00")) {
            issues += ParsedOfferSanityIssue.KMONE_PIU_TEXT_DETECTED
            RadarLogger.i("KM_V2_PARSER", "KM_V2_PARSER_SANITY_PIU_TEXT_DETECTED", "observationId" to draft.observationId)
            status = maxStatus(status, ParsedOfferSanityStatus.WARNING)
        }

        if (draft.multiplier != null && !draft.multiplier.sourceText.orEmpty().contains("x", ignoreCase = true)) {
            issues += ParsedOfferSanityIssue.FALSE_MULTIPLIER_CONTEXT
            status = maxStatus(status, ParsedOfferSanityStatus.WARNING)
        }

        draft.pickupDistanceKm?.let {
            if (it.value > MAX_PLAUSIBLE_PICKUP_DISTANCE_KM) {
                issues += ParsedOfferSanityIssue.PICKUP_DISTANCE_OUT_OF_PLAUSIBLE_RANGE
                status = maxStatus(status, ParsedOfferSanityStatus.WARNING)
                routeImplausibleLog(draft, "pickup_distance", it.value, draft.pickupTimeMinutes?.value)
            }
        }
        draft.tripDistanceKm?.let {
            if (it.value > MAX_NORMAL_TRIP_DISTANCE_KM) {
                issues += ParsedOfferSanityIssue.TRIP_DISTANCE_OUT_OF_PLAUSIBLE_RANGE
                val isPlausibleUberLongTrip = draft.platform == ParsedPlatform.UBER &&
                    it.value <= MAX_PLAUSIBLE_TRIP_DISTANCE_KM
                status = maxStatus(
                    status,
                    when {
                        it.value > MAX_PLAUSIBLE_TRIP_DISTANCE_KM -> ParsedOfferSanityStatus.INVALID
                        isPlausibleUberLongTrip -> ParsedOfferSanityStatus.WARNING
                        else -> ParsedOfferSanityStatus.SUSPICIOUS
                    }
                )
                if (it.value > MAX_PLAUSIBLE_TRIP_DISTANCE_KM || !isPlausibleUberLongTrip) {
                    blockEconomic = true
                }
                routeImplausibleLog(draft, "trip_distance", it.value, draft.tripTimeMinutes?.value)
            }
        }
        if (isImpossiblePair(draft.pickupTimeMinutes, draft.pickupDistanceKm) || isImpossiblePair(draft.tripTimeMinutes, draft.tripDistanceKm)) {
            issues += ParsedOfferSanityIssue.IMPOSSIBLE_TIME_DISTANCE_PAIR
            status = maxStatus(status, ParsedOfferSanityStatus.SUSPICIOUS)
            blockEconomic = true
            routeImplausibleLog(
                draft,
                "impossible_speed",
                draft.tripDistanceKm?.value ?: draft.pickupDistanceKm?.value,
                draft.tripTimeMinutes?.value ?: draft.pickupTimeMinutes?.value
            )
        }

        if (draft.confidence.price < 0.7) {
            issues += ParsedOfferSanityIssue.LOW_CONFIDENCE_PRICE
            status = maxStatus(status, ParsedOfferSanityStatus.WARNING)
        }
        if (draft.confidence.route < 0.5 || draft.warnings.contains("low_confidence_route")) {
            issues += ParsedOfferSanityIssue.LOW_CONFIDENCE_ROUTE
            status = maxStatus(status, ParsedOfferSanityStatus.WARNING)
        }

        adjusted = adjusted.copy(
            overall = when (status) {
                ParsedOfferSanityStatus.VALID -> adjusted.overall
                ParsedOfferSanityStatus.WARNING -> (adjusted.overall - 0.1).coerceAtLeast(0.0)
                ParsedOfferSanityStatus.SUSPICIOUS -> (adjusted.overall - 0.3).coerceAtLeast(0.0)
                ParsedOfferSanityStatus.INVALID -> (adjusted.overall - 0.5).coerceAtLeast(0.0)
            },
            price = if (issues.any { it == ParsedOfferSanityIssue.PRICE_OUT_OF_PLAUSIBLE_RANGE || it == ParsedOfferSanityIssue.LOW_CONFIDENCE_PRICE }) {
                (adjusted.price - 0.4).coerceAtLeast(0.0)
            } else adjusted.price,
            route = if (issues.any { it == ParsedOfferSanityIssue.IMPOSSIBLE_TIME_DISTANCE_PAIR || it == ParsedOfferSanityIssue.TRIP_DISTANCE_OUT_OF_PLAUSIBLE_RANGE || it == ParsedOfferSanityIssue.PICKUP_DISTANCE_OUT_OF_PLAUSIBLE_RANGE || it == ParsedOfferSanityIssue.LOW_CONFIDENCE_ROUTE }) {
                (adjusted.route - 0.4).coerceAtLeast(0.0)
            } else adjusted.route
        )

        val result = ParsedOfferSanityResult(
            status = status,
            issues = issues.toList(),
            adjustedConfidence = adjusted,
            shouldKeepDraft = keepDraft,
            shouldBlockEconomicDecisionFuture = blockEconomic
        )
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_SANITY_RESULT",
            "observationId" to draft.observationId,
            "status" to result.status,
            "issues" to result.issues.joinToString(","),
            "shouldBlockEconomicDecisionFuture" to result.shouldBlockEconomicDecisionFuture
        )
        return result
    }

    private fun isImpossiblePair(time: ParsedNumber?, distance: ParsedNumber?): Boolean {
        val timeMin = time?.value ?: return false
        val distanceKm = distance?.value ?: return false
        if (timeMin <= 0.0) {
            return false
        }
        val speedKmH = distanceKm / (timeMin / 60.0)
        return speedKmH > 180.0
    }

    private fun routeImplausibleLog(
        draft: ParsedOfferDraft,
        kind: String,
        distanceKm: Double?,
        timeMin: Double?
    ) {
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_SANITY_ROUTE_IMPLAUSIBLE",
            "observationId" to draft.observationId,
            "kind" to kind,
            "distanceKm" to distanceKm,
            "timeMin" to timeMin
        )
    }

    private fun maxStatus(first: ParsedOfferSanityStatus, second: ParsedOfferSanityStatus): ParsedOfferSanityStatus {
        return if (first.ordinal >= second.ordinal) first else second
    }

    companion object {
        const val MIN_PLAUSIBLE_OFFER_PRICE_BRL = 4.0
        const val MAX_NORMAL_OFFER_PRICE_BRL = 150.0
        const val MAX_ABSOLUTE_OFFER_PRICE_BRL = 250.0
        const val MAX_PLAUSIBLE_PICKUP_DISTANCE_KM = 20.0
        const val MAX_PLAUSIBLE_TRIP_DISTANCE_KM = 120.0
        const val MAX_NORMAL_TRIP_DISTANCE_KM = 80.0
        const val MAX_PLAUSIBLE_TRIP_TIME_MIN = 180.0
    }
}
