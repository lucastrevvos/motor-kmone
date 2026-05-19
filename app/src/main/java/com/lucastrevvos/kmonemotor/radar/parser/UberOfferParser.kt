package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger

class UberOfferParser(
    helpers: OfferParserHelpers = OfferParserHelpers()
) : GenericOfferParser(helpers) {
    override fun parse(input: OfferParserInput, platform: ParsedPlatform): ParsedOfferDraft {
        val draft = super.parse(input, ParsedPlatform.UBER)
        val product = helpers.extractUberProduct(input)
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_PRODUCT_EXTRACTED",
            "observationId" to input.fingerprint.observationId,
            "product" to product
        )
        return draft.copy(
            product = product ?: draft.product,
            passengerInfo = helpers.mergeInfo(draft.passengerInfo, helpers.extractUberPassengerInfo(input)),
            confidence = draft.confidence.copy(
                product = if (product != null) 0.9 else draft.confidence.product,
                overall = helpers.overallConfidence(
                    draft.confidence.price,
                    draft.confidence.route,
                    draft.confidence.platform,
                    if (product != null) 0.9 else draft.confidence.product
                )
            )
        )
    }
}
