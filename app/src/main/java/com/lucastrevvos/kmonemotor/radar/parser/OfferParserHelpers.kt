package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextNormalizer
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.platform.PlatformInferenceEngine
import com.lucastrevvos.kmonemotor.radar.platform.PlatformInferenceInput
import kotlin.math.round

class OfferParserHelpers(
    private val normalizer: OfferTextNormalizer = OfferTextNormalizer(),
    private val platformInferenceEngine: PlatformInferenceEngine = PlatformInferenceEngine()
) {
    fun normalizeText(rawText: String): String = normalizer.normalize(rawText).normalizedText

    fun inferPlatform(input: OfferParserInput, preferredPlatform: ParsedPlatform = ParsedPlatform.UNKNOWN): ParsedPlatform {
        val preferredHint = when (preferredPlatform) {
            ParsedPlatform.UBER -> PlatformTextHint.UBER
            ParsedPlatform.NINETY_NINE -> PlatformTextHint.NINETY_NINE
            ParsedPlatform.UNKNOWN -> input.fingerprint.platformTextHint
        }
        val inference = platformInferenceEngine.infer(
            PlatformInferenceInput(
                rawText = input.rawText,
                normalizedText = input.normalizedText,
                triggerSource = input.triggerSource,
                currentPlatformHint = preferredHint
            )
        )
        val result = inference.platform.toParsedPlatform()
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_PLATFORM_INFERRED",
            "observationId" to input.fingerprint.observationId,
            "platform" to result,
            "triggerSource" to input.triggerSource,
            "reason" to inference.reason
        )
        return result
    }

    fun extractUberProduct(input: OfferParserInput): String? = extractCanonicalProduct(input.rawText, UBER_PRODUCT_LABELS)

    fun extractNinetyNineProduct(input: OfferParserInput): String? = extractCanonicalProduct(input.rawText, NINETY_NINE_PRODUCT_LABELS)

    fun extractGenericProduct(input: OfferParserInput): String? {
        return extractUberProduct(input) ?: extractNinetyNineProduct(input)
    }

    fun extractPaymentMethod(input: OfferParserInput): String? {
        val text = input.normalizedText
        val payment = when {
            "pagamento no app" in text -> "Pagamento no app"
            "dinheiro" in text -> "Dinheiro"
            "cartao" in text -> "Cartao"
            else -> null
        }
        if (payment != null) {
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_PARSER_PAYMENT_EXTRACTED",
                "observationId" to input.fingerprint.observationId,
                "paymentMethod" to payment
            )
        }
        return payment
    }

    fun extractPassengerInfo(input: OfferParserInput): String? {
        val found = PASSENGER_INFO_LABELS.filter { input.normalizedText.contains(it.first) }.map { it.second }
        val info = found.distinct().joinToString(", ").takeIf { it.isNotBlank() }
        if (info != null) {
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_PARSER_PASSENGER_INFO_EXTRACTED",
                "observationId" to input.fingerprint.observationId,
                "passengerInfo" to info
            )
        }
        return info
    }

    fun extractUberPassengerInfo(input: OfferParserInput): String? {
        val found = listOfNotNull(
            "verificado".takeIf { input.normalizedText.contains(it) }?.replaceFirstChar(Char::uppercase)
        )
        return found.joinToString(", ").takeIf { it.isNotBlank() }
    }

    fun extractNinetyNinePassengerInfo(input: OfferParserInput): String? = extractPassengerInfo(input)

    fun selectMainPrice(input: OfferParserInput): ParsedMoney? {
        val ninetyNineNegotiationContext = isNinetyNineNegotiationContext(input)
        val homeContext = ManualCropHomeContextDetector.inspect(
            rawText = input.rawText,
            normalizedText = input.normalizedText,
            triggerSource = input.triggerSource
        )
        if (homeContext.detected) {
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_MANUAL_CROP_HOME_CONTEXT_DETECTED",
                "triggerSource" to input.triggerSource,
                "matchedTerms" to homeContext.matchedTerms.joinToString(","),
                "hasOfferSignals" to homeContext.hasOfferSignals
            )
        }
        val candidatePool = if (homeContext.detected) {
            input.fingerprint.priceCandidates.filterNot { candidate ->
                val value = candidate.normalizedValue ?: return@filterNot false
                val rejected = ManualCropHomeContextDetector.isHomeGoalPrice(input.rawText, candidate.raw, value)
                if (rejected) {
                    RadarLogger.i(
                        "KM_V2_PARSER",
                        "KM_V2_PRICE_CANDIDATE_REJECTED",
                        "price" to value,
                        "reason" to "kmone_home_goal_context"
                    )
                }
                rejected
            }
        } else {
            input.fingerprint.priceCandidates
        }
        val uberProductCandidate = if (isUberLikeContext(input)) {
            selectUberPriceNearProduct(input, candidatePool)
        } else {
            null
        }
        val candidate = uberProductCandidate ?: if (ninetyNineNegotiationContext) {
            selectNinetyNinePrimaryPrice(input, candidatePool)
        } else {
            candidatePool
                .sortedByDescending { it.normalizedValue ?: Double.MIN_VALUE }
                .firstOrNull { (it.normalizedValue ?: 0.0) >= 5.0 }
                ?: candidatePool.maxByOrNull { it.normalizedValue ?: Double.MIN_VALUE }
        }
        val value = candidate?.normalizedValue ?: return null
        val confidence = when {
            candidate.raw.contains("r$", ignoreCase = true) && value >= 5.0 -> 0.95
            value >= 5.0 -> 0.75
            else -> 0.5
        }
        if (ninetyNineNegotiationContext) {
            val negotiationOptions = input.fingerprint.priceCandidates
                .dropWhile { it != candidate }
                .drop(1)
                .mapNotNull { it.normalizedValue }
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_99_PRICE_SELECTION",
                "allPrices" to input.fingerprint.priceCandidates.mapNotNull { it.normalizedValue },
                "selectedPrice" to value,
                "negotiationOptions" to negotiationOptions,
                "reason" to "first_primary_price_before_negotiation_options"
            )
        }
        if (uberProductCandidate != null) {
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_OFFER_PRICE_SELECTED",
                "price" to value,
                "reason" to if (homeContext.detected) {
                    "uber_price_near_product_after_home_context_filter"
                } else {
                    "uber_price_near_product"
                }
            )
        }
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_PRICE_SELECTED",
            "observationId" to input.fingerprint.observationId,
            "price" to value,
            "source" to candidate.raw
        )
        return ParsedMoney(
            value = value,
            sourceText = candidate.raw,
            confidence = confidence
        )
    }

    fun selectValuePerKm(input: OfferParserInput): ParsedMoney? {
        val candidate = input.fingerprint.valuePerKmCandidates.maxByOrNull { it.normalizedValue ?: Double.MIN_VALUE } ?: return null
        val value = candidate.normalizedValue ?: return null
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_VALUE_PER_KM_SELECTED",
            "observationId" to input.fingerprint.observationId,
            "valuePerKm" to value
        )
        return ParsedMoney(
            value = value,
            sourceText = candidate.raw,
            confidence = if (candidate.raw.contains("/km", ignoreCase = true)) 0.95 else 0.75
        )
    }

    fun selectRoute(input: OfferParserInput, warnings: MutableList<String>): RouteSelection {
        val repairedRouteText = normalizer.repair(input.rawText, emitUberRouteDiagnostics = true)
        val normalizedRouteText = normalizer.normalize(repairedRouteText).normalizedText
        val pairs = ROUTE_PAIR_REGEX.findAll(normalizedRouteText).map { match ->
            val time = parseNumber(match.groupValues[1])
            val distance = normalizeDistanceKm(match.groupValues[2], match.groupValues[3])
            val source = match.value
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_PARSER_ROUTE_PAIR_EXTRACTED",
                "observationId" to input.fingerprint.observationId,
                "timeMinutes" to time,
                "distanceKm" to distance,
                "source" to source
            )
            RoutePair(
                timeMinutes = ParsedNumber(time, "min", source, 0.9),
                distanceKm = ParsedNumber(distance, "km", source, 0.9)
            )
        }.toList()
        if (pairs.isEmpty()) {
            when {
                BROKEN_ROUTE_PAIR_REGEX.containsMatchIn(normalizedRouteText) -> {
                    warnings += "low_confidence_route"
                    RadarLogger.i(
                        "KM_V2_PARSER",
                        "KM_V2_PARSER_ROUTE_PAIR_REJECTED",
                        "observationId" to input.fingerprint.observationId,
                        "reason" to "incomplete_pair"
                    )
                }
                LOOSE_TIME_OR_DISTANCE_REGEX.containsMatchIn(normalizedRouteText) -> {
                    warnings += "low_confidence_route"
                    RadarLogger.i(
                        "KM_V2_PARSER",
                        "KM_V2_PARSER_ROUTE_PAIR_REJECTED",
                        "observationId" to input.fingerprint.observationId,
                        "reason" to "loose_values_not_pair"
                    )
                }
            }
        }
        if (pairs.size > 2) {
            warnings += "extra_route_pairs_detected"
        }
        var selected = when (pairs.size) {
            0 -> RouteSelection(routeConfidence = 0.2)
            1 -> RouteSelection(
                pickupTimeMinutes = pairs[0].timeMinutes,
                pickupDistanceKm = pairs[0].distanceKm,
                routeConfidence = 0.45
            )
            else -> RouteSelection(
                pickupTimeMinutes = pairs[0].timeMinutes,
                pickupDistanceKm = pairs[0].distanceKm,
                tripTimeMinutes = pairs[1].timeMinutes,
                tripDistanceKm = pairs[1].distanceKm,
                routeConfidence = 0.9
            )
        }
        if (pairs.size == 1) {
            maybeAttachStandaloneUberTripDistance(input, selected)?.let { selected = it }
        }
        val correctedByExplicitValuePerKm = if (isNinetyNineLikeContext(input) && pairs.size >= 2) {
            maybeResolveNinetyNineRouteByExplicitValuePerKm(
                input = input,
                warnings = warnings,
                explicitRoutePairs = pairs
            )
        } else {
            null
        }
        if (correctedByExplicitValuePerKm != null) {
            selected = correctedByExplicitValuePerKm
        }
        if (pairs.size <= 1 && correctedByExplicitValuePerKm == null) {
            warnings += "low_confidence_route"
        }
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_ROUTE_CONFIDENCE_UPDATED",
            "observationId" to input.fingerprint.observationId,
            "routeConfidence" to selected.routeConfidence
        )
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_ROUTE_SELECTED",
            "observationId" to input.fingerprint.observationId,
            "pickupTimeMinutes" to selected.pickupTimeMinutes?.value,
            "pickupDistanceKm" to selected.pickupDistanceKm?.value,
            "tripTimeMinutes" to selected.tripTimeMinutes?.value,
            "tripDistanceKm" to selected.tripDistanceKm?.value
        )
        return selected
    }

    fun estimateTripDistance(price: ParsedMoney?, valuePerKm: ParsedMoney?, warnings: MutableList<String>): ParsedNumber? {
        if (price == null || valuePerKm == null || valuePerKm.value <= 0.0) {
            return null
        }
        val estimated = round((price.value / valuePerKm.value) * 100.0) / 100.0
        warnings += "trip_distance_estimated_from_value_per_km"
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_DISTANCE_ESTIMATED_FROM_VALUE_PER_KM",
            "price" to price.value,
            "valuePerKm" to valuePerKm.value,
            "tripDistanceKm" to estimated
        )
        return ParsedNumber(
            value = estimated,
            unit = "km",
            sourceText = "${price.sourceText}/${valuePerKm.sourceText}",
            confidence = 0.55
        )
    }

    private fun isNinetyNineNegotiationContext(input: OfferParserInput): Boolean {
        val text = input.normalizedText
        return isNinetyNineLikeContext(input) &&
            (text.contains("negocia") || text.contains("aceitar por"))
    }

    private fun selectNinetyNinePrimaryPrice(
        input: OfferParserInput,
        candidates: List<ExtractedNumericCandidate>
    ): ExtractedNumericCandidate? {
        val text = input.rawText.lowercase()
        val acceptIndex = text.indexOf("aceitar por")
        if (acceptIndex >= 0) {
            candidates
                .mapNotNull { candidate ->
                    val value = candidate.normalizedValue ?: return@mapNotNull null
                    if (value < 5.0) return@mapNotNull null
                    val index = findPriceIndex(text, candidate.raw, value, startIndex = acceptIndex)
                    if (index >= acceptIndex) candidate to index else null
                }
                .minByOrNull { it.second }
                ?.first
                ?.let { return it }
        }
        return candidates
            .firstOrNull { (it.normalizedValue ?: 0.0) >= 5.0 }
            ?: candidates.firstOrNull()
    }

    private fun isUberLikeContext(input: OfferParserInput): Boolean {
        return input.fingerprint.platformTextHint == PlatformTextHint.UBER ||
            UBER_PRODUCT_LABELS.any { it.first.containsMatchIn(input.rawText) } ||
            UBER_PRODUCT_LABELS.any { it.first.containsMatchIn(input.normalizedText) }
    }

    private fun selectUberPriceNearProduct(
        input: OfferParserInput,
        candidates: List<ExtractedNumericCandidate>
    ): ExtractedNumericCandidate? {
        if (candidates.isEmpty()) {
            return null
        }
        val text = input.rawText.lowercase()
        val productMatches = UBER_PRODUCT_LABELS
            .flatMap { it.first.findAll(input.rawText).map { match -> match.range.first } }
            .ifEmpty {
                UBER_PRODUCT_LABELS.flatMap { it.first.findAll(input.normalizedText).map { match -> match.range.first } }
            }
        if (productMatches.isEmpty()) {
            return null
        }
        return candidates
            .mapNotNull { candidate ->
                val value = candidate.normalizedValue ?: return@mapNotNull null
                if (value < 5.0) {
                    return@mapNotNull null
                }
                val priceIndex = findPriceIndex(text, candidate.raw, value)
                if (priceIndex < 0) {
                    return@mapNotNull null
                }
                val bestDistance = productMatches
                    .map { productIndex -> priceIndex - productIndex }
                    .filter { it >= 0 }
                    .minOrNull()
                    ?: return@mapNotNull null
                if (bestDistance > 80) {
                    return@mapNotNull null
                }
                candidate to bestDistance
            }
            .minWithOrNull(compareBy<Pair<ExtractedNumericCandidate, Int>> { it.second })
            ?.first
    }

    private fun findPriceIndex(text: String, candidateRaw: String, value: Double, startIndex: Int = 0): Int {
        val direct = text.indexOf(candidateRaw.lowercase(), startIndex.coerceAtLeast(0))
        if (direct >= 0) {
            return direct
        }
        val decimal = String.format(java.util.Locale.US, "%.2f", value)
        val comma = decimal.replace(".", ",")
        val safeStart = startIndex.coerceAtLeast(0)
        return text.indexOf("r$ $comma", safeStart).takeIf { it >= 0 }
            ?: text.indexOf("r$$comma", safeStart).takeIf { it >= 0 }
            ?: text.indexOf(comma, safeStart)
    }

    private fun isNinetyNineLikeContext(input: OfferParserInput): Boolean {
        val text = input.normalizedText
        return input.fingerprint.platformTextHint == PlatformTextHint.NINETY_NINE ||
            text.contains("r$/km") ||
            text.contains("cpf") ||
            text.contains("cartao verif") ||
            text.contains("cartão verif") ||
            text.contains("dinheiro") ||
            text.contains("pagamento no app") ||
            text.contains("taxa de deslocamento") ||
            text.contains("corrida longa") ||
            text.contains("negocia")
    }

    private fun maybeResolveNinetyNineRouteByExplicitValuePerKm(
        input: OfferParserInput,
        warnings: MutableList<String>,
        explicitRoutePairs: List<RoutePair>
    ): RouteSelection? {
        val price = selectMainPrice(input)?.value ?: return null
        val explicitValuePerKm = selectValuePerKm(input)?.value ?: return null
        if (explicitValuePerKm <= 0.0) return null
        val inferredTotal = price / explicitValuePerKm
        if (explicitRoutePairs.size >= 2) {
            val explicitPickup = explicitRoutePairs[0]
            val explicitTrip = explicitRoutePairs[1]
            val explicitSegmentTotal = explicitPickup.distanceKm.value + explicitTrip.distanceKm.value
            val explicitDelta = kotlin.math.abs(explicitSegmentTotal - inferredTotal)
            if (explicitDelta <= 0.35 || explicitDelta / inferredTotal <= 0.12) {
                return RouteSelection(
                    pickupTimeMinutes = explicitPickup.timeMinutes,
                    pickupDistanceKm = explicitPickup.distanceKm,
                    tripTimeMinutes = explicitTrip.timeMinutes,
                    tripDistanceKm = explicitTrip.distanceKm,
                    routeConfidence = 0.9
                )
            }
            return null
        }
        val normalizedDistances = input.fingerprint.distanceCandidates.mapNotNull { candidate ->
            if (candidate.raw.contains("min", ignoreCase = true)) return@mapNotNull null
            val normalized = when (candidate.unit) {
                "m" -> (candidate.normalizedValue ?: return@mapNotNull null) / 1000.0
                else -> candidate.normalizedValue
            } ?: return@mapNotNull null
            Triple(candidate.raw, normalized, candidate.unit == "m")
        }
        val kmCandidates = normalizedDistances
            .filter { (_, normalized, isMeter) -> !isMeter && normalized >= 0.3 }
            .map { it.second }
            .distinct()
            .sorted()
        if (kmCandidates.size < 2) {
            return null
        }
        val pickup = kmCandidates.first()
        val trip = kmCandidates.last()
        val sum = pickup + trip
        val delta = kotlin.math.abs(sum - inferredTotal)
        if (delta > 0.35 && delta / inferredTotal > 0.12) {
            return null
        }
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_99_ROUTE_SELECTION_BY_EXPLICIT_VALUE_PER_KM",
            "price" to price,
            "explicitValuePerKm" to explicitValuePerKm,
            "inferredTotalKm" to inferredTotal,
            "candidateDistances" to kmCandidates,
            "selectedPickupKm" to pickup,
            "selectedTripKm" to trip,
            "selectedTotalKm" to sum,
            "delta" to delta,
            "reason" to "pair_sum_closest_to_explicit_value_per_km"
        )
        warnings.remove("low_confidence_route")
        return RouteSelection(
            pickupDistanceKm = ParsedNumber(pickup, "km", "explicit_value_per_km_route_selection", 0.9),
            tripDistanceKm = ParsedNumber(trip, "km", "explicit_value_per_km_route_selection", 0.9),
            routeConfidence = 0.9
        )
    }

    private fun maybeAttachStandaloneUberTripDistance(
        input: OfferParserInput,
        selection: RouteSelection
    ): RouteSelection? {
        if (
            selection.pickupDistanceKm == null ||
            selection.tripDistanceKm != null ||
            input.fingerprint.platformTextHint == PlatformTextHint.NINETY_NINE
        ) {
            return null
        }
        val pickupKm = selection.pickupDistanceKm.value
        val kmCandidates = input.fingerprint.distanceCandidates.mapNotNull { candidate ->
            val normalized = when (candidate.unit) {
                "m" -> (candidate.normalizedValue ?: return@mapNotNull null) / 1000.0
                else -> candidate.normalizedValue
            } ?: return@mapNotNull null
            Triple(candidate.raw, normalized, candidate.unit == "m")
        }
        kmCandidates
            .filter { (_, normalized, isMeter) -> isMeter && normalized < 0.05 && kmCandidates.any { !it.third && it.second >= 1.0 } }
            .forEach { (raw, _, _) ->
                RadarLogger.i(
                    "KM_V2_ROUTE",
                    "KM_V2_UBER_ROUTE_METRIC_CANDIDATE_REJECTED",
                    "candidate" to raw,
                    "reason" to "tiny_meter_noise_near_valid_km_route"
                )
            }
        val standaloneTripKm = kmCandidates
            .filter { (_, normalized, isMeter) ->
                !isMeter &&
                    normalized >= 1.0 &&
                    kotlin.math.abs(normalized - pickupKm) > 0.2
            }
            .maxByOrNull { it.second }
            ?.second
            ?: return null
        return selection.copy(
            tripDistanceKm = ParsedNumber(
                value = standaloneTripKm,
                unit = "km",
                sourceText = "standalone_uber_trip_distance_candidate",
                confidence = 0.7
            ),
            routeConfidence = maxOf(selection.routeConfidence, 0.75)
        )
    }

    fun extractMultiplier(input: OfferParserInput, platform: ParsedPlatform, warnings: MutableList<String>): ParsedNumber? {
        if (platform == ParsedPlatform.UBER || isUberLikeUnknown(input, platform)) {
            findMultiplierCandidate(input.rawText)?.let { candidate ->
                RadarLogger.i(
                    "KM_V2_PARSER",
                    "KM_V2_PARSER_MULTIPLIER_REJECTED",
                    "observationId" to input.fingerprint.observationId,
                    "reason" to if (platform == ParsedPlatform.UBER) "uber_no_multiplier" else "uber_like_unknown_no_multiplier",
                    "platform" to platform,
                    "candidate" to candidate
                )
            }
            return null
        }
        if (platform != ParsedPlatform.NINETY_NINE && !isNinetyNineLikeUnknown(input, platform)) {
            return null
        }
        STRICT_MULTIPLIER_RANGE_REGEX.find(input.rawText)?.let { match ->
            val values = match.groupValues.drop(1).filter { it.isNotBlank() }.map(::parseNumber)
            val first = values.firstOrNull() ?: return@let null
            val second = values.getOrNull(1) ?: first
            warnings += "multiplier_range_detected"
            val selected = maxOf(first, second)
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_PARSER_MULTIPLIER_EXTRACTED",
                "observationId" to input.fingerprint.observationId,
                "platform" to platform,
                "multiplier" to selected,
                "source" to match.value
            )
            return ParsedNumber(selected, "x", match.value, 0.7)
        }
        STRICT_MULTIPLIER_SINGLE_REGEX.find(input.rawText)?.let { match ->
            val rawValue = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return@let null
            val value = parseNumber(rawValue)
            val source = match.value
            if (!source.contains("x", ignoreCase = true)) {
                warnings += "false_multiplier_context"
                RadarLogger.i(
                    "KM_V2_PARSER",
                    "KM_V2_PARSER_MULTIPLIER_REJECTED",
                    "observationId" to input.fingerprint.observationId,
                    "reason" to "missing_x_context",
                    "source" to source
                )
                return null
            }
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_PARSER_MULTIPLIER_EXTRACTED",
                "observationId" to input.fingerprint.observationId,
                "platform" to platform,
                "multiplier" to value,
                "source" to source
            )
            return ParsedNumber(value, "x", source, 0.85)
        }
        return null
    }

    fun extractRating(input: OfferParserInput): ParsedNumber? {
        val match = RATING_REGEX.find(input.rawText) ?: return null
        return ParsedNumber(
            value = parseNumber(match.groupValues[1]),
            unit = "rating",
            sourceText = match.value,
            confidence = 0.6
        )
    }

    fun buildConfidence(
        price: ParsedMoney?,
        routeConfidence: Double,
        platform: ParsedPlatform,
        product: String?
    ): ParsedOfferConfidence {
        val priceConfidence = price?.confidence ?: 0.0
        val platformConfidence = when (platform) {
            ParsedPlatform.UNKNOWN -> 0.2
            else -> 0.9
        }
        val productConfidence = if (product != null) 0.85 else 0.2
        return ParsedOfferConfidence(
            overall = overallConfidence(priceConfidence, routeConfidence, platformConfidence, productConfidence),
            price = priceConfidence,
            route = routeConfidence,
            platform = platformConfidence,
            product = productConfidence
        )
    }

    fun overallConfidence(price: Double, route: Double, platform: Double, product: Double): Double {
        return round(((price + route + platform + product) / 4.0) * 100.0) / 100.0
    }

    fun mergeInfo(primary: String?, secondary: String?): String? {
        return listOfNotNull(primary, secondary)
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
    }

    private fun extractCanonicalProduct(rawText: String, mapping: List<Pair<Regex, String>>): String? {
        return mapping.firstOrNull { it.first.containsMatchIn(rawText) }?.second
    }

    private fun parseNumber(raw: String): Double {
        val trimmed = raw.trim()
        val normalized = when {
            trimmed.contains(",") && trimmed.contains(".") -> trimmed.replace(".", "").replace(",", ".")
            trimmed.contains(",") -> trimmed.replace(",", ".")
            else -> trimmed
        }
        return normalized.toDoubleOrNull() ?: 0.0
    }

    private fun normalizeDistanceKm(rawValue: String, rawUnit: String): Double {
        val value = parseNumber(rawValue)
        return if (rawUnit.equals("m", ignoreCase = true)) value / 1000.0 else value
    }

    private fun isUberLikeUnknown(input: OfferParserInput, platform: ParsedPlatform): Boolean {
        if (platform != ParsedPlatform.UNKNOWN) {
            return false
        }
        return UBER_LIKE_UNKNOWN_PATTERNS.any { it.containsMatchIn(input.rawText) }
    }

    private fun isNinetyNineLikeUnknown(input: OfferParserInput, platform: ParsedPlatform): Boolean {
        if (platform != ParsedPlatform.UNKNOWN) {
            return false
        }
        return NINETY_NINE_LIKE_UNKNOWN_PATTERNS.any { it.containsMatchIn(input.rawText) }
    }

    private fun findMultiplierCandidate(rawText: String): String? {
        return STRICT_MULTIPLIER_RANGE_REGEX.find(rawText)?.value
            ?: STRICT_MULTIPLIER_SINGLE_REGEX.find(rawText)?.value
    }

    private fun PlatformTextHint.toParsedPlatform(): ParsedPlatform {
        return when (this) {
            PlatformTextHint.UBER -> ParsedPlatform.UBER
            PlatformTextHint.NINETY_NINE -> ParsedPlatform.NINETY_NINE
            PlatformTextHint.UNKNOWN -> ParsedPlatform.UNKNOWN
        }
    }

    data class RouteSelection(
        val pickupTimeMinutes: ParsedNumber? = null,
        val pickupDistanceKm: ParsedNumber? = null,
        val tripTimeMinutes: ParsedNumber? = null,
        val tripDistanceKm: ParsedNumber? = null,
        val routeConfidence: Double = 0.2
    )

    private data class RoutePair(
        val timeMinutes: ParsedNumber,
        val distanceKm: ParsedNumber
    )

    companion object {
        private val ROUTE_PAIR_REGEX = Regex("(\\d+[\\d,.]*)\\s*(?:min|minuto|minutos)\\s*\\((\\d+[\\d,.]*)\\s*(m|km)\\)", RegexOption.IGNORE_CASE)
        private val MULTIPLIER_RANGE_REGEX = Regex("\\b(\\d+[\\d,.]*)\\s*[xX]\\s*[-–]\\s*(\\d+[\\d,.]*)\\s*[xX]?\\b|\\b[xX]\\b\\s*(\\d+[\\d,.]*)\\s*[-–]\\s*(\\d+[\\d,.]*)", RegexOption.IGNORE_CASE)
        private val MULTIPLIER_SINGLE_REGEX = Regex("(?i)(?:pre[cç]o\\s*)?\\b[xX]\\b\\s*(\\d+[\\d,.]*)|\\b(\\d+[\\d,.]*)\\s*[xX]\\b", RegexOption.IGNORE_CASE)
        private val RATING_REGEX = Regex("\\*\\s*(\\d+[\\d,.]*)")
        private val STRICT_MULTIPLIER_RANGE_REGEX = Regex(
            "(?i)(?:\\b(\\d+[\\d,.]*)\\s*[xX](?=\\s|$)\\s*[-–]\\s*(\\d+[\\d,.]*)\\s*(?:[xX](?=\\s|$))?)|(?:(?:^|\\s|pre[cç]o\\s+)[xX]\\s*(\\d+[\\d,.]*)\\s*[-–]\\s*(\\d+[\\d,.]*))"
        )
        private val STRICT_MULTIPLIER_SINGLE_REGEX = Regex(
            "(?i)(?:(?:^|\\s|pre[cç]o\\s+)[xX]\\s*(\\d+[\\d,.]*))|(?:\\b(\\d+[\\d,.]*)\\s*[xX](?=\\s|$))"
        )

        private val BROKEN_ROUTE_PAIR_REGEX = Regex("[A-Za-z0-9]+\\s*(?:min|minuto|minutos)\\s*\\(\\s*(?:km|m)?\\s*\\)", RegexOption.IGNORE_CASE)
        private val LOOSE_TIME_OR_DISTANCE_REGEX = Regex("(\\b\\d+[\\d,.]*\\s*(?:min|minuto|minutos)\\b|\\b\\d+[\\d,.]*\\s*(?:km|m)\\b)", RegexOption.IGNORE_CASE)
        private val UBER_LIKE_UNKNOWN_PATTERNS = listOf(
            Regex("uberx", RegexOption.IGNORE_CASE),
            Regex("\\buber\\b", RegexOption.IGNORE_CASE),
            Regex("exclusivo", RegexOption.IGNORE_CASE),
            Regex("verificado", RegexOption.IGNORE_CASE)
        )
        private val NINETY_NINE_LIKE_UNKNOWN_PATTERNS = listOf(
            Regex("pagamento no app", RegexOption.IGNORE_CASE),
            Regex("dinheiro", RegexOption.IGNORE_CASE),
            Regex("taxa de deslocamento", RegexOption.IGNORE_CASE),
            Regex("perfil premium", RegexOption.IGNORE_CASE),
            Regex("corrida longa", RegexOption.IGNORE_CASE),
            Regex("pre[cÃ§]o\\s*x", RegexOption.IGNORE_CASE),
            Regex("\\b99\\b", RegexOption.IGNORE_CASE)
        )
        private val UBER_PRODUCT_PATTERNS = listOf(
            Regex("uberx exclusivo", RegexOption.IGNORE_CASE),
            Regex("uberx", RegexOption.IGNORE_CASE),
            Regex("uber comfort", RegexOption.IGNORE_CASE),
            Regex("uber black", RegexOption.IGNORE_CASE),
            Regex("uber flash", RegexOption.IGNORE_CASE)
        )
        private val NINETY_NINE_TEXT_PATTERNS = listOf(
            Regex("pagamento no app", RegexOption.IGNORE_CASE),
            Regex("perfil premium", RegexOption.IGNORE_CASE),
            Regex("99pop", RegexOption.IGNORE_CASE),
            Regex("preco x", RegexOption.IGNORE_CASE)
        )
        private val UBER_PRODUCT_LABELS = listOf(
            Regex("uberx exclusivo", RegexOption.IGNORE_CASE) to "UberX Exclusivo",
            Regex("uberx", RegexOption.IGNORE_CASE) to "UberX",
            Regex("uber comfort|comfort", RegexOption.IGNORE_CASE) to "Uber Comfort",
            Regex("uber black|black", RegexOption.IGNORE_CASE) to "Uber Black",
            Regex("uber flash|flash", RegexOption.IGNORE_CASE) to "Uber Flash",
            Regex("priority|prioridade", RegexOption.IGNORE_CASE) to "Prioridade"
        )
        private val NINETY_NINE_PRODUCT_LABELS = listOf(
            Regex("99pop", RegexOption.IGNORE_CASE) to "99Pop",
            Regex("pop expresso", RegexOption.IGNORE_CASE) to "Pop Expresso",
            Regex("\\bpop\\b", RegexOption.IGNORE_CASE) to "Pop",
            Regex("comfort", RegexOption.IGNORE_CASE) to "Comfort",
            Regex("entrega", RegexOption.IGNORE_CASE) to "Entrega",
            Regex("perfil premium", RegexOption.IGNORE_CASE) to "Perfil Premium",
            Regex("corrida longa", RegexOption.IGNORE_CASE) to "Corrida longa"
        )
        private val PASSENGER_INFO_LABELS = listOf(
            "perfil premium" to "Perfil Premium",
            "passageiro novo" to "Passageiro novo",
            "corrida longa" to "Corrida longa",
            "verificado" to "Verificado"
        )
    }
}
