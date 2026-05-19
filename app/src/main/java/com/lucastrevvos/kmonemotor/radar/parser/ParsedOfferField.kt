package com.lucastrevvos.kmonemotor.radar.parser

data class ParsedMoney(
    val value: Double,
    val currency: String = "BRL",
    val sourceText: String? = null,
    val confidence: Double
)

data class ParsedNumber(
    val value: Double,
    val unit: String?,
    val sourceText: String? = null,
    val confidence: Double
)
