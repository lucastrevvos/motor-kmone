package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger

open class GenericOfferParser(
    protected val helpers: OfferParserHelpers = OfferParserHelpers()
) {
    open fun parse(input: OfferParserInput, platform: ParsedPlatform): ParsedOfferDraft {
        val warnings = mutableListOf<String>()
        val inferredPlatform = helpers.inferPlatform(input, preferredPlatform = platform)
        val product = helpers.extractGenericProduct(input)
        val paymentMethod = helpers.extractPaymentMethod(input)
        val passengerInfo = helpers.extractPassengerInfo(input)
        val price = helpers.selectMainPrice(input)
        val valuePerKm = helpers.selectValuePerKm(input)
        val routeSelection = helpers.selectRoute(input, warnings)
        val multiplier = helpers.extractMultiplier(input, inferredPlatform, warnings)
        val rating = helpers.extractRating(input)
        val tripDistance = routeSelection.tripDistanceKm ?: helpers.estimateTripDistance(price, valuePerKm, warnings)
        val confidence = helpers.buildConfidence(
            price = price,
            routeConfidence = routeSelection.routeConfidence,
            platform = inferredPlatform,
            product = product
        )
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_RESULT",
            "observationId" to input.fingerprint.observationId,
            "platform" to inferredPlatform,
            "product" to product,
            "price" to price?.value,
            "valuePerKm" to valuePerKm?.value
        )
        return ParsedOfferDraft(
            parserVersion = OfferParser.PARSER_VERSION,
            observationId = input.fingerprint.observationId,
            clusterId = input.clusterId,
            platform = inferredPlatform,
            product = product,
            paymentMethod = paymentMethod,
            price = price,
            valuePerKm = valuePerKm,
            pickupTimeMinutes = routeSelection.pickupTimeMinutes,
            pickupDistanceKm = routeSelection.pickupDistanceKm,
            tripTimeMinutes = routeSelection.tripTimeMinutes,
            tripDistanceKm = tripDistance,
            multiplier = multiplier,
            rating = rating,
            passengerInfo = passengerInfo,
            rawTextPreview = input.rawText.take(180),
            confidence = confidence,
            warnings = warnings,
            sanityStatus = ParsedOfferSanityStatus.VALID,
            sanityIssues = emptyList(),
            shouldBlockEconomicDecisionFuture = false,
            createdAtMs = input.createdAtMs
        )
    }
}
