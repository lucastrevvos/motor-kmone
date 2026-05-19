package com.lucastrevvos.kmonemotor.radar.decision

import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferDraft

enum class EconomicDecisionKind {
    GOOD,
    WARNING,
    BAD,
    BLOCKED,
    UNKNOWN
}

enum class EconomicDecisionReason {
    SANITY_BLOCKED,
    SANITY_INVALID,
    SANITY_SUSPICIOUS,
    MISSING_PRICE,
    MISSING_TRIP_DISTANCE,
    BELOW_MIN_TOTAL_KM,
    ABOVE_GOOD_TOTAL_KM,
    ABOVE_MIN_TOTAL_KM,
    VALUE_PER_KM_GOOD,
    VALUE_PER_KM_LOW,
    LONG_PICKUP_DISTANCE,
    VERY_LONG_PICKUP_DISTANCE,
    LONG_PICKUP_TIME,
    VERY_LONG_PICKUP_TIME,
    SHORT_TRIP,
    VERY_SHORT_TRIP,
    PICKUP_DISTANCE_GREATER_THAN_TRIP,
    PICKUP_TIME_GREATER_THAN_TRIP,
    LOW_CONFIDENCE_ROUTE,
    MIXED_OR_FUEL_CONTEXT_WARNING
}

enum class DecisionSource {
    AUTOMATIC,
    MANUAL
}

data class EconomicDecisionConfig(
    val minAcceptableGrossPerKm: Double = 1.50,
    val goodGrossPerKm: Double = 2.00,
    val minPrice: Double = 6.00,
    val longPickupDistanceKm: Double = 4.0,
    val veryLongPickupDistanceKm: Double = 7.0,
    val longPickupTimeMin: Double = 10.0,
    val veryLongPickupTimeMin: Double = 15.0,
    val shortTripDistanceKm: Double = 3.0,
    val veryShortTripDistanceKm: Double = 1.5,
    val lowConfidencePenaltyEnabled: Boolean = true
)

data class EconomicDecisionMetrics(
    val price: Double?,
    val pickupDistanceKm: Double?,
    val pickupTimeMin: Double?,
    val tripDistanceKm: Double?,
    val tripTimeMin: Double?,
    val valuePerKmExplicit: Double?,
    val grossPerTripKm: Double?,
    val grossPerTotalKm: Double?,
    val totalDistanceKm: Double?,
    val totalTimeMin: Double?
)

data class EconomicDecisionInput(
    val observationId: String,
    val clusterId: String?,
    val parsedOffer: ParsedOfferDraft,
    val source: DecisionSource,
    val createdAtMs: Long
)

data class EconomicDecisionResult(
    val observationId: String,
    val clusterId: String?,
    val decision: EconomicDecisionKind,
    val score: Int?,
    val confidence: Double,
    val metrics: EconomicDecisionMetrics,
    val reasons: List<EconomicDecisionReason>,
    val warnings: List<String>,
    val source: DecisionSource,
    val createdAtMs: Long
)

data class EconomicDecisionProcessResult(
    val status: String,
    val reason: String,
    val result: EconomicDecisionResult? = null,
    val durationMs: Long? = null
)
