package com.lucastrevvos.kmonemotor.radar.parser

enum class ParsedOfferSanityStatus {
    VALID,
    WARNING,
    SUSPICIOUS,
    INVALID
}

enum class ParsedOfferSanityIssue {
    PRICE_OUT_OF_PLAUSIBLE_RANGE,
    PRICE_RESEMBLES_FUEL_PRICE,
    PRICE_PROBABLY_MISSING_DECIMAL_SEPARATOR,
    IMPOSSIBLE_TIME_DISTANCE_PAIR,
    TRIP_DISTANCE_OUT_OF_PLAUSIBLE_RANGE,
    PICKUP_DISTANCE_OUT_OF_PLAUSIBLE_RANGE,
    FALSE_MULTIPLIER_CONTEXT,
    KMONE_PIU_TEXT_DETECTED,
    FUEL_OR_ABASTECE_TEXT_DETECTED,
    MIXED_PLATFORM_TEXT_DETECTED,
    LOW_CONFIDENCE_PRICE,
    LOW_CONFIDENCE_ROUTE
}

data class ParsedOfferSanityResult(
    val status: ParsedOfferSanityStatus,
    val issues: List<ParsedOfferSanityIssue>,
    val adjustedConfidence: ParsedOfferConfidence,
    val shouldKeepDraft: Boolean,
    val shouldBlockEconomicDecisionFuture: Boolean
)
