package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.parser.OfferParserResult
import com.lucastrevvos.kmonemotor.radar.parser.ParsedPlatform
import java.util.UUID

class SeenOfferMapper(
    private val routePreviewExtractor: OfferRoutePreviewExtractor = OfferRoutePreviewExtractor()
) {
    fun fromPipelineResult(
        fingerprint: OfferTextFingerprint,
        observation: OcrObservation,
        parserResult: OfferParserResult?
    ): SeenOffer? {
        if (fingerprint.kind != OfferTextFingerprintKind.OFFER_LIKE) return null
        val draft = parserResult?.draft
        val platform = mapPlatform(draft?.platform, fingerprint.platformTextHint)
        val price = draft?.price?.value ?: selectPrimaryPrice(platform, fingerprint)
        val fallbackValuePerKm = draft?.valuePerKm?.value ?: selectMaxValue(fingerprint.valuePerKmCandidates)
        val distanceSelection = resolveDistancePair(
            platform = platform,
            fingerprint = fingerprint,
            draftPickupDistance = draft?.pickupDistanceKm?.value,
            draftTripDistance = draft?.tripDistanceKm?.value,
            price = price,
            explicitValuePerKm = fallbackValuePerKm
        )
        val pickupDistance = distanceSelection.pickupDistanceKm
        val tripDistance = distanceSelection.tripDistanceKm
        val pickupTime = draft?.pickupTimeMinutes?.value ?: timeAt(fingerprint.timeCandidates, 0)
        val tripTime = draft?.tripTimeMinutes?.value ?: timeAt(fingerprint.timeCandidates, 1)
        val totalTime = listOfNotNull(pickupTime, tripTime).takeIf { it.isNotEmpty() }?.sum()
        val routePreview = routePreviewExtractor.extract(
            rawText = observation.rawText.ifBlank { draft?.rawTextPreview.orEmpty() },
            platform = platform
        )
        if (routePreview.originPreview != null || routePreview.destinationPreview != null) {
            RadarLogger.i(
                "KM_V2_ROUTE",
                "KM_V2_ROUTE_PREVIEW_EXTRACTED",
                "observationId" to observation.observationId,
                "originPreview" to routePreview.originPreview,
                "destinationPreview" to routePreview.destinationPreview,
                "confidence" to routePreview.confidence,
                "reason" to routePreview.reason
            )
        } else {
            RadarLogger.i(
                "KM_V2_ROUTE",
                "KM_V2_ROUTE_PREVIEW_EMPTY",
                "observationId" to observation.observationId,
                "reason" to routePreview.reason
            )
        }
        val mappedEconomics = RideEconomicsCalculator.resolveRideEconomics(
            platform = platform,
            price = price,
            explicitValuePerKm = fallbackValuePerKm,
            totalDistanceKm = null,
            pickupDistanceKm = pickupDistance,
            tripDistanceKm = tripDistance,
            routeWarnings = distanceSelection.warnings
        )
        RadarLogger.i(
            "KM_V2_ROUTE",
            "KM_V2_ROUTE_METRICS_AUDITED",
            "observationId" to observation.observationId,
            "platform" to platform,
            "pickupDistanceKm" to mappedEconomics.pickupDistanceKm,
            "tripDistanceKm" to mappedEconomics.tripDistanceKm,
            "totalDistanceKm" to mappedEconomics.totalDistanceKm,
            "valuePerKm" to mappedEconomics.valuePerKm,
            "warnings" to mappedEconomics.warnings.joinToString(",")
        )
        return SeenOffer(
            id = UUID.randomUUID().toString(),
            observationId = observation.observationId,
            platform = platform,
            sourceTrigger = observation.triggerSource.name,
            status = SeenOfferStatus.SEEN,
            price = price,
            valuePerKm = mappedEconomics.valuePerKm ?: fallbackValuePerKm,
            pickupDistanceKm = mappedEconomics.pickupDistanceKm,
            pickupTimeMin = pickupTime,
            tripDistanceKm = mappedEconomics.tripDistanceKm,
            tripTimeMin = tripTime,
            totalDistanceKm = mappedEconomics.totalDistanceKm,
            estimatedTotalTimeMin = totalTime,
            productName = draft?.product,
            originPreview = routePreview.originPreview,
            destinationPreview = routePreview.destinationPreview,
            rawTextPreview = draft?.rawTextPreview ?: observation.rawText.take(160),
            score = fingerprint.offerLikeScore,
            rawTextHash = fingerprint.rawTextHash,
            routeTextHash = fingerprint.routeTextHash,
            createdAtMs = fingerprint.createdAtMs,
            updatedAtMs = fingerprint.createdAtMs
        )
    }

    private fun mapPlatform(parsed: ParsedPlatform?, fingerprintHint: PlatformTextHint): RidePlatform {
        return when (parsed) {
            ParsedPlatform.UBER -> RidePlatform.UBER
            ParsedPlatform.NINETY_NINE -> RidePlatform.NINETY_NINE
            ParsedPlatform.UNKNOWN, null -> when (fingerprintHint) {
                PlatformTextHint.UBER -> RidePlatform.UBER
                PlatformTextHint.NINETY_NINE -> RidePlatform.NINETY_NINE
                PlatformTextHint.UNKNOWN -> RidePlatform.UNKNOWN
            }
        }
    }

    private fun selectMaxValue(candidates: List<ExtractedNumericCandidate>): Double? {
        return candidates.mapNotNull { it.normalizedValue }.maxOrNull()
    }

    private fun selectPrimaryPrice(platform: RidePlatform, fingerprint: OfferTextFingerprint): Double? {
        if (platform == RidePlatform.NINETY_NINE &&
            (fingerprint.valuePerKmCandidates.isNotEmpty() || fingerprint.positiveSignals.any { it.key.contains("Dinheiro", ignoreCase = true) })
        ) {
            return fingerprint.priceCandidates.firstOrNull { (it.normalizedValue ?: 0.0) >= 5.0 }?.normalizedValue
                ?: fingerprint.priceCandidates.firstOrNull()?.normalizedValue
        }
        return selectMaxValue(fingerprint.priceCandidates)
    }

    private fun distanceAt(candidates: List<ExtractedNumericCandidate>, index: Int): Double? {
        val candidate = candidates.getOrNull(index) ?: return null
        return distanceFromCandidate(candidate)
    }

    private fun distanceFromCandidate(candidate: ExtractedNumericCandidate): Double? {
        val value = candidate.normalizedValue ?: return null
        return when (candidate.unit) {
            "m" -> value / 1000.0
            else -> value
        }
    }

    private fun resolveDistancePair(
        platform: RidePlatform,
        fingerprint: OfferTextFingerprint,
        draftPickupDistance: Double?,
        draftTripDistance: Double?,
        price: Double?,
        explicitValuePerKm: Double?
    ): DistanceSelection {
        if (platform == RidePlatform.NINETY_NINE) {
            val warnings = mutableListOf<String>()
            val normalizedCandidates = fingerprint.distanceCandidates.mapNotNull { candidate ->
                distanceFromCandidate(candidate)?.takeIf { it > 0.0 }?.let { normalized ->
                    Triple(candidate, normalized, candidate.unit == "m")
                }
            }
            val inferredTotal = if (price != null && explicitValuePerKm != null && explicitValuePerKm > 0.0) {
                price / explicitValuePerKm
            } else {
                null
            }
            if (draftPickupDistance != null || draftTripDistance != null) {
                if (
                    draftPickupDistance != null &&
                    draftTripDistance != null &&
                    inferredTotal != null
                ) {
                    val draftTotal = draftPickupDistance + draftTripDistance
                    if (kotlin.math.abs(draftTotal - inferredTotal) <= 0.35 || kotlin.math.abs(draftTotal - inferredTotal) / inferredTotal <= 0.12) {
                        return DistanceSelection(draftPickupDistance, draftTripDistance)
                    }
                } else if (draftPickupDistance != null && draftTripDistance == null && inferredTotal != null) {
                    val kmCandidates = normalizedCandidates
                        .filter { (_, normalized, isMeter) -> !isMeter && normalized >= 0.3 }
                        .map { it.second }
                        .distinct()
                        .sorted()
                    val tripCandidate = kmCandidates.lastOrNull()
                    if (tripCandidate != null && draftPickupDistance + tripCandidate <= inferredTotal + 0.35) {
                        RadarLogger.i(
                            "KM_V2_ROUTE",
                            "KM_V2_99_ROUTE_SELECTION_BY_EXPLICIT_VALUE_PER_KM",
                            "price" to price,
                            "explicitValuePerKm" to explicitValuePerKm,
                            "inferredTotalKm" to inferredTotal,
                            "candidateDistances" to kmCandidates,
                            "selectedPickupKm" to draftPickupDistance,
                            "selectedTripKm" to tripCandidate,
                            "selectedTotalKm" to (draftPickupDistance + tripCandidate),
                            "delta" to kotlin.math.abs((draftPickupDistance + tripCandidate) - inferredTotal),
                            "reason" to "draft_pickup_plus_largest_km_candidate"
                        )
                        return DistanceSelection(draftPickupDistance, tripCandidate)
                    }
                }
            }
            val plausibleTripKmCandidates = normalizedCandidates.filter { (_, normalized, isMeter) ->
                !isMeter && normalized >= 1.0
            }
            val pickupCandidate = normalizedCandidates.firstOrNull { (_, normalized, _) -> normalized >= 0.3 }
            if (pickupCandidate != null && plausibleTripKmCandidates.isNotEmpty()) {
                normalizedCandidates
                    .filter { (candidate, normalized, isMeter) ->
                        isMeter &&
                            normalized < 0.2 &&
                            fingerprint.timeCandidates.any { timeCandidate ->
                                val timeValue = timeCandidate.normalizedValue ?: return@any false
                                val candidateValue = candidate.normalizedValue ?: return@any false
                                kotlin.math.abs(timeValue - candidateValue) < 0.6
                            }
                    }
                    .forEach { (candidate, _, _) ->
                        warnings += "suspicious_meter_distance_probably_time"
                        RadarLogger.i(
                            "KM_V2_ROUTE",
                            "KM_V2_ROUTE_METRIC_CANDIDATE_REJECTED",
                            "reason" to "suspicious_tiny_meter_trip_when_km_candidate_exists",
                            "rawValue" to candidate.raw,
                            "normalizedValue" to candidate.normalizedValue,
                            "unit" to candidate.unit
                        )
                    }
                val pickup = pickupCandidate.second
                val trip = plausibleTripKmCandidates.maxByOrNull { it.second }?.second
                if (trip != null) {
                    return DistanceSelection(pickup, trip, warnings.distinct())
                }
            }
            if (inferredTotal != null) {
                val kmCandidates = normalizedCandidates
                    .filter { (_, normalized, isMeter) -> !isMeter && normalized >= 0.3 }
                    .map { it.second }
                    .distinct()
                    .sorted()
                if (kmCandidates.size >= 2) {
                    val pickup = kmCandidates.first()
                    val trip = kmCandidates.last()
                    val total = pickup + trip
                    val delta = kotlin.math.abs(total - inferredTotal)
                    if (delta <= 0.35 || delta / inferredTotal <= 0.12) {
                        RadarLogger.i(
                            "KM_V2_ROUTE",
                            "KM_V2_99_ROUTE_SELECTION_BY_EXPLICIT_VALUE_PER_KM",
                            "price" to price,
                            "explicitValuePerKm" to explicitValuePerKm,
                            "inferredTotalKm" to inferredTotal,
                            "candidateDistances" to kmCandidates,
                            "selectedPickupKm" to pickup,
                            "selectedTripKm" to trip,
                            "selectedTotalKm" to total,
                            "delta" to delta,
                            "reason" to "sorted_km_candidates_closest_to_inferred_total"
                        )
                        return DistanceSelection(pickup, trip, warnings.distinct())
                    }
                }
            }
            val kmCandidates = normalizedCandidates
                .filter { (_, normalized, isMeter) -> !isMeter && normalized >= 0.3 }
            if (kmCandidates.size >= 2) {
                val sorted = kmCandidates.map { it.second }.distinct().sorted()
                return DistanceSelection(sorted.first(), sorted.last(), warnings.distinct())
            }
            return DistanceSelection(
                pickupDistanceKm = distanceAt(fingerprint.distanceCandidates, 0),
                tripDistanceKm = distanceAt(fingerprint.distanceCandidates, 1),
                warnings = warnings.distinct()
            )
        }
        return DistanceSelection(
            pickupDistanceKm = distanceAt(fingerprint.distanceCandidates, 0),
            tripDistanceKm = distanceAt(fingerprint.distanceCandidates, 1)
        )
    }

    private fun timeAt(candidates: List<ExtractedNumericCandidate>, index: Int): Double? {
        return candidates.getOrNull(index)?.normalizedValue
    }

    private data class DistanceSelection(
        val pickupDistanceKm: Double?,
        val tripDistanceKm: Double?,
        val warnings: List<String> = emptyList()
    )
}
