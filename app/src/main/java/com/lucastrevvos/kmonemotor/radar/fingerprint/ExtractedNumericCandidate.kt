package com.lucastrevvos.kmonemotor.radar.fingerprint

data class ExtractedNumericCandidate(
    val raw: String,
    val normalizedValue: Double?,
    val unit: String?,
    val kind: String,
    val confidence: Int
)
