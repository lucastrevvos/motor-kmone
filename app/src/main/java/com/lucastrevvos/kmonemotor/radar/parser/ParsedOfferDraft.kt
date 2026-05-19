package com.lucastrevvos.kmonemotor.radar.parser

enum class ParsedPlatform {
    UBER,
    NINETY_NINE,
    UNKNOWN
}

data class ParsedOfferDraft(
    val parserVersion: String,
    val observationId: String,
    val clusterId: String?,
    val platform: ParsedPlatform,
    val product: String?,
    val paymentMethod: String?,
    val price: ParsedMoney?,
    val valuePerKm: ParsedMoney?,
    val pickupTimeMinutes: ParsedNumber?,
    val pickupDistanceKm: ParsedNumber?,
    val tripTimeMinutes: ParsedNumber?,
    val tripDistanceKm: ParsedNumber?,
    val multiplier: ParsedNumber?,
    val rating: ParsedNumber?,
    val passengerInfo: String?,
    val rawTextPreview: String?,
    val confidence: ParsedOfferConfidence,
    val warnings: List<String>,
    val sanityStatus: ParsedOfferSanityStatus,
    val sanityIssues: List<ParsedOfferSanityIssue>,
    val shouldBlockEconomicDecisionFuture: Boolean,
    val createdAtMs: Long
)
