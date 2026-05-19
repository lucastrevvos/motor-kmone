package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger

class NinetyNineOfferParser(
    helpers: OfferParserHelpers = OfferParserHelpers()
) : GenericOfferParser(helpers) {
    override fun parse(input: OfferParserInput, platform: ParsedPlatform): ParsedOfferDraft {
        val draft = super.parse(input, ParsedPlatform.NINETY_NINE)
        val product = helpers.extractNinetyNineProduct(input)
        val payment = helpers.extractPaymentMethod(input)
        val passengerInfo = helpers.mergeInfo(draft.passengerInfo, helpers.extractNinetyNinePassengerInfo(input))
        RadarLogger.i(
            "KM_V2_PARSER",
            "KM_V2_PARSER_PRODUCT_EXTRACTED",
            "observationId" to input.fingerprint.observationId,
            "product" to product
        )
        return draft.copy(
            product = product ?: draft.product,
            paymentMethod = payment ?: draft.paymentMethod,
            passengerInfo = passengerInfo,
            confidence = draft.confidence.copy(
                product = if (product != null) 0.85 else draft.confidence.product,
                overall = helpers.overallConfidence(
                    draft.confidence.price,
                    draft.confidence.route,
                    draft.confidence.platform,
                    if (product != null) 0.85 else draft.confidence.product
                )
            )
        )
    }
}
