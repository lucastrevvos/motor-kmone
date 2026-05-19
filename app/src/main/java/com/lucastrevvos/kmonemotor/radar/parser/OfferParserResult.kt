package com.lucastrevvos.kmonemotor.radar.parser

data class OfferParserResult(
    val status: String,
    val reason: String,
    val draft: ParsedOfferDraft? = null,
    val dedupeToParserMs: Long? = null,
    val parserDurationMs: Long? = null
)
