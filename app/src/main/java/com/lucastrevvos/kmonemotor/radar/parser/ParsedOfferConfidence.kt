package com.lucastrevvos.kmonemotor.radar.parser

data class ParsedOfferConfidence(
    val overall: Double,
    val price: Double,
    val route: Double,
    val platform: Double,
    val product: Double
)
