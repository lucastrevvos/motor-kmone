package com.lucastrevvos.kmonemotor.radar.fingerprint

data class NormalizedOcrText(
    val originalText: String,
    val normalizedText: String,
    val lines: List<String>,
    val tokens: List<String>
)
