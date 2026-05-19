package com.lucastrevvos.kmonemotor.radar.decision

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferSanityIssue
import com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferSanityStatus
import kotlin.math.round

class EconomicDecisionEngine(
    private val config: EconomicDecisionConfig = EconomicDecisionConfig()
) {
    fun evaluate(input: EconomicDecisionInput): EconomicDecisionResult {
        val offer = input.parsedOffer
        RadarLogger.i(
            "KM_V2_ECONOMIC",
            "KM_V2_ECONOMIC_DECISION_STARTED",
            "observationId" to input.observationId,
            "clusterId" to input.clusterId,
            "source" to input.source
        )
        val metrics = buildMetrics(offer)
        RadarLogger.i(
            "KM_V2_ECONOMIC",
            "KM_V2_ECONOMIC_DECISION_METRICS",
            "observationId" to input.observationId,
            "price" to metrics.price,
            "pickupDistanceKm" to metrics.pickupDistanceKm,
            "tripDistanceKm" to metrics.tripDistanceKm,
            "grossPerTripKm" to metrics.grossPerTripKm,
            "grossPerTotalKm" to metrics.grossPerTotalKm,
            "valuePerKmExplicit" to metrics.valuePerKmExplicit
        )

        if (offer.shouldBlockEconomicDecisionFuture) {
            return blocked(input, metrics, EconomicDecisionReason.SANITY_BLOCKED)
        }
        if (offer.sanityStatus == ParsedOfferSanityStatus.INVALID) {
            return blocked(input, metrics, EconomicDecisionReason.SANITY_INVALID)
        }
        if (offer.sanityStatus == ParsedOfferSanityStatus.SUSPICIOUS) {
            return blocked(input, metrics, EconomicDecisionReason.SANITY_SUSPICIOUS)
        }
        if (metrics.price == null) {
            return unknown(input, metrics, EconomicDecisionReason.MISSING_PRICE)
        }
        if (metrics.tripDistanceKm == null && metrics.valuePerKmExplicit == null) {
            return unknown(input, metrics, EconomicDecisionReason.MISSING_TRIP_DISTANCE)
        }

        val reasons = linkedSetOf<EconomicDecisionReason>()
        val warnings = mutableListOf<String>()
        var score = 0
        var confidence = offer.confidence.overall

        val grossMetric = metrics.grossPerTotalKm
        when {
            grossMetric != null && grossMetric >= config.goodGrossPerKm -> {
                reasons += EconomicDecisionReason.ABOVE_GOOD_TOTAL_KM
                score += 3
            }
            grossMetric != null && grossMetric >= config.minAcceptableGrossPerKm -> {
                reasons += EconomicDecisionReason.ABOVE_MIN_TOTAL_KM
                score += 1
            }
            grossMetric != null -> {
                reasons += EconomicDecisionReason.BELOW_MIN_TOTAL_KM
                score -= 3
            }
            metrics.valuePerKmExplicit != null && metrics.valuePerKmExplicit >= config.goodGrossPerKm -> {
                reasons += EconomicDecisionReason.VALUE_PER_KM_GOOD
                score += 2
            }
            metrics.valuePerKmExplicit != null && metrics.valuePerKmExplicit >= config.minAcceptableGrossPerKm -> {
                reasons += EconomicDecisionReason.ABOVE_MIN_TOTAL_KM
                score += 1
            }
            metrics.valuePerKmExplicit != null -> {
                reasons += EconomicDecisionReason.VALUE_PER_KM_LOW
                score -= 2
            }
        }

        metrics.pickupDistanceKm?.let {
            when {
                it >= config.veryLongPickupDistanceKm -> {
                    reasons += EconomicDecisionReason.VERY_LONG_PICKUP_DISTANCE
                    score -= 2
                }
                it >= config.longPickupDistanceKm -> {
                    reasons += EconomicDecisionReason.LONG_PICKUP_DISTANCE
                    score -= 1
                }
            }
        }
        metrics.pickupTimeMin?.let {
            when {
                it >= config.veryLongPickupTimeMin -> {
                    reasons += EconomicDecisionReason.VERY_LONG_PICKUP_TIME
                    score -= 2
                }
                it >= config.longPickupTimeMin -> {
                    reasons += EconomicDecisionReason.LONG_PICKUP_TIME
                    score -= 1
                }
            }
        }
        metrics.tripDistanceKm?.let {
            when {
                it <= config.veryShortTripDistanceKm -> {
                    reasons += EconomicDecisionReason.VERY_SHORT_TRIP
                    score -= 2
                }
                it <= config.shortTripDistanceKm -> {
                    reasons += EconomicDecisionReason.SHORT_TRIP
                    score -= 1
                }
            }
        }
        if ((metrics.pickupDistanceKm ?: Double.MIN_VALUE) > (metrics.tripDistanceKm ?: Double.MAX_VALUE) && metrics.tripDistanceKm != null) {
            reasons += EconomicDecisionReason.PICKUP_DISTANCE_GREATER_THAN_TRIP
            score -= 1
        }
        if ((metrics.pickupTimeMin ?: Double.MIN_VALUE) > (metrics.tripTimeMin ?: Double.MAX_VALUE) && metrics.tripTimeMin != null) {
            reasons += EconomicDecisionReason.PICKUP_TIME_GREATER_THAN_TRIP
            score -= 1
        }
        if (offer.sanityIssues.contains(ParsedOfferSanityIssue.LOW_CONFIDENCE_ROUTE)) {
            reasons += EconomicDecisionReason.LOW_CONFIDENCE_ROUTE
            warnings += "low_confidence_route"
            if (config.lowConfidencePenaltyEnabled) {
                score -= 1
                confidence = (confidence - 0.15).coerceAtLeast(0.0)
            }
        }
        if (offer.sanityIssues.any {
                it == ParsedOfferSanityIssue.MIXED_PLATFORM_TEXT_DETECTED ||
                    it == ParsedOfferSanityIssue.FUEL_OR_ABASTECE_TEXT_DETECTED
            }
        ) {
            reasons += EconomicDecisionReason.MIXED_OR_FUEL_CONTEXT_WARNING
            warnings += "mixed_or_fuel_context"
            score -= 2
            confidence = (confidence - 0.2).coerceAtLeast(0.0)
        }
        if (input.source == DecisionSource.MANUAL) {
            score += 1
        }

        val decision = when {
            score >= 3 -> EconomicDecisionKind.GOOD
            score >= 0 -> EconomicDecisionKind.WARNING
            else -> EconomicDecisionKind.BAD
        }
        val result = EconomicDecisionResult(
            observationId = input.observationId,
            clusterId = input.clusterId,
            decision = decision,
            score = score,
            confidence = round2(confidence),
            metrics = metrics,
            reasons = reasons.toList(),
            warnings = warnings.distinct(),
            source = input.source,
            createdAtMs = input.createdAtMs
        )
        RadarLogger.i(
            "KM_V2_ECONOMIC",
            "KM_V2_ECONOMIC_DECISION_RESULT",
            "observationId" to input.observationId,
            "clusterId" to input.clusterId,
            "decision" to result.decision,
            "score" to result.score,
            "price" to result.metrics.price,
            "pickupDistanceKm" to result.metrics.pickupDistanceKm,
            "tripDistanceKm" to result.metrics.tripDistanceKm,
            "grossPerTotalKm" to result.metrics.grossPerTotalKm,
            "reasons" to result.reasons.joinToString(",")
        )
        return result
    }

    private fun buildMetrics(offer: com.lucastrevvos.kmonemotor.radar.parser.ParsedOfferDraft): EconomicDecisionMetrics {
        val price = offer.price?.value
        val pickupDistance = offer.pickupDistanceKm?.value
        val pickupTime = offer.pickupTimeMinutes?.value
        val tripDistance = offer.tripDistanceKm?.value
        val tripTime = offer.tripTimeMinutes?.value
        val valuePerKm = offer.valuePerKm?.value
        val totalDistance = if (pickupDistance != null && tripDistance != null) round2(pickupDistance + tripDistance) else null
        val totalTime = if (pickupTime != null && tripTime != null) round2(pickupTime + tripTime) else null
        return EconomicDecisionMetrics(
            price = price,
            pickupDistanceKm = pickupDistance,
            pickupTimeMin = pickupTime,
            tripDistanceKm = tripDistance,
            tripTimeMin = tripTime,
            valuePerKmExplicit = valuePerKm,
            grossPerTripKm = if (price != null && tripDistance != null && tripDistance > 0.0) round2(price / tripDistance) else null,
            grossPerTotalKm = if (price != null && totalDistance != null && totalDistance > 0.0) round2(price / totalDistance) else null,
            totalDistanceKm = totalDistance,
            totalTimeMin = totalTime
        )
    }

    private fun blocked(
        input: EconomicDecisionInput,
        metrics: EconomicDecisionMetrics,
        reason: EconomicDecisionReason
    ): EconomicDecisionResult {
        RadarLogger.i(
            "KM_V2_ECONOMIC",
            "KM_V2_ECONOMIC_DECISION_BLOCKED",
            "observationId" to input.observationId,
            "reason" to reason
        )
        return EconomicDecisionResult(
            observationId = input.observationId,
            clusterId = input.clusterId,
            decision = EconomicDecisionKind.BLOCKED,
            score = null,
            confidence = 0.0,
            metrics = metrics,
            reasons = listOf(reason),
            warnings = emptyList(),
            source = input.source,
            createdAtMs = input.createdAtMs
        )
    }

    private fun unknown(
        input: EconomicDecisionInput,
        metrics: EconomicDecisionMetrics,
        reason: EconomicDecisionReason
    ): EconomicDecisionResult {
        return EconomicDecisionResult(
            observationId = input.observationId,
            clusterId = input.clusterId,
            decision = EconomicDecisionKind.UNKNOWN,
            score = null,
            confidence = round2(input.parsedOffer.confidence.overall.coerceAtMost(0.6)),
            metrics = metrics,
            reasons = listOf(reason),
            warnings = emptyList(),
            source = input.source,
            createdAtMs = input.createdAtMs
        )
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0
}
